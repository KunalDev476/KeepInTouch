package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pragmaapps.keepintouch.data.permissions.CallScreeningSource

/**
 * Decides, once per fresh start, whether the missing-permissions screen shows. A fresh start can't
 * mean "the process just started": the notification listener keeps the process alive after the
 * user swipes the app away. So it's either a new launch, or the first Activity of a new process
 * (Android restores state after killing the app, as it does when a permission is revoked). Rotating
 * the screen is neither, so a dismissed screen stays dismissed. Held here rather than in saved
 * state because a restored "dismissed" value must not outlive the process.
 */
object MissingPermissionsGate {
    private var checkedThisProcess = false

    var showing by mutableStateOf(false)
        private set

    fun onActivityCreated(isNewLaunch: Boolean, shouldShow: () -> Boolean) {
        if (isNewLaunch || !checkedThisProcess) {
            checkedThisProcess = true
            showing = shouldShow()
        }
    }

    fun dismiss() {
        showing = false
        if (callScreeningQueued) {
            callScreeningQueued = false
            showCallScreening(CallScreeningSource.STARTUP)
        }
    }

    /** Whether the optional call-screening screen is showing over the app. */
    var showingCallScreening by mutableStateOf(false)
        private set

    /** Why the call-screening screen is showing, so closing it can be logged and remembered. */
    var callScreeningSource: CallScreeningSource = CallScreeningSource.SETTINGS
        private set

    private var callScreeningQueued = false

    /**
     * The one-time startup offer for existing users. Waits for the missing-permissions screen if
     * that's showing, so the required permissions always come first.
     */
    fun offerCallScreeningOnStartup() {
        if (showing) callScreeningQueued = true else showCallScreening(CallScreeningSource.STARTUP)
    }

    /**
     * Opens the optional call-screening permission screen over whatever is showing. Safe to call
     * from anywhere on the main thread - Settings uses it, and so can any code that later decides
     * call detection needs the backup (e.g. after repeated unnamed calls).
     */
    fun showCallScreening(source: CallScreeningSource = CallScreeningSource.SETTINGS) {
        callScreeningSource = source
        showingCallScreening = true
    }

    fun dismissCallScreening() {
        showingCallScreening = false
    }
}
