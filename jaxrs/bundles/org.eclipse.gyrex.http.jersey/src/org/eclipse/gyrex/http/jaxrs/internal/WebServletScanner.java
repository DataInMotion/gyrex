/**
 * Copyright (c) 2014 Tasktop Technologies and others.
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

import java.util.HashSet;
import java.util.Set;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.gyrex.common.scanner.BundleAnnotatedClassScanner;

import org.osgi.framework.Bundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A scanner which scans a bundle for classes annotated with WebServlet.
 */
public class WebServletScanner {

	private static final Logger LOG = LoggerFactory.getLogger(WebServletScanner.class);

	private final Bundle bundle;

	private Set<Class<? extends HttpServlet>> classes;

	/**
	 * Creates a new instance.
	 */
	public WebServletScanner(final Bundle bundle) {
		this.bundle = bundle;
		scan();
	}

	public Set<Class<? extends HttpServlet>> getClasses() {
		if (classes == null) {
			classes = new HashSet<>();
		}
		return classes;
	}

	@SuppressWarnings("unchecked")
	private void scan() {
		if (JaxRsDebug.resourceDiscovery) {
			LOG.debug("Scanning bundle '{}' for WebServlet annotated classes.", bundle);
		}

		final BundleAnnotatedClassScanner bundleAnnotatedClassScanner = new BundleAnnotatedClassScanner(bundle, WebServlet.class);

		final Set<Class<?>> annotatedClasses = bundleAnnotatedClassScanner.scan();
		if (annotatedClasses.isEmpty()) {
			LOG.warn("No WebServlet annotated classed found in bundle '{}'.", bundle);
		} else {
			for (final Class<?> annotatedClass : annotatedClasses) {
				if (annotatedClass.isAnnotationPresent(WebServlet.class) && HttpServlet.class.isAssignableFrom(annotatedClass)) {
					if (JaxRsDebug.resourceDiscovery) {
						LOG.debug("Found servlet: {}", annotatedClass.getName());
					}
					getClasses().add((Class<? extends HttpServlet>) annotatedClass);
				} else if (JaxRsDebug.resourceDiscovery) {
					LOG.debug("Not a HttpServlet: {}", annotatedClass.getName());
				}
			}
		}
	}
}
