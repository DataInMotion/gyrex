/*******************************************************************************
 * Copyright (c) 2010, 2011 AGETO Service GmbH and others.
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

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.gyrex.monitoring.metrics.ThroughputMetric;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.impl.LBHttpSolrServer;
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;

/**
 * A {@link SolrServer} implementation collecting metrics.
 */
public class SolrServerWithMetrics extends SolrServer {

	/** serialVersionUID */
	private static final long serialVersionUID = 7409277302681086835L;

	private final SolrServer server;

	private final SolrRepositoryMetrics metrics;

	private final ThroughputMetric queryThroughput;
	private final ThroughputMetric updateThroughput;
	private final ThroughputMetric adminThroughput;
	private final ThroughputMetric otherThroughput;

	/**
	 * Creates a new instance.
	 */
	public SolrServerWithMetrics(final SolrServer server, final SolrRepositoryMetrics metrics) {
		this.server = server;
		this.metrics = metrics;
		queryThroughput = metrics.getQueryThroughputMetric();
		updateThroughput = metrics.getUpdateThroughputMetric();
		adminThroughput = metrics.getAdminThroughputMetric();
		otherThroughput = metrics.getOtherThroughputMetric();
	}

	private ThroughputMetric getRequestMetric(final SolrRequest request) {
		if (request instanceof QueryRequest) {
			return queryThroughput;
		} else if (request instanceof AbstractUpdateRequest) {
			return updateThroughput;
		} else if (request instanceof CoreAdminRequest) {
			return adminThroughput;
		} else {
			return otherThroughput;
		}
	}

	private void recordException(final SolrRequest request, final Exception e) {
		final StringBuilder requestInfo = new StringBuilder();
		requestInfo.append(request.getClass().getSimpleName());
		requestInfo.append('[');
		requestInfo.append(request.getMethod());
		requestInfo.append(' ');
		requestInfo.append(request.getPath());
		final SolrParams params = request.getParams();
		if (params != null) {
			requestInfo.append(' ');
			requestInfo.append(request.getParams().toNamedList());
		}
		requestInfo.append(']');
		metrics.recordException(requestInfo.toString(), e);
	}

	@Override
	public NamedList<Object> request(final SolrRequest request) throws SolrServerException, IOException {
		final ThroughputMetric requestMetric = getRequestMetric(request);
		final long requestStarted = requestMetric.requestStarted();
		try {
			final NamedList<Object> result = server.request(request);
			requestMetric.requestFinished(result.size(), System.currentTimeMillis() - requestStarted);
			return result;
		} catch (final IOException e) {
			requestMetric.requestFailed();
			recordException(request, e);
			throw e;
		} catch (final SolrServerException e) {
			requestMetric.requestFailed();
			recordException(request, e);
			throw e;
		}
	}

	public void shutdown() {
		// no-op
	}

	@Override
	public String toString() {
		final StringBuilder toString = new StringBuilder();
		toString.append(server.getClass().getSimpleName()).append(" {");

		try {
			toString.append(server.getClass().getMethod("getBaseURL").invoke(server));
		} catch (final Exception e) {
			// ignore (might not be there)
		}

		if (server instanceof EmbeddedSolrServer) {
			try {
				toString.append(EmbeddedSolrServer.class.getDeclaredField("coreName").get(server));
			} catch (final Exception e) {
				toString.append(ExceptionUtils.getRootCauseMessage(e));
			}
		} else if (server instanceof LBHttpSolrServer) {
			final LBHttpSolrServer lbHttpSolrServer = (LBHttpSolrServer) server;
			try {
				final Iterator<?> alive = ((CopyOnWriteArrayList<?>) LBHttpSolrServer.class.getDeclaredField("aliveServers").get(lbHttpSolrServer)).iterator();
				if (alive.hasNext()) {
					toString.append("alive:").append(StringUtils.join(alive, ';'));
				}
				final Iterator<?> zombies = ((CopyOnWriteArrayList<?>) LBHttpSolrServer.class.getDeclaredField("zombieServers").get(lbHttpSolrServer)).iterator();
				if (zombies.hasNext()) {
					toString.append(" zombie:").append(StringUtils.join(zombies, ';'));
				}
			} catch (final Exception e) {
				toString.append(ExceptionUtils.getRootCauseMessage(e));
			}
		}
		toString.append("}");
		return toString.toString();
	}
}
