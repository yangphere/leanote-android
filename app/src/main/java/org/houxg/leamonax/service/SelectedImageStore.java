package org.houxg.leamonax.service;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class SelectedImageStore {
    private static final String DIRECTORY_NAME = "selected-images";
    private static final Map<String, String> IMAGE_EXTENSIONS;

    static {
        Map<String, String> extensions = new HashMap<>();
        extensions.put("image/avif", ".avif");
        extensions.put("image/gif", ".gif");
        extensions.put("image/heic", ".heic");
        extensions.put("image/heif", ".heif");
        extensions.put("image/jpeg", ".jpg");
        extensions.put("image/png", ".png");
        extensions.put("image/webp", ".webp");
        IMAGE_EXTENSIONS = Collections.unmodifiableMap(extensions);
    }

    private final File directory;

    public SelectedImageStore(File directory) {
        this.directory = directory;
    }

    public static SelectedImageStore from(Context context) {
        return new SelectedImageStore(new File(context.getFilesDir(), DIRECTORY_NAME));
    }

    public File copy(String mimeType, InputStream input) throws IOException {
        String suffix = IMAGE_EXTENSIONS.get(mimeType);
        if (suffix == null) {
            throw new IOException("Unsupported selected image MIME type: " + mimeType);
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create the selected image directory");
        }

        File destination = File.createTempFile("leanote-image-", suffix, directory);
        try (FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return destination;
        } catch (IOException | RuntimeException exception) {
            deleteIfManaged(destination);
            throw exception;
        }
    }

    public boolean deleteIfManaged(File file) {
        try {
            File canonicalDirectory = directory.getCanonicalFile();
            File canonicalFile = file.getCanonicalFile();
            if (!canonicalDirectory.equals(canonicalFile.getParentFile())) {
                return false;
            }
            return !canonicalFile.exists() || canonicalFile.delete();
        } catch (IOException exception) {
            return false;
        }
    }

    public boolean isManaged(File file) {
        try {
            return directory.getCanonicalFile().equals(file.getCanonicalFile().getParentFile());
        } catch (IOException exception) {
            return false;
        }
    }
}
