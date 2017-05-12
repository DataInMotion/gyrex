/*******************************************************************************
 * Copyright (c) 2012 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.persistence.internal.console;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.gyrex.common.console.Command;
import org.eclipse.gyrex.persistence.internal.PersistenceActivator;
import org.eclipse.gyrex.persistence.internal.storage.RepositoryProviderRegistry;
import org.eclipse.gyrex.persistence.storage.registry.IRepositoryDefinition;
import org.eclipse.gyrex.persistence.storage.settings.IRepositoryPreferences;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Creates a new repo
 */
public class CreateRepo extends Command {

	@Argument(index = 0, metaVar = "REPOSITORY-ID", usage = "repository id", required = true)
	protected String repositoryId;

	@Option(name = "-p", aliases = { "--provider-id" }, metaVar = "PROVIDER-ID", usage = "provider id", required = true)
	protected String providerId;

	@Option(name = "-set", aliases = { "--set-preference" }, metaVar = "KEY=VALUE", usage = "a preference option to set")
	protected Map<String, String> prefsToSet;

	@Option(name = "-t", aliases = { "--tags" }, metaVar = "TAG", usage = "tags to set")
	protected List<String> tags;

	/**
	 * Creates a new instance.
	 */
	public CreateRepo() {
		super("-p <providerId> <repositorId> - creates a new repository");
	}

	@Override
	protected void doExecute() throws Exception {

		// check provider is available
		final RepositoryProviderRegistry providerRegistry = PersistenceActivator.getInstance().getRepositoryProviderRegistry();
		final String providerInfo = providerRegistry.getRepositoryProviderInfo(providerId);

		// create repository
		final IRepositoryDefinition repositoryDefinition = PersistenceActivator.getInstance().getRepositoriesManager().createRepository(repositoryId, providerId);

		// set preferences (if necessary)
		if ((null != prefsToSet) && !prefsToSet.isEmpty()) {
			final IRepositoryPreferences preferences = repositoryDefinition.getRepositoryPreferences();
			for (final Entry<String, String> e : prefsToSet.entrySet()) {
				if (null != e.getValue()) {
					preferences.put(e.getKey(), e.getValue(), false);
				} else {
					preferences.putBoolean(e.getKey(), true, false);
				}
			}
			preferences.flush();
		}

		// set tags (if necessary)
		if ((null != tags) && !tags.isEmpty()) {
			for (final String tag : tags) {
				if (StringUtils.isNotBlank(tag)) {
					repositoryDefinition.addTag(tag);
				}
			}
		}

		printf("Created repository %s using %s (%s)!", repositoryId, providerInfo, providerId);
	}

}
