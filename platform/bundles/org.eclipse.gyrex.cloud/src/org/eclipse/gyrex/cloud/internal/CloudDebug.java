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
package org.eclipse.gyrex.cloud.internal;

import org.eclipse.gyrex.common.debug.BundleDebugOptions;

/**
 * Debug options
 */
public class CloudDebug extends BundleDebugOptions {

	public static boolean debug;
	public static boolean zooKeeperServer;
	public static boolean zooKeeperGateLifecycle;

	public static boolean cloudState;
	public static boolean nodeMetrics;

	public static boolean zooKeeperLockService;
	public static boolean zooKeeperPreferences;
	public static boolean zooKeeperPreferencesSync;

	public static boolean eventService;
}
