package org.houxg.leamonax.service;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class SelectedImageStore {
    private static final String DIRECTORY_NAME = "selected-images";
    private static final Map<String, String> IMAGE_EXTENSIONS;
    private static final String PROCESS_SESSION_ID = UUID.randomUUID().toString();

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
    private final String sessionId;
    private final Supplier<String> importIds;
    private final LongSupplier clock;
    private final SelectedImageLedger ledger;

    public interface RelationshipOwner {
        boolean owns(String canonicalPath);
    }

    public SelectedImageStore(File directory) {
        this(directory, PROCESS_SESSION_ID, () -> UUID.randomUUID().toString(), System::currentTimeMillis);
    }

    SelectedImageStore(File directory, String sessionId, Supplier<String> importIds, LongSupplier clock) {
        this.directory = directory;
        this.sessionId = sessionId;
        this.importIds = importIds;
        this.clock = clock;
        File parent = directory.getAbsoluteFile().getParentFile();
        this.ledger = new SelectedImageLedger(new File(parent, "selected-image-imports.properties"));
    }

    public static SelectedImageStore from(Context context) {
        return new SelectedImageStore(new File(context.getFilesDir(), DIRECTORY_NAME));
    }

    public File copy(String mimeType, InputStream input) throws IOException {
        String suffix = IMAGE_EXTENSIONS.get(mimeType);
        if (suffix == null) {
            throw new IOException("Unsupported selected image MIME type: " + mimeType);
        }
        if (input == null) {
            throw new IOException("Selected image stream is missing");
        }
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Unable to create the selected image directory");
        }

        String importId = importIds.get();
        if (importId == null || !importId.matches("[A-Za-z0-9._-]+")) {
            throw new IOException("Invalid selected image import ID");
        }
        File destination = new File(directory, "leanote-image-" + importId + suffix);
        ledger.recordPending(importId, destination, mimeType, clock.getAsLong(), sessionId);
        try {
            if (!destination.createNewFile()) {
                throw new IOException("Selected image destination already exists");
            }
        } catch (IOException exception) {
            markFailed(destination, exception);
            throw exception;
        }
        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                total += count;
            }
            if (total == 0) {
                throw new IOException("Selected image stream is empty");
            }
            output.getFD().sync();
            ledger.markSucceeded(destination);
            return destination;
        } catch (IOException | RuntimeException exception) {
            markFailed(destination, exception);
            deleteIfManaged(destination);
            throw exception;
        }
    }

    public void acknowledge(File file) {
        try {
            ledger.markAcknowledged(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to acknowledge selected image import", exception);
        }
    }

    public void abandonAndDelete(File file) {
        try {
            ledger.markAbandoned(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to abandon selected image import", exception);
        }
        if (!deleteIfManaged(file)) {
            throw new IllegalStateException("Unable to delete abandoned selected image import");
        }
    }

    public void cleanupAbandoned(RelationshipOwner relationshipOwner) throws IOException {
        List<SelectedImageLedger.Entry> candidates = ledger.cleanupCandidates(sessionId);
        for (SelectedImageLedger.Entry entry : candidates) {
            File image = new File(entry.path);
            ledger.markAbandoned(image);
            if (relationshipOwner.owns(image.getCanonicalPath())) {
                ledger.markAcknowledged(image);
            } else {
                deleteIfManaged(image);
            }
        }
    }

    SelectedImageLedger.State stateOf(File file) throws IOException {
        return ledger.stateOf(file);
    }

    private void markFailed(File destination, Throwable failure) {
        try {
            ledger.markFailed(destination);
        } catch (IOException ledgerFailure) {
            failure.addSuppressed(ledgerFailure);
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
