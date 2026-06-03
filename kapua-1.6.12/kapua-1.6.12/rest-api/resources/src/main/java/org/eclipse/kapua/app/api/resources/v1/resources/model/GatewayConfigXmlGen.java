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

import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;

/**
 * Generates an XML-formatted gateway configuration string for Android/embedded devices.
 */
public class GatewayConfigXmlGen {

    private GatewayConfigModel gatewayConfig;

    public GatewayConfigXmlGen() {
    }

    public GatewayConfigModel getGatewayConfig() {
        return gatewayConfig;
    }

    public void setGatewayConfig(GatewayConfigModel gatewayConfig) {
        this.gatewayConfig = gatewayConfig;
    }

    /**
     * Builds the XML gateway configuration and wraps it in a {@link Words} response.
     */
    public Words build() {
        Words result = new Words();
        if (gatewayConfig == null) {
            result.setValue("");
            return result;
        }
        result.setValue(buildXml());
        return result;
    }

    private String buildXml() {
        String deviceName = nullSafe(gatewayConfig.getDeviceName());
        String accountName = nullSafe(gatewayConfig.getAccountName());
        String protocol = nullSafe(gatewayConfig.getBrokerProtocol());
        String host = nullSafe(gatewayConfig.getBrokerHost());
        String port = nullSafe(gatewayConfig.getBrokerPort());
        String brokerUser = nullSafe(gatewayConfig.getBrokerUser());
        String brokerPass = nullSafe(gatewayConfig.getBrokerPassword());

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<gatewayConfig>\n" +
               "  <deviceName>" + deviceName + "</deviceName>\n" +
               "  <accountName>" + accountName + "</accountName>\n" +
               "  <broker>\n" +
               "    <protocol>" + protocol + "</protocol>\n" +
               "    <host>" + host + "</host>\n" +
               "    <port>" + port + "</port>\n" +
               "    <username>" + brokerUser + "</username>\n" +
               "    <password>" + brokerPass + "</password>\n" +
               "  </broker>\n" +
               "</gatewayConfig>";
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
