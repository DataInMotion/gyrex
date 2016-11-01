/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.eclipse.gyrex.http.jaxrs.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.glassfish.jersey.message.internal.MediaTypes;
import org.glassfish.jersey.server.wadl.WadlApplicationContext;
import org.glassfish.jersey.server.wadl.internal.ApplicationDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A resource for providing WADL as a way of describing/documenting the
 * available API.
 * <p>
 * This resources is based on a similar resource shipped with Jersey. However,
 * it contains a few enhancements:
 * <ul>
 * <li>Caching of multiple different outputs as well as support for conditional
 * caching</li>
 * <li>Proper support of an OSGi runtime environment</li>
 * <li>Support for injecting XSL style-sheet header information into the
 * generated WADL</li>
 * </ul>
 * </p>
 */
@Singleton
@Path("/application.wadl")
public class WadlResource {

	static class WadlRepresentation {
		final byte[] wadlXml;
		final Date lastModified;

		public WadlRepresentation(final byte[] wadlXml) {
			this.wadlXml = wadlXml;
			lastModified = new Date();
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(WadlResource.class);

	private final Map<String, WadlRepresentation> wadlRepresentationByBaseUri = new HashMap<>();
	private final WadlApplicationContext wadlContext;
	private String xslStylesheetLocation;

	public WadlResource(@Context final WadlApplicationContext wadlContext) {
		this.wadlContext = wadlContext;
	}

	@Produces({ "*/*" })
	@GET
	@Path("{path}")
	public synchronized Response geExternalGramar(@Context final UriInfo uriInfo, @PathParam("path") final String path) {
		if (!wadlContext.isWadlGenerationEnabled()) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		// fail if we don't have any metadata for this path
		final ApplicationDescription.ExternalGrammar externalMetadata = wadlContext.getApplication(uriInfo).getExternalGrammar(path);
		if (externalMetadata == null) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		// Return the data
		return Response.ok().type(externalMetadata.getType()).entity(externalMetadata.getContent()).build();
	}

	protected String getStylesheetLocation(final UriInfo uriInfo) {
		final String location = getXslStylesheetLocation();
		if (location != null) {
			// use custom location if available
			return location;
		}
		// fallback to generic one
		return "https://raw.github.com/ipcsystems/wadl-stylesheet/master/wadl.xsl";
	}

	/**
	 * Generates a WADL describing the available resources, operations and data
	 * types of all resources available in the application.
	 *
	 * @param request
	 *            the request
	 * @param uriInfo
	 *            the uri info
	 * @param providers
	 *            available providers (used for producing JSON if necessary)
	 * @return a response to be send to the caller
	 */
	@GET
	@Produces({ MediaTypes.WADL_STRING, MediaTypes.WADL_JSON_STRING, MediaType.APPLICATION_XML })
	public synchronized Response getWadl(@Context final Request request, @Context final UriInfo uriInfo, @Context final Providers providers) {
		if (!wadlContext.isWadlGenerationEnabled()) {
			return Response.status(Response.Status.NOT_FOUND).build();
		}

		// select the right variant based on the request type
		final List<Variant> vl = Variant.mediaTypes(MediaTypes.WADL, MediaTypes.WADL_JSON, MediaType.APPLICATION_XML_TYPE).add().build();
		final Variant v = request.selectVariant(vl);
		if (v == null) {
			return Response.notAcceptable(vl).build();
		}

		// support caching
		final String cacheKey = DigestUtils.md5Hex(uriInfo.getBaseUri().toASCIIString() + "--" + v.toString());
		final EntityTag eTag = new EntityTag(cacheKey);
		final CacheControl cc = new CacheControl();
		cc.setMaxAge(86400);
		cc.setPrivate(true);

		// check for cached version
		WadlRepresentation wadlRepresentation = wadlRepresentationByBaseUri.get(cacheKey);
		if (wadlRepresentation != null) {
			ResponseBuilder builder = request.evaluatePreconditions(wadlRepresentation.lastModified, eTag);
			if (builder == null) {
				builder = Response.ok(new ByteArrayInputStream(wadlRepresentation.wadlXml));
				builder.tag(eTag);
				builder.lastModified(wadlRepresentation.lastModified);
			}
			builder.cacheControl(cc);
			return builder.build();
		}

		// build new version
		try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
			final ApplicationDescription applicationDescription = wadlContext.getApplication(uriInfo, true);
			final Application application = applicationDescription.getApplication();
			if (v.getMediaType().equals(MediaTypes.WADL) || v.getMediaType().equals(MediaType.APPLICATION_XML_TYPE)) {
				try {
					final JAXBContext jaxbContext = wadlContext.getJAXBContext();
					final Marshaller marshaller = jaxbContext.createMarshaller();
					final String xslHeader = "<?xml-stylesheet type='text/xsl' href='" + getStylesheetLocation(uriInfo) + "'?>";
					try {
						marshaller.setProperty("com.sun.xml.internal.bind.xmlHeaders", xslHeader);
					} catch (final PropertyException pex) {
						marshaller.setProperty("com.sun.xml.bind.xmlHeaders", xslHeader);
					}
					marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
					marshaller.marshal(application, os);
				} catch (final Exception e) {
					LOG.error("Could not marshal wadl Application. {}", ExceptionUtils.getRootCauseMessage(e), e);
					return Response.serverError().build();
				}
			} else {
				final MessageBodyWriter<Application> messageBodyWriter = providers.getMessageBodyWriter(Application.class, null, new Annotation[0], v.getMediaType());
				if (messageBodyWriter == null) {
					return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
				}

				try {
					messageBodyWriter.writeTo(application, Application.class, null, new Annotation[0], v.getMediaType(), null /* headers */, os);
				} catch (final Exception e) {
					LOG.error("Could not serialize wadl Application. {}", ExceptionUtils.getRootCauseMessage(e), e);
					return Response.serverError().build();
				}
			}
			wadlRepresentation = new WadlRepresentation(os.toByteArray());
		} catch (final IOException e) {
			// should not happen for ByteArrayOutputStream
			return Response.serverError().entity(ExceptionUtils.getRootCauseMessage(e)).build();
		}

		// put into cache
		wadlRepresentationByBaseUri.put(cacheKey, wadlRepresentation);

		// build response
		final ResponseBuilder builder = Response.ok(new ByteArrayInputStream(wadlRepresentation.wadlXml));
		builder.tag(eTag);
		builder.lastModified(wadlRepresentation.lastModified);
		builder.cacheControl(cc);
		return builder.build();
	}

	/**
	 * Returns the xslStylesheetLocation.
	 *
	 * @return the xslStylesheetLocation
	 */
	public String getXslStylesheetLocation() {
		return xslStylesheetLocation;
	}

	/**
	 * Sets the xslStylesheetLocation.
	 *
	 * @param xslStylesheetLocation
	 *            the xslStylesheetLocation to set
	 */
	public void setXslStylesheetLocation(final String xslStylesheetLocation) {
		this.xslStylesheetLocation = xslStylesheetLocation;
	}
}
