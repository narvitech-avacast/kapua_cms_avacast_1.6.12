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
package org.eclipse.kapua.service.device.management.gatewayconfig;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Request model carrying a registration/access token, device identity,
 * scope and target platform for gateway configuration generation.
 */
@XmlRootElement(name = "deviceTokenGenGatewayconfig")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeviceTokenGenGatewayconfig {

    /**
     * The account (scope) ID that owns the device.
     * Required when calling the no-auth endpoint so the service can
     * locate the device without a Kapua session.
     */
    @XmlElement(name = "scopeId")
    private String scopeId;

    /** The registration token or legacy device clientId token. */
    @XmlElement(name = "accessToken")
    private String accessToken;

    /** The device clientId sent by the device during registration. */
    @XmlElement(name = "clientId")
    private String clientId;

    /** Optional display name to use when creating the device. */
    @XmlElement(name = "displayName")
    private String displayName;

    /** The target platform (e.g. "Android"). */
    @XmlElement(name = "platform")
    private String platform;

    public DeviceTokenGenGatewayconfig() {
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
