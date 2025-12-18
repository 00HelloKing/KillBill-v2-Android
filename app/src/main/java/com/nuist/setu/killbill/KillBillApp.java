package com.nuist.setu.killbill;

import android.app.Application;

/**
 * Application class.
 *
 * Keeping it lightweight: Room is created lazily via AppDatabase.getInstance().
 */
public class KillBillApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // We create NotificationChannels inside the NotificationListenerService
    }
}
