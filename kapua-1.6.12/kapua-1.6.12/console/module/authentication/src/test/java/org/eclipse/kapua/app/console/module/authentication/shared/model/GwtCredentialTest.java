package org.eclipse.kapua.app.console.module.authentication.shared.model;

import org.junit.Assert;
import org.junit.Test;

public class GwtCredentialTest {

    @Test
    public void shouldSetCorrectPassword() {
        GwtCredential gwtCredential = new GwtCredential();
        String password = "foo%!`\"";
        gwtCredential.setCredentialKey(password);
        Assert.assertEquals(password, gwtCredential.getCredentialKey());
    }

}
