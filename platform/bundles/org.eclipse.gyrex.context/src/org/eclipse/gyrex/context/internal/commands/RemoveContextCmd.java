/*******************************************************************************
 * Copyright (c) 2011, 2013 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.context.internal.commands;

import org.eclipse.gyrex.context.definitions.ContextDefinition;

/**
 *
 */
public class RemoveContextCmd extends BaseContextDefinitionCmd {

	/**
	 * Creates a new instance.
	 */
	public RemoveContextCmd() {
		super("<path> - removes a context");
	}

	@Override
	protected void doExecute(final ContextDefinition contextDefinition) throws Exception {
		getRegistry().removeDefinition(contextDefinition);
		printf("context removed");
	}

}
