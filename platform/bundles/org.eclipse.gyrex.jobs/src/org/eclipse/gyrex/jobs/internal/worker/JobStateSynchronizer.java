/*******************************************************************************
 * Copyright (c) 2011 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Mike Tschierschke - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.jobs.internal.worker;

import org.eclipse.gyrex.jobs.IJob;
import org.eclipse.gyrex.jobs.JobState;
import org.eclipse.gyrex.jobs.internal.manager.IJobStateWatch;
import org.eclipse.gyrex.jobs.internal.manager.JobManagerImpl;
import org.eclipse.gyrex.jobs.manager.IJobManager;

import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Synchronizes Gyrex Job state with Eclipse Jobs state.
 */
public final class JobStateSynchronizer implements IJobChangeListener, IJobStateWatch {

	private final Job realJob;
	private final JobContext jobContext;

	public JobStateSynchronizer(final Job realJob, final JobContext jobContext) {
		this.realJob = realJob;
		this.jobContext = jobContext;
	}

	@Override
	public void aboutToRun(final IJobChangeEvent event) {
		// check if job should be executed or was canceled meanwhile
		final JobManagerImpl jobManager = getJobManager();

		final String jobId = getJobId();
		final IJob job = jobManager.getJob(jobId);

		// if the job state is not waiting, we cancel it
		if ((job.getState() != JobState.WAITING)) {
			cancelRealJob();
		}
	}

	@Override
	public void awake(final IJobChangeEvent event) {
		// we're not interested at the moment - job state is running
	}

	private void cancelRealJob() {
		// cancel Eclipse job
		if (realJob.cancel()) {
			// update job if cancellation was successful
			getJobManager().setJobState(getJobId(), JobState.ABORTING, JobState.NONE, null);
		}
	}

	@Override
	public void done(final IJobChangeEvent event) {
		final JobManagerImpl jobManager = getJobManager();

		// update job state
		jobManager.setJobState(getJobId(), null, JobState.NONE, null);

		// update job with result
		jobManager.setResult(getJobId(), event.getResult(), System.currentTimeMillis());
	}

	private String getJobId() {
		return jobContext.getJobId();
	}

	JobManagerImpl getJobManager() {
		return (JobManagerImpl) jobContext.getContext().get(IJobManager.class);
	}

	@Override
	public void jobStateChanged(final String jobId) {
		final IJob job = getJobManager().getJob(getJobId());
		final JobState state = job.getState();
		if (state == JobState.ABORTING) {
			cancelRealJob();
		}
	}

	@Override
	public void running(final IJobChangeEvent event) {
		final JobManagerImpl jobManager = getJobManager();

		// set job state running
		jobManager.setJobState(getJobId(), JobState.WAITING, JobState.RUNNING, this);
	}

	@Override
	public void scheduled(final IJobChangeEvent event) {
		// we're not interested at the moment - job state is waiting
	}

	@Override
	public void sleeping(final IJobChangeEvent event) {
		// we're not interested at the moment - job state is running
	}
}
