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
package org.eclipse.kapua.common.util.GatewayConfig;

/**
 * Generates gateway configuration strings (e.g. for non-Android platforms).
 * Use {@link org.eclipse.kapua.app.api.resources.v1.resources.model.GatewayConfigXmlGen}
 * for Android/XML-based gateway config generation.
 */
public class GatewayConfigGenerator {

    private GatewayConfigModel gatewayConfig;

    public GatewayConfigGenerator() {
    }

    public GatewayConfigModel getGatewayConfig() {
        return gatewayConfig;
    }

    public void setGatewayConfig(GatewayConfigModel gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    /**
     * Builds the gateway configuration as a plain-text key=value string.
     */
    public String build() {
        if (gatewayConfig == null) {
            return "";
        }
        return "deviceName=" + nullSafe(gatewayConfig.getDeviceName()) + "\n" +
               "accountName=" + nullSafe(gatewayConfig.getAccountName()) + "\n" +
               "brokerProtocol=" + nullSafe(gatewayConfig.getBrokerProtocol()) + "\n" +
               "brokerHost=" + nullSafe(gatewayConfig.getBrokerHost()) + "\n" +
               "brokerPort=" + nullSafe(gatewayConfig.getBrokerPort()) + "\n" +
               "brokerUser=" + nullSafe(gatewayConfig.getBrokerUser()) + "\n" +
               "brokerPassword=" + nullSafe(gatewayConfig.getBrokerPassword());
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
