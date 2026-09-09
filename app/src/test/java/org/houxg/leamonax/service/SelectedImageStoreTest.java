package org.houxg.leamonax.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SelectedImageStoreTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void copiesSupportedImageIntoManagedDirectory() throws Exception {
        File managedDirectory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(managedDirectory);
        byte[] content = new byte[]{1, 2, 3, 4};

        File result = store.copy("image/png", new ByteArrayInputStream(content));

        assertTrue(store.isManaged(result));
        assertTrue(result.getName().endsWith(".png"));
        assertArrayEquals(content, java.nio.file.Files.readAllBytes(result.toPath()));
    }

    @Test(expected = IOException.class)
    public void rejectsUnknownMimeBeforeCreatingAFile() throws Exception {
        File managedDirectory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(managedDirectory);

        try {
            store.copy(null, new ByteArrayInputStream(new byte[]{1}));
        } finally {
            assertFalse(managedDirectory.listFiles().length > 0);
        }
    }

    @Test
    public void removesPartialFileWhenCopyFails() throws Exception {
        File managedDirectory = temporaryFolder.newFolder("selected-images");
        SelectedImageStore store = new SelectedImageStore(managedDirectory);
        InputStream failingInput = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("broken provider");
            }
        };

        try {
            store.copy("image/jpeg", failingInput);
        } catch (IOException expected) {
            // Expected provider failure.
        }

        assertFalse(managedDirectory.listFiles().length > 0);
    }

    @Test
    public void neverDeletesFilesOutsideManagedDirectory() throws Exception {
        File managedDirectory = temporaryFolder.newFolder("selected-images");
        File external = temporaryFolder.newFile("keep.jpg");
        SelectedImageStore store = new SelectedImageStore(managedDirectory);

        assertFalse(store.deleteIfManaged(external));
        assertTrue(external.isFile());
    }
}
