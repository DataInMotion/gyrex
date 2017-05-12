/*******************************************************************************
 * Copyright (c) 2010, 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.http.jetty.internal.app;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.gyrex.http.application.context.IResourceProvider;

import org.eclipse.jetty.servlet.Holder;
import org.eclipse.jetty.servlet.Source;
import org.eclipse.jetty.util.URIUtil;

/**
 *
 */
public class ResourceProviderHolder extends Holder<IResourceProvider> {

	private final IResourceProvider provider;
	private final String internalName;

	ResourceProviderHolder(final String internalName, final IResourceProvider provider) {
		super(Source.EMBEDDED);
		this.internalName = internalName;
		this.provider = provider;
		setName(provider.getClass().getName() + "-" + super.hashCode());
	}

	public URL getResource(final String pathInfo) throws MalformedURLException {
		return provider.getResource(URIUtil.addPaths(internalName, pathInfo));
	}

}
