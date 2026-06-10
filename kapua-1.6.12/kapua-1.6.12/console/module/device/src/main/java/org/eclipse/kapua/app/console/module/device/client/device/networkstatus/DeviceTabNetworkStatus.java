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

import com.extjs.gxt.ui.client.Style.Scroll;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.Text;
import com.extjs.gxt.ui.client.widget.layout.TableData;
import com.extjs.gxt.ui.client.widget.layout.TableLayout;
import com.google.gwt.user.client.Element;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.IconSet;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.KapuaIcon;
import org.eclipse.kapua.app.console.module.api.client.ui.tab.KapuaTabItem;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDevice;

public class DeviceTabNetworkStatus extends KapuaTabItem<GwtDevice> {

    private LayoutContainer body;

    public DeviceTabNetworkStatus(GwtSession currentSession) {
        super(currentSession, "Network Status", new KapuaIcon(IconSet.WIFI));
    }

    @Override
    protected void onRender(Element parent, int pos) {
        super.onRender(parent, pos);

        body = new LayoutContainer();
        body.setScrollMode(Scroll.AUTO);
        body.setStyleAttribute("padding", "14px");
        add(body);
    }

    @Override
    protected void doRefresh() {
        if (body == null) {
            return;
        }
        body.removeAll();

        if (selectedEntity == null) {
            Text empty = new Text("請先選取一部裝置。");
            empty.setStyleAttribute("color", "#888");
            empty.setStyleAttribute("font-size", "13px");
            empty.setStyleAttribute("padding", "20px");
            body.add(empty);
            body.layout(true);
            return;
        }

        GwtDevice d = selectedEntity;
        boolean online = d.isOnline();

        // ── Connection section ────────────────────────────────
        body.add(buildSectionHeader("Connection"));

        LayoutContainer connGrid = new LayoutContainer();
        connGrid.setLayout(new TableLayout(2));
        connGrid.setStyleAttribute("margin-bottom", "16px");

        String statusVal = d.getPeerStatus();
        if (statusVal == null || statusVal.isEmpty()) {
            statusVal = online ? "Online" : "Offline";
        }
        String statusColor = online ? "#34a853" : "#ea4335";
        addRow(connGrid, "Status", statusVal, statusColor);

        String ip = d.getConnectionIp();
        if (ip == null || ip.isEmpty()) {
            ip = d.getClientIp();
        }
        addRow(connGrid, "IP Address", ip != null && !ip.isEmpty() ? ip : "—", "#333");

        body.add(connGrid);

        // ── WiFi / Network section ────────────────────────────
        body.add(buildSectionHeader("WiFi / Network"));

        LayoutContainer netGrid = new LayoutContainer();
        netGrid.setLayout(new TableLayout(2));
        netGrid.setStyleAttribute("margin-bottom", "16px");

        String ssid = d.getWifiSSID();
        String networkVal;
        if (ssid != null && !ssid.isEmpty()) {
            networkVal = ssid;
        } else {
            networkVal = online ? "Ethernet" : "—";
        }
        addRow(netGrid, "SSID", networkVal, "#333");

        String signal = d.getSignal();
        addRow(netGrid, "Signal", (signal != null && !signal.isEmpty()) ? signal : "—", "#333");

        String hs = d.getHotSpotStatus();
        String hsVal;
        String hsColor;
        if ("on".equalsIgnoreCase(hs)) {
            hsVal = "On";
            hsColor = "#34a853";
        } else if ("off".equalsIgnoreCase(hs)) {
            hsVal = "Off";
            hsColor = "#888";
        } else {
            hsVal = "—";
            hsColor = "#888";
        }
        addRow(netGrid, "Hotspot (AP)", hsVal, hsColor);

        body.add(netGrid);
        body.layout(true);
    }

    // ── Helpers ───────────────────────────────────────────────

    private LayoutContainer buildSectionHeader(String title) {
        LayoutContainer header = new LayoutContainer();
        header.setStyleAttribute("border-bottom", "1px solid #e0e0e0");
        header.setStyleAttribute("margin-bottom", "6px");
        header.setStyleAttribute("padding-bottom", "4px");

        Text t = new Text(title);
        t.setStyleAttribute("font-size", "13px");
        t.setStyleAttribute("font-weight", "bold");
        t.setStyleAttribute("color", "#444");
        header.add(t);
        return header;
    }

    private void addRow(LayoutContainer grid, String label, String value, String valueColor) {
        TableData tdLabel = new TableData();
        tdLabel.setWidth("130px");
        tdLabel.setPadding(4);

        Text labelText = new Text(label + ":");
        labelText.setStyleAttribute("font-size", "12px");
        labelText.setStyleAttribute("color", "#666");
        labelText.setStyleAttribute("white-space", "nowrap");
        grid.add(labelText, tdLabel);

        TableData tdValue = new TableData();
        tdValue.setPadding(4);

        Text valueText = new Text(value != null ? value : "—");
        valueText.setStyleAttribute("font-size", "12px");
        valueText.setStyleAttribute("color", valueColor != null ? valueColor : "#333");
        valueText.setStyleAttribute("font-weight", "500");
        grid.add(valueText, tdValue);
    }
}
