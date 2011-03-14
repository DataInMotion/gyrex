/*******************************************************************************
 * Copyright (c) 2008 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.preferences.internal;

import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.gyrex.preferences.CloudScope;
import org.eclipse.gyrex.server.Platform;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScope;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link IScope} factory for the cloud scope.
 */
public class CloudPreferencesScopeFactory implements IScope {

	private static final Logger LOG = LoggerFactory.getLogger(CloudPreferencesScopeFactory.class);
	private static final AtomicReference<CloudPreferences> rootCloudNode = new AtomicReference<CloudPreferences>();

	/**
	 * Stops the factory and releases any resource.
	 */
	public static void stop() {
		// flush the preferences
		final CloudPreferences node = rootCloudNode.get();
		if (node != null) {
			try {
				node.flush();
			} catch (final Exception e) {
				LOG.warn("Failed to flush cloud preferences. Changes migt be lost. {}", ExceptionUtils.getRootCauseMessage(e));
			}
		}
	}

	@Override
	public IEclipsePreferences create(final IEclipsePreferences parent, final String name) {
		// sanity check
		if (!CloudScope.NAME.equals(name)) {
			LOG.error("Cloud preference factory called with illegal node name {} for parent {}.", new Object[] { name, parent.absolutePath(), new Exception("Call Stack") });
			throw new IllegalArgumentException("invalid node name");
		}

		// allow explicit fallback to instance based preferences
		if (Platform.inDevelopmentMode() && Boolean.getBoolean("gyrex.preferences.instancebased")) {
			LOG.info("Using instance based preferences as specified via system property!");
			return new InstanceBasedPreferences(parent, name);
		}

		// check if already created
		final CloudPreferences node = rootCloudNode.get();
		if (null != node) {
			if (PreferencesDebug.debug) {
				LOG.debug("Cloud preference factory called multiple times for name {} and parent {}.", new Object[] { name, parent.absolutePath(), new Exception("Call Stack") });
			}
			return node;
		}

		if (PreferencesDebug.debug) {
			LOG.debug("Creating ZooKeeper preferences '{}' (parent {})", name, parent);
		}

		// create
		rootCloudNode.compareAndSet(null, new CloudPreferences(parent, name));

		// done
		return rootCloudNode.get();
	}
}