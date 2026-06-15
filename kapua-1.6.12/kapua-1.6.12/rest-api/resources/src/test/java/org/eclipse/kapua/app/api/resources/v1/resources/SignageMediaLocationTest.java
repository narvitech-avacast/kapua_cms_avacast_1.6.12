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

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SignageMediaLocationTest {

    @Test
    public void shouldResolveMediaPathAndHttpsUrl() {
        SignageMediaLocation location =
                new SignageMediaLocation("target/signage-media", "https://cms.test");

        SignageMediaLocation.StoredMedia media =
                location.resolve("AQ", "playlist-1", "image-1.jpg");

        Assert.assertEquals(
                "/signage-media/AQ/playlist-1/image-1.jpg",
                media.getPublicPath());
        Assert.assertEquals(
                "https://cms.test/signage-media/AQ/playlist-1/image-1.jpg",
                media.getPublicUrl());
        Assert.assertTrue(media.getDiskPath().startsWith(
                Paths.get("target/signage-media").toAbsolutePath().normalize()));
    }

    @Test
    public void shouldNormalizeBaseUrlWithTrailingSlash() {
        SignageMediaLocation location =
                new SignageMediaLocation("target/signage-media", "https://cms.test/");

        Assert.assertEquals(
                "https://cms.test/signage-media/AQ/p/image.jpg",
                location.toAbsoluteUrl("/signage-media/AQ/p/image.jpg"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectHttpBaseUrl() {
        new SignageMediaLocation("target/signage-media", "http://cms.test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectHttpResourceUrl() {
        SignageMediaLocation location =
                new SignageMediaLocation("target/signage-media", "https://cms.test");

        location.toAbsoluteUrl("http://cms.test/signage-media/AQ/p/image.jpg");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPathTraversalSegment() {
        SignageMediaLocation location =
                new SignageMediaLocation("target/signage-media", "https://cms.test");

        location.resolve("AQ", "..", "image.jpg");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSlashInFileName() {
        SignageMediaLocation location =
                new SignageMediaLocation("target/signage-media", "https://cms.test");

        location.resolve("AQ", "playlist", "../image.jpg");
    }

    @Test
    public void shouldFindLegacyFileRepoMediaWithinScope() throws Exception {
        Path root = Files.createTempDirectory("signage-media-test");
        SignageMediaLocation location =
                new SignageMediaLocation(root.toString(), "https://cms.test");
        Path media = location.resolve("AQ", "device-1", "resource-1.png").getDiskPath();
        Files.createDirectories(media.getParent());
        Files.write(media, new byte[]{1, 2, 3});

        Assert.assertEquals(media, location.findByResourceId("AQ", "resource-1"));
        Assert.assertNull(location.findByResourceId("OTHER", "resource-1"));
    }

    @Test
    public void shouldIgnoreUnsupportedLegacyFileExtension() throws Exception {
        Path root = Files.createTempDirectory("signage-media-test");
        SignageMediaLocation location =
                new SignageMediaLocation(root.toString(), "https://cms.test");
        Path media = root.resolve("AQ/device-1/resource-1.exe");
        Files.createDirectories(media.getParent());
        Files.write(media, new byte[]{1});

        Assert.assertNull(location.findByResourceId("AQ", "resource-1"));
    }
}
