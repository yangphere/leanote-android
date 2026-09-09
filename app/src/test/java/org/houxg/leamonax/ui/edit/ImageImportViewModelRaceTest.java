package org.houxg.leamonax.ui.edit;

import android.net.Uri;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.houxg.leamonax.service.SelectedImageStore;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ImageImportViewModelRaceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Queue<Runnable> pendingMainTasks = new ArrayDeque<>();

    @Before
    public void delayMainThreadDelivery() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override
            public void executeOnDiskIO(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void postToMainThread(Runnable runnable) {
                pendingMainTasks.add(runnable);
            }

            @Override
            public boolean isMainThread() {
                return true;
            }
        });
    }

    @After
    public void restoreTaskExecutor() {
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void clearingBeforeLiveDataDeliveryStillDeletesCompletedFile() throws Exception {
        File imported = temporaryFolder.newFile("image.png");
        ImageImportViewModel.Importer importer = source -> imported;
        Executor directExecutor = Runnable::run;
        ImageImportViewModel viewModel = new ImageImportViewModel(importer, directExecutor);

        viewModel.importImage(mock(Uri.class));
        viewModel.onCleared();

        while (!pendingMainTasks.isEmpty()) {
            pendingMainTasks.remove().run();
        }

        assertFalse(imported.exists());
    }

    @Test
    public void productionCleanupDoesNotDeleteFilesOutsideManagedDirectory() throws Exception {
        File managedDirectory = temporaryFolder.newFolder("selected-images");
        File external = temporaryFolder.newFile("external.png");
        SelectedImageStore store = new SelectedImageStore(managedDirectory);
        ImageImportViewModel viewModel = new ImageImportViewModel(
                source -> external,
                Runnable::run,
                store::deleteIfManaged
        );

        viewModel.importImage(mock(Uri.class));
        viewModel.onCleared();

        assertTrue(external.isFile());
    }
}
