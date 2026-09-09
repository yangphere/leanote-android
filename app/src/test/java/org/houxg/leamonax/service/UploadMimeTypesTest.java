package org.houxg.leamonax.service;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;

public class UploadMimeTypesTest {
    @Test
    public void resolvesKnownImageExtensionCaseInsensitively() {
        assertEquals("image/jpeg", UploadMimeTypes.forFileName("PHOTO.JPEG"));
    }

    @Test
    public void usesBinaryMediaTypeForUnknownExtension() {
        assertEquals("application/octet-stream", UploadMimeTypes.forFileName("attachment.img"));
        assertEquals("application/octet-stream", UploadMimeTypes.forFileName("no-extension"));
    }

    @Test
    public void missingUploadBodyFailsWithLocalFileIdentity() {
        File missing = new File("missing-upload-body.img");

        try {
            UploadMimeTypes.requireReadableFile(missing.getPath(), "local-123");
        } catch (IllegalStateException expected) {
            org.junit.Assert.assertTrue(expected.getMessage().contains("local-123"));
            return;
        }
        org.junit.Assert.fail("Missing upload body must fail before building the request");
    }
}
