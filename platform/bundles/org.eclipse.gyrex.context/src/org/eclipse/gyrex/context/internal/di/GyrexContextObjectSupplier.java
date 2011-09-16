/*******************************************************************************
 * Copyright (c) 2010 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.context.internal.di;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;

import org.eclipse.gyrex.common.internal.services.IServiceProxyChangeListener;
import org.eclipse.gyrex.common.internal.services.IServiceProxyDisposalListener;
import org.eclipse.gyrex.common.internal.services.ServiceProxy;
import org.eclipse.gyrex.common.services.IServiceProxy;
import org.eclipse.gyrex.common.services.ServiceNotAvailableException;
import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.context.internal.ContextDebug;
import org.eclipse.gyrex.context.internal.GyrexContextImpl;
import org.eclipse.gyrex.context.internal.IContextDisposalListener;

import org.eclipse.e4.core.di.IInjector;
import org.eclipse.e4.core.di.suppliers.IObjectDescriptor;
import org.eclipse.e4.core.di.suppliers.IRequestor;
import org.eclipse.e4.core.di.suppliers.PrimaryObjectSupplier;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Gyrex object supplier for the e4 injector.
 */
@SuppressWarnings("restriction")
public class GyrexContextObjectSupplier extends PrimaryObjectSupplier {

	/** executes re-injection on service proxy disposal */
	private static final class ReinjectOnDisposal implements IServiceProxyDisposalListener {
		private final IRequestor requestor;

		private ReinjectOnDisposal(final IRequestor requestor) {
			this.requestor = requestor;
		}

		@Override
		public void disposed(final IServiceProxy<?> proxy) {
			if (!requestor.isValid()) {
				return; // ignore
			}
			if (ContextDebug.injection) {
				LOG.debug("Service proxy ({}) disposed, re-injecting ({})", proxy, requestor);
			}
			requestor.resolveArguments(false);
			requestor.execute();
		}
	}

	/** executes re-injection on service changes */
	private static final class ReinjectOnUpdate implements IServiceProxyChangeListener {
		private final IRequestor requestor;

		private ReinjectOnUpdate(final IRequestor requestor) {
			this.requestor = requestor;
		}

		@Override
		public boolean serviceChanged(final IServiceProxy<?> proxy) {
			if (!requestor.isValid()) {
				return false; // remove this listener
			}
			if (ContextDebug.injection) {
				LOG.debug("Service proxy ({}) changed, re-injecting ({})", proxy, requestor);
			}
			requestor.resolveArguments(false);
			requestor.execute();
			return true;
		}
	}

	/**
	 * allows to enable dynamic injection (not recommended at this point, see
	 * http://dev.eclipse.org/mhonarc/lists/e4-dev/msg05749.html)
	 */
	public static boolean dynamicInjectionEnabled = Boolean.getBoolean("gyrex.context.dynamicInjection");

	/** logger */
	private static final Logger LOG = LoggerFactory.getLogger(GyrexContextObjectSupplier.class);

	/** associated context */
	private final GyrexContextImpl context;

	/**
	 * Creates a new instance.
	 * 
	 * @param context
	 */
	public GyrexContextObjectSupplier(final GyrexContextImpl context) {
		this.context = context;
	}

	@Override
	public void get(final IObjectDescriptor[] descriptors, final Object[] actualValues, final IRequestor requestor, final boolean initial, final boolean track, final boolean group) {
		// process descriptors
		for (int i = 0; i < descriptors.length; i++) {
			// only fill in missing values
			if (actualValues[i] != IInjector.NOT_A_VALUE) {
				continue;
			}

			final Class<?> key = getKey(descriptors[i]);
			if (IRuntimeContext.class.equals(key)) {
				// inject the handle to the underlying context
				actualValues[i] = context.getHandle();
			} else if (null != key) {
				// find a context object
				final Object value = context.get(key);
				if (null != value) {
					actualValues[i] = value;
				} else {
					// the context does not contain a context object
					// however, there might still be a service available
					// that the requestor is able to use (in the context)
					// thus, get the bundle context of the requestor
					final BundleContext bundleContext = getBundleContext(requestor);
					if (null != bundleContext) {
						// check if we have a collection
						final Class<?> serviceInterface;
						final boolean collectionOfServices;
						if (Collection.class.isAssignableFrom(key)) {
							serviceInterface = getCollectionElementType(descriptors[i]);
							collectionOfServices = true;
						} else {
							collectionOfServices = false;
							serviceInterface = key;
						}

						// check if it's possible to get a service
						if (null != bundleContext.getServiceReference(serviceInterface)) {
							// track service
							final IServiceProxy<?> proxy = context.getServiceLocator(bundleContext).trackService(serviceInterface);
							if (collectionOfServices) {
								// inject read-only collection which contains service references
								actualValues[i] = proxy.getServices();

								// log message
								if (ContextDebug.injection) {
									LOG.debug("Injected service collection ({}).", proxy);
								}
							} else {
								if (track && dynamicInjectionEnabled) {
									// try to inject actual service object because we can update dynamically
									try {
										actualValues[i] = proxy.getService();

										// dynamic injection was successful
										if (ContextDebug.injection) {
											LOG.debug("Injected real service instance ({}).", proxy);
										}

										// add a listener that updates the injection whenever the service changes
										((ServiceProxy<?>) proxy).addChangeListener(new ReinjectOnUpdate(requestor));
										// add a listener that updates the injection whenever the proxy goes
										((ServiceProxy<?>) proxy).addDisposalListener(new ReinjectOnDisposal(requestor));
									} catch (final ServiceNotAvailableException e) {
										if (ContextDebug.injection) {
											LOG.debug("Service not available ({}), falling back to proxy.", proxy);
										}
										// fallback to proxy
										actualValues[i] = proxy.getProxy();
									}
								} else {
									// inject proxy because services may come and go at any time
									actualValues[i] = proxy.getProxy();
								}
							}
						}
					}
				}
			}
		}

		// hook dispose listener (we just support disposals at the moment)
		// TODO: investigate support for individual value changes
		// FIXME: this may also be subjected to memory issues (too many listeners)
		if (track) {
			context.addDisposable(new IContextDisposalListener() {
				@Override
				public void contextDisposed(final IRuntimeContext handle) {
					if (requestor.isValid()) {
						requestor.disposed(GyrexContextObjectSupplier.this);
					}
				}
			});
		}
	}

	private BundleContext getBundleContext(final IRequestor requestor) {
		Bundle bundle = null;

		final Object requestingObject = requestor.getRequestingObject();
		if (null != requestingObject) {
			bundle = FrameworkUtil.getBundle(requestingObject.getClass());
		}

		if (null == bundle) {
			// ConstructorRequestor
			// we are very bad and use reflection until there is API
			try {
				final Field field = requestor.getClass().getDeclaredField("constructor");
				if (!field.isAccessible()) {
					field.setAccessible(true);
				}
				bundle = FrameworkUtil.getBundle(((Constructor<?>) field.get(requestor)).getDeclaringClass());
			} catch (final Exception e) {
				LOG.debug("exception accessing data in requestor {}", requestor, e);
			}
		}

		if (null != bundle) {
			return bundle.getBundleContext();
		}

		// give up
		return null;
	}

	private Class<?> getClass(final Type type) {
		if (type instanceof Class<?>) {
			return (Class<?>) type;
		} else if (type instanceof ParameterizedType) {
			try {
				return (Class<?>) ((ParameterizedType) type).getRawType();
			} catch (final Exception ignored) {
				if (ContextDebug.injection) {
					LOG.debug("Unable to cast raw type of ParameterizedType ({}) to Class.", type, ignored);
				}
			}
		}
		return null;
	}

	private Class<?> getCollectionElementType(final IObjectDescriptor descriptor) {
		// assume that the element is a ParameterizedType
		final Type elementType = descriptor.getDesiredType();
		if (elementType instanceof ParameterizedType) {
			final Type[] typeArguments = ((ParameterizedType) elementType).getActualTypeArguments();
			// Collections can only be parameterized with one argument
			if (typeArguments.length == 1) {
				return getClass(typeArguments[0]);
			}
		}

		if (ContextDebug.injection) {
			LOG.debug("Unable to detect collection element type for object descriptor ({}).", descriptor);
		}
		return null;
	}

	private Class<?> getKey(final IObjectDescriptor descriptor) {
		return getClass(descriptor.getDesiredType());
	}

	@Override
	public void pauseRecording() {
		// no-op
	}

	@Override
	public void resumeRecoding() {
		// no-op
	}

}
