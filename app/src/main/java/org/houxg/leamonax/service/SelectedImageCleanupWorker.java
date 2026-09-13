package org.houxg.leamonax.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.houxg.leamonax.database.NoteFileDataStore;

import java.io.IOException;

public final class SelectedImageCleanupWorker extends Worker {
    public SelectedImageCleanupWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            SelectedImageStore.from(getApplicationContext())
                    .cleanupAbandoned(NoteFileDataStore::hasLocalPath);
            return Result.success();
        } catch (IOException | RuntimeException exception) {
            return Result.retry();
        }
    }
}
