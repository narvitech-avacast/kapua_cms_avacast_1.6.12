/*******************************************************************************
 * Copyright (c) 2024 Eurotech and/or its affiliates and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.kapua.app.api.resources.v1.resources;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.KapuaIllegalArgumentException;
import org.eclipse.kapua.app.api.core.model.EntityId;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.eclipse.kapua.commons.jpa.JdbcConnectionUrlResolvers;
import org.eclipse.kapua.commons.model.id.IdGenerator;
import org.eclipse.kapua.commons.model.id.KapuaEid;
import org.eclipse.kapua.commons.setting.system.SystemSetting;
import org.eclipse.kapua.commons.setting.system.SystemSettingKey;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.domain.Actions;
import org.eclipse.kapua.service.authorization.AuthorizationService;
import org.eclipse.kapua.service.authorization.permission.PermissionFactory;
import org.eclipse.kapua.service.device.management.DeviceManagementDomains;
import org.eclipse.kapua.service.device.management.command.DeviceCommandFactory;
import org.eclipse.kapua.service.device.management.command.DeviceCommandInput;
import org.eclipse.kapua.service.device.management.command.DeviceCommandManagementService;
import org.eclipse.kapua.service.device.management.exception.DeviceManagementTimeoutException;
import org.eclipse.kapua.service.device.management.exception.DeviceNotConnectedException;
import org.eclipse.kapua.service.device.management.message.KapuaMethod;
import org.eclipse.kapua.service.device.management.message.KapuaAppProperties;
import org.eclipse.kapua.service.device.management.request.GenericRequestFactory;
import org.eclipse.kapua.service.device.management.request.DeviceRequestManagementService;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestChannel;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestMessage;
import org.eclipse.kapua.service.device.management.request.message.request.GenericRequestPayload;
import org.eclipse.kapua.service.device.management.request.message.response.GenericResponseMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Path("{scopeId}/devices/{deviceId}/digitalsignage")
public class DeviceManagementDigitalSignage extends AbstractKapuaResource {

    private static final String PLAYLIST_TABLE = "signage_playlist_custom";
    private static final String PLAYLIST_METRIC = "ds.playlist";
    private static final long MAX_IMAGE_SIZE = 20L * 1024L * 1024L;
    private static final long DEFAULT_DEVICE_TIMEOUT = 5000L;
    private static final long MAX_DEVICE_TIMEOUT = 10000L;
    private static final Map<String, String> IMAGE_EXTENSIONS = createImageExtensions();
    private static final Logger LOGGER =
            LoggerFactory.getLogger(DeviceManagementDigitalSignage.class);
    private static final SignageMediaLocation MEDIA_LOCATION =
            SignageMediaLocation.fromConfig();
    private static final SignagePlaylistResourceNormalizer RESOURCE_NORMALIZER =
            new SignagePlaylistResourceNormalizer(MEDIA_LOCATION);

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();
    private static final SystemSetting SYSTEM_SETTING = SystemSetting.getInstance();
    private static final DeviceRequestManagementService REQUEST_SERVICE =
            LOCATOR.getService(DeviceRequestManagementService.class);
    private static final GenericRequestFactory REQUEST_FACTORY =
            LOCATOR.getFactory(GenericRequestFactory.class);
    private static final AuthorizationService AUTHORIZATION_SERVICE =
            LOCATOR.getService(AuthorizationService.class);
    private static final PermissionFactory PERMISSION_FACTORY =
            LOCATOR.getFactory(PermissionFactory.class);
    private static final DeviceCommandManagementService COMMAND_SERVICE =
            LOCATOR.getService(DeviceCommandManagementService.class);
    private static final DeviceCommandFactory COMMAND_FACTORY =
            LOCATOR.getFactory(DeviceCommandFactory.class);

    private static final KapuaAppProperties APP_NAME = () -> "SIGNAGE";
    private static final KapuaAppProperties APP_VERSION = () -> "V1";

    /** Pre-generate a playlist ID so the client can use it for image uploads before creating the playlist. */
    @GET
    @Path("_new_playlist_id")
    @Produces(MediaType.APPLICATION_JSON)
    public Response newPlaylistId(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        String playlistId = new KapuaEid(IdGenerator.generate()).toCompactId();
        JsonObject response = Json.createObjectBuilder()
                .add("playlistId", playlistId)
                .build();
        return Response.ok(toJson(response), MediaType.APPLICATION_JSON).build();
    }

    /**
     * Upload an image for a playlist.
     * Pass ?playlistId=xxx (obtained from _new_playlist_id) to store the image under
     * {scopeId}/{playlistId}/ so the device can resolve the download URL from the playlist JSON.
     * Falls back to {scopeId}/{deviceId}/ when playlistId is omitted.
     */
    @POST
    @Path("media")
    @Consumes({ "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp" })
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadImage(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("playlistId") String playlistId,
            @HeaderParam("Content-Type") String contentType,
            @HeaderParam("Content-Length") Long contentLength,
            @HeaderParam("X-File-Name") String originalFileName,
            InputStream input) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        String normalizedType = normalizeContentType(contentType);
        String extension = IMAGE_EXTENSIONS.get(normalizedType);
        if (extension == null) {
            return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Unsupported image type\"}")
                    .build();
        }
        if (contentLength != null && contentLength > MAX_IMAGE_SIZE) {
            return imageTooLargeResponse();
        }

        String mediaId = new KapuaEid(IdGenerator.generate()).toCompactId();
        String storedFileName = mediaId + extension;
        String folder = (playlistId != null && !playlistId.trim().isEmpty())
                ? playlistId.trim()
                : deviceId.toCompactId();
        SignageMediaLocation.StoredMedia media;
        try {
            media = MEDIA_LOCATION.resolve(scopeId.toCompactId(), folder, storedFileName);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
        java.nio.file.Path destination = media.getDiskPath();
        java.nio.file.Path directory = destination.getParent();
        java.nio.file.Path temporary = directory.resolve(storedFileName + ".upload");
        ImageCopyResult copyResult;

        try {
            Files.createDirectories(directory);
            copyResult = copyImage(input, temporary);
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (ImageTooLargeException e) {
            deleteQuietly(temporary);
            return imageTooLargeResponse();
        } catch (IOException e) {
            deleteQuietly(temporary);
            throw KapuaException.internalError(e, "Unable to store Signage image");
        }

        JsonObject response = Json.createObjectBuilder()
                .add("id", mediaId)
                .add("name", mediaId)
                .add("fileName", sanitizeFileName(originalFileName, storedFileName))
                .add("contentType", normalizedType)
                .add("size", copyResult.size)
                .add("sha256", copyResult.sha256)
                .add("path", media.getPublicPath())
                .add("url", media.getPublicUrl())
                .build();
        return Response.status(Response.Status.CREATED)
                .type(MediaType.APPLICATION_JSON)
                .entity(toJson(response))
                .build();
    }

    /** Query playlists from DB (not from device). */
    @POST
    @Path("_query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response query(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            String body) throws KapuaException {
        checkPermission(scopeId, Actions.read);
        int limit = 200, offset = 0;
        if (body != null && !body.trim().isEmpty()) {
            try (JsonReader r = Json.createReader(new StringReader(body))) {
                JsonObject q = r.readObject();
                limit = q.getInt("limit", limit);
                offset = q.getInt("offset", offset);
            } catch (RuntimeException ignored) {
            }
        }
        String json = loadPlaylists(scopeId, deviceId, Math.max(1, limit), Math.max(0, offset));
        return Response.ok(json, MediaType.APPLICATION_JSON).build();
    }

    /** Create playlist: save to DB then push to device. */
    @POST
    @Path("create_playlist")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPlaylist(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout,
            String body) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        LOGGER.info("Create Signage playlist requested scopeId={} deviceId={}", scopeId, deviceId);
        String playlistId = extractPlaylistId(body);
        if (playlistId == null) {
            playlistId = new KapuaEid(IdGenerator.generate()).toCompactId();
        }
        String dbPayload = buildDbPayload(body, scopeId, playlistId);
        savePlaylist(scopeId, deviceId, playlistId, dbPayload, "PENDING", null);
        return deliverPersistedPlaylist(
                scopeId, deviceId, playlistId, timeout, dbPayload, "setup_playList", "saved");
    }

    /** Update playlist: save to DB then push to device. */
    @PUT
    @Path("update_playlist/{playlistId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updatePlaylist(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @PathParam("playlistId") String playlistId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout,
            String body) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        LOGGER.info("Update Signage playlist requested scopeId={} deviceId={} playlistId={}",
                scopeId, deviceId, playlistId);
        String dbPayload = buildDbPayload(body, scopeId, playlistId);
        savePlaylist(scopeId, deviceId, playlistId, dbPayload, "PENDING", null);
        return deliverPersistedPlaylist(
                scopeId, deviceId, playlistId, timeout, dbPayload, "update_playList", "saved");
    }

    /** Re-send a saved playlist to an online device. */
    @POST
    @Path("play_playlist/{playlistId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response playPlaylist(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @PathParam("playlistId") String playlistId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.execute);
        LOGGER.info("Play Signage playlist requested scopeId={} deviceId={} playlistId={}",
                scopeId, deviceId, playlistId);
        String dbPayload = loadPlaylist(scopeId, deviceId, playlistId);
        if (dbPayload == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Playlist not found\"}")
                    .build();
        }
        updatePlaylistStatus(scopeId, playlistId, "PENDING", null);
        return deliverPersistedPlaylist(
                scopeId, deviceId, playlistId, timeout, dbPayload, "update_playList", "played");
    }

    /** Create or update a marquee and propagate delivery failures to the REST exception mapper. */
    @POST
    @Path("marquee")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createMarquee(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout,
            String body) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        LOGGER.info("Create Signage marquee requested scopeId={} deviceId={}", scopeId, deviceId);
        String playlistId = extractPlaylistId(body);
        if (playlistId == null) {
            playlistId = new KapuaEid(IdGenerator.generate()).toCompactId();
        }
        String dbPayload = buildDbPayload(body, scopeId, playlistId, "marquee");
        savePlaylist(scopeId, deviceId, playlistId, dbPayload, "PENDING", null);
        return deliverPersistedPlaylist(
                scopeId, deviceId, playlistId, timeout, dbPayload, "setup_marquee", "saved");
    }

    /**
     * Send an async shell command that sleeps N seconds then runs the given restart command.
     * Useful when a device fails its first AccessToken attempt and needs a nudge to retry.
     */
    @POST
    @Path("_wakeup")
    @Produces(MediaType.APPLICATION_JSON)
    public Response wakeup(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @QueryParam("delaySeconds") @DefaultValue("5") int delaySeconds,
            @QueryParam("restartCommand") String restartCommand) throws KapuaException {
        checkPermission(scopeId, Actions.execute);

        if (restartCommand == null || restartCommand.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"restartCommand query parameter is required\"}")
                    .build();
        }

        int safeDelay = Math.max(0, Math.min(delaySeconds, 300));
        DeviceCommandInput cmd = COMMAND_FACTORY.newCommandInput();
        cmd.setCommand("sh");
        cmd.setArguments(new String[]{"-c", "sleep " + safeDelay + " && " + restartCommand});
        cmd.setRunAsynch(true);
        cmd.setTimeout(10000);

        try {
            COMMAND_SERVICE.exec(scopeId, deviceId, cmd, 15000L);
        } catch (KapuaException e) {
            JsonObject body = Json.createObjectBuilder()
                    .add("delivered", false)
                    .add("error", e.getMessage() != null ? e.getMessage() : "dispatch failed")
                    .build();
            return Response.status(202)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(toJson(body))
                    .build();
        }

        JsonObject body = Json.createObjectBuilder()
                .add("delivered", true)
                .add("delaySeconds", safeDelay)
                .add("restartCommand", restartCommand)
                .build();
        return Response.status(202)
                .type(MediaType.APPLICATION_JSON)
                .entity(toJson(body))
                .build();
    }

    /** Delete playlist: remove from DB then notify device. */
    @DELETE
    @Path("delete_playlist/{playlistId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deletePlaylist(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("deviceId") EntityId deviceId,
            @PathParam("playlistId") String playlistId,
            @QueryParam("timeout") @DefaultValue("5000") Long timeout) throws KapuaException {
        checkPermission(scopeId, Actions.write);
        checkPermission(scopeId, Actions.execute);
        LOGGER.info("Delete Signage playlist requested scopeId={} deviceId={} playlistId={}",
                scopeId, deviceId, playlistId);
        String dbPayload = loadPlaylist(scopeId, deviceId, playlistId);
        if (dbPayload == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Playlist not found\"}")
                    .build();
        }
        updatePlaylistStatus(scopeId, playlistId, "PENDING_DELETE", null);
        try {
            Response response = deliverPlaylist(
                    scopeId, deviceId, playlistId, timeout, dbPayload, "delete_playList");
            deletePlaylistRecord(scopeId, playlistId);
            return response;
        } catch (DeviceNotConnectedException e) {
            deletePlaylistRecord(scopeId, playlistId);
            return deliveryPendingResponse(playlistId, "deleted", "DEVICE_OFFLINE", e);
        } catch (DeviceManagementTimeoutException e) {
            deletePlaylistRecord(scopeId, playlistId);
            return deliveryPendingResponse(playlistId, "deleted", "DELIVERY_TIMEOUT", e);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private void checkPermission(ScopeId scopeId, Actions action) throws KapuaException {
        AUTHORIZATION_SERVICE.checkPermission(PERMISSION_FACTORY.newPermission(
                DeviceManagementDomains.DEVICE_MANAGEMENT_DOMAIN, action, scopeId));
    }

    private Response deliverPersistedPlaylist(
            ScopeId scopeId,
            EntityId deviceId,
            String playlistId,
            Long timeout,
            String dbPayload,
            String requestType,
            String persistedAction) throws KapuaException {
        try {
            return deliverPlaylist(scopeId, deviceId, playlistId, timeout, dbPayload, requestType);
        } catch (DeviceNotConnectedException e) {
            return deliveryPendingResponse(playlistId, persistedAction, "DEVICE_OFFLINE", e);
        } catch (DeviceManagementTimeoutException e) {
            return deliveryPendingResponse(playlistId, persistedAction, "DELIVERY_TIMEOUT", e);
        }
    }

    private Response deliveryPendingResponse(
            String playlistId,
            String persistedAction,
            String deliveryStatus,
            KapuaException error) {
        LOGGER.warn("Signage playlist {} but not delivered playlistId={} deliveryStatus={} error={}",
                persistedAction, playlistId, deliveryStatus, safeMessage(error));
        JsonObject body = Json.createObjectBuilder()
                .add("playlistId", playlistId)
                .add(persistedAction, true)
                .add("delivered", false)
                .add("controlTimeout", true)
                .add("deliveryStatus", deliveryStatus)
                .add("error", safeMessage(error))
                .build();
        return Response.status(Response.Status.ACCEPTED)
                .type(MediaType.APPLICATION_JSON)
                .entity(toJson(body))
                .build();
    }

    /** Extract "id" field from JSON body as the pre-generated playlist ID; returns null if absent or blank. */
    private String extractPlaylistId(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try (JsonReader r = Json.createReader(new StringReader(body))) {
            javax.json.JsonValue val = r.readObject().get("id");
            if (val != null && val.getValueType() == javax.json.JsonValue.ValueType.STRING) {
                String id = ((javax.json.JsonString) val).getString().trim();
                return id.isEmpty() ? null : id;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private Response imageTooLargeResponse() {
        return Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE)
                .type(MediaType.APPLICATION_JSON)
                .entity("{\"error\":\"Image exceeds 20 MB\"}")
                .build();
    }

    private Response badRequest(String message) {
        return errorResponse(Response.Status.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    private Response errorResponse(
            Response.Status status, String errorCode, String message) {
        JsonObject body = Json.createObjectBuilder()
                .add("errorCode", errorCode)
                .add("message", message == null ? "" : message)
                .build();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(toJson(body))
                .build();
    }

    private static Map<String, String> createImageExtensions() {
        Map<String, String> extensions = new HashMap<>();
        extensions.put("image/jpeg", ".jpg");
        extensions.put("image/png", ".png");
        extensions.put("image/gif", ".gif");
        extensions.put("image/webp", ".webp");
        extensions.put("image/bmp", ".bmp");
        return extensions;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int separator = contentType.indexOf(';');
        String value = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private ImageCopyResult copyImage(InputStream input, java.nio.file.Path destination)
            throws IOException, ImageTooLargeException {
        if (input == null) {
            throw new IOException("Missing image body");
        }
        byte[] buffer = new byte[8192];
        long total = 0;
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 is unavailable", e);
        }
        try (java.io.OutputStream output = Files.newOutputStream(destination)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_IMAGE_SIZE) {
                    throw new ImageTooLargeException();
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
        }
        return new ImageCopyResult(total, toHex(digest.digest()));
    }

    private String sanitizeFileName(String fileName, String fallback) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return fallback;
        }
        String normalized = fileName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        normalized = normalized.replaceAll("[\\p{Cntrl}\"]", "").trim();
        return normalized.isEmpty() ? fallback : normalized;
    }

    private void deleteQuietly(java.nio.file.Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            value.append(String.format("%02x", b & 0xff));
        }
        return value.toString();
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static final class ImageTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class ImageCopyResult {
        private final long size;
        private final String sha256;

        private ImageCopyResult(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }

    /**
     * Normalise the incoming request body for DB storage.
     * Strips requestType, injects scopeId and id as plain compact-ID strings.
     */
    private String buildDbPayload(String body, ScopeId scopeId, String playlistId)
            throws KapuaException {
        return buildDbPayload(body, scopeId, playlistId, null);
    }

    private String buildDbPayload(
            String body, ScopeId scopeId, String playlistId, String listType)
            throws KapuaException {
        JsonObject source = Json.createObjectBuilder().build();
        if (body != null && !body.trim().isEmpty()) {
            try (JsonReader r = Json.createReader(new StringReader(body))) {
                source = r.readObject();
            } catch (RuntimeException e) {
                throw KapuaException.internalError(e, "Invalid Signage playlist JSON");
            }
        }
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, javax.json.JsonValue> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!"requestType".equals(key)
                    && !"scopeId".equals(key)
                    && !"id".equals(key)
                    && !"resources".equals(key)
                    && !"playContent".equals(key)
                    && !"listType".equals(key)
                    && !"schedule".equals(key)
                    && !"entityAttributes".equals(key)
                    && !"listTypeName".equals(key)) {
                builder.add(key, entry.getValue());
            }
        }
        javax.json.JsonArray resources = RESOURCE_NORMALIZER.forStorage(source);
        builder.add("resources", resources);
        if (listType == null) {
            builder.add("playContent", normalizePlayContent(source, resources));
            builder.add("listType", normalizedListType(source));
            builder.add("schedule", normalizedSchedule(source));
            builder.add("entityAttributes", objectOrEmpty(source, "entityAttributes"));
        } else {
            copyIfPresent(builder, source, "playContent");
            copyIfPresent(builder, source, "listType");
            copyIfPresent(builder, source, "schedule");
            copyIfPresent(builder, source, "entityAttributes");
        }
        if (listType != null) {
            builder.add("listTypeName", listType);
        } else if (source.containsKey("listTypeName")) {
            builder.add("listTypeName", source.get("listTypeName"));
        }
        builder.add("scopeId", scopeId.toCompactId());
        builder.add("id", playlistId);
        return toJson(builder.build());
    }

    private javax.json.JsonArray normalizePlayContent(
            JsonObject source, javax.json.JsonArray resources) {
        JsonArrayBuilder contents = Json.createArrayBuilder();
        javax.json.JsonArray sourceContents = source.getJsonArray("playContent");
        for (int index = 0; index < resources.size(); index++) {
            JsonObject resource = resources.getJsonObject(index);
            JsonObject original = sourceContents != null && index < sourceContents.size()
                    && sourceContents.get(index).getValueType()
                    == javax.json.JsonValue.ValueType.OBJECT
                    ? sourceContents.getJsonObject(index)
                    : Json.createObjectBuilder().build();
            String resourceName = resource.getString(
                    "name", resource.getString("id", "Image-" + index));
            contents.add(Json.createObjectBuilder()
                    .add("appName", "Image")
                    .add("duration", normalizeDuration(original.getString("duration", "10")))
                    .add("action", "show")
                    .add("parameters", Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("id", "image")
                                    .add("value", "$" + resourceName))));
        }
        return contents.build();
    }

    private String normalizeDuration(String value) {
        String duration = value == null ? "" : value.trim();
        if (duration.matches("\\d+:\\d+:\\d+")) {
            return duration;
        }
        int seconds;
        try {
            seconds = Math.max(1, Integer.parseInt(duration));
        } catch (NumberFormatException e) {
            seconds = 10;
        }
        return (seconds / 3600) + ":" + ((seconds % 3600) / 60) + ":" + (seconds % 60);
    }

    private int normalizedListType(JsonObject source) {
        JsonObject schedule = source.getJsonObject("schedule");
        return schedule != null && schedule.getBoolean("enable", false)
                ? source.getInt("listType", 1)
                : 0;
    }

    private JsonObject normalizedSchedule(JsonObject source) {
        JsonObject schedule = source.getJsonObject("schedule");
        if (schedule != null) {
            return schedule;
        }
        return Json.createObjectBuilder()
                .add("startDate", "")
                .add("endDate", "")
                .add("startTime", "")
                .add("endTime", "")
                .add("weekdays", Json.createArrayBuilder())
                .add("enable", false)
                .build();
    }

    private void copyIfPresent(JsonObjectBuilder target, JsonObject source, String key) {
        if (source.containsKey(key)) {
            target.add(key, source.get(key));
        }
    }

    private JsonObject objectOrEmpty(JsonObject source, String key) {
        JsonObject value = source.getJsonObject(key);
        return value != null ? value : Json.createObjectBuilder().build();
    }

    private Response deliverPlaylist(
            ScopeId scopeId,
            EntityId deviceId,
            String playlistId,
            Long timeout,
            String dbPayload,
            String requestType) throws KapuaException {
        try {
            Response response = forward(scopeId, deviceId, timeout, dbPayload, requestType);
            if (response.getStatus() < 400) {
                updatePlaylistStatus(scopeId, playlistId, "ACTIVE", null);
            } else {
                updatePlaylistStatus(
                        scopeId, playlistId, "FAILED",
                        "Device response HTTP " + response.getStatus());
            }
            return response;
        } catch (DeviceNotConnectedException e) {
            updatePlaylistStatus(scopeId, playlistId, "DEVICE_OFFLINE", safeMessage(e));
            throw e;
        } catch (DeviceManagementTimeoutException e) {
            updatePlaylistStatus(scopeId, playlistId, "DELIVERY_TIMEOUT", safeMessage(e));
            throw e;
        } catch (KapuaException e) {
            updatePlaylistStatus(scopeId, playlistId, "FAILED", safeMessage(e));
            throw e;
        }
    }

    /**
     * Send the playlist payload to the device via MQTT (GenericRequestManagementService).
     * The device expects scopeId/id wrapped as {"eid":"..."} objects.
     */
    private Response forward(ScopeId scopeId, EntityId deviceId, Long timeout,
            String dbPayload, String requestType) throws KapuaException {

        String deviceJson = toDeviceJson(dbPayload, requestType);
        logDeviceContract(deviceJson);

        GenericRequestChannel channel = REQUEST_FACTORY.newRequestChannel();
        channel.setAppName(APP_NAME);
        channel.setVersion(APP_VERSION);
        channel.setMethod(KapuaMethod.EXECUTE);
        channel.setResources(Arrays.asList("signageplaylist"));

        GenericRequestPayload payload = REQUEST_FACTORY.newRequestPayload();
        payload.getMetrics().put(PLAYLIST_METRIC, deviceJson);

        GenericRequestMessage request = REQUEST_FACTORY.newRequestMessage();
        request.setScopeId(scopeId);
        request.setDeviceId(deviceId);
        request.setCapturedOn(new Date());
        request.setChannel(channel);
        request.setPayload(payload);

        long requestTimeout = boundedDeviceTimeout(timeout);
        LOGGER.info(
                "Sending Signage request scopeId={} deviceId={} playlistId={} requestType={} "
                        + "appId=SIGNAGE-V1 resource=signageplaylist timeout={}",
                scopeId, deviceId, extractPlaylistId(dbPayload), requestType, requestTimeout);
        GenericResponseMessage response =
                REQUEST_SERVICE.exec(scopeId, deviceId, request, requestTimeout);

        if (response == null) {
            return errorResponse(
                    Response.Status.BAD_GATEWAY,
                    "INVALID_DEVICE_RESPONSE",
                    "Device returned no response");
        }

        byte[] bodyBytes = (response.getPayload() != null) ? response.getPayload().getBody() : null;
        String responseJson = (bodyBytes != null && bodyBytes.length > 0)
                ? new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8)
                : "{}";

        int httpStatus = mapResponseCode(response);
        return Response.status(httpStatus)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseJson)
                .build();
    }

    private void logDeviceContract(String deviceJson) {
        try (JsonReader reader = Json.createReader(new StringReader(deviceJson))) {
            JsonObject payload = reader.readObject();
            javax.json.JsonArray contents = payload.getJsonArray("playContent");
            JsonObject content = contents != null && !contents.isEmpty()
                    ? contents.getJsonObject(0)
                    : Json.createObjectBuilder().build();
            javax.json.JsonArray parameters = content.getJsonArray("parameters");
            JsonObject parameter = parameters != null && !parameters.isEmpty()
                    ? parameters.getJsonObject(0)
                    : Json.createObjectBuilder().build();
            LOGGER.info(
                    "Signage device contract playlistId={} requestType={} listType={} "
                            + "appName={} action={} duration={} parameterId={} metric={}",
                    payload.getJsonObject("id").getString("eid", ""),
                    payload.getString("requestType", ""),
                    payload.getInt("listType", -1),
                    content.getString("appName", ""),
                    content.getString("action", ""),
                    content.getString("duration", ""),
                    parameter.getString("id", ""),
                    PLAYLIST_METRIC);
        } catch (RuntimeException e) {
            LOGGER.warn("Unable to summarize Signage device contract", e);
        }
    }

    /**
     * Convert DB payload (plain string IDs) to device-expected format:
     * scopeId and id wrapped as {"eid":"..."} objects, plus requestType added.
     */
    private String toDeviceJson(String dbPayload, String requestType) throws KapuaException {
        try (JsonReader reader = Json.createReader(new StringReader(dbPayload))) {
            JsonObject source = reader.readObject();
            JsonObject normalized = normalizeStoredPlaylistForDevice(source);
            JsonObjectBuilder builder = Json.createObjectBuilder();
            for (Map.Entry<String, javax.json.JsonValue> entry : normalized.entrySet()) {
                String key = entry.getKey();
                if (!"scopeId".equals(key) && !"id".equals(key)) {
                    builder.add(key, entry.getValue());
                }
            }
            builder.add("requestType", requestType);
            builder.add("scopeId", Json.createObjectBuilder()
                    .add("id", normalized.getString("scopeId", "")));
            builder.add("id", Json.createObjectBuilder()
                    .add("eid", normalized.getString("id", "")));
            return toJson(builder.build());
        } catch (RuntimeException e) {
            throw KapuaException.internalError(e, "Unable to build device Signage JSON");
        }
    }

    private JsonObject normalizeStoredPlaylistForDevice(JsonObject source) throws KapuaException {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        for (Map.Entry<String, javax.json.JsonValue> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!"resources".equals(key)
                    && !"playContent".equals(key)
                    && !"listType".equals(key)
                    && !"schedule".equals(key)
                    && !"entityAttributes".equals(key)) {
                builder.add(key, entry.getValue());
            }
        }
        javax.json.JsonArray resources = RESOURCE_NORMALIZER.forDevice(source);
        javax.json.JsonArray playContent = normalizePlayContent(source, resources);
        builder.add("resources", resources);
        builder.add(
                "playContent",
                LegacySignagePlaybackCompatibility.expandSingleContent(playContent));
        builder.add("listType", normalizedListType(source));
        builder.add("schedule", normalizedSchedule(source));
        builder.add("entityAttributes", objectOrEmpty(source, "entityAttributes"));
        return builder.build();
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

    // ── JDBC helpers ─────────────────────────────────────────────────────────

    private String loadPlaylists(ScopeId scopeId, EntityId deviceId, int limit, int offset)
            throws KapuaException {
        JsonArrayBuilder result = Json.createArrayBuilder();
        String sql = "SELECT playlist_id, payload, delivery_status, last_error FROM "
                + PLAYLIST_TABLE
                + " WHERE scope_id = ? AND device_id = ?"
                + " ORDER BY updated_on DESC LIMIT ? OFFSET ?";
        try (Connection conn = openConnection()) {
            ensurePlaylistTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, scopeId.toCompactId());
                ps.setString(2, deviceId.toCompactId());
                ps.setInt(3, limit);
                ps.setInt(4, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try (JsonReader r = Json.createReader(new StringReader(rs.getString(2)))) {
                            JsonObject payload = r.readObject();
                            JsonObjectBuilder item = Json.createObjectBuilder();
                            for (Map.Entry<String, javax.json.JsonValue> entry
                                    : payload.entrySet()) {
                                item.add(entry.getKey(), entry.getValue());
                            }
                            item.add("deliveryStatus", rs.getString(3));
                            String lastError = rs.getString(4);
                            if (lastError != null) {
                                item.add("lastError", lastError);
                            }
                            result.add(item);
                        }
                    }
                }
            }
            return toJson(result.build());
        } catch (SQLException | RuntimeException e) {
            throw KapuaException.internalError(e, "Unable to load Signage playlists");
        }
    }

    private String loadPlaylist(ScopeId scopeId, EntityId deviceId, String playlistId)
            throws KapuaException {
        String sql = "SELECT payload FROM " + PLAYLIST_TABLE
                + " WHERE scope_id = ? AND device_id = ? AND playlist_id = ?";
        try (Connection conn = openConnection()) {
            ensurePlaylistTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, scopeId.toCompactId());
                ps.setString(2, deviceId.toCompactId());
                ps.setString(3, playlistId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            }
        } catch (SQLException e) {
            throw KapuaException.internalError(e, "Unable to load Signage playlist");
        }
    }

    private void savePlaylist(
            ScopeId scopeId,
            EntityId deviceId,
            String playlistId,
            String payload,
            String status,
            String lastError) throws KapuaException {
        String updateSql = "UPDATE " + PLAYLIST_TABLE
                + " SET device_id = ?, payload = ?, delivery_status = ?, last_error = ?,"
                + " updated_on = CURRENT_TIMESTAMP"
                + " WHERE scope_id = ? AND playlist_id = ?";
        String insertSql = "INSERT INTO " + PLAYLIST_TABLE
                + " (scope_id, device_id, playlist_id, payload, delivery_status,"
                + " last_error, updated_on)"
                + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = openConnection()) {
            ensurePlaylistTable(conn);
            try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                upd.setString(1, deviceId.toCompactId());
                upd.setString(2, payload);
                upd.setString(3, status);
                upd.setString(4, lastError);
                upd.setString(5, scopeId.toCompactId());
                upd.setString(6, playlistId);
                if (upd.executeUpdate() == 0) {
                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        ins.setString(1, scopeId.toCompactId());
                        ins.setString(2, deviceId.toCompactId());
                        ins.setString(3, playlistId);
                        ins.setString(4, payload);
                        ins.setString(5, status);
                        ins.setString(6, lastError);
                        ins.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw KapuaException.internalError(e, "Unable to save Signage playlist");
        }
    }

    private void updatePlaylistStatus(
            ScopeId scopeId, String playlistId, String status, String lastError)
            throws KapuaException {
        String sql = "UPDATE " + PLAYLIST_TABLE
                + " SET delivery_status = ?, last_error = ?, updated_on = CURRENT_TIMESTAMP"
                + " WHERE scope_id = ? AND playlist_id = ?";
        try (Connection conn = openConnection()) {
            ensurePlaylistTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, lastError);
                ps.setString(3, scopeId.toCompactId());
                ps.setString(4, playlistId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw KapuaException.internalError(e, "Unable to update Signage delivery status");
        }
    }

    private void deletePlaylistRecord(ScopeId scopeId, String playlistId) throws KapuaException {
        String sql = "DELETE FROM " + PLAYLIST_TABLE
                + " WHERE scope_id = ? AND playlist_id = ?";
        try (Connection conn = openConnection()) {
            ensurePlaylistTable(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, scopeId.toCompactId());
                ps.setString(2, playlistId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw KapuaException.internalError(e, "Unable to delete Signage playlist");
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                JdbcConnectionUrlResolvers.resolveJdbcUrl(),
                SYSTEM_SETTING.getString(SystemSettingKey.DB_USERNAME),
                SYSTEM_SETTING.getString(SystemSettingKey.DB_PASSWORD));
    }

    private void ensurePlaylistTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS " + PLAYLIST_TABLE + " ("
                + "scope_id     VARCHAR(64)  NOT NULL,"
                + "device_id    VARCHAR(64)  NOT NULL,"
                + "playlist_id  VARCHAR(64)  NOT NULL,"
                + "payload      CLOB         NOT NULL,"
                + "delivery_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',"
                + "last_error   VARCHAR(1000),"
                + "updated_on   TIMESTAMP    NOT NULL,"
                + "PRIMARY KEY (scope_id, playlist_id))";
        try (Statement st = conn.createStatement()) {
            st.execute(sql);
            st.execute("ALTER TABLE " + PLAYLIST_TABLE
                    + " ADD COLUMN IF NOT EXISTS delivery_status"
                    + " VARCHAR(32) NOT NULL DEFAULT 'PENDING'");
            st.execute("ALTER TABLE " + PLAYLIST_TABLE
                    + " ADD COLUMN IF NOT EXISTS last_error VARCHAR(1000)");
        }
    }

    private String toJson(javax.json.JsonStructure value) {
        StringWriter out = new StringWriter();
        try (JsonWriter w = Json.createWriter(out)) {
            w.write(value);
        }
        return out.toString();
    }
}
