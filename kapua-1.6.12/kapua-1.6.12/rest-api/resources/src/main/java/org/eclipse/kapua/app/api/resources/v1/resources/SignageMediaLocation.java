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

import java.net.URI;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * Centralises signage media path resolution so that every upload and playlist
 * normalization produces a consistent absolute HTTPS URL instead of a bare
 * relative path.
 *
 * Configuration (system property takes precedence over env var):
 *   System property : kapua.signage.media.storage-root   / kapua.signage.media.public-base-url
 *   Environment var : SIGNAGE_MEDIA_STORAGE_ROOT         / SIGNAGE_MEDIA_PUBLIC_BASE_URL
 */
public final class SignageMediaLocation {

    static final String STORAGE_ROOT_PROP = "kapua.signage.media.storage-root";
    static final String STORAGE_ROOT_ENV  = "SIGNAGE_MEDIA_STORAGE_ROOT";
    static final String DEFAULT_STORAGE_ROOT = "/var/lib/kapua-signage";

    static final String PUBLIC_BASE_URL_PROP = "kapua.signage.media.public-base-url";
    static final String PUBLIC_BASE_URL_ENV  = "SIGNAGE_MEDIA_PUBLIC_BASE_URL";
    static final String DEFAULT_PUBLIC_BASE_URL = "https://localhost";

    private final Path storageRoot;
    private final URI publicBaseUri;

    public SignageMediaLocation(String storageRoot, String publicBaseUrl) {
        this.storageRoot = Paths.get(storageRoot).toAbsolutePath().normalize();

        URI uri = URI.create(publicBaseUrl);
        if (!uri.isAbsolute() || uri.getHost() == null) {
            throw new IllegalArgumentException(
                    "signage.media.public-base-url must be an absolute URL with a hostname");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(
                    "signage.media.public-base-url must use https");
        }
        String normalized = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        this.publicBaseUri = URI.create(normalized);
    }

    /** Build from system properties / environment variables. */
    public static SignageMediaLocation fromConfig() {
        return new SignageMediaLocation(
                readConfig(STORAGE_ROOT_PROP, STORAGE_ROOT_ENV, DEFAULT_STORAGE_ROOT),
                readConfig(PUBLIC_BASE_URL_PROP, PUBLIC_BASE_URL_ENV, DEFAULT_PUBLIC_BASE_URL));
    }

    /**
     * Resolve a media file location.
     *
     * @param scopeId        compact scope ID (alphanumeric + ._-)
     * @param folder         playlist ID or device ID used as sub-folder
     * @param storedFileName server-generated filename (e.g. "abc123.jpg")
     * @return StoredMedia with disk path, relative public path and absolute public URL
     */
    public StoredMedia resolve(String scopeId, String folder, String storedFileName) {
        String safeScopeId  = safeSegment(scopeId,        "scopeId");
        String safeFolder   = safeSegment(folder,         "folder");
        String safeFileName = safeSegment(storedFileName, "storedFileName");

        String relativePath = safeScopeId + "/" + safeFolder + "/" + safeFileName;
        Path diskPath = storageRoot.resolve(relativePath).normalize();

        if (!diskPath.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Path traversal detected in signage media path");
        }

        String publicPath = "/signage-media/" + relativePath.replace('\\', '/');
        String publicUrl = publicBaseUri.resolve(publicPath.substring(1)).toString();

        return new StoredMedia(diskPath, publicPath, publicUrl);
    }

    /**
     * Convert a relative /signage-media/… path to an absolute URL.
     * Returns the input unchanged if it is already an absolute URL.
     */
    public String toAbsoluteUrl(String path) {
        if (path == null || path.trim().isEmpty()) {
            return path;
        }
        String trimmed = path.trim();
        URI uri = URI.create(trimmed);
        if (uri.isAbsolute()) {
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Signage resource URL must use HTTPS");
            }
            return uri.toString();
        }
        if (!trimmed.startsWith("/signage-media/")) {
            throw new IllegalArgumentException(
                    "Unsupported relative signage resource path: " + trimmed);
        }
        return publicBaseUri.resolve(trimmed.substring(1)).toString();
    }

    public Path getStorageRoot() {
        return storageRoot;
    }

    /**
     * Resolve media by the FileRepo-compatible resource ID used by legacy
     * signage players. The search is constrained to the requested scope.
     */
    public Path findByResourceId(String scopeId, String resourceId) throws IOException {
        String safeScopeId = safeSegment(scopeId, "scopeId");
        String safeResourceId = safeSegment(resourceId, "resourceId");
        Path scopeRoot = storageRoot.resolve(safeScopeId).normalize();
        if (!scopeRoot.startsWith(storageRoot) || !Files.isDirectory(scopeRoot)) {
            return null;
        }
        try (Stream<Path> paths = Files.walk(scopeRoot, 4)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isResourceFile(path, safeResourceId))
                    .findFirst()
                    .orElse(null);
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static String safeSegment(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Signage media " + field + " must not be blank");
        }
        String v = value.trim();
        if (!v.matches("[A-Za-z0-9._-]+") || ".".equals(v) || "..".equals(v)) {
            throw new IllegalArgumentException(
                    "Invalid signage media " + field + ": " + v);
        }
        return v;
    }

    private static String readConfig(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(envVar);
        }
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }

    private static boolean isResourceFile(Path path, String resourceId) {
        String name = path.getFileName().toString();
        return name.startsWith(resourceId + ".")
                && (name.endsWith(".jpg")
                || name.endsWith(".png")
                || name.endsWith(".gif")
                || name.endsWith(".webp")
                || name.endsWith(".bmp"));
    }

    // ── inner value type ──────────────────────────────────────────────────────

    public static final class StoredMedia {
        private final Path diskPath;
        private final String publicPath; // e.g. /signage-media/scope/folder/file.jpg
        private final String publicUrl;  // e.g. https://cms.example.com/signage-media/...

        StoredMedia(Path diskPath, String publicPath, String publicUrl) {
            this.diskPath   = diskPath;
            this.publicPath = publicPath;
            this.publicUrl  = publicUrl;
        }

        public Path getDiskPath()    { return diskPath; }
        public String getPublicPath() { return publicPath; }
        public String getPublicUrl()  { return publicUrl; }
    }
}
