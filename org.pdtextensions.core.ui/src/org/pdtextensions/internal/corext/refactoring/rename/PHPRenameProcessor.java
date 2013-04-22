/*******************************************************************************
 * Copyright (c) 2000, 2009, 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     The PDT Extension Group - initial port to the PDT Extension Group Core Plugin
 *******************************************************************************/
package org.pdtextensions.internal.corext.refactoring.rename;

import java.util.StringTokenizer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.dltk.core.IDLTKLanguageToolkit;
import org.eclipse.dltk.core.IField;
import org.eclipse.dltk.core.ILocalVariable;
import org.eclipse.dltk.core.IMember;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ISourceRange;
import org.eclipse.dltk.core.ModelException;
import org.eclipse.dltk.core.manipulation.IScriptRefactorings;
import org.eclipse.dltk.core.search.IDLTKSearchConstants;
import org.eclipse.dltk.core.search.IDLTKSearchScope;
import org.eclipse.dltk.core.search.SearchEngine;
import org.eclipse.dltk.core.search.SearchMatch;
import org.eclipse.dltk.core.search.SearchParticipant;
import org.eclipse.dltk.core.search.SearchPattern;
import org.eclipse.dltk.core.search.SearchRequestor;
import org.eclipse.dltk.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.dltk.internal.corext.refactoring.ScriptRefactoringArguments;
import org.eclipse.dltk.internal.corext.refactoring.ScriptRefactoringDescriptor;
import org.eclipse.dltk.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.dltk.internal.corext.refactoring.code.ScriptableRefactoring;
import org.eclipse.dltk.internal.corext.refactoring.participants.ScriptProcessors;
import org.eclipse.dltk.internal.corext.refactoring.rename.RenameModifications;
import org.eclipse.dltk.internal.corext.refactoring.rename.ScriptRenameProcessor;
import org.eclipse.dltk.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.dltk.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.dltk.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.dltk.internal.corext.util.SearchUtils;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.pdtextensions.core.ui.Messages;
import org.pdtextensions.core.ui.PEXUIPlugin;
import org.pdtextensions.internal.corext.refactoring.RenamePHPElementDescriptor;

/**
 * This is the replacement of the {@link org.eclipse.dltk.internal.corext.refactoring.rename.RenameModelElementProcessor}
 * class for PHP elements.
 *
 * @since 0.17.0
 */
public abstract class PHPRenameProcessor extends ScriptRenameProcessor implements IReferenceUpdating {
	protected IModelElement fModelElement;
	protected ISourceModule fCu;

	//the following fields are set or modified after the construction
	protected boolean fUpdateReferences;
	protected String fCurrentName;

	private TextChangeManager fChangeManager;
	private final IDLTKLanguageToolkit fToolkit;

	public PHPRenameProcessor(IModelElement localVariable, IDLTKLanguageToolkit toolkit) {
		fToolkit = toolkit;
		fModelElement = localVariable;
		fCu = (ISourceModule) fModelElement.getAncestor(IModelElement.SOURCE_MODULE);
		fChangeManager = new TextChangeManager(true);
	}

	public RefactoringStatus initialize(RefactoringArguments arguments) {
		if (!(arguments instanceof ScriptRefactoringArguments))
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.InitializableRefactoring_inacceptable_arguments);
		final ScriptRefactoringArguments extended = (ScriptRefactoringArguments) arguments;
		final String handle = extended.getAttribute(ScriptRefactoringDescriptor.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IModelElement element = ScriptRefactoringDescriptor.handleToElement(extended.getProject(), handle, false);
			if (element != null && element.exists()) {
				if (element.getElementType() == IModelElement.SOURCE_MODULE) {
					fCu = (ISourceModule) element;
				} else if (element.getElementType() == IModelElement.LOCAL_VARIABLE) {
					fModelElement = (ILocalVariable) element;
					fCu = (ISourceModule) fModelElement.getAncestor(IModelElement.SOURCE_MODULE);
					if (fCu == null)
						return ScriptableRefactoring.createInputFatalStatus(element, getProcessorName(), IScriptRefactorings.RENAME_LOCAL_VARIABLE);
				} else
					return ScriptableRefactoring.createInputFatalStatus(element, getProcessorName(), IScriptRefactorings.RENAME_LOCAL_VARIABLE);
			} else
				return ScriptableRefactoring.createInputFatalStatus(element, getProcessorName(), IScriptRefactorings.RENAME_LOCAL_VARIABLE);
		} else {
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ScriptRefactoringDescriptor.ATTRIBUTE_INPUT));
		}
		final String name = extended.getAttribute(ScriptRefactoringDescriptor.ATTRIBUTE_NAME);
		if (name != null && !"".equals(name)) //$NON-NLS-1$
			setNewElementName(name);
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ScriptRefactoringDescriptor.ATTRIBUTE_NAME));
		if (fCu != null && fModelElement == null) {
			final String selection = extended.getAttribute(ScriptRefactoringDescriptor.ATTRIBUTE_SELECTION);
			if (selection != null) {
				int offset= -1;
				int length= -1;
				final StringTokenizer tokenizer = new StringTokenizer(selection);
				if (tokenizer.hasMoreTokens())
					offset = Integer.valueOf(tokenizer.nextToken()).intValue();
				if (tokenizer.hasMoreTokens())
					length = Integer.valueOf(tokenizer.nextToken()).intValue();
				if (offset >= 0 && length >= 0) {
					try {
						final IModelElement[] elements = fCu.codeSelect(offset, length);
						if (elements != null) {
							for (int index = 0; index < elements.length; index++) {
								final IModelElement element = elements[index];
								if (element instanceof ILocalVariable)
									fModelElement = (ILocalVariable) element;
							}
						}
						if (fModelElement == null)
							return ScriptableRefactoring.createInputFatalStatus(null, getProcessorName(), IScriptRefactorings.RENAME_LOCAL_VARIABLE);
					} catch (ModelException exception) {
						PEXUIPlugin.getDefault().getLog().log(new Status(IStatus.ERROR, PEXUIPlugin.PLUGIN_ID, exception.getMessage(), exception));
					}
				} else
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection,
							ScriptRefactoringDescriptor.ATTRIBUTE_SELECTION }));
			} else
				return RefactoringStatus
						.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ScriptRefactoringDescriptor.ATTRIBUTE_SELECTION));
		}
		final String references= extended.getAttribute(ScriptRefactoringDescriptor.ATTRIBUTE_REFERENCES);
		if (references != null) {
			fUpdateReferences= Boolean.valueOf(references).booleanValue();
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ScriptRefactoringDescriptor.ATTRIBUTE_REFERENCES));
		return new RefactoringStatus();
	}

	public String getCurrentElementName() {
		return fCurrentName;
	}

	public boolean canEnableUpdateReferences() {
		return true;
	}

	public void setUpdateReferences(boolean update) {
		fUpdateReferences = update;
	}

	public boolean getUpdateReferences() {
		return fUpdateReferences;
	}

	@Override
	protected RenameModifications computeRenameModifications()
			throws CoreException {
		RenameModifications result = new RenameModifications();
		if (fModelElement instanceof ILocalVariable) {
			result.rename((ILocalVariable)fModelElement, new RenameArguments(getNewElementName(), getUpdateReferences()));
		} else if (fModelElement instanceof IField) {
			// TODO: add switching method in RenameModifications
			result.rename((IField)fModelElement, new RenameArguments(getNewElementName(), getUpdateReferences()));
		}

		return result;
	}

	@Override
	protected IFile[] getChangedFiles() throws CoreException {
		return ResourceUtil.getFiles(fChangeManager.getAllSourceModules());
	}

	@Override
	protected RefactoringStatus doCheckFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException, OperationCanceledException {
		pm.beginTask("", 1); //$NON-NLS-1$

		try {
			RefactoringStatus result = checkNewElementName(getNewElementName());
			if (result.hasFatalError()) {
				return result;
			}

			createEdits(pm);

			return result;
		} finally {
			pm.done();
		}
	}

	private void createEdits(IProgressMonitor pm) throws CoreException {
		fChangeManager.clear();
		IDLTKSearchScope scope = SearchEngine.createWorkspaceScope(fToolkit);
		SearchEngine engine = new SearchEngine();
		if (fUpdateReferences) {
			SearchPattern pattern = SearchPattern.createPattern(fModelElement, IDLTKSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE, fToolkit);
			IProgressMonitor monitor = new SubProgressMonitor(pm, 1000);
			engine.search(pattern, new SearchParticipant[]{ SearchEngine.getDefaultSearchParticipant() }, scope, new SearchRequestor() {
				@Override
				public void acceptSearchMatch(SearchMatch match) throws CoreException {
					if (!(match.getElement() instanceof IModelElement)) return;
					IModelElement elem = (IModelElement)match.getElement();
					ISourceModule cu = (ISourceModule)elem.getAncestor(IModelElement.SOURCE_MODULE);
					if (cu != null) {
						ReplaceEdit edit = new ReplaceEdit(match.getOffset(), fCurrentName.length(), getNewElementName());
						addTextEdit(fChangeManager.get(cu), getProcessorName(), edit);
					}
				}
			}, monitor);
		}
		ISourceRange decl = null;
		if (fModelElement instanceof ILocalVariable) {
			decl = ((ILocalVariable)fModelElement).getNameRange();
		} else if (fModelElement instanceof IMember) {
			decl = ((IMember)fModelElement).getNameRange();
		}
		if (decl != null) {
			ReplaceEdit edit = new ReplaceEdit(decl.getOffset(), fCurrentName.length(), getNewElementName());
			addTextEdit(fChangeManager.get(fCu), getProcessorName(), edit);
		}
	}
	
	private static void addTextEdit(TextChange change, String name, TextEdit edit) throws MalformedTreeException {
		TextEdit root = change.getEdit();
		if (root == null) {
			root = new MultiTextEdit();
			change.setEdit(root);
		}

		root.addChild(edit);
		change.addTextEditGroup(new TextEditGroup(name, edit));
	}

	@Override
	protected String[] getAffectedProjectNatures() throws CoreException {
		return ScriptProcessors.computeAffectedNatures(fCu);
	}

	@Override
	public Object[] getElements() {
		return new Object[]{ fModelElement };
	}
	
	public String getNewElement() {
		return getNewElementName();
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException, OperationCanceledException {
		fCurrentName = fModelElement.getElementName();

		return new RefactoringStatus();
	}

	public Change createChange(IProgressMonitor monitor) throws CoreException {
		monitor.beginTask(RefactoringCoreMessages.RenameFieldRefactoring_checking, 1);

		try {
			TextChange[] changes = fChangeManager.getAllChanges();
			RefactoringDescriptor descriptor = createRefactoringDescriptor();
			return new DynamicValidationRefactoringChange(descriptor, getProcessorName(), changes);
		} finally {
			monitor.done();
		}
	}

	protected abstract RefactoringDescriptor createRefactoringDescriptor();

	protected void configureRefactoringDescriptor(RenamePHPElementDescriptor descriptor) {
		if (fCu.getScriptProject() != null) {
			descriptor.setProject(fCu.getScriptProject().getElementName());
		}

		descriptor.setFlags(RefactoringDescriptor.NONE);
		descriptor.setModelElement(fModelElement);
		descriptor.setNewName(getNewElementName());
		descriptor.setUpdateReferences(fUpdateReferences);
	}

	@Override
	public boolean needsSavedEditors() {
		return false;
	}
}