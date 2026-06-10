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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;

/**
 * Generates an XML-formatted gateway configuration string for Android/embedded devices.
 */
public class GatewayConfigXmlGen {

    private final Words rootElements;

    public GatewayConfigXmlGen() {
        rootElements = new Words();
    }

    public void setGatewayConfig(GatewayConfigModel model) {
        rootElements.setValue("GatewayConfig");

        List<Word> elements = new ArrayList<>();
        if (model != null) {
            elements.add(genElement("ClientId", model.getDeviceName()));
            elements.add(genElement("AccountName", model.getAccountName()));
            elements.add(genElement("BrokerProtocol", model.getBrokerProtocol()));
            elements.add(genElement("BrokerUser", model.getBrokerUser()));
            elements.add(genElement("BrokerPassword", model.getBrokerPassword()));
            elements.add(genElement("BrokerHost", model.getBrokerHost()));
            elements.add(genElement("BrokerPort", model.getBrokerPort()));
        }
        rootElements.setWords(elements);
    }

    public Words build() {
        return rootElements;
    }

    private Word genElement(String key, String value) {
        return new Word(key, value != null ? value : "");
    }
}
