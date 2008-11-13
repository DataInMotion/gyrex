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
package org.eclipse.cloudfree.persistence.solr.internal;

import java.io.File;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.io.FileUtils;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.eclipse.cloudfree.common.runtime.BaseBundleActivator;
import org.eclipse.cloudfree.common.services.IServiceProxy;
import org.eclipse.cloudfree.configuration.PlatformConfiguration;
import org.eclipse.cloudfree.persistence.storage.type.RepositoryType;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.osgi.service.datalocation.Location;
import org.osgi.framework.BundleContext;

public class SolrActivator extends BaseBundleActivator {

	public static final String PLUGIN_ID = "org.eclipse.cloudfree.persistence.solr";
	private static final AtomicReference<SolrActivator> instance = new AtomicReference<SolrActivator>();

	/**
	 * Returns the instance.
	 * 
	 * @return the instance
	 */
	public static SolrActivator getInstance() {
		return instance.get();
	}

	private final AtomicReference<CoreContainer> coreContainerRef = new AtomicReference<CoreContainer>();
	private final AtomicReference<SolrCore> adminCoreRef = new AtomicReference<SolrCore>();
	private final AtomicReference<IServiceProxy<Location>> instanceLocationRef = new AtomicReference<IServiceProxy<Location>>();
	private volatile File solrBase;

	/**
	 * Creates a new instance.
	 */
	public SolrActivator() {
		super(PLUGIN_ID);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cloudfree.common.runtime.BaseBundleActivator#doStart(org.osgi.framework.BundleContext)
	 */
	@Override
	protected void doStart(final BundleContext context) throws Exception {
		instance.set(this);

		// get instance location
		instanceLocationRef.set(getServiceHelper().trackService(Location.class, context.createFilter(Location.INSTANCE_FILTER)));

		// start server
		startEmbeddedSolrServer(context);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.cloudfree.common.runtime.BaseBundleActivator#doStop(org.osgi.framework.BundleContext)
	 */
	@Override
	protected void doStop(final BundleContext context) throws Exception {
		shutdownEmbeddedSolrServer();
		instance.set(null);
	}

	public SolrCore getEmbeddedAdminCore() {
		return adminCoreRef.get();
	}

	public CoreContainer getEmbeddedCoreContainer() {
		return coreContainerRef.get();
	}

	public File getEmbeddedSolrBase() {
		return solrBase;
	}

	public Location getInstanceLocation() {
		final IServiceProxy<Location> serviceProxy = instanceLocationRef.get();
		if (null == serviceProxy) {
			throw createBundleInactiveException();
		}

		return serviceProxy.getService();
	}

	private void shutdownEmbeddedSolrServer() {
		final SolrCore adminCore = adminCoreRef.getAndSet(null);
		if (null != adminCore) {
			adminCore.close();
		}
		final CoreContainer coreContainer = coreContainerRef.getAndSet(null);
		if (null != coreContainer) {
			coreContainer.persist();
			coreContainer.shutdown();
		}
	}

	private void startEmbeddedSolrServer(final BundleContext context) throws Exception {
		// only in dev mode
		if (!PlatformConfiguration.isOperatingInDevelopmentMode()) {
			return;
		}

		// disable Solr logging
		final Logger log = Logger.getLogger("org.apache.solr");
		log.log(Level.INFO, "Changing log level to WARNING");
		log.setLevel(Level.WARNING);

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
			FileUtils.copyDirectory(configTemplate, solrBase);
		}

		// get multicore config file
		final File configFile = new File(solrBase, "solr.xml");
		if (null == configFile) {
			throw new IllegalStateException("no file system support available");
		}
		if (!configFile.isFile()) {
			throw new IllegalStateException("config file '" + configFile.getPath() + "' is missing");
		}

		// create core container
		if (!coreContainerRef.compareAndSet(null, new CoreContainer())) {
			// already initialized
			return;
		}

		final CoreContainer coreContainer = coreContainerRef.get();
		coreContainer.load(solrBase.getAbsolutePath(), configFile);

		// set admin core
		adminCoreRef.set(coreContainer.getCore("admin"));
		final SolrCore adminCore = adminCoreRef.get();
		if (null == adminCore) {
			throw new IllegalStateException("admin core not available");
		}
		coreContainer.setAdminCore(adminCore);

		// register the embedded repository type
		getServiceHelper().registerService(RepositoryType.class.getName(), new EmbeddedSolrRepositoryType(coreContainer), "CloudFree.net", "Embedded Solr Repository", null, null);
	}
}
