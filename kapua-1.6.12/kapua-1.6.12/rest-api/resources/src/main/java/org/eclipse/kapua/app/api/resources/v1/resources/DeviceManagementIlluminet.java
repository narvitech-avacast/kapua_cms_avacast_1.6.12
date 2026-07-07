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
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;

/**
 * Controls Illuminet Matrix RX devices two ways:
 * <ul>
 *   <li>Direct HTTP ({@code annotator/*} methods below): the CMS backend calls the RX
 *   device's own local API ({@code http://{ip}:9090/illuminet/...}) directly, bypassing
 *   MQTT/Kura entirely. Works today with zero device-side changes, but requires the CMS
 *   host to have direct inbound-reachable network access to the device (same LAN, no
 *   NAT/firewall in between).</li>
 *   <li>MQTT ({@code mqtt/annotator/*} methods below): the same command is sent as an
 *   MQTT message over the device's already-subscribed {@code SIGNAGE-V1} app (same
 *   appName/version as {@link DeviceManagementDigitalSignage}), resource={@code illuminet},
 *   so it works even if the device is behind NAT/firewall and only has outbound MQTT
 *   connectivity — but requires the device-side SIGNAGE-V1 handler to add a branch for
 *   resource=="illuminet" that reads the {@code illuminet.ip}/{@code illuminet.port}/
 *   {@code illuminet.feature} metrics (e.g. ip=172.16.19.37, port=9090,
 *   feature="Annotator?enable=1"), calls {@code http://<ip>:<port>/illuminet/<feature>}
 *   (not necessarily its own localhost — the receiving device can act as a relay to a
 *   different target), and returns the JSON as the response body. That device-side
 *   change is out of scope of this repository.</li>
 * </ul>
 */
@Path("{scopeId}/devices/{deviceId}/illuminet")
public class DeviceManagementIlluminet extends AbstractKapuaResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceManagementIlluminet.class);

    private static final int ILLUMINET_PORT = 9090;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 5000;
    private static final java.util.regex.Pattern IPV4_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final AuthorizationService AUTHORIZATION_SERVICE =
            LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY =
            LOCATOR.getFactory(PermissionFactory.class);
    private static final DeviceRequestManagementService REQUEST_SERVICE =
            LOCATOR.getService(DeviceRequestManagementService.class);
    private static final GenericRequestFactory REQUEST_FACTORY =
            LOCATOR.getFactory(GenericRequestFactory.class);

    // Piggyback on the device's already-subscribed SIGNAGE-V1 app instead of a new,
    // unsubscribed ILLUMINET-V1 app (see DeviceManagementDigitalSignage for the same values).
    private static final KapuaAppProperties MQTT_APP_NAME = () -> "SIGNAGE";
    private static final KapuaAppProperties MQTT_APP_VERSION = () -> "V1";
    private static final String MQTT_ILLUMINET_RESOURCE = "illuminet";
    private static final String MQTT_FEATURE_METRIC = "illuminet.feature";
    private static final String MQTT_IP_METRIC = "illuminet.ip";
    private static final String MQTT_PORT_METRIC = "illuminet.port";
    private static final long MQTT_DEFAULT_TIMEOUT = 5000L;
    private static final long MQTT_MAX_TIMEOUT = 10000L;

    // ── Direct HTTP (CMS backend -> device's own :9090 API) ─────────────────────

    /** Enable/disable the annotator feature. Mirrors {@code GET /illuminet/Annotator?enable=0|1}. */
    @PUT
    @Path("annotator/enable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorEnable(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("enable") int enable) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (enable != 0 && enable != 1) {
            return badRequest("enable must be 0 or 1");
        }
        LOGGER.info("Illuminet: set annotator enable={} ip={}", enable, ip);
        return forward(ip, "Annotator?enable=" + enable);
    }

    /** Show/hide the annotator bar. Mirrors {@code GET /illuminet/Annotator?visible=0|1}. */
    @PUT
    @Path("annotator/visible")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorVisible(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("visible") int visible) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (visible != 0 && visible != 1) {
            return badRequest("visible must be 0 or 1");
        }
        LOGGER.info("Illuminet: set annotator visible={} ip={}", visible, ip);
        return forward(ip, "Annotator?visible=" + visible);
    }

    /** Set the annotator brush color (1-5). Mirrors {@code GET /illuminet/Annotator?brush-color=N}. */
    @PUT
    @Path("annotator/brush-color")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorBrushColor(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("color") int color) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (color < 1 || color > 5) {
            return badRequest("color must be between 1 and 5");
        }
        LOGGER.info("Illuminet: set annotator brush-color={} ip={}", color, ip);
        return forward(ip, "Annotator?brush-color=" + color);
    }

    // ── MQTT (piggybacked on SIGNAGE-V1, for devices without direct HTTP reachability) ──

    /** MQTT equivalent of {@link #setAnnotatorEnable}. */
    @PUT
    @Path("mqtt/annotator/enable")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorEnableMqtt(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("port") @DefaultValue("9090") int port,
            @QueryParam("enable") int enable,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (enable != 0 && enable != 1) {
            return badRequest("enable must be 0 or 1");
        }
        LOGGER.info("Illuminet (MQTT): set annotator enable={} target={}:{} scopeId={} deviceId={}", enable, ip, port, scopeId, deviceId);
        return forwardMqtt(scopeId, deviceId, timeout, "Annotator?enable=" + enable, ip, port);
    }

    /** MQTT equivalent of {@link #setAnnotatorVisible}. */
    @PUT
    @Path("mqtt/annotator/visible")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorVisibleMqtt(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("port") @DefaultValue("9090") int port,
            @QueryParam("visible") int visible,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (visible != 0 && visible != 1) {
            return badRequest("visible must be 0 or 1");
        }
        LOGGER.info("Illuminet (MQTT): set annotator visible={} target={}:{} scopeId={} deviceId={}", visible, ip, port, scopeId, deviceId);
        return forwardMqtt(scopeId, deviceId, timeout, "Annotator?visible=" + visible, ip, port);
    }

    /** MQTT equivalent of {@link #setAnnotatorBrushColor}. */
    @PUT
    @Path("mqtt/annotator/brush-color")
    @Produces(MediaType.APPLICATION_JSON)
    public Response setAnnotatorBrushColorMqtt(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("ip") String ip,
            @QueryParam("port") @DefaultValue("9090") int port,
            @QueryParam("color") int color,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        if (color < 1 || color > 5) {
            return badRequest("color must be between 1 and 5");
        }
        LOGGER.info("Illuminet (MQTT): set annotator brush-color={} target={}:{} scopeId={} deviceId={}", color, ip, port, scopeId, deviceId);
        return forwardMqtt(scopeId, deviceId, timeout, "Annotator?brush-color=" + color, ip, port);
    }

    /**
     * Send the given Illuminet feature path to the device over the (already-subscribed)
     * SIGNAGE-V1 MQTT app, resource=illuminet, telling the device which IP:port to visit
     * and execute the command against (instead of assuming the device should always call
     * its own localhost) — this lets the receiving device act as a relay/gateway to a
     * different target if needed. Relays the device's JSON response straight through as
     * the REST response body.
     *
     * @param feature the Illuminet HTTP feature path + query, e.g. "Annotator?enable=1"
     * @param ip target IP the device should visit, e.g. "172.16.19.37"
     * @param port target port the device should visit, e.g. 9090
     */
    private Response forwardMqtt(ScopeId scopeId, EntityId deviceId, Long timeout, String feature, String ip, int port) throws KapuaException {
        if (ip == null || !isValidIpv4(ip)) {
            return badRequest("ip must be a valid IPv4 address, e.g. 172.16.19.37");
        }
        if (port < 1 || port > 65535) {
            return badRequest("port must be between 1 and 65535");
        }

        GenericRequestChannel channel = REQUEST_FACTORY.newRequestChannel();
        channel.setAppName(MQTT_APP_NAME);
        channel.setVersion(MQTT_APP_VERSION);
        channel.setMethod(KapuaMethod.EXECUTE);
        channel.setResources(Arrays.asList(MQTT_ILLUMINET_RESOURCE));

        GenericRequestPayload payload = REQUEST_FACTORY.newRequestPayload();
        payload.getMetrics().put(MQTT_FEATURE_METRIC, feature);
        payload.getMetrics().put(MQTT_IP_METRIC, ip);
        payload.getMetrics().put(MQTT_PORT_METRIC, String.valueOf(port));

        GenericRequestMessage request = REQUEST_FACTORY.newRequestMessage();
        request.setScopeId(scopeId);
        request.setDeviceId(deviceId);
        request.setCapturedOn(new Date());
        request.setChannel(channel);
        request.setPayload(payload);

        long requestTimeout = (timeout == null || timeout <= 0) ? MQTT_DEFAULT_TIMEOUT : Math.min(timeout, MQTT_MAX_TIMEOUT);
        LOGGER.info(
                "Illuminet (MQTT): sending scopeId={} deviceId={} appId=SIGNAGE-V1 resource=illuminet "
                        + "target={}:{} feature={} timeout={}",
                scopeId, deviceId, ip, port, feature, requestTimeout);

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

        LOGGER.info("Illuminet (MQTT): device replied responseCode={} body={}",
                response.getResponseCode(), responseJson);

        return Response.status(mapResponseCode(response))
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

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void checkPermission(ScopeId scopeId, Actions action) throws KapuaException {
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(
                DeviceManagementDomains.DEVICE_MANAGEMENT_DOMAIN, action, scopeId));
    }

    /**
     * Call {@code http://<ip>:9090/illuminet/<pathAndQuery>} directly and relay the
     * device's JSON response straight through as the REST response body.
     */
    private Response forward(String ip, String pathAndQuery) {
        if (ip == null || !IPV4_PATTERN.matcher(ip).matches() || !isValidIpv4(ip)) {
            return badRequest("ip must be a valid IPv4 address, e.g. 172.16.19.37");
        }

        String urlStr = "http://" + ip + ":" + ILLUMINET_PORT + "/illuminet/" + pathAndQuery;
        LOGGER.info("Illuminet: calling device directly url={}", urlStr);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            int status = conn.getResponseCode();
            InputStream is = status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String body = readBody(is);

            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body.isEmpty() ? "{}" : body)
                    .build();
        } catch (ConnectException e) {
            return errorResponse(Response.Status.SERVICE_UNAVAILABLE, "DEVICE_UNREACHABLE", safeMessage(e));
        } catch (SocketTimeoutException e) {
            return errorResponse(Response.Status.GATEWAY_TIMEOUT, "DEVICE_TIMEOUT", safeMessage(e));
        } catch (IOException e) {
            return errorResponse(Response.Status.BAD_GATEWAY, "DEVICE_CALL_FAILED", safeMessage(e));
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean isValidIpv4(String ip) {
        java.util.regex.Matcher matcher = IPV4_PATTERN.matcher(ip);
        if (!matcher.matches()) {
            return false;
        }
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(matcher.group(i));
            if (octet < 0 || octet > 255) {
                return false;
            }
        }
        return true;
    }

    private String readBody(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String safeMessage(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private Response badRequest(String message) {
        return errorResponse(Response.Status.BAD_REQUEST, "INVALID_REQUEST", message);
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
