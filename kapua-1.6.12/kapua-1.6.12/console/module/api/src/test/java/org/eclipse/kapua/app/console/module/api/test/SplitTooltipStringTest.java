package org.eclipse.kapua.app.console.module.api.test;

import org.eclipse.kapua.app.console.module.api.client.util.SplitTooltipStringUtil;
import org.eclipse.kapua.qa.markers.junit.JUnitTests;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Category(JUnitTests.class)
public class SplitTooltipStringTest {

    @Test
    public void testStringSplit() {
        String inputString = "testtesttesttest";
        String result = SplitTooltipStringUtil.splitTooltipString(inputString, 10);
        Assert.assertTrue(result.contains("</br>"));
    }
}
