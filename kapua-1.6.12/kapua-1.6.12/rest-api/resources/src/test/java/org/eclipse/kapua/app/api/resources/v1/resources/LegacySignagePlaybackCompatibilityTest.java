/*******************************************************************************
 * Copyright (c) 2026 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources;

import org.junit.Assert;
import org.junit.Test;

import javax.json.Json;
import javax.json.JsonArray;

public class LegacySignagePlaybackCompatibilityTest {

    @Test
    public void shouldDuplicateSingleContentForLegacyPlayer() {
        JsonArray contents = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("appName", "Image")
                        .add("duration", "0:0:10")
                        .add("action", "show"))
                .build();

        JsonArray compatible =
                LegacySignagePlaybackCompatibility.expandSingleContent(contents);

        Assert.assertEquals(2, compatible.size());
        Assert.assertEquals(compatible.get(0), compatible.get(1));
    }

    @Test
    public void shouldNotChangeMultipleContents() {
        JsonArray contents = Json.createArrayBuilder()
                .add(Json.createObjectBuilder().add("appName", "Image"))
                .add(Json.createObjectBuilder().add("appName", "Video"))
                .build();

        JsonArray compatible =
                LegacySignagePlaybackCompatibility.expandSingleContent(contents);

        Assert.assertSame(contents, compatible);
    }

    @Test
    public void shouldKeepEmptyContentsEmpty() {
        JsonArray contents = Json.createArrayBuilder().build();

        JsonArray compatible =
                LegacySignagePlaybackCompatibility.expandSingleContent(contents);

        Assert.assertSame(contents, compatible);
    }
}
