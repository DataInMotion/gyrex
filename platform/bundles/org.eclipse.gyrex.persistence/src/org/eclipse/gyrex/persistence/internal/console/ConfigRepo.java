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

import org.eclipse.gyrex.persistence.internal.storage.RepositoryDefinition;
import org.eclipse.gyrex.persistence.storage.settings.IRepositoryPreferences;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/**
 * Configures a repository
 */
public class ConfigRepo extends BaseRepoSelectingCommand {

	@Argument(index = 0, metaVar = "ID-SUB-STRING", usage = "repository sub-string id filter", required = true)
	protected String repositoryIdFilterArg;

	@Option(name = "-set", aliases = { "--set-preference" }, metaVar = "KEY=VALUE", usage = "a preference option to set")
	protected Map<String, String> prefsToSet;

	@Option(name = "-rm", aliases = { "--remove-preference" }, metaVar = "KEY", usage = "a preference option to set")
	protected List<String> prefsToRemove;

	@Option(name = "-t", aliases = { "--tags" }, metaVar = "TAG", usage = "tags to set")
	protected List<String> tags;

	/**
	 * Creates a new instance.
	 *
	 * @param description
	 */
	public ConfigRepo() {
		super("<repoIdFilter> -set <key>=value - sets a repository preference");
	}

	@Override
	protected String getRepositoryIdFilter() {
		// allow specifying filter as argument
		if (null != repositoryIdFilterArg)
			return repositoryIdFilterArg;

		// fallback to option
		return super.getRepositoryIdFilter();
	}

	@Override
	protected void processRepository(final String repositoryId) throws Exception {

		final RepositoryDefinition definition = getRegistry().getRepositoryDefinition(repositoryId);

		final IRepositoryPreferences preferences = definition.getRepositoryPreferences();
		preferences.sync();

		boolean modified = false;
		if ((null != prefsToSet) && !prefsToSet.isEmpty()) {
			modified = true;
			for (final Entry<String, String> e : prefsToSet.entrySet()) {
				if (null != e.getValue()) {
					preferences.put(e.getKey(), e.getValue(), false);
				} else {
					preferences.putBoolean(e.getKey(), true, false);
				}
			}
		}
		if ((null != prefsToRemove) && !prefsToRemove.isEmpty()) {
			modified = true;
			for (final String key : prefsToRemove) {
				preferences.remove(key);
			}
		}
		if ((null != tags) && !tags.isEmpty()) {
			modified = true;
			for (final String tag : tags) {
				if (StringUtils.isNotBlank(tag)) {
					definition.addTag(tag);
				}
			}
		}

		if (modified) {
			preferences.flush();
			printf("Updated repository %s!", repositoryId);
		} else {
			printf("Nothing to update for repository %s!", repositoryId);
		}
	}

}
