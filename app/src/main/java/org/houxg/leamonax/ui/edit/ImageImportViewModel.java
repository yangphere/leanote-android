package org.houxg.leamonax.ui.edit;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import org.houxg.leamonax.service.SelectedImageStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ImageImportViewModel extends ViewModel {
    interface Importer {
        File importImage(Uri source) throws Exception;
    }

    public static final class Result {
        private final File file;
        private final Throwable error;

        private Result(File file, Throwable error) {
            this.file = file;
            this.error = error;
        }

        public File getFile() {
            return file;
        }

        public Throwable getError() {
            return error;
        }
    }

    public static final class Factory implements ViewModelProvider.Factory {
        private final Context applicationContext;

        public Factory(Context context) {
            applicationContext = context.getApplicationContext();
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(ImageImportViewModel.class)) {
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
            }
            SelectedImageStore store = SelectedImageStore.from(applicationContext);
            ContentResolver resolver = applicationContext.getContentResolver();
            Importer importer = source -> {
                String mimeType = resolver.getType(source);
                try (InputStream input = resolver.openInputStream(source)) {
                    if (input == null) {
                        throw new IOException("Content resolver returned no stream for selected image");
                    }
                    return store.copy(mimeType, input);
                }
            };
            return (T) new ImageImportViewModel(importer, Executors.newSingleThreadExecutor(), store::deleteIfManaged);
        }
    }

    private final Importer importer;
    private final Executor executor;
    private final Consumer<File> abandonedFileCleanup;
    private final MutableLiveData<Result> result = new MutableLiveData<>();
    private Result pendingResult;
    private boolean importRunning;
    private boolean cleared;

    ImageImportViewModel(Importer importer, Executor executor) {
        this(importer, executor, File::delete);
    }

    ImageImportViewModel(Importer importer, Executor executor, Consumer<File> abandonedFileCleanup) {
        this.importer = importer;
        this.executor = executor;
        this.abandonedFileCleanup = abandonedFileCleanup;
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public synchronized void importImage(Uri source) {
        if (cleared || importRunning || pendingResult != null) {
            return;
        }
        importRunning = true;
        executor.execute(() -> completeImport(source));
    }

    private void completeImport(Uri source) {
        Result completed;
        try {
            completed = new Result(importer.importImage(source), null);
        } catch (Exception exception) {
            completed = new Result(null, exception);
        }

        synchronized (this) {
            importRunning = false;
            if (cleared) {
                if (completed.file != null) {
                    abandonedFileCleanup.accept(completed.file);
                }
                return;
            }
            pendingResult = completed;
            result.postValue(completed);
        }
    }

    public synchronized void acknowledge(Result handled) {
        if (pendingResult == handled) {
            pendingResult = null;
            result.setValue(null);
        }
    }

    @Override
    protected synchronized void onCleared() {
        cleared = true;
        Result pending = pendingResult;
        pendingResult = null;
        if (pending != null && pending.file != null) {
            abandonedFileCleanup.accept(pending.file);
        }
        if (executor instanceof ExecutorService) {
            ((ExecutorService) executor).shutdownNow();
        }
    }
}
