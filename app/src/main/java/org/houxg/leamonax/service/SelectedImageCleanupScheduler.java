package org.houxg.leamonax.service;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.elvishew.xlog.XLog;

import org.houxg.leamonax.database.NoteFileDataStore;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class SelectedImageCleanupScheduler {
    private static final String PERIODIC_WORK_NAME = "selected-image-abandoned-cleanup";

    private SelectedImageCleanupScheduler() {
    }

    public static void start(Context context) {
        Context applicationContext = context.getApplicationContext();
        Thread startupCleanup = new Thread(
                () -> cleanup(applicationContext),
                "selected-image-startup-cleanup"
        );
        startupCleanup.start();

        Constraints constraints = new Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build();
        PeriodicWorkRequest compensation = new PeriodicWorkRequest.Builder(
                SelectedImageCleanupWorker.class,
                1,
                TimeUnit.DAYS
        ).setConstraints(constraints).build();
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                compensation
        );
    }

    private static void cleanup(Context context) {
        try {
            SelectedImageStore.from(context).cleanupAbandoned(NoteFileDataStore::hasLocalPath);
        } catch (IOException | RuntimeException exception) {
            XLog.e("Selected image startup cleanup failed; compensation remains scheduled", exception);
        }
    }
}
