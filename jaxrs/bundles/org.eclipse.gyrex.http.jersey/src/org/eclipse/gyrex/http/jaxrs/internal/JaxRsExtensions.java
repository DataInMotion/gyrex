/**
 * Copyright (c) 2012 Gunnar Wagenknecht and others.
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

import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.http.application.context.IApplicationContext;
import org.eclipse.gyrex.http.jaxrs.jersey.spi.inject.ContextApplicationContextInjectableProvider;
import org.eclipse.gyrex.http.jaxrs.jersey.spi.inject.ContextRuntimeContextInjectableProvider;
import org.eclipse.gyrex.http.jaxrs.jersey.spi.inject.InjectApplicationContextInjectableProvider;
import org.eclipse.gyrex.http.jaxrs.jersey.spi.inject.InjectRuntimeContextInjectableProvider;
import org.eclipse.gyrex.server.Platform;

import org.osgi.framework.FrameworkUtil;

import org.glassfish.jersey.server.wadl.config.WadlGeneratorConfig;
import org.glassfish.jersey.server.wadl.internal.generators.WadlGeneratorJAXBGrammarGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to enable certain JAX-RS extensions.
 */
public class JaxRsExtensions {

	private static final Logger LOG = LoggerFactory.getLogger(JaxRsExtensions.class);

	public static void addCommonInjectors(final Set<Object> singletons, final IRuntimeContext context, final IApplicationContext applicationContext) {
		singletons.add(new ContextRuntimeContextInjectableProvider(context));
		singletons.add(new ContextApplicationContextInjectableProvider(applicationContext));
		singletons.add(new InjectRuntimeContextInjectableProvider(context));
		singletons.add(new InjectApplicationContextInjectableProvider(applicationContext));
	}

	public static void addJsonProviderIfPossible(final Set<Object> singletons) {
		final Object jsonProvider = createMoxyJsonProvider();
		if (null != jsonProvider) {
			singletons.add(jsonProvider);
		}
	}

	public static void addWadlSupport(final org.glassfish.jersey.server.ResourceConfig resourceConfig) {
//		return generator( WadlGeneratorApplicationDoc.class )
//      .prop( "applicationDocsStream", "application-doc.xml" )
//    .generator( WadlGeneratorGrammarsSupport.class )
//      .prop( "grammarsStream", "application-grammars.xml" )
//    .generator( WadlGeneratorResourceDocSupport.class )
//      .prop( "resourceDocStream", "resourcedoc.xml" ) .
//      generator(WadlGeneratorJAXBGrammarGenerator.class).descriptions();
		resourceConfig.getSingletons().add(WadlGeneratorConfig.generator(WadlGeneratorJAXBGrammarGenerator.class).build());
		resourceConfig.getClasses().add(WadlResource.class);
	}

	private static Object createMoxyJsonProvider() {
		// use reflection to avoid dependency
		try {
			final Class<?> providerClass = JaxRsExtensions.class.getClassLoader().loadClass("org.eclipse.persistence.jaxb.rs.MOXyJsonProvider");
			final Object moxyJsonProvider = providerClass.newInstance();
			if (JaxRsDebug.debug || Platform.inDevelopmentMode()) {
				try {
					providerClass.getMethod("setFormattedOutput", Boolean.TYPE).invoke(moxyJsonProvider, Boolean.TRUE);
				} catch (final Exception e) {
					LOG.warn("Unable to configure formatted output for MOXy JSON Provider. Please update the JAX-RS application bundle to an implementation which is compatible with the EclipseLink MOXy implementation in use ({}).", FrameworkUtil.getBundle(providerClass), e);
				}
			}
			return moxyJsonProvider;
		} catch (final Exception e) {
			// not available
			if (JaxRsDebug.debug) {
				LOG.debug("Unable to load MOXy JSON Provider. Please make sure MOXy is properly installed and resolved in order to use it.", e);
			}
			return null;
		}
	}

	private JaxRsExtensions() {
		// empty
	}

}
