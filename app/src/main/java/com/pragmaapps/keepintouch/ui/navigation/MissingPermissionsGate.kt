package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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
    }
}
