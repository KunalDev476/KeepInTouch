package com.pragmaapps.keepintouch.ui.navigation

/**
 * Coordinates the "always land back on Today when the app returns from the background" behavior
 * (see KeepInTouchRoot's ON_STOP/ON_START observer) with everything in the app that itself causes
 * a stop/start pair the user never asked for - a runtime permission dialog or an Android Settings
 * screen launched from within the app (contacts, notifications, exact-alarm access, the
 * notification-listener settings page). Those interruptions are indistinguishable from real
 * backgrounding purely from Activity lifecycle callbacks, so anything that launches one calls
 * [suppressNext] immediately beforehand to skip the reset exactly once.
 */
object ForegroundResetGate {
    @Volatile
    private var suppressed = false

    fun suppressNext() {
        suppressed = true
    }

    /** Consumes (resets) the flag - call once per ON_START, and only actually reset if this returns false. */
    fun consumeSuppress(): Boolean {
        val was = suppressed
        suppressed = false
        return was
    }
}
