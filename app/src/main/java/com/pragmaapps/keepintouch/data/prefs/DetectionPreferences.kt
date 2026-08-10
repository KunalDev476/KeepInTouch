package com.pragmaapps.keepintouch.data.prefs

import android.content.Context
import com.pragmaapps.keepintouch.domain.util.DetectionSettings
import com.pragmaapps.keepintouch.domain.util.QualifyingCallRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "detection_preferences"
private const val KEY_MIN_DURATION_MS = "min_duration_ms"
private const val KEY_COUNT_INCOMING = "count_incoming"
private const val KEY_COUNT_OUTGOING = "count_outgoing"
private const val KEY_VIDEO_CALLS_COUNT = "video_calls_count"

/**
 * User-configurable detection knobs (Settings screen). Not secret data, so plain
 * SharedPreferences is fine here - only the SQLCipher DB passphrase needs Keystore protection.
 */
@Singleton
class DetectionPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readFromDisk())
    val settings: StateFlow<DetectionSettings> = _settings.asStateFlow()

    private fun readFromDisk() = DetectionSettings(
        minDurationMs = prefs.getLong(KEY_MIN_DURATION_MS, QualifyingCallRule.DEFAULT_MIN_DURATION_MS),
        countIncoming = prefs.getBoolean(KEY_COUNT_INCOMING, true),
        countOutgoing = prefs.getBoolean(KEY_COUNT_OUTGOING, true),
        videoCallsCount = prefs.getBoolean(KEY_VIDEO_CALLS_COUNT, true)
    )

    fun setMinDurationMs(value: Long) = update { it.copy(minDurationMs = value) }
    fun setCountIncoming(value: Boolean) = update { it.copy(countIncoming = value) }
    fun setCountOutgoing(value: Boolean) = update { it.copy(countOutgoing = value) }
    fun setVideoCallsCount(value: Boolean) = update { it.copy(videoCallsCount = value) }

    private fun update(transform: (DetectionSettings) -> DetectionSettings) {
        val next = transform(_settings.value)
        prefs.edit()
            .putLong(KEY_MIN_DURATION_MS, next.minDurationMs)
            .putBoolean(KEY_COUNT_INCOMING, next.countIncoming)
            .putBoolean(KEY_COUNT_OUTGOING, next.countOutgoing)
            .putBoolean(KEY_VIDEO_CALLS_COUNT, next.videoCallsCount)
            .apply()
        _settings.value = next
    }
}
