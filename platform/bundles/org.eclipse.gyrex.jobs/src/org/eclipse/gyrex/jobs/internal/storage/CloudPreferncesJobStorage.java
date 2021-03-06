/*******************************************************************************
 * Copyright (c) 2011, 2013 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Mike Tschierschke - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.jobs.internal.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.jobs.JobState;
import org.eclipse.gyrex.jobs.internal.JobsActivator;
import org.eclipse.gyrex.jobs.internal.manager.JobImpl;
import org.eclipse.gyrex.jobs.internal.util.ContextHashUtil;
import org.eclipse.gyrex.preferences.CloudScope;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import org.apache.commons.lang.StringUtils;

/**
 * Store which persists job history in cloud preferences.
 * <p>
 * FIXME: This is a mess; we need to move job data out of the preferences and
 * directly into ZooKeeper. Preferences are really good for configuration data
 * that does not change often. However, they are an extra sync layer between job
 * data and zookeeper which adds complexity and calls for trouble.
 * </p>
 */
public class CloudPreferncesJobStorage {

	public static final String NODE_PARAMETER = "parameter";
	static final String NODE_STATES = "status";

	public static final String PROPERTY_TYPE = "type";
	public static final String PROPERTY_STATUS = "status";
	public static final String PROPERTY_LAST_START = "lastStart";
	public static final String PROPERTY_LAST_QUEUED = "lastQueued";
	public static final String PROPERTY_LAST_QUEUED_TRIGGER = "lastQueuedTrigger";
	public static final String PROPERTY_LAST_CANCELLED = "lastCancelled";
	public static final String PROPERTY_LAST_CANCELLED_TRIGGER = "lastCancelledTrigger";
	public static final String PROPERTY_LAST_SUCCESSFUL_START = "lastSuccessfulStart";
	public static final String PROPERTY_LAST_SUCCESSFUL_FINISH = "lastSuccessfulFinish";
	public static final String PROPERTY_LAST_RESULT_MESSAGE = "lastResultMessage";
	public static final String PROPERTY_LAST_RESULT_SEVERITY = "lastResultSeverity";
	public static final String PROPERTY_LAST_RESULT = "lastResultTimestamp";
	public static final String PROPERTY_ACTIVE = "active";

	private static final String NODE_JOBS = "jobs";

	public static Collection<String> getAllJobStorageKeysByState(final JobState state) {
		if (null == state)
			throw new IllegalArgumentException("Status must not be null");

		try {
			final String[] storageIds = CloudPreferncesJobStorage.getJobsNode().childrenNames();
			final List<String> jobIds = new ArrayList<String>(storageIds.length);
			for (final String internalId : storageIds) {
				if (StringUtils.equals(CloudPreferncesJobStorage.getJobsNode().node(internalId).get(CloudPreferncesJobStorage.PROPERTY_STATUS, null), state.name())) {
					jobIds.add(internalId);
				}
			}
			return Collections.unmodifiableCollection(jobIds);
		} catch (final BackingStoreException e) {
			throw new IllegalStateException(String.format("Error reading job data. %s", e.getMessage()), e);
		}
	}

	public static IEclipsePreferences getJobsNode() {
		return (IEclipsePreferences) CloudScope.INSTANCE.getNode(JobsActivator.SYMBOLIC_NAME).node(NODE_JOBS);
	}

	/**
	 * FIXME: This is a workaround till {@link CloudPreferncesJobStorage} is
	 * also refactored!!!
	 */
	public static void mayTriggerCleanup() {
		CloudPreferencesCleanupJobProvider.triggerCleanUp();
	}

	public static JobImpl readJob(final String jobId, final Preferences node) throws BackingStoreException {
		// ensure the node is current (bug 360402)
		// (note, this is really expensive, we don't perform it here)
		// (instead callers should call #syncJobNode where appropriate)
		//node.sync();

		// create job
		final JobImpl job = new JobImpl(node.name());

		job.setId(jobId);
		job.setTypeId(node.get(PROPERTY_TYPE, null));

		job.setStatus(JobState.toState(node.get(PROPERTY_STATUS, null)));
		job.setActive(node.getBoolean(PROPERTY_ACTIVE, false));
		job.setLastStart(node.getLong(PROPERTY_LAST_START, -1));
		job.setLastSuccessfulStart(node.getLong(PROPERTY_LAST_SUCCESSFUL_START, -1));
		job.setLastSuccessfulFinish(node.getLong(PROPERTY_LAST_SUCCESSFUL_FINISH, -1));
		final long lastResultTimestamp = node.getLong(PROPERTY_LAST_RESULT, -1);
		if (lastResultTimestamp > -1) {
			job.setLastResult(lastResultTimestamp, node.getInt(PROPERTY_LAST_RESULT_SEVERITY, IStatus.CANCEL), node.get(PROPERTY_LAST_RESULT_MESSAGE, ""));
		}
		job.setLastQueued(node.getLong(PROPERTY_LAST_QUEUED, -1));
		job.setLastQueuedTrigger(node.get(PROPERTY_LAST_QUEUED_TRIGGER, null));
		job.setLastCancelled(node.getLong(PROPERTY_LAST_CANCELLED, -1));
		job.setLastCancelledTrigger(node.get(PROPERTY_LAST_CANCELLED_TRIGGER, null));

		final HashMap<String, String> jobParamater = readParameter(node);
		job.setParameter(jobParamater);

		return job;
	}

	static HashMap<String, String> readParameter(final Preferences node) throws BackingStoreException {
		if (!node.nodeExists(NODE_PARAMETER))
			return null;
		final Preferences paramNode = node.node(NODE_PARAMETER);
		final String[] keys = paramNode.keys();
		final HashMap<String, String> jobParamater = new HashMap<String, String>(keys.length);
		for (final String key : keys) {
			jobParamater.put(key, paramNode.get(key, null));
		}
		return jobParamater;
	}

	private final IRuntimeContext context;

	private final ContextHashUtil contextHash;

	/**
	 * Creates a new instance.
	 */
	@Inject
	public CloudPreferncesJobStorage(final IRuntimeContext context) {
		this.context = context;
		contextHash = new ContextHashUtil(context);
	}

	private String toExternalId(final String internalId) {
		return contextHash.toExternalId(internalId);
	}

	private String toInternalId(final String id) {
		return contextHash.toInternalId(id);
	}

}
