package com.pragmaapps.keepintouch.ui.screens.voice

import android.os.Build

/**
 * Voice scheduling is offered on Android 15+ only - older versions don't get the mic button, the
 * one-time intro overlay, or any way into the voice screen.
 */
object VoiceFeature {
    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
}
