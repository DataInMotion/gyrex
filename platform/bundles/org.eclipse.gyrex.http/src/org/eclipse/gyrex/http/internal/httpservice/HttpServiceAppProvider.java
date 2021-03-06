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
package org.eclipse.gyrex.http.internal.httpservice;

import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.http.application.Application;
import org.eclipse.gyrex.http.application.provider.ApplicationProvider;
import org.eclipse.gyrex.http.internal.HttpActivator;

import org.eclipse.core.runtime.CoreException;

/**
 * Application provider for the HTTP Service application.
 */
public class HttpServiceAppProvider extends ApplicationProvider {

	public static final String ID = HttpActivator.SYMBOLIC_NAME + ".httpservice.application.provider";

	/**
	 * Creates a new instance.
	 * 
	 * @param id
	 */
	public HttpServiceAppProvider() {
		super(ID);
	}

	@Override
	public Application createApplication(final String applicationId, final IRuntimeContext context) throws CoreException {
		return new HttpServiceApp(applicationId, context);
	}

}
