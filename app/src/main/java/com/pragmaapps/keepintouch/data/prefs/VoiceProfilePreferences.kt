package com.pragmaapps.keepintouch.data.prefs

import android.content.Context
import com.pragmaapps.keepintouch.domain.voice.Honorific
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "voice_profile_preferences"
private const val KEY_INTRO_DONE = "voice_intro_conversation_done"
private const val KEY_HONORIFIC = "voice_honorific"

/**
 * What the voice assistant remembers about the user, on this phone only: whether the first-time
 * introduction already happened, and whether to say "sir", "ma'am" or neither. The user's name is
 * deliberately not stored - it's only used once, to make that guess.
 */
@Singleton
class VoiceProfilePreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val introDone: Boolean get() = prefs.getBoolean(KEY_INTRO_DONE, false)

    fun markIntroDone() {
        prefs.edit().putBoolean(KEY_INTRO_DONE, true).apply()
    }

    val honorific: Honorific
        get() = prefs.getString(KEY_HONORIFIC, null)?.let { saved -> Honorific.entries.firstOrNull { it.name == saved } }
            ?: Honorific.NEUTRAL

    fun setHonorific(honorific: Honorific) {
        prefs.edit().putString(KEY_HONORIFIC, honorific.name).apply()
    }
}
