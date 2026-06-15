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

import org.eclipse.kapua.app.api.core.model.EntityId;
import org.eclipse.kapua.app.api.core.model.ScopeId;
import org.eclipse.kapua.app.api.core.resources.AbstractKapuaResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;

/**
 * FileRepo-compatible download endpoint for legacy SIGNAGE-V1 players.
 *
 * Legacy devices ignore resource.path and fetch media using scopeId plus
 * resource.id. Newer clients can continue using the absolute signage-media URL.
 */
@Path("{scopeId}/file-repo")
public class SignageFileRepos extends AbstractKapuaResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignageFileRepos.class);
    private static final SignageMediaLocation MEDIA_LOCATION =
            SignageMediaLocation.fromConfig();

    @GET
    @Path("{fileRepoId}/get-image")
    @Produces({
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            MediaType.APPLICATION_OCTET_STREAM
    })
    public Response getImage(
            @PathParam("scopeId") ScopeId scopeId,
            @PathParam("fileRepoId") EntityId fileRepoId) throws IOException {
        java.nio.file.Path media;
        try {
            media = MEDIA_LOCATION.findByResourceId(
                    scopeId.toCompactId(), fileRepoId.toCompactId());
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"Invalid FileRepo resource ID\"}")
                    .build();
        }
        if (media == null) {
            LOGGER.warn("Signage FileRepo media not found scopeId={} resourceId={}",
                    scopeId, fileRepoId);
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.APPLICATION_JSON)
                    .entity("{\"error\":\"File not found\"}")
                    .build();
        }

        String contentType = contentType(media);
        LOGGER.info("Serving Signage FileRepo media scopeId={} resourceId={} file={}",
                scopeId, fileRepoId, media.getFileName());
        return Response.ok(media.toFile(), contentType)
                .header(HttpHeaders.CONTENT_LENGTH, Files.size(media))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + media.getFileName() + "\"")
                .build();
    }

    private String contentType(java.nio.file.Path media) {
        String name = media.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        if (name.endsWith(".bmp")) {
            return "image/bmp";
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
