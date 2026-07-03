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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonStructure;
import javax.json.JsonWriter;
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

/**
 * Calls the Illuminet Matrix RX device's own local HTTP API
 * ({@code http://{ip}:9090/illuminet/...}) directly from the CMS backend, bypassing
 * MQTT/Kura entirely. This works because the RX device already exposes this API on its
 * own, on the same LAN as the CMS host (per "Illuminet Matrix feature command guide"),
 * so no device-side firmware/bridge changes are needed at all.
 * <p>
 * The target device is identified by IP address (query param {@code ip}), not by Kapua
 * device/clientId, since Illuminet's own API has no such concept.
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
