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

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaIllegalArgumentException;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonValue;
import java.util.Map;

final class SignagePlaylistResourceNormalizer {

    private final SignageMediaLocation mediaLocation;

    SignagePlaylistResourceNormalizer(SignageMediaLocation mediaLocation) {
        this.mediaLocation = mediaLocation;
    }

    JsonArray forStorage(JsonObject source) throws KapuaException {
        return normalize(source, false);
    }

    JsonArray forDevice(JsonObject source) throws KapuaException {
        return normalize(source, true);
    }

    private JsonArray normalize(JsonObject source, boolean deviceContract)
            throws KapuaException {
        JsonArrayBuilder resources = Json.createArrayBuilder();
        JsonArray sourceResources = source.getJsonArray("resources");
        if (sourceResources == null) {
            return resources.build();
        }
        for (JsonValue value : sourceResources) {
            if (value.getValueType() != JsonValue.ValueType.OBJECT) {
                throw new KapuaIllegalArgumentException(
                        "resources", "Signage resource must be a JSON object");
            }
            JsonObject resource = value.asJsonObject();
            String candidate = resource.getString(
                    "url", resource.getString("path", "")).trim();
            if (candidate.isEmpty()) {
                throw new KapuaIllegalArgumentException(
                        "resources[].path", "Signage resource path is required");
            }
            final String absoluteUrl;
            try {
                absoluteUrl = mediaLocation.toAbsoluteUrl(candidate);
            } catch (IllegalArgumentException e) {
                throw new KapuaIllegalArgumentException("resources[].path", candidate);
            }
            JsonObjectBuilder normalized = Json.createObjectBuilder();
            for (Map.Entry<String, JsonValue> entry : resource.entrySet()) {
                if (!"path".equals(entry.getKey()) && !"url".equals(entry.getKey())) {
                    normalized.add(entry.getKey(), entry.getValue());
                }
            }
            normalized.add("path", deviceContract ? "" : absoluteUrl);
            normalized.add("url", absoluteUrl);
            resources.add(normalized);
        }
        return resources.build();
    }
}
