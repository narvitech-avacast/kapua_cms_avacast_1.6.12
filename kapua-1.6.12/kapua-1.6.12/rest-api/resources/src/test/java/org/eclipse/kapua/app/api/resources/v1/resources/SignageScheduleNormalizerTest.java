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
import javax.json.JsonObject;

public class SignageScheduleNormalizerTest {

    @Test
    public void shouldCreateDisabledDefaultScheduleWhenMissing() {
        JsonObject normalized = SignageScheduleNormalizer.normalize(
                Json.createObjectBuilder().build());

        Assert.assertEquals("", normalized.getString("startDate"));
        Assert.assertEquals("", normalized.getString("endDate"));
        Assert.assertEquals("", normalized.getString("startTime"));
        Assert.assertEquals("", normalized.getString("endTime"));
        Assert.assertTrue(normalized.getJsonArray("weekdays").isEmpty());
        Assert.assertFalse(normalized.getBoolean("enable"));
        Assert.assertEquals(0, SignageScheduleNormalizer.normalizedListType(
                Json.createObjectBuilder().build()));
    }

    @Test
    public void shouldKeepScheduledPlaylistContract() {
        JsonObject source = Json.createObjectBuilder()
                .add("listType", 1)
                .add("schedule", Json.createObjectBuilder()
                        .add("enable", true)
                        .add("startDate", "2026-06-11")
                        .add("endDate", "2026-12-31")
                        .add("startTime", "08:00:00")
                        .add("endTime", "22:00:00")
                        .add("weekdays", Json.createArrayBuilder()
                                .add(1)
                                .add(2)
                                .add(3)
                                .add(4)
                                .add(5)))
                .build();

        JsonObject normalized = SignageScheduleNormalizer.normalize(source);

        Assert.assertTrue(normalized.getBoolean("enable"));
        Assert.assertEquals("2026-06-11", normalized.getString("startDate"));
        Assert.assertEquals("2026-12-31", normalized.getString("endDate"));
        Assert.assertEquals("08:00:00", normalized.getString("startTime"));
        Assert.assertEquals("22:00:00", normalized.getString("endTime"));
        Assert.assertEquals(5, normalized.getJsonArray("weekdays").size());
        Assert.assertEquals(1, SignageScheduleNormalizer.normalizedListType(source));
    }

    @Test
    public void shouldFillMissingScheduleFieldsAndPreserveExtraMetadata() {
        JsonObject source = Json.createObjectBuilder()
                .add("schedule", Json.createObjectBuilder()
                        .add("enable", "true")
                        .add("timezone", "Asia/Taipei"))
                .build();

        JsonObject normalized = SignageScheduleNormalizer.normalize(source);

        Assert.assertTrue(normalized.getBoolean("enable"));
        Assert.assertEquals("", normalized.getString("startDate"));
        Assert.assertEquals("", normalized.getString("endTime"));
        Assert.assertTrue(normalized.getJsonArray("weekdays").isEmpty());
        Assert.assertEquals("Asia/Taipei", normalized.getString("timezone"));
        Assert.assertEquals(1, SignageScheduleNormalizer.normalizedListType(source));
    }

    @Test
    public void shouldIgnoreMalformedScheduleWithoutFailingPlaylistFlow() {
        JsonObject source = Json.createObjectBuilder()
                .add("listType", 1)
                .add("schedule", "not-an-object")
                .build();

        JsonObject normalized = SignageScheduleNormalizer.normalize(source);

        Assert.assertFalse(normalized.getBoolean("enable"));
        Assert.assertTrue(normalized.getJsonArray("weekdays").isEmpty());
        Assert.assertEquals(0, SignageScheduleNormalizer.normalizedListType(source));
    }
}
