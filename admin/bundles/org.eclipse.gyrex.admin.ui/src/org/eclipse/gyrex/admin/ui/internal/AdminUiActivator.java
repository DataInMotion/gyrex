/*******************************************************************************
 * Copyright (c) 2010, 2013 AGETO Service GmbH and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Contributors:
 *      Mike Tschierschke - initial API and implementation
 *******************************************************************************/
package org.eclipse.gyrex.admin.ui.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.gyrex.admin.ui.internal.application.AdminApplicationConfiguration;
import org.eclipse.gyrex.admin.ui.internal.jetty.AdminServletHolder;
import org.eclipse.gyrex.admin.ui.internal.jetty.SimpleAdminLoginService;
import org.eclipse.gyrex.admin.ui.internal.servlets.AdminServletTracker;
import org.eclipse.gyrex.boot.internal.app.ServerApplication;
import org.eclipse.gyrex.common.runtime.BaseBundleActivator;
import org.eclipse.gyrex.monitoring.diagnostics.StatusTracker;
import org.eclipse.gyrex.server.Platform;
import org.eclipse.gyrex.server.settings.SystemSetting;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.application.ApplicationRunner;
import org.eclipse.rap.rwt.engine.RWTServlet;
import org.eclipse.swt.widgets.Display;

import org.osgi.framework.BundleContext;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The activator of the admin ui bundle. Serves also images.
 */
public class AdminUiActivator extends BaseBundleActivator {

	public static final String SYMBOLIC_NAME = "org.eclipse.gyrex.admin.ui"; //$NON-NLS-1$

	private static final String IMAGE_REGISTRY = SYMBOLIC_NAME + "#imageRegistry";

	private static final int DEFAULT_ADMIN_PORT = 3110;
	private static final Logger LOG = LoggerFactory.getLogger(AdminUiActivator.class);

	private static volatile AdminUiActivator instance;
	private static volatile Server server;

	private static final SystemSetting<Boolean> useSslConnector = SystemSetting.newBooleanSetting("gyrex.admin.secure", "enables the Gyrex Admin UI to be deliviered via HTTPS instead of plain HTTP").usingDefault(Boolean.FALSE).create();
	private static final SystemSetting<String> authenticationConfigString = SystemSetting.newStringSetting("gyrex.admin.auth", "authentication string containing username and password hash").create();

	private static final SystemSetting<Integer> adminHttpPort = SystemSetting.newIntegerSetting("gyrex.admin.http.port", "port of the Gyrex Admin UI").usingDefault(Platform.getInstancePort(DEFAULT_ADMIN_PORT)).create();
	private static final SystemSetting<String> adminHttpHost = SystemSetting.newStringSetting("gyrex.admin.http.host", "host address the Gyrex Admin UI should accept requests on").create();

	/**
	 * Returns an image descriptor for the image file at the given plug-in
	 * relative path
	 *
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(final String path) {
		return ImageDescriptor.createFromURL(FileLocator.find(instance.getBundle(), new Path(path), null));
	}

	/**
	 * Returns the instance.
	 *
	 * @return the instance
	 */
	public static AdminUiActivator getInstance() {
		final AdminUiActivator activator = instance;
		if (activator == null)
			throw new IllegalStateException("inactive");
		return activator;
	}

	private ApplicationRunner adminApplicationRunner;
	private StatusTracker statusTracker;

	/**
	 * The constructor
	 */
	public AdminUiActivator() {
		super(SYMBOLIC_NAME);
	}

	private void addNonSslConnector(final Server server) {
		final HttpConfiguration httpConfiguration = new HttpConfiguration();
		httpConfiguration.setSendServerVersion(false);
		httpConfiguration.setSendDateHeader(false);

		final ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));

		connector.setPort(adminHttpPort.get());
		if (adminHttpHost.isSet()) {
			connector.setHost(adminHttpHost.get());
		}
		connector.setIdleTimeout(60000);

		// see https://bugs.eclipse.org/bugs/show_bug.cgi?id=356988 for an issue
		// with configuring the connector

		server.addConnector(connector);
	}

	private void addSslConnector(final Server server) {
		try {

			final File keystoreFile = Platform.getStateLocation(AdminUiActivator.getInstance().getBundle()).append("jettycerts").toFile();
			if (!keystoreFile.isFile()) {
				if (!keystoreFile.getParentFile().isDirectory() && !keystoreFile.getParentFile().mkdirs())
					throw new IllegalStateException("Error creating directory for jetty ssl certificates");

				final InputStream stream = getBundle().getEntry("cert/jettycerts.jks").openStream();
				FileUtils.copyInputStreamToFile(stream, keystoreFile);
				IOUtils.closeQuietly(stream);
			}

			final SslContextFactory sslContextFactory = new SslContextFactory(keystoreFile.getCanonicalPath());
			sslContextFactory.setKeyStorePassword("changeit");
			sslContextFactory.setKeyManagerPassword("changeit");

			final HttpConfiguration httpConfiguration = new HttpConfiguration();
			httpConfiguration.setSendServerVersion(false);
			httpConfiguration.setSendDateHeader(false);
			httpConfiguration.setSecurePort(adminHttpPort.get());

			final ServerConnector connector = new ServerConnector(server, sslContextFactory, new HttpConnectionFactory(httpConfiguration));

			connector.setPort(adminHttpPort.get());
			if (adminHttpHost.isSet()) {
				connector.setHost(adminHttpHost.get());
			}
			connector.setIdleTimeout(60000);

			server.addConnector(connector);
		} catch (final Exception e) {
			throw new IllegalStateException("Error configuring jetty ssl connector for admin ui.", e);
		}

	}

	private void configureContextWithServletsAndResources(final ServletContextHandler contextHandler) throws MalformedURLException, IOException {
		// configure context base directory (required for RAP/RWT resources)
		final IPath contextBase = Platform.getStateLocation(getBundle()).append("context");
		contextHandler.setBaseResource(Resource.newResource(contextBase.toFile()));

		// configure defaults for resources served by Jetty's DefaultServlet
		if (Platform.inDevelopmentMode()) {
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "true");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.maxCachedFiles", "0");
		} else {
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", "false");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.maxCacheSize", "2000000");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.maxCachedFileSize", "254000");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.maxCachedFiles", "1000");
			contextHandler.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "true");
		}

		// initialize and start RWT application
		adminApplicationRunner = new ApplicationRunner(new AdminApplicationConfiguration(), contextHandler.getServletContext());
		adminApplicationRunner.start();

		// serve admin application directly
		contextHandler.addServlet(new AdminServletHolder(new RWTServlet()), "/admin");

		// register additional static resources references in widgets
		final ServletHolder staticResources = new AdminServletHolder(new DefaultServlet());
		staticResources.setInitParameter("resourceBase", FileLocator.resolve(FileLocator.find(getBundle(), new Path("html"), null)).toExternalForm());
		contextHandler.addServlet(staticResources, "/static/*");

		// redirect to admin
		contextHandler.addServlet(new AdminServletHolder(new HttpServlet() {
			private static final long serialVersionUID = 1L;

			@Override
			protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
				resp.sendRedirect("/admin");
			}
		}), "");

		// serve context resources (required for RAP/RWT resources)
		contextHandler.addServlet(new AdminServletHolder(new DefaultServlet()), "/" + ApplicationRunner.RESOURCES + "/*");

		// register Logback status servlet
		try {
			// note, we don't reference the class directly because the package import is optional
			final Class<?> servletClass = AdminUiActivator.getInstance().getBundle().loadClass("ch.qos.logback.classic.ViewStatusMessagesServlet");
			contextHandler.addServlet(new AdminServletHolder((Servlet) servletClass.newInstance()), "/logbackstatus");
		} catch (final ClassNotFoundException | LinkageError e) {
			LOG.warn("Logback status servlet not available. {}", e.getMessage(), e);
		} catch (final Exception e) {
			LOG.error("An error occurred while registering the Logback status servlet. {}", e.getMessage(), e);
		}

		// allow extension using custom servlets
		final AdminServletTracker adminServletTracker = new AdminServletTracker(getBundle().getBundleContext(), contextHandler);
		contextHandler.addBean(new AbstractLifeCycle() {
			@Override
			protected void doStart() throws Exception {
				adminServletTracker.open();
			}

			@Override
			protected void doStop() throws Exception {
				adminServletTracker.close();
			}
		});
	}

	private SecurityHandler createSecurityHandler(final Handler baseHandler, final String username, final String password) {
		final ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
		final ConstraintMapping authenticationContraintMapping = new ConstraintMapping();
		final Constraint constraint = new Constraint(Constraint.__BASIC_AUTH, AdminServletHolder.ADMIN_ROLE);
		constraint.setAuthenticate(true);
		authenticationContraintMapping.setConstraint(constraint);
		authenticationContraintMapping.setPathSpec("/*");
		securityHandler.addConstraintMapping(authenticationContraintMapping);
		securityHandler.setAuthenticator(new BasicAuthenticator());
		securityHandler.setHandler(baseHandler);
		securityHandler.setLoginService(new SimpleAdminLoginService(username, password));
		return securityHandler;
	}

	private SessionHandler createSessionHandler() {
		final SessionHandler sessionHandler = new SessionHandler();
		sessionHandler.setMaxInactiveInterval(1200);
		sessionHandler.setUsingCookies(false); // allows to use RAP in multiple tabs
		return sessionHandler;
	}

	@Override
	protected void doStart(final BundleContext context) throws Exception {
		instance = this;

		statusTracker = new StatusTracker(context);
		statusTracker.open();

		// start the admin server asynchronously
		final Job jettyStartJob = new Job("Start Jetty Admin Server") {

			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				try {
					startServer();
				} catch (final Exception e) {
					LOG.error("Failed to start Jetty Admin server.", e);
					ServerApplication.shutdown(new IllegalStateException("Unable to start Jetty admin server.", e));
					return Status.CANCEL_STATUS;
				}
				return Status.OK_STATUS;
			}
		};
		jettyStartJob.setSystem(true);
		jettyStartJob.setPriority(Job.LONG);
		jettyStartJob.schedule();
	}

	@Override
	protected void doStop(final BundleContext context) throws Exception {
		instance = null;

		statusTracker.close();
		statusTracker = null;

		stopServer();
	}

	public ImageRegistry getImageRegistry() {
		// ImageRegistry must be session scoped in RAP
		ImageRegistry imageRegistry = (ImageRegistry) RWT.getUISession().getAttribute(IMAGE_REGISTRY);
		if (imageRegistry == null) {
			imageRegistry = new ImageRegistry(Display.getCurrent());
			AdminUiImages.initializeImageRegistry(imageRegistry);
			RWT.getUISession().setAttribute(IMAGE_REGISTRY, imageRegistry);
		}
		return imageRegistry;
	}

	public IStatus getSystemStatus() {
		final StatusTracker tracker = statusTracker;
		if (tracker == null)
			throw createBundleInactiveException();
		return tracker.getSystemStatus();
	}

	private void startServer() {
		try {
			server = new Server();

			if (useSslConnector.isTrue()) {
				addSslConnector(server);
			} else {
				addNonSslConnector(server);
			}

			// tweak server
			server.setStopAtShutdown(true);
			server.setStopTimeout(5000);

			// create context
			final ServletContextHandler contextHandler = new ServletContextHandler();
			contextHandler.setSessionHandler(createSessionHandler());
			configureContextWithServletsAndResources(contextHandler);

			// enable authentication if configured
			final String authenticationPhrase = authenticationConfigString.get();
			if (useSslConnector.isTrue() && StringUtils.isNotBlank(authenticationPhrase)) {
				final String[] segments = authenticationPhrase.split("/");
				if (segments.length != 3)
					throw new IllegalArgumentException("Illegal authentication configuration. Must be three string separated by '/'");
				else if (!StringUtils.equals(segments[0], "BASIC"))
					throw new IllegalArgumentException("Illegal authentication configuration. Only method 'BASIC' is supported. Found " + segments[0]);

				server.setHandler(createSecurityHandler(contextHandler, segments[1], segments[2]));
			} else {
				server.setHandler(contextHandler);
			}
			server.start();

		} catch (final Exception e) {
			throw new IllegalStateException("Error starting jetty for admin ui", e);
		}
	}

	private void stopServer() {
		try {
			adminApplicationRunner.stop();
			adminApplicationRunner = null;

			server.stop();
			server = null;
		} catch (final Exception e) {
			throw new IllegalStateException("Error stopping jetty for admin ui", e);
		}
	}
}
