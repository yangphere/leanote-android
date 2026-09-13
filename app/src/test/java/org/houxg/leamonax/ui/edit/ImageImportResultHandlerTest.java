package org.houxg.leamonax.ui.edit;

import org.houxg.leamonax.service.SelectedImageStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageImportResultHandlerTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void registersImageAndInsertsReturnedUriIntoEditor() throws Exception {
        File directory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(directory);
        File image = store.copy("image/png", new ByteArrayInputStream(new byte[]{1}));
        AtomicReference<String> inserted = new AtomicReference<>();

        ImageImportResultHandler.handle(
                image,
                path -> "file:/getImage?id=local-id",
                inserted::set,
                uri -> { },
                store
        );

        assertEquals("file:/getImage?id=local-id", inserted.get());
        assertTrue(image.isFile());
    }

    @Test
    public void deletesManagedCopyWhenRelationshipPersistenceFails() throws Exception {
        File directory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(directory);
        File image = store.copy("image/png", new ByteArrayInputStream(new byte[]{1}));

        try {
            ImageImportResultHandler.handle(
                    image,
                    path -> { throw new IllegalStateException("database rejected relation"); },
                    uri -> { },
                    uri -> { },
                    store
            );
        } catch (IllegalStateException expected) {
            assertEquals("database rejected relation", expected.getMessage());
        }

        assertFalse(image.exists());
    }

    @Test
    public void rollsBackPersistedRelationshipWhenEditorInsertionFails() throws Exception {
        File directory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(directory);
        File image = store.copy("image/png", new ByteArrayInputStream(new byte[]{1}));
        AtomicReference<String> rolledBack = new AtomicReference<>();

        try {
            ImageImportResultHandler.handle(
                    image,
                    path -> "file:/getImage?id=local-id",
                    uri -> { throw new IllegalStateException("editor rejected image"); },
                    rolledBack::set,
                    store
            );
        } catch (IllegalStateException expected) {
            assertEquals("editor rejected image", expected.getMessage());
        }

        assertEquals("file:/getImage?id=local-id", rolledBack.get());
    }

    @Test
    public void cleansManagedCopyWhenRelationshipRollbackFails() throws Exception {
        File directory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(directory);
        File image = store.copy("image/png", new ByteArrayInputStream(new byte[]{1}));

        try {
            ImageImportResultHandler.handle(
                    image,
                    path -> "file:/getImage?id=local-id",
                    uri -> { throw new IllegalStateException("editor rejected image"); },
                    uri -> { throw new IllegalStateException("relation rollback failed"); },
                    store
            );
        } catch (IllegalStateException expected) {
            assertEquals("editor rejected image", expected.getMessage());
            assertEquals(1, expected.getSuppressed().length);
        }

        assertFalse(image.exists());
    }
}
