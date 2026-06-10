/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.console.module.device.client.device.networkstatus;

import org.eclipse.kapua.app.console.module.api.client.ui.view.descriptor.AbstractEntityTabDescriptor;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;
import org.eclipse.kapua.app.console.module.device.client.device.DeviceView;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDevice;
import org.eclipse.kapua.app.console.module.device.shared.model.permission.DeviceSessionPermission;

public class DeviceTabNetworkStatusDescriptor extends AbstractEntityTabDescriptor<GwtDevice, DeviceTabNetworkStatus, DeviceView> {

    @Override
    public DeviceTabNetworkStatus getTabViewInstance(DeviceView view, GwtSession currentSession) {
        return new DeviceTabNetworkStatus(currentSession);
    }

    @Override
    public String getViewId() {
        return "device.networkStatus";
    }

    @Override
    public Integer getOrder() {
        return 200;
    }

    @Override
    public Boolean isEnabled(GwtSession currentSession) {
        return currentSession.hasPermission(DeviceSessionPermission.read());
    }
}
