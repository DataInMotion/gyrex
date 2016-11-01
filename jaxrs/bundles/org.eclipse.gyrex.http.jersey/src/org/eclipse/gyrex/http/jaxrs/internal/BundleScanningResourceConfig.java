/**
 * Copyright (c) 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 */
package org.eclipse.gyrex.http.jaxrs.internal;

import java.util.Set;

import javax.ws.rs.Path;
import javax.ws.rs.ext.Provider;

import org.eclipse.gyrex.common.scanner.BundleAnnotatedClassScanner;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Jersey resource configuration which scans a bundle for annotated classes.
 */
public class BundleScanningResourceConfig extends ResourceConfig {

	private static final Logger LOG = LoggerFactory.getLogger(BundleScanningResourceConfig.class);

	private final Bundle bundle;

	/**
	 * Creates a new instance.
	 */
	public BundleScanningResourceConfig(final Bundle bundle) {
		this.bundle = bundle;

		final BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
		if (null == bundleWiring) {
			throw new IllegalStateException(String.format("No wiring available for bundle '%s'", bundle));
		}

		final ClassLoader loader = bundleWiring.getClassLoader();
		if (null == loader) {
			throw new IllegalStateException(String.format("No class loader available for bundle '%s'", bundle));
		}

		setClassLoader(loader);

//		scan();
	}

	private void scan() {
		if (JaxRsDebug.resourceDiscovery) {
			LOG.debug("Scanning bundle '{}' for annotated classes.", bundle);
		}

		final BundleAnnotatedClassScanner scanner = new BundleAnnotatedClassScanner(bundle, Path.class);

		final Set<Class<?>> annotatedClasses = scanner.scan();
		if (annotatedClasses.isEmpty()) {
			LOG.warn("No JAX-RS annotated classed found in bundle '{}'.", bundle);
		} else {
			for (final Class<?> annotatedClass : annotatedClasses) {
				if (JaxRsDebug.resourceDiscovery) {
					if (annotatedClass.isAnnotationPresent(Path.class)) {
						LOG.debug("Found resource: {}", annotatedClass.getName());
					} else if (annotatedClass.isAnnotationPresent(Provider.class)) {
						LOG.debug("Found provider: {}", annotatedClass.getName());
					}
				}
				getClasses().add(annotatedClass);
			}
		}
	}
}
