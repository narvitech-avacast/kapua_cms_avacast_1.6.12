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
 * Response codes for Account-related errors.
 */
public enum AccountResponseCode {

    NOT_FIND_ACCOUNT("5400", "Account not found"),
    ACCOUNT_ALREADY_EXISTS("5401", "Account already exists"),
    ACCOUNT_EXPIRED("5402", "Account has expired");

    private final String code;
    private final String description;

    AccountResponseCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String fullDescription(AccountResponseCode responseCode) {
        return responseCode.code + ":" + responseCode.description;
    }
}
