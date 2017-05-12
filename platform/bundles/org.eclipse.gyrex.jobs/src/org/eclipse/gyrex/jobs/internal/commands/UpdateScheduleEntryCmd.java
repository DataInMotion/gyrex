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
package org.eclipse.gyrex.jobs.internal.commands;

import java.util.HashMap;
import java.util.List;

import org.eclipse.gyrex.common.identifiers.IdHelper;
import org.eclipse.gyrex.jobs.internal.schedules.ScheduleImpl;
import org.eclipse.gyrex.jobs.internal.schedules.ScheduleStore;
import org.eclipse.gyrex.jobs.schedules.manager.IScheduleEntryWorkingCopy;

import org.apache.commons.lang.StringUtils;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

public class UpdateScheduleEntryCmd extends BaseScheduleStoreCmd {

	@Argument(index = 1, usage = "the entry id", required = true, metaVar = "ENTRYID")
	String entryId;

	@Option(name = "--queue-id", aliases = { "-queue" }, usage = "updates the queue id", metaVar = "QUEUEID")
	String queueId;

	@Option(name = "--reset-queue-id", aliases = { "-noqueue" }, usage = "unsets an entry specific queue id")
	Boolean removeQueueId;

	@Option(name = "--cron-expression", aliases = { "-cron" }, usage = "updates the cron expression", metaVar = "EXPR")
	String cronExpression;

	@Option(name = "--add-preceding-entry", aliases = { "-preceding" }, usage = "sets a dependency on another schedule entry id ", metaVar = "OTHERENTRYID")
	List<String> precedingEntries;

	@Option(name = "--remove-all-preceding-entries", aliases = { "-nopreceding" }, usage = "unsets all dependencies on other schedule entries")
	Boolean resetPrecedingEntries;

	@Option(name = "--enable", aliases = { "-on" }, usage = "enables the schedule entry")
	Boolean enable;

	@Option(name = "--disable", aliases = { "-off" }, usage = "disables the schedule entry")
	Boolean disable;

	@Option(name = "--set-parameter", aliases = { "-set" }, usage = "sets a schedule paramater", metaVar = "KEY=VALUE")
	List<String> parameterToSet;

	@Option(name = "--unset-parameter", aliases = { "-unset" }, usage = "unsets a schedule paramater", metaVar = "KEY")
	List<String> parameterToRemove;

	/**
	 * Creates a new instance.
	 */
	public UpdateScheduleEntryCmd() {
		super("<entryId> - Updates a schedule entry");
	}

	@Override
	protected void doExecute(final String storageId, final String scheduleId) throws Exception {
		final ScheduleImpl schedule = ScheduleStore.load(storageId, scheduleId, true);

		if (schedule.isEnabled()) {
			printf("Schedule %s is enabled, please disable first!", scheduleId);
			return;
		}

		if (!IdHelper.isValidId(entryId))
			throw new IllegalArgumentException("invalid entry id");

		final IScheduleEntryWorkingCopy entry = schedule.getEntry(entryId);

		String action;
		if ((null != enable) && enable) {
			// enable
			action = "Enabled";
			entry.setEnabled(true);
		} else if ((null != disable) && disable) {
			// disable
			action = "Disabled";
			entry.setEnabled(false);
		} else {
			// update
			action = "Updated";

			// queue id
			if (null != queueId) {
				entry.setQueueId(queueId);
			} else if ((null != removeQueueId) && removeQueueId) {
				entry.setQueueId(null);
			}

			// cron
			if (null != cronExpression) {
				try {
					entry.setCronExpression(cronExpression);
				} catch (final Exception e) {
					throw new IllegalArgumentException("invalid cron expression, please see http://en.wikipedia.org/wiki/Cron#CRON_expression", e);
				}
			}

			// parameter
			final HashMap<String, String> newParameter = new HashMap<String, String>(entry.getJobParameter());
			if (null != parameterToSet) {
				for (final String param : parameterToSet) {
					final String[] args = StringUtils.split(param, "=", 2);
					if ((null == args) || (args.length != 2))
						throw new IllegalArgumentException(String.format("cannot parse parameter to set: %s; please check syntax", param));
					newParameter.put(args[0], args[1]);
				}
				entry.setJobParameter(newParameter);
			}
			if (null != parameterToRemove) {
				for (final String key : parameterToRemove) {
					newParameter.remove(key);
				}
				entry.setJobParameter(newParameter);
			}

			// trigger after
			if (null != precedingEntries) {
				entry.setPrecedingEntries(precedingEntries.toArray(new String[precedingEntries.size()]));
			}
			if ((null != resetPrecedingEntries) && resetPrecedingEntries.booleanValue()) {
				entry.setPrecedingEntries(new String[0]);
			}
		}

		ScheduleStore.flush(storageId, schedule);
		printf("%s entry %s in schedule %s!", action, entryId, scheduleId);
	}

}
