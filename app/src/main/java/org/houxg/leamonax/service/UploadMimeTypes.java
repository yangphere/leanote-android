package org.houxg.leamonax.service;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class UploadMimeTypes {
    private static final String BINARY = "application/octet-stream";
    private static final Map<String, String> TYPES;

    static {
        Map<String, String> types = new HashMap<>();
        types.put("avif", "image/avif");
        types.put("gif", "image/gif");
        types.put("heic", "image/heic");
        types.put("heif", "image/heif");
        types.put("jpeg", "image/jpeg");
        types.put("jpg", "image/jpeg");
        types.put("png", "image/png");
        types.put("webp", "image/webp");
        TYPES = Collections.unmodifiableMap(types);
    }

    private UploadMimeTypes() {
    }

    public static String forFileName(String fileName) {
        int extensionStart = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (extensionStart < 0 || extensionStart == fileName.length() - 1) {
            return BINARY;
        }
        String extension = fileName.substring(extensionStart + 1).toLowerCase(Locale.US);
        String mimeType = TYPES.get(extension);
        return mimeType == null ? BINARY : mimeType;
    }

    static File requireReadableFile(String localPath, String localId) {
        File file = localPath == null ? null : new File(localPath);
        if (file == null || !file.isFile()) {
            throw new IllegalStateException(
                    "Missing upload body for local file " + localId + ": " + localPath
            );
        }
        return file;
    }
}
