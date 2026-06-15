/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.console.module.device.client.device.signage;

import com.extjs.gxt.ui.client.Style.Scroll;
import com.extjs.gxt.ui.client.widget.Html;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.google.gwt.user.client.Element;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.IconSet;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.KapuaIcon;
import org.eclipse.kapua.app.console.module.api.client.ui.tab.KapuaTabItem;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDevice;

public class DeviceTabSignage extends KapuaTabItem<GwtDevice> {

    private LayoutContainer body;

    public DeviceTabSignage(GwtSession currentSession) {
        super(currentSession, "Signage", new KapuaIcon(IconSet.PICTURE_O));
    }

    @Override
    protected void onRender(Element parent, int pos) {
        super.onRender(parent, pos);
        body = new LayoutContainer();
        body.setScrollMode(Scroll.AUTO);
        body.setStyleAttribute("width", "100%");
        body.setStyleAttribute("height", "100%");
        add(body);
    }

    @Override
    protected void doRefresh() {
        if (body == null) {
            return;
        }
        body.removeAll();

        if (selectedEntity == null) {
            Html empty = new Html("<p style='color:#888;font-size:13px;padding:20px;'>請先選取一部裝置。</p>");
            body.add(empty);
            body.layout(true);
            return;
        }

        String src = buildPanelSrc();
        Html iframe = new Html(
                "<iframe src='" + src + "'"
                + " style='width:100%;height:700px;border:none;display:block;'"
                + " frameborder='0' allowfullscreen>"
                + "</iframe>");
        body.add(iframe);
        body.layout(true);
    }

    private String buildPanelSrc() {
        String token   = currentSession.getTokenId();
        String scopeId = currentSession.getSelectedAccountId();
        String deviceId = selectedEntity.getId();
        return "/signage-panel.html#" + token + "/" + scopeId + "/" + deviceId;
    }
}
