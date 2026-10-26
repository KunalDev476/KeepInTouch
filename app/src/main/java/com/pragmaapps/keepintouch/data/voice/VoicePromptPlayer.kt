package com.pragmaapps.keepintouch.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.pragmaapps.keepintouch.R
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.voice.VoicePrompt

/**
 * Plays the assistant's pre-recorded lines back to back. No text-to-speech or network involved -
 * every line is a bundled clip (see [VoicePrompt]). Main thread only.
 */
class VoicePromptPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    private var queue: List<VoicePrompt> = emptyList()
    private var onDone: (() -> Unit)? = null

    val isPlaying: Boolean get() = player != null

    /** Plays [prompts] in order, then calls [onFinished] - unless [stop] interrupts it first. */
    fun play(prompts: List<VoicePrompt>, onFinished: () -> Unit) {
        stop()
        queue = prompts
        onDone = onFinished
        playNext()
    }

    fun stop() {
        onDone = null
        queue = emptyList()
        player?.release()
        player = null
    }

    private fun playNext() {
        player?.release()
        player = null
        val next = queue.firstOrNull()
        if (next == null) {
            val done = onDone
            onDone = null
            done?.invoke()
            return
        }
        queue = queue.drop(1)
        try {
            val afd = context.resources.openRawResourceFd(next.audioRes())
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                setOnCompletionListener { playNext() }
                setOnErrorListener { _, what, extra ->
                    AppLogger.e(TAG, "Couldn't play ${next.name} ($what/$extra)")
                    playNext()
                    true
                }
                prepare()
                start()
            }
        } catch (t: Throwable) {
            // A missing/broken clip must never stall the conversation - skip to the next line.
            AppLogger.e(TAG, "Couldn't play ${next.name}", t)
            playNext()
        }
    }

    companion object {
        private const val TAG = "VoicePromptPlayer"
    }
}

/** Referenced directly (not looked up by name) so resource shrinking keeps every clip. */
@RawRes
fun VoicePrompt.audioRes(): Int = when (this) {
    VoicePrompt.WAKE_ACK -> R.raw.voice_wake_ack
    VoicePrompt.NO_SPEECH -> R.raw.voice_no_speech
    VoicePrompt.LISTENING_TIMEOUT -> R.raw.voice_listening_timeout
    VoicePrompt.CONTACT_NO_NAME -> R.raw.voice_contact_no_name
    VoicePrompt.CONTACT_CONFIRM -> R.raw.voice_contact_confirm
    VoicePrompt.CONTACT_MULTIPLE -> R.raw.voice_contact_multiple
    VoicePrompt.CONTACT_NOT_FOUND -> R.raw.voice_contact_not_found
    VoicePrompt.CONTACT_NOT_FOUND_2 -> R.raw.voice_contact_not_found_2
    VoicePrompt.CONTACT_RETRY -> R.raw.voice_contact_retry
    VoicePrompt.CONTACT_HAS_SCHEDULE -> R.raw.voice_contact_has_schedule
    VoicePrompt.CONTACT_NO_PHONE -> R.raw.voice_contact_no_phone
    VoicePrompt.NAME_CLASH -> R.raw.voice_name_clash
    VoicePrompt.CONTACTS_PERMISSION_REQUIRED -> R.raw.voice_contacts_permission_required
    VoicePrompt.ASK_TYPE -> R.raw.voice_ask_type
    VoicePrompt.ASK_FREQUENCY -> R.raw.voice_ask_frequency
    VoicePrompt.ASK_WEEKDAYS -> R.raw.voice_ask_weekdays
    VoicePrompt.WEEKDAYS_UNCLEAR -> R.raw.voice_weekdays_unclear
    VoicePrompt.ASK_DATE -> R.raw.voice_ask_date
    VoicePrompt.DATE_IN_PAST -> R.raw.voice_date_in_past
    VoicePrompt.ASK_TIME -> R.raw.voice_ask_time
    VoicePrompt.TIME_AM_PM -> R.raw.voice_time_am_pm
    VoicePrompt.TIME_IN_PAST -> R.raw.voice_time_in_past
    VoicePrompt.TIME_WINDOW_INVALID -> R.raw.voice_time_window_invalid
    VoicePrompt.SUMMARY_CONFIRM -> R.raw.voice_summary_confirm
    VoicePrompt.EDIT_SUMMARY -> R.raw.voice_edit_summary
    VoicePrompt.DUPLICATE_SCHEDULE -> R.raw.voice_duplicate_schedule
    VoicePrompt.SAVED_ONE -> R.raw.voice_saved_one
    VoicePrompt.SAVED_MANY -> R.raw.voice_saved_many
    VoicePrompt.PARTIAL_SAVE_FAILED -> R.raw.voice_partial_save_failed
    VoicePrompt.SAVE_FAILED -> R.raw.voice_save_failed
    VoicePrompt.NOTIFICATION_PERMISSION_REQUIRED -> R.raw.voice_notification_permission_required
    VoicePrompt.ASK_ANOTHER -> R.raw.voice_ask_another
    VoicePrompt.CANCELLED -> R.raw.voice_cancelled
    VoicePrompt.GOODBYE -> R.raw.voice_goodbye
    VoicePrompt.NOT_UNDERSTOOD -> R.raw.voice_not_understood
    VoicePrompt.NOT_UNDERSTOOD_2 -> R.raw.voice_not_understood_2
    VoicePrompt.YES_NO_UNCLEAR -> R.raw.voice_yes_no_unclear
    VoicePrompt.MIC_ERROR -> R.raw.voice_mic_error
    VoicePrompt.MIC_PERMISSION_REQUIRED -> R.raw.voice_mic_permission_required
}
