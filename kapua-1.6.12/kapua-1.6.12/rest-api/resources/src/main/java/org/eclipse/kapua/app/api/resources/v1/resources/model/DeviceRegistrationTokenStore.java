/*******************************************************************************
 * Copyright (c) 2017, 2022 Eurotech and/or its affiliates and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 */
package org.eclipse.kapua.app.api.resources.v1.resources.model;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory one-time token store used for device self-registration.
 */
public final class DeviceRegistrationTokenStore {

    private static final DeviceRegistrationTokenStore INSTANCE = new DeviceRegistrationTokenStore();
    private static final int TOKEN_BYTES = 32;
    private static final long TOKEN_TTL_SECONDS = 15 * 60;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Entry> tokens = new ConcurrentHashMap<>();

    private DeviceRegistrationTokenStore() {
    }

    public static DeviceRegistrationTokenStore getInstance() {
        return INSTANCE;
    }

    public String issue(String scopeId) {
        cleanupExpiredTokens();

        String token;
        Entry entry = new Entry(scopeId, Instant.now().plusSeconds(TOKEN_TTL_SECONDS).toEpochMilli());
        do {
            token = newToken();
        } while (tokens.putIfAbsent(token, entry) != null);

        return token;
    }

    public boolean consume(String scopeId, String token) {
        if (isBlank(scopeId) || isBlank(token)) {
            return false;
        }

        cleanupExpiredTokens();

        Entry entry = tokens.get(token);
        if (entry == null || entry.isExpired() || !scopeId.equals(entry.scopeId)) {
            return false;
        }

        return tokens.remove(token, entry);
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanupExpiredTokens() {
        for (Map.Entry<String, Entry> token : tokens.entrySet()) {
            if (token.getValue().isExpired()) {
                tokens.remove(token.getKey(), token.getValue());
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Entry {

        private final String scopeId;
        private final long expiresAt;

        private Entry(String scopeId, long expiresAt) {
            this.scopeId = scopeId;
            this.expiresAt = expiresAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
