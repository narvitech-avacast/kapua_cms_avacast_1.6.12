/*******************************************************************************
 * Copyright (c) 2026 Narvitech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.app.api.core.model.EntityId;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.device.management.DeviceManagementDomains;
import org.eclipse.kapua.service.device.management.exception.DeviceManagementTimeoutException;
import org.eclipse.kapua.service.device.management.exception.DeviceNotConnectedException;
import org.eclipse.kapua.service.device.management.message.KapuaAppProperties;
import org.eclipse.kapua.service.device.management.message.KapuaMethod;
import org.eclipse.kapua.service.device.management.request.DeviceRequestManagementService;
import org.eclipse.kapua.service.device.management.request.GenericRequestFactory;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestChannel;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestMessage;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestPayload;
import org.eclipse.kapua.service.device.management.request.message.response.GenericResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Bridges the Illuminet Matrix RX/TX HTTP command set ({@code http://{ip}:9090/illuminet/...})
 * onto Kapua's MQTT Device Management framework, following the same GenericRequestChannel
 * pattern already used by {@link DeviceManagementDigitalSignage} for SIGNAGE-V1.
 * <p>
 * A companion MQTT&lt;-&gt;HTTP bridge agent must run on (or alongside) the Illuminet RX device,
 * subscribed to the {@code ILLUMINET-V1} app topics, translating each request into the
 * corresponding local {@code http://127.0.0.1:9090/illuminet/...} call and relaying the
 * JSON response back. That agent is out of scope of this repository.
 */
@Path("{scopeId}/devices/{deviceId}/illuminet")
public class DeviceManagementIlluminet extends AbstractKapuaResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceManagementIlluminet.class);

    private static final long DEFAULT_DEVICE_TIMEOUT = 5000L;
    private static final long MAX_DEVICE_TIMEOUT = 10000L;

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final DeviceRequestManagementService REQUEST_SERVICE =
            LOCATOR.getService(DeviceRequestManagementService.class);
    private static final GenericRequestFactory REQUEST_FACTORY =
            LOCATOR.getFactory(GenericRequestFactory.class);
    private static final AuthorizationService AUTHORIZATION_SERVICE =
            LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY =
            LOCATOR.getFactory(PermissionFactory.class);

    private static final KapuaAppProperties APP_NAME = () -> "ILLUMINET";
    private static final KapuaAppProperties APP_VERSION = () -> "V1";

    /**
     * Check the current multicast status.
     * Mirrors {@code GET http://{ip}:9090/illuminet/status/multicast}.
     */
    @GET
    @Path("status/multicast")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMulticastStatus(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.read);
        LOGGER.info("Illuminet: query multicast status scopeId={} deviceId={}", scopeId, deviceId);
        return forward(scopeId, deviceId, timeout, KapuaMethod.GET, Arrays.asList("status", "multicast"), null);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void checkPermission(ScopeId scopeId, Actions action) throws KapuaException {
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(
                DeviceManagementDomains.DEVICE_MANAGEMENT_DOMAIN, action, scopeId));
    }

    /**
     * Send a request to the device over the ILLUMINET-V1 MQTT app and relay the device's
     * JSON response straight through as the REST response body.
     *
     * @param resources ordered topic segments appended after the method, e.g. ["status", "multicast"]
     * @param metricValue optional single string value sent as the {@code illuminet.value} metric
     *                    (e.g. "1"/"0" for enable flags); pass {@code null} for parameterless requests
     */
    private Response forward(ScopeId scopeId, EntityId deviceId, Long timeout,
            KapuaMethod method, List<String> resources, String metricValue) throws KapuaException {

        GenericRequestChannel channel = REQUEST_FACTORY.newRequestChannel();
        channel.setAppName(APP_NAME);
        channel.setVersion(APP_VERSION);
        channel.setMethod(method);
        channel.setResources(resources);

        GenericRequestPayload payload = REQUEST_FACTORY.newRequestPayload();
        if (metricValue != null) {
            payload.getMetrics().put("illuminet.value", metricValue);
        }

        GenericRequestMessage request = REQUEST_FACTORY.newRequestMessage();
        request.setScopeId(scopeId);
        request.setDeviceId(deviceId);
        request.setCapturedOn(new Date());
        request.setChannel(channel);
        request.setPayload(payload);

        long requestTimeout = boundedDeviceTimeout(timeout);
        LOGGER.info(
                "Sending Illuminet request scopeId={} deviceId={} appId=ILLUMINET-V1 method={} resources={} timeout={}",
                scopeId, deviceId, method, resources, requestTimeout);

        GenericResponseMessage response;
        try {
            response = REQUEST_SERVICE.exec(scopeId, deviceId, request, requestTimeout);
        } catch (DeviceNotConnectedException e) {
            return errorResponse(Response.Status.SERVICE_UNAVAILABLE, "DEVICE_OFFLINE", safeMessage(e));
        } catch (DeviceManagementTimeoutException e) {
            return errorResponse(Response.Status.GATEWAY_TIMEOUT, "DEVICE_TIMEOUT", safeMessage(e));
        }

        if (response == null) {
            return errorResponse(Response.Status.BAD_GATEWAY, "INVALID_DEVICE_RESPONSE", "Device returned no response");
        }

        byte[] bodyBytes = (response.getPayload() != null) ? response.getPayload().getBody() : null;
        String responseJson = (bodyBytes != null && bodyBytes.length > 0)
                ? new String(bodyBytes, StandardCharsets.UTF_8)
                : "{}";

        int httpStatus = mapResponseCode(response);
        return Response.status(httpStatus)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseJson)
                .build();
    }

    private int mapResponseCode(GenericResponseMessage response) {
        if (response.getResponseCode() == null) {
            return Response.Status.BAD_GATEWAY.getStatusCode();
        }
        switch (response.getResponseCode()) {
            case ACCEPTED: return 200;
            case SENT: return 202;
            case BAD_REQUEST: return 400;
            case NOT_FOUND: return 404;
            case INTERNAL_ERROR: return 500;
            default: return 502;
        }
    }

    private long boundedDeviceTimeout(Long timeout) {
        if (timeout == null || timeout <= 0) {
            return DEFAULT_DEVICE_TIMEOUT;
        }
        return Math.min(timeout, MAX_DEVICE_TIMEOUT);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private Response errorResponse(Response.Status status, String errorCode, String message) {
        JsonObject body = Json.createObjectBuilder()
                .add("errorCode", errorCode)
                .add("message", message == null ? "" : message)
                .build();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(toJson(body))
                .build();
    }

    private String toJson(JsonStructure value) {
        StringWriter out = new StringWriter();
        try (JsonWriter w = Json.createWriter(out)) {
            w.write(value);
        }
        return out.toString();
    }
}
