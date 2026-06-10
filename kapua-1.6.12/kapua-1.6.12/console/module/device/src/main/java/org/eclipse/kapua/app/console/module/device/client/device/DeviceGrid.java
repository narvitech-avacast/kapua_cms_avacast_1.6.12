/*******************************************************************************
 * Copyright (c) 2018, 2022 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.app.console.module.device.client.device;

import com.extjs.gxt.ui.client.Style.HorizontalAlignment;
import com.extjs.gxt.ui.client.data.BasePagingLoadResult;
import com.extjs.gxt.ui.client.data.LoadEvent;
import com.extjs.gxt.ui.client.data.PagingLoadConfig;
import com.extjs.gxt.ui.client.data.PagingLoadResult;
import com.extjs.gxt.ui.client.data.RpcProxy;
import com.extjs.gxt.ui.client.event.LoadListener;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.grid.ColumnConfig;
import com.extjs.gxt.ui.client.widget.grid.ColumnData;
import com.extjs.gxt.ui.client.widget.grid.Grid;
import com.extjs.gxt.ui.client.widget.grid.GridCellRenderer;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.AsyncCallback;
import org.eclipse.kapua.app.console.module.api.client.messages.ConsoleMessages;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.IconSet;
import org.eclipse.kapua.app.console.module.api.client.resources.icons.KapuaIcon;
import org.eclipse.kapua.app.console.module.api.client.ui.color.Color;
import org.eclipse.kapua.app.console.module.api.client.ui.grid.EntityGrid;
import org.eclipse.kapua.app.console.module.api.client.ui.view.AbstractEntityView;
import org.eclipse.kapua.app.console.module.api.client.ui.widget.EntityCRUDToolbar;
import org.eclipse.kapua.app.console.module.api.client.ui.widget.KapuaPagingToolbarMessages;
import org.eclipse.kapua.app.console.module.api.shared.model.GwtConfigComponent;
import org.eclipse.kapua.app.console.module.api.shared.model.GwtConfigParameter;
import org.eclipse.kapua.app.console.module.api.shared.model.query.GwtQuery;
import org.eclipse.kapua.app.console.module.api.shared.model.session.GwtSession;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDevice;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDeviceQuery;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDeviceQueryPredicates;
import org.eclipse.kapua.app.console.module.device.shared.model.GwtDeviceQueryPredicates.GwtDeviceStatus;
import org.eclipse.kapua.app.console.module.device.shared.model.permission.DeviceSessionPermission;
import org.eclipse.kapua.app.console.module.device.shared.service.GwtDeviceManagementService;
import org.eclipse.kapua.app.console.module.device.shared.service.GwtDeviceManagementServiceAsync;
import org.eclipse.kapua.app.console.module.device.shared.service.GwtDeviceService;
import org.eclipse.kapua.app.console.module.device.shared.service.GwtDeviceServiceAsync;

import java.util.ArrayList;
import java.util.List;

public class DeviceGrid extends EntityGrid<GwtDevice> {

    private GwtDeviceQuery query;
    private DeviceGridToolbar toolbar;

    private static final GwtDeviceServiceAsync GWT_DEVICE_SERVICE = GWT.create(GwtDeviceService.class);
    private static final GwtDeviceManagementServiceAsync GWT_DEVICE_MANAGEMENT_SERVICE = GWT.create(GwtDeviceManagementService.class);

    private static final ConsoleMessages MSGS = GWT.create(ConsoleMessages.class);
    private static final String DEVICE = "device";
    private static final String NETWORK_CONFIG_COMPONENT_ID = "com.PeerInternet.Network.Config";
    private static final String WIFI_CONNECTED_SSID = "WiFiConnectedSSID";
    private static final String WIFI_SIGNAL = "WiFiSignal";
    private static final String HOT_SPOT_STATUS = "HotSpotStatus";

    public DeviceGrid(AbstractEntityView<GwtDevice> entityView, GwtSession currentSession) {
        super(entityView, currentSession);
        query = new GwtDeviceQuery();
        query.setScopeId(currentSession.getSelectedAccountId());
        query.setPredicates(new GwtDeviceQueryPredicates());
        enforceEnabledDevices(query);
    }

    @Override
    protected RpcProxy<PagingLoadResult<GwtDevice>> getDataProxy() {
        return new RpcProxy<PagingLoadResult<GwtDevice>>() {

            @Override
            protected void load(Object loadConfig, AsyncCallback<PagingLoadResult<GwtDevice>> callback) {
                GWT_DEVICE_SERVICE.query((PagingLoadConfig) loadConfig,
                        query,
                        callback);
            }
        };
    }

    @Override
    public String getEmptyGridText() {
        return MSGS.gridNoResultFound(DEVICE);
    }

    @Override
    protected KapuaPagingToolbarMessages getKapuaPagingToolbarMessages() {
        return new KapuaPagingToolbarMessages() {

            @Override
            public String pagingToolbarShowingPost() {
                return MSGS.specificPagingToolbarShowingPost(DEVICE);
            }

            @Override
            public String pagingToolbarNoResult() {
                return MSGS.specificPagingToolbarNoResult(DEVICE);
            }
        };
    }

    @Override
    protected List<ColumnConfig> getColumns() {
        //
        // Column Configuration
        List<ColumnConfig> columnConfigs = new ArrayList<ColumnConfig>();

        ColumnConfig column = new ColumnConfig("displayName", "Name", 150);
        column.setSortable(true);
        columnConfigs.add(column);

        column = new ColumnConfig("peerStatus", "Status", 120);
        GridCellRenderer<GwtDevice> statusRenderer = new GridCellRenderer<GwtDevice>() {

            @Override
            public String render(GwtDevice gwtDevice, String property, ColumnData config, int rowIndex, int colIndex, ListStore<GwtDevice> deviceList, Grid<GwtDevice> grid) {
                KapuaIcon icon;
                if (gwtDevice.isOnline()) {
                    icon = new KapuaIcon(IconSet.CIRCLE);
                    icon.setColor(Color.GREEN);
                } else {
                    icon = new KapuaIcon(IconSet.CIRCLE);
                    icon.setColor(Color.GREY);
                }
                icon.setTitle(gwtDevice.getPeerStatus());
                return icon.getInlineHTML() + "&nbsp;" + gwtDevice.getPeerStatus();
            }
        };
        column.setRenderer(statusRenderer);
        column.setSortable(false);
        columnConfigs.add(column);

        column = new ColumnConfig("modelName", "Device", 120);
        columnConfigs.add(column);

        column = new ColumnConfig("version", "Version", 100);
        columnConfigs.add(column);

        column = new ColumnConfig("network", "Network", 140);
        column.setSortable(false);
        columnConfigs.add(column);

        column = new ColumnConfig("clientIp", "IP", 110);
        column.setSortable(false);
        columnConfigs.add(column);

        column = new ColumnConfig("hotSpotStatus", "AP", 50);
        GridCellRenderer<GwtDevice> apRenderer = new GridCellRenderer<GwtDevice>() {

            @Override
            public String render(GwtDevice gwtDevice, String property, ColumnData columnData, int row, int colum, ListStore<GwtDevice> listStore, Grid<GwtDevice> grid) {
                KapuaIcon icon = new KapuaIcon(IconSet.CIRCLE);
                if ("on".equalsIgnoreCase(gwtDevice.getHotSpotStatus())) {
                    icon.setColor(Color.GREEN);
                    icon.setTitle("Online");
                } else {
                    icon.setColor(Color.GREY);
                    icon.setTitle("Offline");
                }
                return icon.getInlineHTML();
            }
        };
        column.setRenderer(apRenderer);
        column.setAlignment(HorizontalAlignment.CENTER);
        column.setSortable(false);
        columnConfigs.add(column);

        column = new ColumnConfig("signal", "Signal", 80);
        column.setSortable(false);
        columnConfigs.add(column);

        column = new ColumnConfig("standbyApp", "Showing", 120);
        column.setSortable(false);
        columnConfigs.add(column);

        return columnConfigs;
    }

    @Override
    protected void configureEntityGrid() {
        super.configureEntityGrid();
        entityLoader.addLoadListener(new DeviceLoadListener());
    }

    @Override
    protected void selectionChangedEvent(GwtDevice selectedItem) {
        super.selectionChangedEvent(selectedItem);
        getToolbar().getAddEntityButton().setEnabled(currentSession.hasPermission(DeviceSessionPermission.write()));
        if (selectedItem != null) {
            getToolbar().getEditEntityButton().setEnabled(currentSession.hasPermission(DeviceSessionPermission.write()));
            getToolbar().getDeleteEntityButton().setEnabled(currentSession.hasPermission(DeviceSessionPermission.delete()));
        } else {
            getToolbar().getEditEntityButton().setEnabled(false);
            getToolbar().getDeleteEntityButton().setEnabled(false);
        }
    }

    @Override
    public GwtQuery getFilterQuery() {
        return query;
    }

    @Override
    public void setFilterQuery(GwtQuery filterQuery) {
        query = (GwtDeviceQuery) filterQuery;
        enforceEnabledDevices(query);
    }

    @Override
    protected EntityCRUDToolbar<GwtDevice> getToolbar() {
        if (toolbar == null) {
            toolbar = new DeviceGridToolbar(currentSession);
        }
        return toolbar;
    }

    private void enforceEnabledDevices(GwtDeviceQuery deviceQuery) {
        if (deviceQuery.getPredicates() == null) {
            deviceQuery.setPredicates(new GwtDeviceQueryPredicates());
        }
        deviceQuery.getPredicates().setDeviceStatus(GwtDeviceStatus.ENABLED.name());
    }

    private class DeviceLoadListener extends LoadListener {
        @Override
        public void loaderLoad(LoadEvent le) {
            super.loaderLoad(le);
            toolbar.setExportEnabled(((BasePagingLoadResult) le.getData()).getTotalLength() != 0);
            loadOnlineDeviceNetworkConfigurations((BasePagingLoadResult<GwtDevice>) le.getData());
        }
    }

    private void loadOnlineDeviceNetworkConfigurations(BasePagingLoadResult<GwtDevice> devices) {
        for (final GwtDevice device : devices.getData()) {
            if (device.isOnline()) {
                GWT_DEVICE_MANAGEMENT_SERVICE.findDeviceConfigurations(device, new AsyncCallback<List<GwtConfigComponent>>() {

                    @Override
                    public void onFailure(Throwable caught) {
                        device.setWifiSSID("");
                        device.setWifiSignal("");
                        device.setHotSpotStatus("");
                        entityStore.update(device);
                    }

                    @Override
                    public void onSuccess(List<GwtConfigComponent> components) {
                        applyNetworkConfiguration(device, components);
                        entityStore.update(device);
                    }
                });
            } else {
                device.setWifiSSID("");
                device.setWifiSignal("");
                device.setHotSpotStatus("");
                entityStore.update(device);
            }
        }
    }

    private void applyNetworkConfiguration(GwtDevice device, List<GwtConfigComponent> components) {
        GwtConfigComponent networkConfig = findNetworkConfiguration(components);
        if (networkConfig == null) {
            device.setWifiSSID("");
            device.setWifiSignal("");
            device.setHotSpotStatus("");
            return;
        }

        device.setWifiSSID(getParameterValue(networkConfig, WIFI_CONNECTED_SSID));
        device.setWifiSignal(getParameterValue(networkConfig, WIFI_SIGNAL));
        device.setHotSpotStatus(getParameterValue(networkConfig, HOT_SPOT_STATUS));
    }

    private GwtConfigComponent findNetworkConfiguration(List<GwtConfigComponent> components) {
        if (components == null) {
            return null;
        }
        for (GwtConfigComponent component : components) {
            if (NETWORK_CONFIG_COMPONENT_ID.equals(component.getComponentId())) {
                return component;
            }
        }
        return null;
    }

    private String getParameterValue(GwtConfigComponent component, String parameterName) {
        GwtConfigParameter parameter = component.getParameter(parameterName);
        if (parameter == null) {
            return "";
        }
        if (parameter.getValue() != null && !parameter.getValue().isEmpty()) {
            return parameter.getValue();
        }
        if (parameter.getValues() != null && parameter.getValues().length > 0 && parameter.getValues()[0] != null) {
            return parameter.getValues()[0];
        }
        return "";
    }
}
