/*******************************************************************************
 * Copyright (c) 2011, 2012 Ageto Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Mike Tschierschke - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.admin.ui.internal.jetty;

import org.eclipse.jetty.security.AbstractLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.util.security.Credential;

import org.apache.commons.lang.StringUtils;

/**
 * This class represents a simple {@link LoginService}, that only knows one user
 * with the given name, that only can have the role
 * {@link AdminServletHolder#ADMIN_DEFAULT_ROLE}
 */
public class SimpleAdminLoginService extends AbstractLoginService {

	private final UserPrincipal userPrincipal;

	/**
	 * Creates a new instance with a given password. It's highly recommended to
	 * save only hashed passwords.
	 *
	 * @see Credential#getCredential(String)
	 */
	public SimpleAdminLoginService(final String username, final String password) {

		if (StringUtils.isBlank(username) || StringUtils.isBlank(password))
			throw new IllegalArgumentException("Username and password must not be blank");

		userPrincipal = new UserPrincipal(username, Credential.getCredential(password));
	}

	@Override
	protected String[] loadRoleInfo(final UserPrincipal user) {
		if ((user == null) || !userPrincipal.getName().equals(user.getName()))
			return null;

		return new String[] { AdminServletHolder.ADMIN_ROLE };
	}

	@Override
	protected UserPrincipal loadUserInfo(final String username) {
		if ((username == null) || !username.equals(userPrincipal.getName()))
			return null;

		return userPrincipal;
	}

}
