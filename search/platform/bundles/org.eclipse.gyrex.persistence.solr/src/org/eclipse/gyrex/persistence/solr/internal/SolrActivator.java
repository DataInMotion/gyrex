/*******************************************************************************
 * Copyright (c) 2008, 2011 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 *     Mike Tschierschke - rework of the SolrRepository concept (https://bugs.eclipse.org/bugs/show_bug.cgi?id=337404)
 *******************************************************************************/
package org.eclipse.gyrex.persistence.solr.internal;

import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.gyrex.common.runtime.BaseBundleActivator;
import org.eclipse.gyrex.common.services.IServiceProxy;
import org.eclipse.gyrex.persistence.storage.provider.RepositoryProvider;
import org.eclipse.gyrex.server.Platform;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.service.datalocation.Location;

import org.osgi.framework.BundleContext;

import org.apache.commons.io.FileUtils;
import org.apache.solr.core.CoreContainer;

public class SolrActivator extends BaseBundleActivator {

	public static final String PLUGIN_ID = "org.eclipse.gyrex.persistence.solr";
	private static final AtomicReference<SolrActivator> instance = new AtomicReference<SolrActivator>();

	public static String getEmbeddedSolrCoreName(final String repositoryId) {
		return repositoryId;
	}

	/**
	 * Returns the instance.
	 * 
	 * @return the instance
	 */
	public static SolrActivator getInstance() {
		return instance.get();
	}

	private final AtomicReference<CoreContainer> coreContainerRef = new AtomicReference<CoreContainer>();
	private final AtomicReference<IServiceProxy<Location>> instanceLocationRef = new AtomicReference<IServiceProxy<Location>>();

	private volatile File solrBase;

	/**
	 * Creates a new instance.
	 */
	public SolrActivator() {
		super(PLUGIN_ID);
	}

	@Override
	protected void doStart(final BundleContext context) throws Exception {
		instance.set(this);

		// get instance location
		instanceLocationRef.set(getServiceHelper().trackService(Location.class, context.createFilter(Location.INSTANCE_FILTER)));

		// start server
		startEmbeddedSolrServer(context);
	}

	@Override
	protected void doStop(final BundleContext context) throws Exception {
		shutdownEmbeddedSolrServer();
		instance.set(null);
	}

	public CoreContainer getEmbeddedCoreContainer() {
		return coreContainerRef.get();
	}

	public File getEmbeddedSolrBase() {
		return solrBase;
	}

	public File getEmbeddedSolrCoreBase(final String coreName) {
		if (null == solrBase) {
			throw new IllegalStateException("no Solr base directory");
		}
		return new File(solrBase, coreName);
	}

	public Location getInstanceLocation() {
		final IServiceProxy<Location> serviceProxy = instanceLocationRef.get();
		if (null == serviceProxy) {
			throw createBundleInactiveException();
		}

		return serviceProxy.getService();
	}

	private void shutdownEmbeddedSolrServer() {
		final CoreContainer coreContainer = coreContainerRef.getAndSet(null);
		if (null != coreContainer) {
			coreContainer.persist();
			coreContainer.shutdown();
		}
	}

	private void startEmbeddedSolrServer(final BundleContext context) throws Exception {
		// only in dev mode
		if (!Platform.inDevelopmentMode()) {
			return;
		}

		// the configuration template
		final File configTemplate = new File(FileLocator.toFileURL(context.getBundle().getEntry("conf-embeddedsolr")).getFile());

		// get embedded Solr home directory
		final URL instanceLocation = getInstanceLocation().getURL();
		if (null == instanceLocation) {
			throw new IllegalStateException("no instance location available");
		}

		solrBase = new Path(instanceLocation.getFile()).append("solr").toFile();
		if (!solrBase.isDirectory()) {
			// initialize dir
			solrBase.mkdirs();
		}

		// get multicore config file
		final File configFile = new File(solrBase, "solr.xml");
		if (!configFile.isFile()) {
			// deploy base configuration
			FileUtils.copyDirectory(configTemplate, solrBase);
			if (!configFile.isFile()) {
				throw new IllegalStateException("config file '" + configFile.getPath() + "' is missing");
			}
		}

		// create core container
		if (!coreContainerRef.compareAndSet(null, new CoreContainer())) {
			// already initialized
			return;
		}

		final CoreContainer coreContainer = coreContainerRef.get();
		coreContainer.load(solrBase.getAbsolutePath(), configFile);

		// register the embedded repository type
		getServiceHelper().registerService(RepositoryProvider.class.getName(), new SolrRepositoryProvider(coreContainer), "Eclipse Gyrex", "Apache Solr Repository provider implementation.", null, null);
	}
}
