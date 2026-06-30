/*******************************************************************************
 * Copyright (c) 2016, 2022 Eurotech and/or its affiliates and others
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
package org.eclipse.kapua.app.api.resources.v1.resources;

import com.google.common.base.Strings;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.app.api.core.model.CountResult;
import org.eclipse.kapua.app.api.core.model.EntityId;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Capacity;
import org.eclipse.kapua.app.api.resources.v1.resources.model.DeviceRegistrationTokenStore;
import org.eclipse.kapua.app.api.resources.v1.resources.model.DeviceType;
import org.eclipse.kapua.app.api.resources.v1.resources.model.GatewayConfigXmlGen;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Str;
import org.eclipse.kapua.app.api.resources.v1.resources.model.Words;
import org.eclipse.kapua.common.util.GatewayConfig.GatewayConfigModel;
import org.eclipse.kapua.commons.responeCode.DeviceResponseCode;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.commons.responeCode.AccountResponseCode;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.query.SortOrder;
import org.eclipse.kapua.model.query.predicate.AndPredicate;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.authentication.credential.CredentialListResult;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.device.management.gatewayconfig.DeviceTokenGenGatewayconfig;
import org.eclipse.kapua.service.device.registry.Device;
import org.eclipse.kapua.service.device.registry.DeviceAttributes;
import org.eclipse.kapua.service.device.registry.DeviceCreator;
import org.eclipse.kapua.service.device.registry.DeviceFactory;
import org.eclipse.kapua.service.device.registry.DeviceListResult;
import org.eclipse.kapua.service.device.registry.DeviceQuery;
import org.eclipse.kapua.service.device.registry.DeviceRegistryService;
import org.eclipse.kapua.service.device.registry.connection.DeviceConnectionStatus;
import org.eclipse.kapua.service.device.registry.event.DeviceEvent;
import org.eclipse.kapua.service.device.registry.event.DeviceEventFactory;
import org.eclipse.kapua.service.device.registry.event.DeviceEventListResult;
import org.eclipse.kapua.service.device.registry.event.DeviceEventQuery;
import org.eclipse.kapua.service.device.registry.event.DeviceEventService;
import org.eclipse.kapua.service.tag.Tag;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserFactory;
import org.eclipse.kapua.service.user.UserListResult;
import org.eclipse.kapua.service.user.UserQuery;
import org.eclipse.kapua.service.user.UserService;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Path("{scopeId}/devices")
public class Devices extends AbstractKapuaResource {

    private final KapuaLocator locator = KapuaLocator.getInstance();

    private final DeviceRegistryService deviceService = locator.getService(DeviceRegistryService.class);
    private final DeviceFactory deviceFactory = locator.getFactory(DeviceFactory.class);

    private final DeviceEventService deviceEventService = locator.getService(DeviceEventService.class);
    private final DeviceEventFactory deviceEventFactory = locator.getFactory(DeviceEventFactory.class);

    private final UserService userService = locator.getService(UserService.class);
    private final UserFactory userFactory = locator.getFactory(UserFactory.class);

    private final AccountService accountService = locator.getService(AccountService.class);

    private final CredentialService credentialService = locator.getService(CredentialService.class);

    @Context
    private HttpHeaders headers;

    // ─── Standard CRUD ──────────────────────────────────────────────────────────

    /**
     * Gets the {@link Device} list in the scope.
     *
     * @param scopeId          The {@link ScopeId} in which to search results.
     * @param tagId            The id of the {@link Tag} in which to search results
     * @param clientId         The id of the {@link Device} in which to search results
     * @param connectionStatus The {@link DeviceConnectionStatus} in which to search results
     * @param matchTerm        A term to be matched in at least one of the configured fields
     * @param fetchAttributes  Additional attributes to be returned. Allowed values: connection, lastEvent
     * @param askTotalCount    Ask for the total count of the matched entities in the result
     * @param sortParam        The name of the parameter that will be used as a sorting key
     * @param sortDir          The sort direction. Can be ASCENDING (default), DESCENDING.
     * @param offset           The result set offset.
     * @param limit            The result set limit.
     * @return The {@link DeviceListResult} of all the devices associated to the current selected scope.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceListResult simpleQuery(
            @PathParam("scopeId") ScopeId scopeId,
            @QueryParam("tagId") EntityId tagId,
            @QueryParam("clientId") String clientId,
            @QueryParam("status") DeviceConnectionStatus connectionStatus,
            @QueryParam("matchTerm") String matchTerm,
            @QueryParam("fetchAttributes") List<String> fetchAttributes,
            @QueryParam("askTotalCount") boolean askTotalCount,
            @QueryParam("sortParam") String sortParam,
            @QueryParam("sortDir") @DefaultValue("ASCENDING") SortOrder sortDir,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("limit") @DefaultValue("50") int limit) throws KapuaException {

        DeviceQuery query = deviceFactory.newQuery(scopeId);
        AndPredicate andPredicate = query.andPredicate();

        if (tagId != null) {
            andPredicate.and(query.attributePredicate(DeviceAttributes.TAG_IDS, tagId));
        }
        if (!Strings.isNullOrEmpty(clientId)) {
            andPredicate.and(query.attributePredicate(DeviceAttributes.CLIENT_ID, clientId));
        }
        if (connectionStatus != null) {
            andPredicate.and(query.attributePredicate(DeviceAttributes.CONNECTION_STATUS, connectionStatus));
        }
        if (!Strings.isNullOrEmpty(matchTerm)) {
            andPredicate.and(query.matchPredicate(matchTerm));
        }
        if (!Strings.isNullOrEmpty(sortParam)) {
            query.setSortCriteria(query.fieldSortCriteria(sortParam, sortDir));
        }

        query.setPredicate(andPredicate);
        query.setFetchAttributes(fetchAttributes);
        query.setOffset(offset);
        query.setLimit(limit);
        query.setAskTotalCount(askTotalCount);

        return query(scopeId, query);
    }

    /**
     * Queries the results with the given {@link DeviceQuery} parameter.
     *
     * @param scopeId The {@link ScopeId} in which to search results.
     * @param query   The {@link DeviceQuery} to use to filter results.
     * @return The {@link DeviceListResult} of all the result matching the given {@link DeviceQuery} parameter.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @POST
    @Path("_query")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public DeviceListResult query(
            @PathParam("scopeId") ScopeId scopeId,
            DeviceQuery query) throws KapuaException {
        query.setScopeId(scopeId);
        return deviceService.query(query);
    }

    /**
     * Counts the results with the given {@link DeviceQuery} parameter.
     *
     * @param scopeId The {@link ScopeId} in which to search results.
     * @param query   The {@link DeviceQuery} to use to filter results.
     * @return The count of all the result matching the given {@link DeviceQuery} parameter.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @POST
    @Path("_count")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public CountResult count(
            @PathParam("scopeId") ScopeId scopeId,
            DeviceQuery query) throws KapuaException {
        query.setScopeId(scopeId);
        return new CountResult(deviceService.count(query));
    }

    /**
     * Creates a new Device based on the information provided in DeviceCreator parameter.
     *
     * @param scopeId       The {@link ScopeId} in which to create the {@link Device}
     * @param deviceCreator Provides the information for the new Device to be created.
     * @return The newly created Device object.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @POST
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response create(
            @PathParam("scopeId") ScopeId scopeId,
            DeviceCreator deviceCreator) throws KapuaException {
        deviceCreator.setScopeId(scopeId);
        return returnCreated(deviceService.create(deviceCreator));
    }

    /**
     * Returns the Device specified by the "deviceId" path parameter.
     *
     * @param scopeId  The {@link ScopeId} of the requested {@link Device}.
     * @param deviceId The id of the requested Device.
     * @return The requested Device object.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @GET
    @Path("{deviceId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Device find(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId) throws KapuaException {
        Device device = deviceService.find(scopeId, deviceId);
        return returnNotNullEntity(device, Device.TYPE, deviceId);
    }

    /**
     * Updates the Device based on the information provided in the Device parameter.
     *
     * @param scopeId  The ScopeId of the requested Device.
     * @param deviceId The id of the requested {@link Device}
     * @param device   The modified Device whose attributed need to be updated.
     * @return The updated device.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @PUT
    @Path("{deviceId}")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Device update(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            Device device) throws KapuaException {
        device.setScopeId(scopeId);
        device.setId(deviceId);
        return deviceService.update(device);
    }

    /**
     * Deletes the Device specified by the "deviceId" path parameter.
     *
     * @param scopeId  The ScopeId of the requested {@link Device}.
     * @param deviceId The id of the Device to be deleted.
     * @return HTTP 204 if operation has completed successfully.
     * @throws KapuaException Whenever something bad happens. See specific {@link KapuaService} exceptions.
     */
    @DELETE
    @Path("{deviceId}")
    public Response deleteDevice(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId) throws KapuaException {
        deviceService.delete(scopeId, deviceId);
        return returnNoContent();
    }

    // ─── Extended endpoints ──────────────────────────────────────────────────────

    /**
     * Count how many distinct device model types appear in the scope.
     *
     * @param scopeId The {@link ScopeId} to count types in.
     * @return The number of distinct modelId values.
     * @throws KapuaException Whenever something bad happens.
     */
    @GET
    @Path("deviceTypeNumber")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public CountResult deviceTypeNumber(
            @PathParam("scopeId") ScopeId scopeId) throws KapuaException {
        return new CountResult(deviceTypeList(scopeId).size());
    }

    /**
     * List distinct device model types with their occurrence count in the scope.
     *
     * @param scopeId The {@link ScopeId} to search in.
     * @return A list of {@link DeviceType} objects (modelId + count).
     * @throws KapuaException Whenever something bad happens.
     */
    @GET
    @Path("deviceTypeList")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceType> deviceTypeList(
            @PathParam("scopeId") ScopeId scopeId) throws KapuaException {

        DeviceQuery query = deviceFactory.newQuery(scopeId);
        DeviceListResult deviceList = query(scopeId, query);

        List<DeviceType> result = new ArrayList<>();
        for (int i = 0; i < deviceList.getSize(); i++) {
            String modelId = deviceList.getItem(i).getModelId();
            if (modelId == null) {
                modelId = "";
            }

            boolean found = false;
            for (int j = 0; j < result.size(); j++) {
                if (modelId.equals(result.get(j).getModelId())) {
                    result.get(j).setCount(result.get(j).getCount() + 1);
                    found = true;
                    break;
                }
            }
            if (!found) {
                result.add(new DeviceType(modelId, 1));
            }
        }
        return result;
    }

    /**
     * Returns all device events in the scope sorted from newest to oldest.
     *
     * @param scopeId The {@link ScopeId} to query events in.
     * @return Sorted {@link DeviceEventListResult}.
     * @throws KapuaException Whenever something bad happens.
     */
    @GET
    @Path("sortedEvents")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceEventListResult sortedEvents(
            @PathParam("scopeId") ScopeId scopeId) throws KapuaException {

        DeviceEventQuery query = deviceEventFactory.newQuery(scopeId);
        DeviceEventListResult eventList = deviceEventService.query(query);
        eventList.sort(RECEIVED_ON_DESC);
        return eventList;
    }

    private static final Comparator<DeviceEvent> RECEIVED_ON_DESC = (a, b) -> {
        if (a.getReceivedOn().after(b.getReceivedOn())) {
            return -1;
        } else if (a.getReceivedOn().before(b.getReceivedOn())) {
            return 1;
        }
        return 0;
    };

    /**
     * Placeholder: returns static capacity figures for a device.
     *
     * @param scopeId The {@link ScopeId}.
     * @return A {@link Capacity} object with total/used values.
     */
    @POST
    @Path("capacity")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Capacity deviceCapacity(
            @PathParam("scopeId") ScopeId scopeId) {
        return new Capacity(128, 64);
    }

    /**
     * Test endpoint – returns a fixed string.
     *
     * @param scopeId The {@link ScopeId}.
     * @return A {@link Str} wrapping "128".
     */
    @POST
    @Path("deviceTest")
    @Consumes({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Str deviceTest(
            @PathParam("scopeId") ScopeId scopeId) {
        return new Str("128");
    }

    /**
     * Generates a short-lived one-time token that a device can use to
     * self-register through the no-auth gateway config endpoint.
     *
     * @param scopeId The {@link ScopeId} in which the device will be created.
     * @return A {@link Words} response whose value is the registration token.
     */
    @POST
    @Path("registrationToken")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Words createDeviceRegistrationToken(
            @PathParam("scopeId") ScopeId scopeId) {

        try {
            Account account = accountService.find(scopeId);
            if (account == null) {
                AccountResponseCode code = AccountResponseCode.NOT_FIND_ACCOUNT;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }

            return new Words(DeviceRegistrationTokenStore.getInstance().issue(scopeId.toCompactId()));
        } catch (KapuaException e) {
            e.printStackTrace();
            Words err = new Words();
            err.setValue("500:" + e.getMessage());
            return err;
        }
    }

    /**
     * Creates a new Device and returns the gateway connection configuration.
     * <p>
     * The gateway config includes the broker connection parameters needed by the
     * newly created device to connect to Kapua.
     * </p>
     *
     * @param scopeId       The {@link ScopeId} in which to create the device.
     * @param deviceCreator The device creation request.
     * @return Gateway configuration as an XML string wrapped in {@link Words}, or an error code.
     */
    @POST
    @Path("deviceAndGatewayConfig")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Words createDeviceAndGiveGatewayConfig(
            @PathParam("scopeId") ScopeId scopeId,
            DeviceCreator deviceCreator) {

        deviceCreator.setScopeId(scopeId);
        SystemSetting systemSetting = SystemSetting.getInstance();

        try {
            Device device = deviceService.create(deviceCreator);

            GatewayConfigModel gcm = new GatewayConfigModel();
            gcm.setDeviceName(device.getClientId());

            // Look up the broker user (the auto-created "-broker" user in this account)
            User brokerUser = findBrokerUser(device.getScopeId());
            if (brokerUser == null) {
                Words err = new Words();
                err.setValue("5200:USER_NOT_FIND");
                return err;
            }

            gcm.setBrokerUser(brokerUser.getName());
            gcm.setBrokerHost(resolveBrokerHost(systemSetting));
            gcm.setBrokerPort(System.getProperty("gateway.broker.port",
                    systemSetting.getString(SystemSettingKey.BROKER_PORT, "1883")));
            gcm.setBrokerProtocol(System.getProperty("gateway.broker.scheme",
                    systemSetting.getString(SystemSettingKey.BROKER_SCHEME, "tcp")));

            // Resolve account name
            Account account = accountService.find(brokerUser.getScopeId());
            if (account == null) {
                AccountResponseCode code = AccountResponseCode.NOT_FIND_ACCOUNT;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }
            gcm.setAccountName(account.getName());

            // Resolve broker user's password
            CredentialListResult credList = credentialService.findByUserId(
                    brokerUser.getScopeId(), brokerUser.getId());
            if (credList == null || credList.getSize() == 0) {
                Words err = new Words();
                err.setValue("5104:NOT_FIND_CREDENTIAL");
                return err;
            }
            gcm.setBrokerPassword(resolveBrokerPassword(systemSetting, brokerUser));

            GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
            gcxg.setGatewayConfig(gcm);
            return gcxg.build();

        } catch (KapuaException e) {
            e.printStackTrace();
            Words err = new Words();
            err.setValue("500:" + e.getMessage());
            return err;
        }
    }

    /**
     * Returns the gateway configuration for the device identified by the given access token.
     *
     * @deprecated Use {@code DeviceNoAuth.getAndroidGatewayConfigByAccessToken} for unauthenticated access.
     *
     * @param scopeId          The {@link ScopeId}.
     * @param tokenWithPlatform Request containing the access token and target platform.
     * @return Gateway configuration as an XML string wrapped in {@link Words}, or an error code.
     */
    @Deprecated
    @POST
    @Path("getAndroidGatewayConfigByAccessToken")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Words getAndroidGatewayConfigByAccessToken(
            @PathParam("scopeId") ScopeId scopeId,
            DeviceTokenGenGatewayconfig tokenWithPlatform) {

        SystemSetting systemSetting = SystemSetting.getInstance();

        try {
            // Find device by client id (which acts as the device's access token)
            Device device = deviceService.findByClientId(scopeId, tokenWithPlatform.getAccessToken());
            if (device == null) {
                DeviceResponseCode code = DeviceResponseCode.NOT_FIND_DEVICE;
                Words err = new Words();
                err.setValue(code.fullDescription(code));
                return err;
            }

            GatewayConfigModel gcm = buildGatewayConfigModel(device, systemSetting);
            if (gcm == null) {
                Words err = new Words();
                err.setValue("5200:USER_NOT_FIND");
                return err;
            }

            if ("Android".equals(tokenWithPlatform.getPlatform())) {
                GatewayConfigXmlGen gcxg = new GatewayConfigXmlGen();
                gcxg.setGatewayConfig(gcm);
                return gcxg.build();
            }
            // Non-Android platforms not yet supported; return empty
            return new Words("");

        } catch (KapuaException e) {
            e.printStackTrace();
            Words err = new Words();
            err.setValue("500:" + e.getMessage());
            return err;
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────

    /**
     * Finds the broker user within the given scope by looking for a user whose name
     * ends with "-broker" (convention established by SimpleRegistrationProcessor).
     */
    private User findBrokerUser(org.eclipse.kapua.model.id.KapuaId scopeId) throws KapuaException {
        UserQuery query = userFactory.newQuery(scopeId);
        UserListResult userList = userService.query(query);
        if (userList == null || userList.getSize() == 0) {
            return null;
        }
        for (int i = 0; i < userList.getSize(); i++) {
            User user = userList.getItem(i);
            if (user.getName() != null && user.getName().endsWith("-broker")) {
                return user;
            }
        }
        return null;
    }

    /**
     * Builds a {@link GatewayConfigModel} for the given device, resolving broker user,
     * account, and system settings. Returns {@code null} if the broker user is not found.
     */
    private GatewayConfigModel buildGatewayConfigModel(Device device, SystemSetting systemSetting)
            throws KapuaException {

        GatewayConfigModel gcm = new GatewayConfigModel();
        gcm.setDeviceName(device.getClientId());

        User brokerUser = findBrokerUser(device.getScopeId());
        if (brokerUser == null) {
            return null;
        }

        gcm.setBrokerUser(brokerUser.getName());
        gcm.setBrokerHost(resolveBrokerHost(systemSetting));
        gcm.setBrokerPort(systemSetting.getString(SystemSettingKey.BROKER_PORT, "1883"));
        gcm.setBrokerProtocol(systemSetting.getString(SystemSettingKey.BROKER_SCHEME));

        Account account = accountService.find(brokerUser.getScopeId());
        if (account != null) {
            gcm.setAccountName(account.getName());
        }

        gcm.setBrokerPassword(resolveBrokerPassword(systemSetting, brokerUser));
        return gcm;
    }

    private String resolveBrokerHost(SystemSetting systemSetting) {
        String configuredHost = systemSetting.getString(SystemSettingKey.BROKER_HOST);
        String requestHost = firstForwardedValue(header("X-Forwarded-Host"));
        if (Strings.isNullOrEmpty(requestHost)) {
            requestHost = header("Host");
        }

        String host = stripPort(requestHost);
        if (!Strings.isNullOrEmpty(host)) {
            if (isLocalHost(host) && !Strings.isNullOrEmpty(configuredHost)) {
                return configuredHost;
            }
            return host;
        }

        return configuredHost;
    }

    private String header(String name) {
        return headers != null ? headers.getHeaderString(name) : null;
    }

    private String firstForwardedValue(String value) {
        if (Strings.isNullOrEmpty(value)) {
            return value;
        }

        int commaIndex = value.indexOf(',');
        return commaIndex >= 0 ? value.substring(0, commaIndex).trim() : value.trim();
    }

    private String stripPort(String host) {
        if (Strings.isNullOrEmpty(host)) {
            return host;
        }

        String trimmedHost = host.trim();
        if (trimmedHost.startsWith("[")) {
            int endBracket = trimmedHost.indexOf(']');
            return endBracket > 0 ? trimmedHost.substring(1, endBracket) : trimmedHost;
        }

        int colonIndex = trimmedHost.indexOf(':');
        return colonIndex >= 0 ? trimmedHost.substring(0, colonIndex) : trimmedHost;
    }

    private boolean isLocalHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    private String resolveBrokerPassword(SystemSetting systemSetting, User brokerUser) {
        String configuredPassword = systemSetting.getString(SystemSettingKey.BROKER_PASSWORD, "");
        if (configuredPassword != null && !configuredPassword.trim().isEmpty()) {
            return configuredPassword;
        }

        String accountBaseName = brokerUser.getName().replace("-broker", "");
        return accountBaseName + "-Password1!";
    }
}
