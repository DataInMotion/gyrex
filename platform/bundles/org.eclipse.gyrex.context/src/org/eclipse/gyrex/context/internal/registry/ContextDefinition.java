/*******************************************************************************
 * Copyright (c) 2011, 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.context.internal.registry;

import org.eclipse.core.runtime.IPath;

import org.apache.commons.lang.StringUtils;

/**
 *
 */
public class ContextDefinition {

	private final IPath path;
	private String name;

	/**
	 * Creates a new instance.
	 * 
	 * @param path
	 */
	public ContextDefinition(final IPath path) {
		if (path == null) {
			throw new IllegalArgumentException("path must not be null");
		}
		this.path = path;
	}

	/**
	 * Returns the name.
	 * 
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the path.
	 * 
	 * @return the path
	 */
	public IPath getPath() {
		return path;
	}

	/**
	 * Sets the name.
	 * 
	 * @param name
	 *            the name to set
	 */
	public void setName(final String name) {
		this.name = StringUtils.isNotBlank(name) ? name : null;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append(path);
		if (null != name) {
			builder.append(" (").append(name).append(")");
		}
		return builder.toString();
	}

}
