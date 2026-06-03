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
package org.eclipse.kapua.commons.responeCode;

/**
 * Response codes for Device-related errors.
 */
public enum DeviceResponseCode {

    NOT_FIND_DEVICE("5300", "Device not found"),
    DEVICE_ALREADY_EXISTS("5301", "Device already exists"),
    DEVICE_ACCESS_TOKEN_INVALID("5302", "Device access token is invalid or expired");

    private final String code;
    private final String description;

    DeviceResponseCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String fullDescription(DeviceResponseCode responseCode) {
        return responseCode.code + ":" + responseCode.description;
    }
}
