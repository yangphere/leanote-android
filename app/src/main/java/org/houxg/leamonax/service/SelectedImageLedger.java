package org.houxg.leamonax.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

final class SelectedImageLedger {
    enum State {
        PENDING,
        SUCCEEDED,
        ACKNOWLEDGED,
        FAILED,
        ABANDONED
    }

    static final class Entry {
        final String id;
        final String path;
        final String mimeType;
        final long createdAt;
        final String sessionId;
        final State state;

        Entry(String id, String path, String mimeType, long createdAt, String sessionId, State state) {
            this.id = id;
            this.path = path;
            this.mimeType = mimeType;
            this.createdAt = createdAt;
            this.sessionId = sessionId;
            this.state = state;
        }

        Entry withState(State nextState) {
            return new Entry(id, path, mimeType, createdAt, sessionId, nextState);
        }
    }

    private static final Object FILE_LOCK = new Object();
    private static final String FIELD_SEPARATOR = "|";
    private final File file;

    SelectedImageLedger(File file) {
        this.file = file;
    }

    void recordPending(String id, File image, String mimeType, long createdAt, String sessionId) throws IOException {
        synchronized (FILE_LOCK) {
            Properties properties = read();
            if (properties.containsKey(id)) {
                throw new IOException("Selected image import ID already exists: " + id);
            }
            Entry entry = new Entry(id, image.getCanonicalPath(), mimeType, createdAt, sessionId, State.PENDING);
            properties.setProperty(id, encode(entry));
            write(properties);
        }
    }

    void markSucceeded(File image) throws IOException {
        update(image, State.SUCCEEDED);
    }

    void markAcknowledged(File image) throws IOException {
        update(image, State.ACKNOWLEDGED);
    }

    void markFailed(File image) throws IOException {
        update(image, State.FAILED);
    }

    void markAbandoned(File image) throws IOException {
        update(image, State.ABANDONED);
    }

    State stateOf(File image) throws IOException {
        String canonicalPath = image.getCanonicalPath();
        synchronized (FILE_LOCK) {
            for (Entry entry : entries(read())) {
                if (entry.path.equals(canonicalPath)) {
                    return entry.state;
                }
            }
        }
        return null;
    }

    List<Entry> cleanupCandidates(String currentSessionId) throws IOException {
        List<Entry> candidates = new ArrayList<>();
        synchronized (FILE_LOCK) {
            for (Entry entry : entries(read())) {
                boolean previousSessionPending = !entry.sessionId.equals(currentSessionId)
                        && (entry.state == State.PENDING || entry.state == State.SUCCEEDED);
                boolean retryableCleanup = entry.state == State.FAILED || entry.state == State.ABANDONED;
                if (previousSessionPending || retryableCleanup) {
                    candidates.add(entry);
                }
            }
        }
        return candidates;
    }

    private void update(File image, State state) throws IOException {
        String canonicalPath = image.getCanonicalPath();
        synchronized (FILE_LOCK) {
            Properties properties = read();
            for (String id : properties.stringPropertyNames()) {
                Entry entry = decode(id, properties.getProperty(id));
                if (entry.path.equals(canonicalPath)) {
                    properties.setProperty(id, encode(entry.withState(state)));
                    write(properties);
                    return;
                }
            }
            throw new IOException("Selected image import is missing from the ledger: " + canonicalPath);
        }
    }

    private Properties read() throws IOException {
        Properties properties = new Properties();
        if (!file.isFile()) {
            return properties;
        }
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }

    private void write(Properties properties) throws IOException {
        File parent = file.getCanonicalFile().getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("Unable to create selected image ledger directory");
        }
        File temporary = new File(parent, file.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temporary, false)) {
            properties.store(output, "Leanote selected image import ledger");
            output.getFD().sync();
        }
        try {
            Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
    }

    private static List<Entry> entries(Properties properties) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (String id : properties.stringPropertyNames()) {
            entries.add(decode(id, properties.getProperty(id)));
        }
        return entries;
    }

    private static String encode(Entry entry) {
        return encodeField(entry.path)
                + FIELD_SEPARATOR + encodeField(entry.mimeType)
                + FIELD_SEPARATOR + entry.createdAt
                + FIELD_SEPARATOR + encodeField(entry.sessionId)
                + FIELD_SEPARATOR + entry.state.name();
    }

    private static Entry decode(String id, String value) throws IOException {
        String[] fields = value.split("\\|", -1);
        if (fields.length != 5) {
            throw new IOException("Malformed selected image ledger entry: " + id);
        }
        try {
            return new Entry(
                    id,
                    decodeField(fields[0]),
                    decodeField(fields[1]),
                    Long.parseLong(fields[2]),
                    decodeField(fields[3]),
                    State.valueOf(fields[4])
            );
        } catch (IllegalArgumentException exception) {
            throw new IOException("Malformed selected image ledger entry: " + id, exception);
        }
    }

    private static String encodeField(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeField(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
