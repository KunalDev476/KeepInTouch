package com.pragmaapps.keepintouch.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.pragmaapps.keepintouch.data.logging.AppLogger

/**
 * Thin wrapper over [SpeechRecognizer] for voice scheduling: one utterance per [start], results
 * delivered through [onEvent]. Must be used from the main thread (SpeechRecognizer's own rule).
 *
 * Prefers the on-device recognizer, so audio stays on the phone. If this phone has no on-device
 * Hindi model yet, it asks the system to download one and falls back to the default recognizer
 * (with offline preferred) for the rest of the session, rather than leaving voice unusable.
 *
 * Never logs what was heard - only error codes reach AppLogger.
 */
class SpeechListener(private val context: Context) {

    sealed interface Event {
        data object Ready : Event
        /** Normalised input loudness, 0..1 - drives the mic ring. */
        data class Level(val level: Float) : Event
        data class Partial(val text: String) : Event
        data class Results(val hypotheses: List<String>) : Event
        data class Error(val kind: ErrorKind) : Event
    }

    enum class ErrorKind { SILENCE, BUSY, PERMISSION, FATAL }

    var onEvent: (Event) -> Unit = {}

    private var recognizer: SpeechRecognizer? = null
    private var usingOnDevice = false
    private var onDeviceFailed = false
    private var biasing: List<String> = emptyList()

    fun isAvailable(): Boolean =
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context) || SpeechRecognizer.isRecognitionAvailable(context)

    /** [biasingStrings] = words to favour (the wake word, contact names) - see EXTRA_BIASING_STRINGS. */
    fun start(biasingStrings: List<String>) {
        biasing = biasingStrings
        val r = recognizer ?: create().also { recognizer = it }
        r.startListening(intent())
    }

    fun stop() {
        recognizer?.cancel()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun create(): SpeechRecognizer {
        usingOnDevice = !onDeviceFailed && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val r = if (usingOnDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        return r
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        if (biasing.isNotEmpty()) putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasing))
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onEvent(Event.Ready)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = onEvent(Event.Level(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)))
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) onEvent(Event.Partial(text))
        }

        override fun onResults(results: Bundle?) {
            val hypotheses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
            onEvent(if (hypotheses.isEmpty()) Event.Error(ErrorKind.SILENCE) else Event.Results(hypotheses))
        }

        override fun onError(error: Int) {
            if (usingOnDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                AppLogger.i(TAG, "On-device recognizer has no $LANGUAGE model (error $error) - requesting download, using default recognizer")
                recognizer?.let { r -> runCatching { r.triggerModelDownload(intent()) } }
                onDeviceFailed = true
                release()
                // Retry straight away with the fallback; the caller just sees a slightly slower turn.
                start(biasing)
                return
            }
            val kind = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> ErrorKind.SILENCE
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> ErrorKind.BUSY
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ErrorKind.PERMISSION
                else -> ErrorKind.FATAL
            }
            if (kind == ErrorKind.FATAL) AppLogger.e(TAG, "Speech recognition error $error (onDevice=$usingOnDevice)")
            onEvent(Event.Error(kind))
        }
    }

    companion object {
        private const val TAG = "SpeechListener"
        /** Hindi - it also transcribes the English words of Hinglish well, unlike en-IN for Hindi words. */
        private const val LANGUAGE = "hi-IN"
    }
}
