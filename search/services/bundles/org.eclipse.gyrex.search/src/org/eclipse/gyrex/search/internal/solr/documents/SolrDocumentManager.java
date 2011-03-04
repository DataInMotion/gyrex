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
 *******************************************************************************/
package org.eclipse.gyrex.search.internal.solr.documents;

import org.eclipse.gyrex.context.IRuntimeContext;
import org.eclipse.gyrex.persistence.solr.SolrServerRepository;
import org.eclipse.gyrex.search.internal.SearchActivator;
import org.eclipse.gyrex.search.solr.documents.BaseSolrDocumentManager;

/**
 * A default {@link BaseSolrDocumentManager} that can be used out of the box.
 */
public class SolrDocumentManager extends BaseSolrDocumentManager {

	/**
	 * Creates a new instance.
	 * 
	 * @param context
	 *            the context
	 * @param repository
	 *            the repository
	 */
	SolrDocumentManager(final IRuntimeContext context, final SolrServerRepository repository) {
		super(context, repository, SearchActivator.SYMBOLIC_NAME + ".solr.model.documents.metrics");
	}
}
