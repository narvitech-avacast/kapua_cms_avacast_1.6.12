package org.eclipse.kapua.app.console.module.authentication.client.tabs.credentials;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Element;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.IconSet;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.KapuaIcon;
import org.eclipse.kapua.app.console.module.api.client.ui.dialog.InfoDialog;
import org.eclipse.kapua.app.console.module.api.client.util.DialogUtils;
import org.eclipse.kapua.app.console.module.authentication.client.messages.ConsoleCredentialMessages;

public class ApiKeyConfirmationDialog extends InfoDialog {

    protected static final ConsoleCredentialMessages CRED_MSGS = GWT.create(ConsoleCredentialMessages.class);

    public ApiKeyConfirmationDialog(String apiKey) {
        super(CRED_MSGS.dialogConfirmationAPI(), new KapuaIcon(IconSet.KEY), new SafeHtmlBuilder().appendEscapedLines(CRED_MSGS.dialogAddConfirmationApiKey(apiKey)).toSafeHtml().asString());
        setStyleAttribute("background-color", "#F0F0F0");
        setBodyStyle("background-color: #F0F0F0");

        DialogUtils.resizeDialog(this, 350, 200);
    }

    @Override
    protected void onRender(Element parent, int pos) {
        super.onRender(parent, pos);
    }
}
