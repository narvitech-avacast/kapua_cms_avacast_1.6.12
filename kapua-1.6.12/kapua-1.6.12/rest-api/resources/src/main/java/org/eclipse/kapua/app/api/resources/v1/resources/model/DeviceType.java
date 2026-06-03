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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "deviceType")
@XmlAccessorType(XmlAccessType.FIELD)
public class DeviceType {

    @XmlElement(name = "modelId")
    private String modelId;

    @XmlElement(name = "count")
    private int count;

    public DeviceType() {
    }

    public DeviceType(String modelId, int count) {
        this.modelId = modelId;
        this.count = count;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
