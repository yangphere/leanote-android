package org.houxg.leamonax.ui.edit;

import org.houxg.leamonax.service.SelectedImageStore;

import java.io.File;

final class ImageImportResultHandler {
    interface ImageRegistration {
        String register(String path);
    }

    interface ImageInsertion {
        void insert(String uri);
    }

    interface ImageRollback {
        void rollback(String uri);
    }

    private ImageImportResultHandler() {
    }

    static void handle(
            File image,
            ImageRegistration registration,
            ImageInsertion insertion,
            ImageRollback rollback,
            SelectedImageStore store
    ) {
        String imageUri = null;
        try {
            imageUri = registration.register(image.getAbsolutePath());
            if (imageUri == null) {
                throw new IllegalStateException("Unable to persist selected image");
            }
            insertion.insert(imageUri);
            store.acknowledge(image);
        } catch (RuntimeException exception) {
            if (imageUri == null) {
                abandonAndDelete(store, image, exception);
            } else {
                try {
                    rollback.rollback(imageUri);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                } finally {
                    // The relationship owner normally removes the file. Keep the
                    // import boundary fail-closed if relationship rollback fails.
                    abandonAndDelete(store, image, exception);
                }
            }
            throw exception;
        }
    }

    private static void abandonAndDelete(SelectedImageStore store, File image, RuntimeException failure) {
        try {
            store.abandonAndDelete(image);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
