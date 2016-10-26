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
package org.eclipse.gyrex.common.scanner;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.EmptyVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bundle scanner utility which collects annotated classes.
 */
public class BundleAnnotatedClassScanner {

	private static final Logger LOG = LoggerFactory.getLogger(BundleAnnotatedClassScanner.class);

	final Bundle bundle;
	final Set<Class<?>> annotatedClasses = new HashSet<>();
	final Set<String> annotations;

	private final ClassVisitor classVisitor = new EmptyVisitor() {

		private String currentClassName;
		private boolean hasProperVisibility;
		private boolean annotationFound;

		private boolean isPublic(final int access) {
			return (access & Opcodes.ACC_PUBLIC) != 0;
		}

		private boolean isStatic(final int access) {
			return (access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC;
		}

		@Override
		public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
			currentClassName = name;
			hasProperVisibility = isPublic(access);
			annotationFound = false;
		}

		@Override
		public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
			if (annotations.contains(desc)) {
				annotationFound = true;
			}
			return null;
		}

		@Override
		public void visitEnd() {
			if (hasProperVisibility && annotationFound) {
				try {
					annotatedClasses.add(bundle.loadClass(currentClassName.replaceAll("/", ".")));
				} catch (final ClassNotFoundException e) {
					throw new IllegalStateException(String.format("Unable to load class '%s' in bundle '%s'. %s", currentClassName, bundle, ExceptionUtils.getRootCauseMessage(e)), e);
				}
			}
		}

		@Override
		public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
			// inner classes need to be public as well as static
			if (StringUtils.equals(name, currentClassName)) {
				hasProperVisibility = isPublic(access) && isStatic(access);
			}
		}
	};

	@SafeVarargs
	public BundleAnnotatedClassScanner(final Bundle bundle, final Class<? extends Annotation>... annotations) {
		if (bundle == null)
			throw new IllegalArgumentException("bundle must be provided");
		if ((annotations == null) || (annotations.length == 0))
			throw new IllegalArgumentException("annotations must be provided");
		this.bundle = bundle;
		this.annotations = new HashSet<>();
		for (final Class<? extends Annotation> annotaton : annotations) {
			this.annotations.add("L" + annotaton.getName().replaceAll("\\.", "/") + ";");
		}
	}

	public Set<Class<?>> scan() {
		LOG.trace("Scanning bundle '{}' for annotated classes.", bundle);

		final BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
		if (null == bundleWiring)
			throw new IllegalStateException(String.format("No wiring available for bundle '%s'", bundle));

		final ClassLoader loader = bundleWiring.getClassLoader();
		if (null == loader)
			throw new IllegalStateException(String.format("No class loader available for bundle '%s'", bundle));

		final Collection<String> resources = bundleWiring.listResources("/", "*.class", BundleWiring.LISTRESOURCES_LOCAL | BundleWiring.LISTRESOURCES_RECURSE);
		if (null == resources)
			throw new IllegalStateException(String.format("No resources available for bundle '%s'", bundle));
		for (final String resource : resources) {
			LOG.trace("Found resource: {}", resource);
			try (InputStream in = loader.getResourceAsStream(resource)) {
				new ClassReader(in).accept(classVisitor, 0);
			} catch (final IOException e) {
				throw new IllegalStateException(String.format("Error scanning resource '%s': %s", resource, ExceptionUtils.getRootCauseMessage(e)), e);
			}
		}

		return annotatedClasses;
	}
}
