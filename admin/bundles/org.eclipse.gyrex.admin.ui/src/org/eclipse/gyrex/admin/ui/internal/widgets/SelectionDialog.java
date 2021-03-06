/*******************************************************************************
 * Copyright (c) 2000, 2012 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Gunnar Wagenknecht - fork for Gyrex Admin UI 
 *******************************************************************************/
package org.eclipse.gyrex.admin.ui.internal.widgets;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * The abstract implementation of a selection dialog. It can be primed with
 * initial selections (<code>setInitialSelections</code>), and returns the final
 * selection (via <code>getResult</code>) after completion.
 * <p>
 * Clients may subclass this dialog to inherit its selection facilities.
 * </p>
 * 
 * @since 1.0
 */
public abstract class SelectionDialog extends NonBlockingTrayDialog {
	/** serialVersionUID */
	private static final long serialVersionUID = 1L;

	// the final collection of selected elements, or null if this dialog was
	// canceled
	private Object[] result;

	// a collection of the initially-selected elements
	private List<Object> initialSelections = new ArrayList<>();

	// title of dialog
	private String title;

	// message to show user
	private String message = ""; //$NON-NLS-1$

	// dialog bounds strategy (since 3.2)
	private int dialogBoundsStrategy = Dialog.DIALOG_PERSISTLOCATION | Dialog.DIALOG_PERSISTSIZE;

	// dialog settings for storing bounds (since 3.2)
	private IDialogSettings dialogBoundsSettings = null;

	final String SELECT_ALL_TITLE = WidgetMessages.get().SelectionDialog_selectLabel;
	final String DESELECT_ALL_TITLE = WidgetMessages.get().SelectionDialog_deselectLabel;

	/**
	 * Creates a dialog instance. Note that the dialog will have no visual
	 * representation (no widgets) until it is told to open.
	 * 
	 * @param parentShell
	 *            the parent shell
	 */
	protected SelectionDialog(final Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void configureShell(final Shell shell) {
		super.configureShell(shell);
		if (title != null) {
			shell.setText(title);
		}
	}

	@Override
	protected void createButtonsForButtonBar(final Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.get().OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.get().CANCEL_LABEL, false);
	}

	/**
	 * Creates the message area for this dialog.
	 * <p>
	 * This method is provided to allow subclasses to decide where the message
	 * will appear on the screen.
	 * </p>
	 * 
	 * @param composite
	 *            the parent composite
	 * @return the message label
	 */
	protected Label createMessageArea(final Composite composite) {
		final Label label = new Label(composite, SWT.NONE);
		if (message != null) {
			label.setText(message);
		}
		label.setFont(composite.getFont());
		return label;
	}

	/**
	 * Gets the dialog settings that should be used for remembering the bounds
	 * of the dialog, according to the dialog bounds strategy. Overridden to
	 * provide the dialog settings that were set using
	 * {@link #setDialogBoundsSettings(IDialogSettings, int)}.
	 * 
	 * @return the dialog settings used to store the dialog's location and/or
	 *         size, or <code>null</code> if the dialog's bounds should not be
	 *         stored.
	 * @see Dialog#getDialogBoundsStrategy()
	 * @see #setDialogBoundsSettings(IDialogSettings, int)
	 */
	@Override
	protected IDialogSettings getDialogBoundsSettings() {
		return dialogBoundsSettings;
	}

	/**
	 * Get the integer constant that describes the strategy for persisting the
	 * dialog bounds. Overridden to provide the dialog bounds strategy that was
	 * set using {@link #setDialogBoundsSettings(IDialogSettings, int)}.
	 * 
	 * @return the constant describing the strategy for persisting the dialog
	 *         bounds.
	 * @see Dialog#DIALOG_PERSISTLOCATION
	 * @see Dialog#DIALOG_PERSISTSIZE
	 * @see Dialog#getDialogBoundsSettings()
	 * @see #setDialogBoundsSettings(IDialogSettings, int)
	 */
	@Override
	protected int getDialogBoundsStrategy() {
		return dialogBoundsStrategy;
	}

	/**
	 * Returns the list of initial element selections.
	 * 
	 * @return List
	 */
	protected List getInitialElementSelections() {
		return initialSelections;
	}

	/**
	 * Returns the message for this dialog.
	 * 
	 * @return the message for this dialog
	 */
	protected String getMessage() {
		return message;
	}

	/**
	 * Returns the ok button.
	 * 
	 * @return the ok button or <code>null</code> if the button is not created
	 *         yet.
	 */
	public Button getOkButton() {
		return getButton(IDialogConstants.OK_ID);
	}

	/**
	 * Returns the list of selections made by the user, or <code>null</code> if
	 * the selection was canceled.
	 * 
	 * @return the array of selected elements, or <code>null</code> if Cancel
	 *         was pressed
	 */
	public Object[] getResult() {
		return result;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	/**
	 * Set the dialog settings that should be used to save the bounds of this
	 * dialog. This method is provided so that clients that directly use
	 * SelectionDialogs without subclassing them may specify how the bounds of
	 * the dialog are to be saved.
	 * 
	 * @param settings
	 *            the {@link IDialogSettings} that should be used to store the
	 *            bounds of the dialog
	 * @param strategy
	 *            the integer constant specifying how the bounds are saved.
	 *            Specified using {@link Dialog#DIALOG_PERSISTLOCATION} and
	 *            {@link Dialog#DIALOG_PERSISTSIZE}.
	 * @see Dialog#getDialogBoundsStrategy()
	 * @see Dialog#getDialogBoundsSettings()
	 */
	public void setDialogBoundsSettings(final IDialogSettings settings, final int strategy) {
		dialogBoundsStrategy = strategy;
		dialogBoundsSettings = settings;
	}

	/**
	 * Sets the initial selection in this selection dialog to the given
	 * elements.
	 * 
	 * @param selectedElements
	 *            the List of elements to select
	 */
	public void setInitialElementSelections(final List<Object> selectedElements) {
		initialSelections = selectedElements;
	}

	/**
	 * Sets the initial selection in this selection dialog to the given
	 * elements.
	 * 
	 * @param selectedElements
	 *            the array of elements to select
	 */
	public void setInitialSelections(final Object[] selectedElements) {
		initialSelections = new ArrayList<>(selectedElements.length);
		for (final Object selectedElement : selectedElements) {
			initialSelections.add(selectedElement);
		}
	}

	/**
	 * Sets the message for this dialog.
	 * 
	 * @param message
	 *            the message
	 */
	public void setMessage(final String message) {
		this.message = message;
	}

	/**
	 * Set the selections made by the user, or <code>null</code> if the
	 * selection was canceled.
	 * 
	 * @param newResult
	 *            list of selected elements, or <code>null</code> if Cancel was
	 *            pressed
	 */
	protected void setResult(final List<?> newResult) {
		if (newResult == null) {
			result = null;
		} else {
			result = new Object[newResult.size()];
			newResult.toArray(result);
		}
	}

	/**
	 * Set the selections made by the user, or <code>null</code> if the
	 * selection was canceled.
	 * <p>
	 * The selections may accessed using <code>getResult</code>.
	 * </p>
	 * 
	 * @param newResult
	 *            - the new values
	 */
	protected void setSelectionResult(final Object[] newResult) {
		result = newResult;
	}

	/**
	 * Sets the title for this dialog.
	 * 
	 * @param title
	 *            the title
	 */
	public void setTitle(final String title) {
		this.title = title;
	}
}
