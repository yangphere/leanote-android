package org.houxg.leamonax.ui.edit;

import android.net.Uri;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class ImageImportViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void keepsCompletedImportUntilTheEditorAcknowledgesIt() {
        File imported = new File("selected-images/image.png");
        ImageImportViewModel.Importer importer = source -> imported;
        Executor directExecutor = Runnable::run;
        ImageImportViewModel viewModel = new ImageImportViewModel(importer, directExecutor);

        viewModel.importImage(mock(Uri.class));

        ImageImportViewModel.Result result = viewModel.getResult().getValue();
        assertEquals(imported, result.getFile());
        assertNull(result.getError());
        assertEquals(result, viewModel.getResult().getValue());

        viewModel.acknowledge(result);
        assertNull(viewModel.getResult().getValue());
    }
}
