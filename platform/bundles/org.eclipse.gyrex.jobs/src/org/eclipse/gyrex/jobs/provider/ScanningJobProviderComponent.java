/*******************************************************************************
 * Copyright (c) 2014 Tasktop Technologies and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.jobs.provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.gyrex.common.identifiers.IdHelper;
import org.eclipse.gyrex.common.scanner.BundleAnnotatedClassScanner;
import org.eclipse.gyrex.context.IModifiableRuntimeContext;
import org.eclipse.gyrex.jobs.IJobContext;
import org.eclipse.gyrex.jobs.annotation.JobType;
import org.eclipse.gyrex.jobs.internal.JobsDebug;

import org.eclipse.core.runtime.jobs.Job;

import org.osgi.service.component.ComponentConstants;
import org.osgi.service.component.ComponentContext;

import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OSGi Declarative Service component class for providing {@link Job job
 * instances} in Gyrex.
 * <p>
 * Essentially, this class is a {@link JobProvider} which which can be used out
 * of the box as OSGi component class. It provides convenience features which
 * ease the integration for jobs into an OSGi environment. For example, jobs
 * will be automatically discovered in the bundle which defines the actual
 * component by scanning the bundle for class files annotated with
 * {@link org.eclipse.gyrex.jobs.annotation.JobType}.
 * </p>
 * <p>
 * Clients which want to provider a set of jobs typically create an OSGi
 * Declarative Service component definition XML within the bundle containing the
 * annotaded job classes.
 * </p>
 * <p>
 * The component definition XML can be as simple as the following XML. However,
 * the <code>activate</code> and <code>deactivate</code> attributes <strong>must
 * be either be not set at all or set</strong> as specified below as well as the
 * <code>provide</code> element <strong>must be set</strong>.
 * 
 * <pre>
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" activate="activate" deactivate="deactivate" name="hello.jaxrs"&gt;
 *    &lt;implementation class="org.eclipse.gyrex.jobs.provider.ScanningJobProviderComponent"/&gt;
 *    &lt;service&gt;
 *       &lt;provide interface="org.eclipse.gyrex.jobs.provider.JobProvider"/&gt;
 *    &lt;/service&gt;
 * &lt;/scr:component&gt;
 * </pre>
 * 
 * </p>
 */
public class ScanningJobProviderComponent extends JobProvider {

	private static final Logger LOG = LoggerFactory.getLogger(ScanningJobProviderComponent.class);

	private volatile Map<String, Class<Job>> jobClassesByTypeId;

	/**
	 * Creates a new instance.
	 */
	public ScanningJobProviderComponent() {
		super();
	}

	@SuppressWarnings("unchecked")
	public void activate(final ComponentContext context) {
		if (JobsDebug.scanningProvider) {
			LOG.debug("ScanningJobProviderComponent activation triggered for component '{}' (bundle {})", context.getProperties().get(ComponentConstants.COMPONENT_NAME), context.getBundleContext().getBundle());
		}

		// scan for provided jobs
		final Map<String, Class<Job>> jobClassesByTypeId = new HashMap<>();
		final Set<Class<?>> foundClasses = new BundleAnnotatedClassScanner(context.getBundleContext().getBundle(), JobType.class).scan();
		for (final Class<?> clazz : foundClasses) {
			final JobType jobProvider = clazz.getAnnotation(JobType.class);
			if (!IdHelper.isValidId(jobProvider.typeId()))
				throw new IllegalStateException(String.format("Invalid job type id (%s) found. Please check your component configuration!", jobProvider.typeId()));
			if (!Job.class.isAssignableFrom(clazz))
				throw new IllegalStateException(String.format("Job type class (%s) must be a sub-class of org.eclipse.core.runtime.jobs.Job but it isn't. Please check your component configuration!", clazz));
			if (JobsDebug.scanningProvider) {
				LOG.debug("Found job type '{}' using class '{}' for component '{}' (bundle {})", jobProvider.typeId(), clazz, context.getProperties().get(ComponentConstants.COMPONENT_NAME), context.getBundleContext().getBundle());
			}
			jobClassesByTypeId.put(jobProvider.typeId(), (Class<Job>) clazz);
		}

		// activate job types
		this.jobClassesByTypeId = jobClassesByTypeId;
	}

	@Override
	public Job createJob(final String typeId, final IJobContext context) throws Exception {
		final Map<String, Class<Job>> jobClassesByTypeId = this.jobClassesByTypeId;
		if (jobClassesByTypeId == null) {
			LOG.debug("Job provider is inactive! It either hasn't been started yet or it's shutting down.");
			return null;
		}
		final Class<Job> clazz = jobClassesByTypeId.get(typeId);
		if (clazz == null) {
			LOG.debug("No class found for job type '{}'.", typeId);
			return null;
		}

		try (final IModifiableRuntimeContext runtimeContext = context.getContext().createWorkingCopy()) {
			runtimeContext.setLocal(IJobContext.class, context);
			runtimeContext.setLocal(Logger.class, context.getLogger());
			return runtimeContext.getInjector().make(clazz);
		} catch (final Exception e) {
			// report injection issues with hints
			if (StringUtils.contains(e.getMessage(), "no actual value was found for the argument"))
				throw new IllegalArgumentException("Please verify the job configuration. Not all required parameter seems to be available.", e);
			// re-throw
			throw e;
		}
	}

	public void deactivate(final ComponentContext context) {
		if (JobsDebug.scanningProvider) {
			LOG.debug("ScanningJobProviderComponent de-activation triggered for component '{}' (bundle {})", context.getProperties().get(ComponentConstants.COMPONENT_NAME), context.getBundleContext().getBundle());
		}
		jobClassesByTypeId = null;
	}

	@Override
	List<String> discoverProvidedTypeIds() {
		final Map<String, Class<Job>> jobClassesByTypeId = this.jobClassesByTypeId;
		if (jobClassesByTypeId == null)
			throw new IllegalStateException("inactive");
		return new ArrayList<>(jobClassesByTypeId.keySet());
	}
}
