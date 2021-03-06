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
 *     Mike Tschierschke - improvements due working on https://bugs.eclipse.org/bugs/show_bug.cgi?id=344467
 *******************************************************************************/
package org.eclipse.gyrex.jobs.internal.scheduler;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.gyrex.jobs.internal.schedules.ScheduleImpl;
import org.eclipse.gyrex.jobs.internal.schedules.SchedulingUtil;
import org.eclipse.gyrex.jobs.internal.worker.JobLogHelper;
import org.eclipse.gyrex.jobs.manager.IJobManager;
import org.eclipse.gyrex.jobs.schedules.IScheduleEntry;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.apache.commons.lang.StringUtils;

import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Quartz job implementation that simply queues a triggered quartz job entry
 * with the Gyrex IJobManager for distributed processing by the worker engine.
 */
public class SchedulingJob implements Job {

	public static final Logger LOG = LoggerFactory.getLogger(SchedulingJob.class);

	static final String INTERNAL_PROP_PREFIX = "gyrex.job.";
	public static final String PROP_JOB_ID = INTERNAL_PROP_PREFIX + "id";
	public static final String PROP_JOB_TYPE_ID = INTERNAL_PROP_PREFIX + "type";
	public static final String PROP_JOB_CONTEXT_PATH = INTERNAL_PROP_PREFIX + "contextPath";
	public static final String PROP_SCHEDULE_ID = INTERNAL_PROP_PREFIX + "scheduleId";
	public static final String PROP_SCHEDULE_ENTRY_ID = INTERNAL_PROP_PREFIX + "scheduleEntryId";
	public static final String PROP_SCHEDULE_ENTRIES_TO_TRIGGER_AFTER_RUN = INTERNAL_PROP_PREFIX + "scheduleEntriesToTriggerAfterRun";
	public static final String PROP_ENABLED = INTERNAL_PROP_PREFIX + "enabled";
	public static final String PROP_QUEUE_ID = INTERNAL_PROP_PREFIX + "queueId";

	public static final char SEPARATOR_CHAR = ',';

	/**
	 * populates a JobDataMap for later queuing with {@link queueJob}.
	 * 
	 * @param jobDataMap
	 * @param entry
	 * @param schedule
	 */
	public static void populateJobDataMap(final JobDataMap jobDataMap, final IScheduleEntry entry, final ScheduleImpl schedule) {
		jobDataMap.putAll(entry.getJobParameter());
		jobDataMap.put(PROP_JOB_TYPE_ID, entry.getJobTypeId());
		jobDataMap.put(PROP_JOB_ID, entry.getJobId());
		jobDataMap.put(PROP_ENABLED, entry.isEnabled());
		jobDataMap.put(PROP_JOB_CONTEXT_PATH, schedule.getContextPath().toString());
		jobDataMap.put(PROP_SCHEDULE_ID, entry.getSchedule().getId());
		jobDataMap.put(PROP_SCHEDULE_ENTRY_ID, entry.getId());
		jobDataMap.put(PROP_SCHEDULE_ENTRIES_TO_TRIGGER_AFTER_RUN, SchedulingUtil.getScheduleEntriesToTriggerAfterRun(entry, schedule));
		jobDataMap.put(PROP_QUEUE_ID, StringUtils.isNotBlank(entry.getQueueId()) ? entry.getQueueId() : schedule.getQueueId());
	}

	/**
	 * Queues a Gyrex job based on data in a Quartz JobDataMap.
	 * 
	 * @param dataMap
	 * @throws Exception
	 */
	public static void queueJob(final JobDataMap dataMap) throws Exception {
		final String jobId = dataMap.getString(PROP_JOB_ID);
		final String jobTypeId = dataMap.getString(PROP_JOB_TYPE_ID);
		final String jobContextPath = dataMap.getString(PROP_JOB_CONTEXT_PATH);
		final String scheduleId = dataMap.getString(PROP_SCHEDULE_ID);
		final String scheduleEntryId = dataMap.getString(PROP_SCHEDULE_ENTRY_ID);
		final String scheduleEntriesToTriggerAfterRun = dataMap.getString(PROP_SCHEDULE_ENTRIES_TO_TRIGGER_AFTER_RUN);
		final Boolean enabled = dataMap.containsKey(PROP_ENABLED) ? dataMap.getBooleanValue(PROP_ENABLED) : null;
		final String queueId = StringUtils.isNotBlank(dataMap.getString(PROP_QUEUE_ID)) ? dataMap.getString(PROP_QUEUE_ID) : IJobManager.DEFAULT_QUEUE;

		// check if enabled
		// (note, we must keep this here because there are multiple entry paths)
		if ((null != enabled) && !enabled) {
			LOG.debug(String.format("Skipping execution of job '%s' - entry is not enabled", jobId));
			return;
		}

		// parse path
		final IPath contextPath = new Path(jobContextPath);

		// populate map
		final Map<String, String> parameter = new HashMap<String, String>();
		for (final Object keyObj : dataMap.keySet()) {
			if (!(keyObj instanceof String)) {
				continue;
			}
			final String key = (String) keyObj;
			if (!StringUtils.startsWith(key, INTERNAL_PROP_PREFIX)) {
				final Object value = dataMap.get(key);
				if (value instanceof String) {
					parameter.put(key, (String) value);
				}
			}
		}

		SchedulingUtil.queueJob(jobId, jobTypeId, contextPath, scheduleId, scheduleEntryId, scheduleEntriesToTriggerAfterRun, queueId, parameter);
	}

	@Override
	public void execute(final JobExecutionContext context) throws JobExecutionException {
		try {
			final JobDataMap dataMap = context.getMergedJobDataMap();

			// setup MDC
			JobLogHelper.setupMdc(dataMap.getString(PROP_JOB_ID), new Path(dataMap.getString(PROP_JOB_CONTEXT_PATH)));

			// queue job
			queueJob(dataMap);
		} catch (final Exception e) {
			throw new JobExecutionException(String.format("Error queuing job '%s'. %s", context.getJobDetail().getFullName(), e.getMessage()), e);
		} finally {
			// clear MDC
			JobLogHelper.clearMdc();
		}
	}
}