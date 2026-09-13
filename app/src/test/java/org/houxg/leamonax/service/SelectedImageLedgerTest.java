package org.houxg.leamonax.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectedImageLedgerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsPendingAndSucceededStatesAcrossStoreRecreation() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore first = new SelectedImageStore(managed, "old-session", () -> "import-1", () -> 123L);

        File image = first.copy("image/png", new java.io.ByteArrayInputStream(new byte[]{1, 2}));

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        assertEquals(SelectedImageLedger.State.SUCCEEDED, restarted.stateOf(image));
    }

    @Test
    public void startupCleanupDeletesUnrelatedCopyAndLeavesAbandonedReceipt() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore previousProcess = new SelectedImageStore(managed, "old-session", () -> "import-1", () -> 123L);
        File image = previousProcess.copy("image/jpeg", new java.io.ByteArrayInputStream(new byte[]{1}));

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        restarted.cleanupAbandoned(path -> false);
        restarted.cleanupAbandoned(path -> false);

        assertFalse(image.exists());
        assertEquals(SelectedImageLedger.State.ABANDONED, restarted.stateOf(image));
    }

    @Test
    public void startupCleanupClosesPendingReceiptCreatedBeforeDestinationFile() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        File image = new File(managed, "leanote-image-import-1.png");
        SelectedImageLedger ledger = new SelectedImageLedger(new File(root, "selected-image-imports.properties"));
        ledger.recordPending("import-1", image, "image/png", 123L, "old-session");

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        restarted.cleanupAbandoned(path -> false);

        assertFalse(image.exists());
        assertEquals(SelectedImageLedger.State.ABANDONED, restarted.stateOf(image));
    }

    @Test
    public void startupCleanupDeletesPartialCopyFromPreviousProcess() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        assertTrue(managed.mkdirs());
        File image = new File(managed, "leanote-image-import-1.png");
        Files.write(image.toPath(), new byte[]{1});
        SelectedImageLedger ledger = new SelectedImageLedger(new File(root, "selected-image-imports.properties"));
        ledger.recordPending("import-1", image, "image/png", 123L, "old-session");

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        restarted.cleanupAbandoned(path -> false);

        assertFalse(image.exists());
        assertEquals(SelectedImageLedger.State.ABANDONED, restarted.stateOf(image));
    }

    @Test
    public void startupCleanupPreservesCopyOwnedByPersistedRelationship() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore previousProcess = new SelectedImageStore(managed, "old-session", () -> "import-1", () -> 123L);
        File image = previousProcess.copy("image/webp", new java.io.ByteArrayInputStream(new byte[]{1}));
        String canonicalPath = image.getCanonicalPath();

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        restarted.cleanupAbandoned(path -> path.equals(canonicalPath));

        assertTrue(image.isFile());
        assertEquals(SelectedImageLedger.State.ACKNOWLEDGED, restarted.stateOf(image));
    }

    @Test
    public void cleanupDoesNotTouchImportsFromCurrentProcess() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore store = new SelectedImageStore(managed, "current-session", () -> "import-1", () -> 123L);
        File image = store.copy("image/avif", new java.io.ByteArrayInputStream(new byte[]{1}));

        store.cleanupAbandoned(path -> false);

        assertTrue(image.isFile());
        assertEquals(SelectedImageLedger.State.SUCCEEDED, store.stateOf(image));
    }

    @Test
    public void failedDeletionRemainsAbandonedForCompensationRetry() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageLedger ledger = new SelectedImageLedger(new File(root, "selected-image-imports.properties"));
        File image = new File(managed, "nested/image.png");
        Files.createDirectories(image.toPath());
        ledger.recordPending("import-1", image, "image/png", 123L, "old-session");
        ledger.markSucceeded(image);

        SelectedImageStore restarted = new SelectedImageStore(managed, "new-session", () -> "unused", () -> 456L);
        restarted.cleanupAbandoned(path -> false);

        assertEquals(SelectedImageLedger.State.ABANDONED, restarted.stateOf(image));
        assertTrue(image.exists());
    }

    @Test
    public void startupCleanupRetriesAFileLeftByFailedCopyCleanup() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        assertTrue(managed.mkdirs());
        File image = new File(managed, "leanote-image-import-1.png");
        Files.write(image.toPath(), new byte[]{1});
        SelectedImageLedger ledger = new SelectedImageLedger(new File(root, "selected-image-imports.properties"));
        ledger.recordPending("import-1", image, "image/png", 123L, "current-session");
        ledger.markFailed(image);

        SelectedImageStore currentProcess = new SelectedImageStore(managed, "current-session", () -> "unused", () -> 456L);
        currentProcess.cleanupAbandoned(path -> false);

        assertFalse(image.exists());
        assertEquals(SelectedImageLedger.State.ABANDONED, currentProcess.stateOf(image));
    }

    @Test
    public void acknowledgementIsIdempotent() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore store = new SelectedImageStore(managed, "current-session", () -> "import-1", () -> 123L);
        File image = store.copy("image/heic", new java.io.ByteArrayInputStream(new byte[]{1}));

        store.acknowledge(image);
        store.acknowledge(image);

        assertEquals(SelectedImageLedger.State.ACKNOWLEDGED, store.stateOf(image));
    }

    @Test
    public void failedCopyIsRecordedAndLeavesNoManagedFile() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        SelectedImageStore store = new SelectedImageStore(managed, "current-session", () -> "import-1", () -> 123L);
        File image = new File(managed, "leanote-image-import-1.png");

        try {
            store.copy("image/png", new java.io.ByteArrayInputStream(new byte[0]));
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }

        assertFalse(image.exists());
        assertEquals(SelectedImageLedger.State.FAILED, store.stateOf(image));
    }

    @Test
    public void importIdsAreNotReused() throws Exception {
        File root = temporaryFolder.newFolder("files");
        File managed = new File(root, "selected-images");
        AtomicInteger ids = new AtomicInteger();
        SelectedImageStore store = new SelectedImageStore(
                managed,
                "current-session",
                () -> "import-" + ids.incrementAndGet(),
                () -> 123L
        );

        File first = store.copy("image/png", new java.io.ByteArrayInputStream(new byte[]{1}));
        File second = store.copy("image/png", new java.io.ByteArrayInputStream(new byte[]{2}));

        assertFalse(first.equals(second));
    }
}
