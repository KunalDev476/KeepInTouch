package com.pragmaapps.keepintouch

import android.app.Application
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.reminder.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class KeepInTouchApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hilt's SingletonComponent is ready as soon as super.onCreate() returns, so the
        // EntryPointAccessors lookup inside AppLogger.init() is safe here.
        AppLogger.init(this)
        AppAnalytics.init(this)
        ReminderNotifications.ensureChannel(this)
    }
}
