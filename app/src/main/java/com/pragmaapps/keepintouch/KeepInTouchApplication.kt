package com.pragmaapps.keepintouch

import android.app.Application
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.logging.CrashReporter
import com.pragmaapps.keepintouch.data.notification.CallEpisodeProcessor
import com.pragmaapps.keepintouch.data.notification.PhoneCallStateTracker
import com.pragmaapps.keepintouch.data.reminder.ReminderNotifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KeepInTouchApplication : Application() {

    @Inject lateinit var phoneCallStateTracker: PhoneCallStateTracker
    @Inject lateinit var callEpisodeProcessor: CallEpisodeProcessor

    private val processScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.init(this)
        AppAnalytics.init(this)
        ReminderNotifications.ensureChannel(this)
        // One collector for the whole process, whoever registered call-state tracking - the
        // notification listener, the app's UI, or the call-screening backup. Each ended session
        // is handled exactly once, and none is dropped for lack of a listener.
        processScope.launch {
            phoneCallStateTracker.sessions.collect { callEpisodeProcessor.onCallSessionEnded(it) }
        }
    }
}
