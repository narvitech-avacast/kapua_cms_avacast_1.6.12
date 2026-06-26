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
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import java.util.Map;

final class SignageScheduleNormalizer {

    private SignageScheduleNormalizer() {
    }

    static JsonObject normalize(JsonObject source) {
        JsonObject schedule = scheduleObject(source);
        boolean enable = readBoolean(schedule, "enable", false);
        String startDate = readString(schedule, "startDate");
        String endDate   = readString(schedule, "endDate");
        String startTime = readString(schedule, "startTime");
        String endTime   = readString(schedule, "endTime");

        // When schedule is enabled but date fields are empty, fill wide-open defaults
        // so the device DateFormat.parse() succeeds (empty/null strings throw ParseException).
        if (enable) {
            if (startDate.isEmpty()) startDate = "2000-01-01";
            if (startTime.isEmpty()) startTime = "00:00:00";
            if (endDate.isEmpty())   endDate   = "2099-12-31";
            if (endTime.isEmpty())   endTime   = "23:59:59";
        }

        JsonObjectBuilder builder = Json.createObjectBuilder()
                .add("startDate", startDate)
                .add("endDate",   endDate)
                .add("startTime", startTime)
                .add("endTime",   endTime)
                .add("weekdays", readWeekdays(schedule))
                .add("enable", enable);

        if (schedule != null) {
            for (Map.Entry<String, JsonValue> entry : schedule.entrySet()) {
                if (!isScheduleField(entry.getKey())) {
                    builder.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return builder.build();
    }

    static int normalizedListType(JsonObject source) {
        return normalize(source).getBoolean("enable", false)
                ? Math.max(1, readInt(source, "listType", 1))
                : 0;
    }

    private static JsonObject scheduleObject(JsonObject source) {
        if (source == null) {
            return null;
        }
        JsonValue value = source.get("schedule");
        return value != null && value.getValueType() == JsonValue.ValueType.OBJECT
                ? value.asJsonObject()
                : null;
    }

    private static String readString(JsonObject object, String key) {
        if (object == null) {
            return "";
        }
        JsonValue value = object.get(key);
        return value != null && value.getValueType() == JsonValue.ValueType.STRING
                ? ((JsonString) value).getString()
                : "";
    }

    private static boolean readBoolean(JsonObject object, String key, boolean fallback) {
        if (object == null) {
            return fallback;
        }
        JsonValue value = object.get(key);
        if (JsonValue.TRUE.equals(value)) {
            return true;
        }
        if (JsonValue.FALSE.equals(value)) {
            return false;
        }
        if (value != null && value.getValueType() == JsonValue.ValueType.STRING) {
            String text = ((JsonString) value).getString().trim();
            if ("true".equalsIgnoreCase(text)) {
                return true;
            }
            if ("false".equalsIgnoreCase(text)) {
                return false;
            }
        }
        return fallback;
    }

    private static JsonArray readWeekdays(JsonObject schedule) {
        JsonArrayBuilder weekdays = Json.createArrayBuilder();
        if (schedule == null) {
            return weekdays.build();
        }
        JsonValue value = schedule.get("weekdays");
        if (value == null || value.getValueType() != JsonValue.ValueType.ARRAY) {
            return weekdays.build();
        }
        for (JsonValue day : value.asJsonArray()) {
            Integer weekday = readInteger(day);
            if (weekday != null) {
                weekdays.add(weekday);
            }
        }
        return weekdays.build();
    }

    private static int readInt(JsonObject object, String key, int fallback) {
        if (object == null) {
            return fallback;
        }
        return readInteger(object.get(key), fallback);
    }

    private static Integer readInteger(JsonValue value) {
        return readInteger(value, null);
    }

    private static Integer readInteger(JsonValue value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.getValueType() == JsonValue.ValueType.NUMBER) {
            return ((JsonNumber) value).intValue();
        }
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            try {
                return Integer.valueOf(((JsonString) value).getString().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean isScheduleField(String key) {
        return "startDate".equals(key)
                || "endDate".equals(key)
                || "startTime".equals(key)
                || "endTime".equals(key)
                || "weekdays".equals(key)
                || "enable".equals(key);
    }
}
