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
package org.eclipse.gyrex.preferences.internal.console;

import java.util.concurrent.TimeUnit;

import org.osgi.service.prefs.Preferences;

/**
 * Syncs preferences
 */
public class SyncCmd extends PathBasedCmd {

	/**
	 * Creates a new instance.
	 */
	public SyncCmd() {
		super(" - syncs a preference hierarchy");
	}

	@Override
	protected void doExecute(final Preferences node) throws Exception {
		final long start = System.nanoTime();
		node.sync();
		final long duration = System.nanoTime() - start;
		if (TimeUnit.NANOSECONDS.toMillis(duration) > 1000) {
			printf("Sync finished in %d seconds.", TimeUnit.NANOSECONDS.toSeconds(duration));
		} else if (TimeUnit.NANOSECONDS.toMillis(duration) > 10) {
			printf("Sync finished in %d milli seconds.", TimeUnit.NANOSECONDS.toMillis(duration));
		} else {
			printf("Sync finished in %d nano seconds.", duration);
		}
	}

}
