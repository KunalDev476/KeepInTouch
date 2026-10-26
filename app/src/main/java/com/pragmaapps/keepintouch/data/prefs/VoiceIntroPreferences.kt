package com.pragmaapps.keepintouch.data.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "voice_intro_preferences"
private const val KEY_SEEN = "voice_intro_seen"

/**
 * Whether the one-time "In Touch just got smarter" overlay pointing at the voice mic has been
 * shown - same shape and lifecycle as [OnboardingPreferences]: false until dismissed once, then
 * true forever (cleared only with the app's data).
 */
@Singleton
class VoiceIntroPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(prefs.getBoolean(KEY_SEEN, false))
    val seen: StateFlow<Boolean> = _seen.asStateFlow()

    fun markSeen() {
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
        _seen.value = true
    }
}
