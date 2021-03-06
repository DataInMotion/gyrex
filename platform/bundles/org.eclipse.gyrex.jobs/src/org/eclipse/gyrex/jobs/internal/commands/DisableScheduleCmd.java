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
package org.eclipse.gyrex.jobs.internal.commands;

import org.eclipse.gyrex.jobs.internal.schedules.ScheduleImpl;
import org.eclipse.gyrex.jobs.internal.schedules.ScheduleStore;

public class DisableScheduleCmd extends BaseScheduleStoreCmd {

	/**
	 * Creates a new instance.
	 */
	public DisableScheduleCmd() {
		super("- Disables a schedule");
	}

	@Override
	protected void doExecute(final String storageId, final String scheduleId) throws Exception {
		final ScheduleImpl schedule = ScheduleStore.load(storageId, scheduleId, true);

		if (!schedule.isEnabled()) {
			printf("Schedule %s not enabled!", scheduleId);
			return;
		}

		schedule.setEnabled(false);
		ScheduleStore.flush(storageId, schedule);
		printf("Disabled schedule %s!", scheduleId);
	}

}
