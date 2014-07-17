/**
 * Copyright (c) 2011, 2012 Gunnar Wagenknecht and others.
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Gunnar Wagenknecht - initial API and implementation
 */
package org.eclipse.gyrex.admin.ui.internal.wizards.dialogfields;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.rap.rwt.RWT;
import org.eclipse.rap.rwt.service.ServerPushSession;
import org.eclipse.rap.rwt.service.ServiceHandler;
import org.eclipse.rap.rwt.widgets.FileUpload;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import org.apache.commons.fileupload.FileItemHeaders;
import org.apache.commons.fileupload.FileItemHeadersSupport;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang.math.NumberUtils;

/**
 * DialogField using RWT Upload widget
 */
public class UploadDialogField extends DialogField {

	private interface IUploadHandlerListener {

		void uploadFailed(Throwable e);

		void uploadFinished(String fileName);

	}

	/**
	 * RAP handler for receiving uploads.
	 */
	private final class UploadHandler implements ServiceHandler {

		final Pattern jSessionIdPattern = Pattern.compile(";jsessionid=[a-z0-9A-Z]+");
		private final String handlerId;
		private final IUploadAdapter uploadAdapter;
		private IUploadHandlerListener listener;

		public UploadHandler(final IUploadAdapter uploadAdapter) {
			this.uploadAdapter = uploadAdapter;
			handlerId = UploadHandler.class.getName() + "@" + System.identityHashCode(this);
			if (!uploadHandlerRef.compareAndSet(null, this))
				throw new IllegalStateException("Concurrent upload in progress!");
			RWT.getServiceManager().registerServiceHandler(handlerId, this);
		}

		public void dispose() {
			uploadHandlerRef.compareAndSet(this, null);
			RWT.getServiceManager().unregisterServiceHandler(handlerId);
		}

		private long getContentLength(final FileItemHeadersSupport itemWithHeaders) {
			final FileItemHeaders headers = itemWithHeaders.getHeaders();
			return NumberUtils.toLong(null != headers ? headers.getHeader(FileUploadBase.CONTENT_LENGTH) : null, -1L);
		}

		public String getUploadUrl() {
			// must return absolute url (see discussion on bug 437211 for details and limitation)
			// strip ';jsessionid=... part
			final String requestURI = jSessionIdPattern.matcher(RWT.getRequest().getRequestURI()).replaceAll(StringUtils.EMPTY);
			final String serviceHandlerUrl = jSessionIdPattern.matcher(RWT.getServiceManager().getServiceHandlerUrl(handlerId)).replaceAll(StringUtils.EMPTY);
			if (!serviceHandlerUrl.startsWith(requestURI))
				return RWT.getResponse().encodeURL(requestURI + serviceHandlerUrl);
			else
				return RWT.getResponse().encodeURL(serviceHandlerUrl);
		}

		@Override
		public void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
			// Ignore requests to this service handler without a valid session for security reasons
			final boolean hasSession = request.getSession(false) != null;
			if (!hasSession) {
				response.sendError(HttpServletResponse.SC_BAD_REQUEST);
				return;
			}
			if (!"POST".equals(request.getMethod().toUpperCase())) {
				response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				return;
			}

			if (!ServletFileUpload.isMultipartContent(request)) {
				response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
				return;
			}

			final ServletFileUpload servletFileUpload = new ServletFileUpload();
			servletFileUpload.setFileSizeMax(1024 * 1024 * 5); // 5 MB

			try {
				final FileItemIterator itemIterator = servletFileUpload.getItemIterator(request);
				while (itemIterator.hasNext()) {
					final FileItemStream item = itemIterator.next();
					if (item.isFormField()) {
						continue; // skip form fields (just files)
					}

					final InputStream stream = item.openStream();
					try {
						uploadAdapter.receive(stream, item.getName(), item.getContentType(), getContentLength(item));
					} finally {
						IOUtils.closeQuietly(stream);
					}

					// at most one file will be received; unregister the handler and return
					try {
						if (null != listener) {
							listener.uploadFinished(item.getName());
						}
					} finally {
						dispose();
					}
					return;
				}
			} catch (final VirtualMachineError e) {
				throw e;
			} catch (final Throwable t) {
				if (null != listener) {
					listener.uploadFailed(t);
				}
				throw new ServletException("Error retrieving files.", t);
			}

			// no files?
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
		}

		public void setListener(final IUploadHandlerListener listener) {
			this.listener = listener;
		}
	}

	protected static GridData gridDataForUpload(final int span) {
		final GridData gd = new GridData();
		gd.horizontalAlignment = GridData.FILL;
		gd.grabExcessHorizontalSpace = false;
		gd.horizontalSpan = span;
		return gd;
	}

	final AtomicReference<UploadHandler> uploadHandlerRef = new AtomicReference<UploadHandler>();

	private String uploadButtonLabel;
	private Text fileText;
	private FileUpload uploadControl;

	private boolean uploadInProgress;
	private String fileName = "";

	public UploadDialogField() {
		super();
		uploadButtonLabel = "Browse..."; //$NON-NLS-1$
	}

	@Override
	public Control[] doFillIntoGrid(final Composite parent, final int nColumns) {
		assertEnoughColumns(nColumns);

		final Label label = getLabelControl(parent);
		label.setLayoutData(gridDataForLabel(1));
		final Text text = getFileTextControl(parent);
		text.setLayoutData(StringDialogField.gridDataForText(nColumns - 2));
		final FileUpload upload = getUploadControl(parent);
		upload.setLayoutData(gridDataForUpload(1));

		return new Control[] { label, text, upload };
	}

	private void doModifyFileName() {
		if (isOkToUse(uploadControl)) {
			fileName = StringUtils.trimToEmpty(uploadControl.getFileName());
			fileText.setText(fileName);
		}
		dialogFieldChanged();
	}

	/**
	 * Returns the name of the selected file.
	 */
	public String getFileName() {
		return fileName;
	}

	public Text getFileTextControl(final Composite parent) {
		if (fileText == null) {
			assertCompositeNotNull(parent);

			fileText = new Text(parent, SWT.BORDER | SWT.SINGLE);
			fileText.setText(fileName);
			fileText.setToolTipText("Selected file");
			fileText.setEditable(false);
			fileText.setFont(parent.getFont());
			fileText.setEnabled(isEnabled());
		}
		return fileText;
	}

	@Override
	public int getNumberOfControls() {
		return 3;
	}

	/**
	 * Creates or returns the created text control.
	 *
	 * @param parent
	 *            The parent composite or <code>null</code> when the widget has
	 *            already been created.
	 * @return the text control
	 */
	public FileUpload getUploadControl(final Composite parent) {
		if (uploadControl == null) {
			assertCompositeNotNull(parent);

			uploadControl = new FileUpload(parent, SWT.NONE);

			uploadControl.setText(uploadButtonLabel);
			uploadControl.setToolTipText("Select a file");
			uploadControl.setFont(parent.getFont());
			uploadControl.addSelectionListener(new SelectionAdapter() {
				/** serialVersionUID */
				private static final long serialVersionUID = 1L;

				@Override
				public void widgetSelected(final SelectionEvent e) {
					doModifyFileName();
				}
			});
		}
		return uploadControl;
	}

	@Override
	public void refresh() {
		super.refresh();
		if (isOkToUse(uploadControl)) {
		}
	}

	@Override
	public boolean setFocus() {
		if (isOkToUse(uploadControl)) {
			uploadControl.setFocus();
		}
		return true;
	}

	/**
	 * Sets the uploadButtonLabel.
	 *
	 * @param uploadButtonLabel
	 *            the uploadButtonLabel to set
	 */
	public void setUploadButtonLabel(final String uploadButtonLabel) {
		this.uploadButtonLabel = uploadButtonLabel;
		if (isOkToUse(uploadControl)) {
			uploadControl.setText(uploadButtonLabel);
		}
	}

	public void startUpload(final IUploadAdapter receiver) {
		uploadInProgress = true;
		updateEnableState();

		// activate background updates
		final ServerPushSession pushSession = new ServerPushSession();

		// create handler
		final UploadHandler handler = new UploadHandler(receiver);
		uploadControl.addDisposeListener(new DisposeListener() {
			/** serialVersionUID */
			private static final long serialVersionUID = 1L;

			@Override
			public void widgetDisposed(final DisposeEvent event) {
				handler.dispose();
				pushSession.stop();
			}
		});

		final Display display = uploadControl.getDisplay();
		final String url = handler.getUploadUrl();
		handler.setListener(new IUploadHandlerListener() {

			@Override
			public void uploadFailed(final Throwable e) {
				handler.dispose();
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						fileText.setText(String.format("ERROR: %s", ExceptionUtils.getRootCauseMessage(e)));
						uploadInProgress = false;
						setUploadButtonLabel(uploadButtonLabel);
						updateEnableState();
						pushSession.stop();
					}
				});
			}

			@Override
			public void uploadFinished(final String fileName) {
				handler.dispose();
				display.asyncExec(new Runnable() {
					@Override
					public void run() {
						if (isOkToUse(fileText)) {
							fileText.setText(getFileName());
						}
						uploadInProgress = false;
						setUploadButtonLabel(uploadButtonLabel);
						updateEnableState();
						pushSession.stop();
					}
				});
			}
		});

		// start upload
		pushSession.start();
		uploadControl.setText("Uploading...");
		uploadControl.submit(url);
	}

	@Override
	protected void updateEnableState() {
		super.updateEnableState();

		if (isOkToUse(fileText)) {
			fileText.setEnabled(isEnabled());
		}
		if (isOkToUse(uploadControl)) {
			uploadControl.setEnabled(isEnabled() && !uploadInProgress);
		}
	}
}
