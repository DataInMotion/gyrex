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
package org.eclipse.gyrex.search.internal.solr.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.gyrex.search.facets.IFacet;
import org.eclipse.gyrex.search.query.IAttributeFilter;
import org.eclipse.gyrex.search.query.IFacetFilter;
import org.eclipse.gyrex.search.query.IQuery;
import org.eclipse.gyrex.search.query.ResultProjection;
import org.eclipse.gyrex.search.query.SortDirection;

import org.eclipse.core.runtime.PlatformObject;

import org.apache.commons.lang.StringUtils;

/**
 * Solr {@link IQuery} implementation.
 */
public class QueryImpl extends PlatformObject implements IQuery {

	private String query;
	private String advancedQuery;

	private long startIndex = 0;
	private int maxResults = 10;
	private ResultProjection resultProjection = ResultProjection.COMPACT;
	private Map<String, IFacet> facetsInUse;

	private final List<String> filterQueries = new ArrayList<String>(4);
	private final List<IAttributeFilter> attributeFilters = new ArrayList<IAttributeFilter>(4);
	private final List<IFacetFilter> facetFilters = new ArrayList<IFacetFilter>(4);

	private final LinkedHashMap<String, SortDirection> sortFields = new LinkedHashMap<String, SortDirection>(2);
	private final LinkedHashMap<String, String> queryOptions = new LinkedHashMap<String, String>(2);

	@Override
	public IAttributeFilter addAttributeFilter(final String attributeId) {
		final AttributeFilter filter = new AttributeFilter(attributeId);
		attributeFilters.add(filter);
		return filter;
	}

	@Override
	public IFacetFilter addFacetFilter(final IFacet facet) {
		final FacetFilter filter = new FacetFilter(facet);
		facetFilters.add(filter);
		return filter;
	}

	@Override
	public QueryImpl addFilterQuery(final String filterQuery) {
		if (StringUtils.isNotBlank(filterQuery)) {
			filterQueries.add(filterQuery);
		}
		return this;
	}

	@Override
	public QueryImpl addSortField(final String fieldName, final SortDirection direction) {
		if (StringUtils.isNotBlank(fieldName)) {
			sortFields.remove(fieldName);
			sortFields.put(fieldName, direction);
		}
		return this;
	}

	@Override
	public String getAdvancedQuery() {
		return advancedQuery;
	}

	@Override
	public List<IAttributeFilter> getAttributeFilters() {
		return Collections.unmodifiableList(attributeFilters);
	}

	@Override
	public List<IFacetFilter> getFacetFilters() {
		return Collections.unmodifiableList(facetFilters);
	}

	/**
	 * Returns the facetsInUse.
	 * 
	 * @return the facetsInUse
	 */
	public Map<String, IFacet> getFacetsInUse() {
		return facetsInUse;
	}

	@Override
	public List<String> getFilterQueries() {
		return Collections.unmodifiableList(filterQueries);
	}

	@Override
	public int getMaxResults() {
		return maxResults;
	}

	@Override
	public String getQuery() {
		return query;
	}

	@Override
	public Map<String, String> getQueryOptions() {
		return Collections.unmodifiableMap(queryOptions);
	}

	@Override
	public ResultProjection getResultProjection() {
		return resultProjection;
	}

	@Override
	public Map<String, SortDirection> getSortFields() {
		return Collections.unmodifiableMap(sortFields);
	}

	@Override
	public long getStartIndex() {
		return startIndex;
	}

	@Override
	public QueryImpl setAdvancedQuery(final String advancedQuery) {
		if (StringUtils.isNotEmpty(advancedQuery)) {
			this.advancedQuery = advancedQuery;
		} else {
			this.advancedQuery = null;
		}
		return this;
	}

	/**
	 * Sets the facetsInUse.
	 * 
	 * @param facetsInUse
	 *            the facetsInUse to set
	 */
	public void setFacetsInUse(final Map<String, IFacet> facetsInUse) {
		this.facetsInUse = facetsInUse;
	}

	@Override
	public QueryImpl setMaxResults(final int maxResults) {
		if (maxResults < 0) {
			throw new IllegalArgumentException("maxResults must not be negative");
		}
		this.maxResults = maxResults;
		return this;
	}

	@Override
	public QueryImpl setQuery(final String query) {
		this.query = query;
		return this;
	}

	@Override
	public IQuery setQueryOption(final String name, final String value) {
		if (null == name) {
			throw new IllegalArgumentException("name must not be null");
		}

		if (null != value) {
			// set value
			queryOptions.put(name, value);
		} else {
			// null value means unset
			queryOptions.remove(name);
		}

		return this;
	}

	@Override
	public void setResultProjection(final ResultProjection resultProjection) {
		this.resultProjection = resultProjection;
	}

	@Override
	public QueryImpl setSortField(final String fieldName, final SortDirection direction) {
		sortFields.clear();
		if (StringUtils.isNotBlank(fieldName)) {
			sortFields.put(fieldName, direction);
		}
		return this;
	}

	@Override
	public QueryImpl setStartIndex(final long startIndex) {
		this.startIndex = startIndex;
		return this;
	}

	@Override
	public String toString() {
		final StringBuilder toString = new StringBuilder();
		toString.append("SolrQuery[");
		if (null != advancedQuery) {
			toString.append(" advancedQuery(").append(advancedQuery).append(')');
		}
		if (null != query) {
			toString.append(" query(").append(query).append(')');
		}
		toString.append(" filterQueries(").append(filterQueries).append(')');
		toString.append(" sortFields(").append(sortFields).append(')');
		toString.append(" startIndex(").append(startIndex).append(')');
		toString.append(" maxResults(").append(maxResults).append(')');
		toString.append(" queryOptions(").append(queryOptions).append(')');
		toString.append(" ]");
		return toString.toString();
	}

}
