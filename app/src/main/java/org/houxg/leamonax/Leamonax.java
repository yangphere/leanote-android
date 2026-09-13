package org.houxg.leamonax;


import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.facebook.stetho.Stetho;
import com.github.piasy.biv.BigImageViewer;
import com.github.piasy.biv.loader.glide.GlideImageLoader;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.tencent.bugly.crashreport.CrashReport;

import net.danlew.android.joda.JodaTimeAndroid;

import org.greenrobot.eventbus.EventBus;
import org.houxg.leamonax.service.SelectedImageCleanupScheduler;

import dagger.hilt.android.HiltAndroidApp;

@HiltAndroidApp
public class Leamonax extends Application {

    private static Context mContext;

    public static Context getContext() {
        return mContext;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        XLog.init(BuildConfig.DEBUG ? LogLevel.ALL : LogLevel.NONE);
        if (!TextUtils.isEmpty(BuildConfig.BUGLY_KEY)) {
            initBugly();
        }
        BigImageViewer.initialize(GlideImageLoader.with(this));
        EventBus.builder()
                .logNoSubscriberMessages(false)
                .sendNoSubscriberEvent(false)
                .throwSubscriberException(true)
                .installDefaultEventBus();
        FlowManager.init(new FlowConfig.Builder(this).build());
        SelectedImageCleanupScheduler.start(this);
        JodaTimeAndroid.init(this);
        if (BuildConfig.DEBUG) {
            Stetho.initializeWithDefaults(this);
        }
    }

    private void initBugly() {
        CrashReport.UserStrategy strategy = new CrashReport.UserStrategy(this);
        strategy.setEnableNativeCrashMonitor(false);
        CrashReport.initCrashReport(this, BuildConfig.BUGLY_KEY, BuildConfig.DEBUG, strategy);
    }
}
