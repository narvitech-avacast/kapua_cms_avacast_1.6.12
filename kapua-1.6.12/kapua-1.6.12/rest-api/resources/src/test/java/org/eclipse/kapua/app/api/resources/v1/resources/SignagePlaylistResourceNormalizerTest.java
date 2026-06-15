/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
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
import javax.json.JsonObject;

public class SignagePlaylistResourceNormalizerTest {

    private final SignagePlaylistResourceNormalizer normalizer =
            new SignagePlaylistResourceNormalizer(
                    new SignageMediaLocation("target/signage-media", "https://cms.test"));

    @Test
    public void shouldKeepAbsolutePathForStorage() throws Exception {
        JsonObject source = playlist("/signage-media/AQ/playlist/image-1.png");

        JsonObject resource = normalizer.forStorage(source).getJsonObject(0);

        Assert.assertEquals(
                "https://cms.test/signage-media/AQ/playlist/image-1.png",
                resource.getString("path"));
        Assert.assertEquals(resource.getString("path"), resource.getString("url"));
        Assert.assertEquals("resource-1", resource.getString("id"));
    }

    @Test
    public void shouldClearPathForLegacyDeviceContract() throws Exception {
        JsonObject source = playlist(
                "https://cms.test/signage-media/AQ/playlist/image-1.png");

        JsonObject resource = normalizer.forDevice(source).getJsonObject(0);

        Assert.assertEquals("", resource.getString("path"));
        Assert.assertEquals(
                "https://cms.test/signage-media/AQ/playlist/image-1.png",
                resource.getString("url"));
        Assert.assertEquals("resource-1", resource.getString("id"));
        Assert.assertEquals("Image-resource-1", resource.getString("name"));
    }

    private JsonObject playlist(String path) {
        JsonArray resources = Json.createArrayBuilder()
                .add(Json.createObjectBuilder()
                        .add("name", "Image-resource-1")
                        .add("id", "resource-1")
                        .add("fileName", "image.png")
                        .add("path", path))
                .build();
        return Json.createObjectBuilder()
                .add("resources", resources)
                .build();
    }
}
