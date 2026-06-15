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

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;

final class LegacySignagePlaybackCompatibility {

    private LegacySignagePlaybackCompatibility() {
    }

    static JsonArray expandSingleContent(JsonArray contents) {
        if (contents == null || contents.size() != 1) {
            return contents;
        }

        JsonArrayBuilder compatibleContents = Json.createArrayBuilder();
        compatibleContents.add(contents.get(0));
        compatibleContents.add(contents.get(0));
        return compatibleContents.build();
    }
}
