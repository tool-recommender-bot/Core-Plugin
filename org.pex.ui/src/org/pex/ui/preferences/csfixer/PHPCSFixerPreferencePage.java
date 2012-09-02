package org.pex.ui.preferences.csfixer;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.pex.ui.PEXUIPlugin;
import org.pex.ui.preferences.AbstractPropertyAndPreferencePage;


@SuppressWarnings("restriction")
public class PHPCSFixerPreferencePage extends AbstractPropertyAndPreferencePage {

	public static final String PREF_ID = "org.pex.ui.preferences.PHPCSFixerPreferencePage"; //$NON-NLS-1$
	public static final String PROP_ID = "org.pex.ui.propertyPages.PHPCSFixerPreferencePage"; //$NON-NLS-1$

	public PHPCSFixerPreferencePage() {
		setPreferenceStore(PEXUIPlugin.getDefault().getPreferenceStore());
		setDescription("Configure PHP CS-Fixer phars and fixer options");
	}

	@Override
	public void createControl(Composite parent) {

		IWorkbenchPreferenceContainer container = (IWorkbenchPreferenceContainer) getContainer();
		fConfigurationBlock = new PHPCSFixerConfigurationBlock(
				getNewStatusChangedListener(), getProject(), container);

		super.createControl(parent);
	}

	@Override
	protected String getPreferencePageID() {
		return PREF_ID;
	}

	@Override
	protected String getPropertyPageID() {
		return PROP_ID;
	}
}