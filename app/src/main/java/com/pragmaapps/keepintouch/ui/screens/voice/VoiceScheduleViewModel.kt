package com.pragmaapps.keepintouch.ui.screens.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.BuildConfig
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.voice.SpeechListener
import com.pragmaapps.keepintouch.data.voice.VoicePromptPlayer
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.usecase.CreateScheduleUseCase
import com.pragmaapps.keepintouch.domain.usecase.SyncContactsUseCase
import com.pragmaapps.keepintouch.domain.voice.ChatItem
import com.pragmaapps.keepintouch.domain.voice.InputOptions
import com.pragmaapps.keepintouch.domain.voice.QuickReply
import com.pragmaapps.keepintouch.domain.voice.Reply
import com.pragmaapps.keepintouch.domain.voice.SaveOutcome
import com.pragmaapps.keepintouch.domain.voice.ScheduleRequest
import com.pragmaapps.keepintouch.domain.voice.VoiceConversation
import com.pragmaapps.keepintouch.domain.voice.VoicePrompt
import com.pragmaapps.keepintouch.domain.voice.VoiceScheduleBackend
import com.pragmaapps.keepintouch.domain.voice.WakeWord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoicePhase {
    /** Mic off. */
    IDLE,
    /** Mic on, but only listening for the wake word - nothing is shown or acted on until it's heard. */
    WAITING_FOR_WAKE_WORD,
    /** Mid-conversation, listening for the answer to the last question. */
    LISTENING,
    /** Working out what was said. */
    THINKING,
    /** Playing the assistant's reply. */
    SPEAKING
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val items: List<ChatItem> = emptyList(),
    val input: InputOptions = InputOptions.None,
    /** Live transcript of the answer being spoken - only ever shown mid-conversation, never before the wake word. */
    val partialTranscript: String? = null,
    val level: Float = 0f,
    val assistantName: String = "",
    val wakeWord: String = "",
    val micPermissionDenied: Boolean = false
)

/**
 * Glue between [VoiceConversation] (what to say/ask next), [SpeechListener] (hearing) and
 * [VoicePromptPlayer] (speaking). Turn-taking: play the reply, then listen - the mic is never open
 * while the assistant itself is talking, so it can't hear its own voice.
 */
@HiltViewModel
class VoiceScheduleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository,
    private val createSchedule: CreateScheduleUseCase,
    private val syncContacts: SyncContactsUseCase
) : ViewModel() {

    private val wakeWord = WakeWord(BuildConfig.WAKE_WORD)
    private val assistantName = BuildConfig.WAKE_WORD.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }

    private val listener = SpeechListener(context)
    private val player = VoicePromptPlayer(context)

    private val backend = object : VoiceScheduleBackend {
        override suspend fun contacts(): List<Contact> {
            if (!granted(Manifest.permission.READ_CONTACTS)) return emptyList()
            val synced = contactRepository.observeAll().first()
            if (synced.isNotEmpty()) return synced
            syncContacts()
            return contactRepository.observeAll().first()
        }

        override suspend fun activeSchedulesFor(contact: Contact): List<Schedule> =
            scheduleRepository.findByContactLookupKey(contact.lookupKey).filter { it.active }

        override suspend fun save(request: ScheduleRequest): SaveOutcome =
            when (
                createSchedule(
                    contact = request.contact,
                    recurrence = request.recurrence,
                    nextDueDate = request.nextDueDate,
                    note = null,
                    preferredTimeMinutes = request.preferredTimeMinutes,
                    comfortableTimeEndMinutes = request.comfortableTimeEndMinutes
                )
            ) {
                is CreateScheduleUseCase.Result.Created -> SaveOutcome.CREATED
                is CreateScheduleUseCase.Result.NameCollision -> SaveOutcome.NAME_COLLISION
            }

        override fun notificationsBlocked(): Boolean = !granted(Manifest.permission.POST_NOTIFICATIONS)
    }

    private val conversation = VoiceConversation(backend)

    private val _uiState = MutableStateFlow(VoiceUiState(assistantName = assistantName, wakeWord = assistantName))
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private var wakeDeadline = 0L
    private var consecutiveErrors = 0
    private var turnJob: Job? = null
    private var biasing: List<String> = listOf(BuildConfig.WAKE_WORD)

    init {
        listener.onEvent = ::onListenerEvent
        viewModelScope.launch {
            // Contact names as recognition hints - helps the recognizer spell "Rahul" rather than "राहुल".
            biasing = listOf(BuildConfig.WAKE_WORD) + backend.contacts().map { it.displayName }.take(MAX_BIASING_NAMES)
        }
    }

    // region UI actions

    /** Mic tapped with RECORD_AUDIO already granted. */
    fun onMicTapped() {
        if (_uiState.value.phase != VoicePhase.IDLE) {
            stopEverything()
            return
        }
        _uiState.update { it.copy(micPermissionDenied = false) }
        if (!listener.isAvailable()) {
            handleReply(conversation.onMicUnavailable(permissionDenied = false))
            return
        }
        if (conversation.inConversation) listenForAnswer() else startWaitingForWakeWord()
    }

    fun onMicPermissionDenied() {
        _uiState.update { it.copy(micPermissionDenied = true) }
        handleReply(conversation.onMicUnavailable(permissionDenied = true))
    }

    fun onYesNo(yes: Boolean) = runTurn { conversation.onYesNoTapped(yes) }

    fun onQuickReply(reply: QuickReply) = runTurn { conversation.onQuickReply(reply) }

    fun onContactPicked(contact: Contact) = runTurn { conversation.onContactTapped(contact) }

    fun onContactTyped(query: String) = runTurn { conversation.onContactTyped(query) }

    fun onWrongContact() = runTurn { conversation.onWrongContactTapped() }

    fun onContactsPermissionResult(granted: Boolean) {
        if (!granted) return
        runTurn {
            syncContacts()
            biasing = listOf(BuildConfig.WAKE_WORD) + backend.contacts().map { it.displayName }.take(MAX_BIASING_NAMES)
            conversation.onContactsAvailable()
        }
    }

    /** Notification permission answered - the conversation already moved on to "anyone else?". */
    fun onNotificationPermissionHandled() {
        _uiState.update { it.copy(input = InputOptions.YesNo) }
    }

    /** Screen left or app backgrounded - never keep the mic open behind the user's back. */
    fun onScreenHidden() = stopEverything()

    // endregion

    private fun runTurn(block: suspend () -> Reply) {
        listener.stop()
        player.stop()
        turnJob?.cancel()
        _uiState.update { it.copy(phase = VoicePhase.THINKING, partialTranscript = null, level = 0f) }
        turnJob = viewModelScope.launch { handleReply(block()) }
    }

    private fun startWaitingForWakeWord() {
        wakeDeadline = System.currentTimeMillis() + WAKE_TIMEOUT_MS
        consecutiveErrors = 0
        _uiState.update { it.copy(phase = VoicePhase.WAITING_FOR_WAKE_WORD, partialTranscript = null) }
        listener.start(listOf(BuildConfig.WAKE_WORD))
    }

    private fun listenForAnswer() {
        consecutiveErrors = 0
        _uiState.update { it.copy(phase = VoicePhase.LISTENING, partialTranscript = null) }
        listener.start(biasing)
    }

    private fun handleReply(reply: Reply) {
        publishConversation()
        logMilestones(reply)
        if (reply.prompts.isEmpty()) {
            afterReply(reply)
            return
        }
        _uiState.update { it.copy(phase = VoicePhase.SPEAKING, partialTranscript = null, level = 0f) }
        player.play(reply.prompts) { afterReply(reply) }
    }

    private fun afterReply(reply: Reply) {
        when {
            reply.endSession -> setIdle()
            reply.listen -> listenForAnswer()
            !conversation.inConversation -> startWaitingForWakeWord()
            else -> setIdle()
        }
    }

    private fun onListenerEvent(event: SpeechListener.Event) {
        val phase = _uiState.value.phase
        if (phase != VoicePhase.WAITING_FOR_WAKE_WORD && phase != VoicePhase.LISTENING) return
        when (event) {
            SpeechListener.Event.Ready -> consecutiveErrors = 0
            is SpeechListener.Event.Level -> _uiState.update { it.copy(level = event.level) }
            is SpeechListener.Event.Partial ->
                if (phase == VoicePhase.LISTENING) _uiState.update { it.copy(partialTranscript = event.text) }
            is SpeechListener.Event.Results -> if (phase == VoicePhase.WAITING_FOR_WAKE_WORD) {
                val remainder = event.hypotheses.firstNotNullOfOrNull { wakeWord.find(it) }
                if (remainder != null) {
                    AppAnalytics.logVoiceScheduling("wake")
                    runTurn { conversation.onWake(remainder) }
                } else {
                    continueWaitingForWakeWord()
                }
            } else {
                runTurn { conversation.onSpeech(event.hypotheses) }
            }
            is SpeechListener.Event.Error -> onListenerError(event.kind, phase)
        }
    }

    private fun onListenerError(kind: SpeechListener.ErrorKind, phase: VoicePhase) {
        when (kind) {
            SpeechListener.ErrorKind.PERMISSION -> onMicPermissionDenied()
            SpeechListener.ErrorKind.SILENCE ->
                if (phase == VoicePhase.WAITING_FOR_WAKE_WORD) continueWaitingForWakeWord()
                else runTurn { conversation.onSilence() }
            SpeechListener.ErrorKind.BUSY, SpeechListener.ErrorKind.FATAL -> {
                consecutiveErrors++
                if (consecutiveErrors > MAX_CONSECUTIVE_ERRORS) {
                    handleReply(conversation.onMicUnavailable(permissionDenied = false))
                    return
                }
                viewModelScope.launch {
                    delay(RETRY_DELAY_MS)
                    when (_uiState.value.phase) {
                        VoicePhase.WAITING_FOR_WAKE_WORD -> listener.start(listOf(BuildConfig.WAKE_WORD))
                        VoicePhase.LISTENING -> listener.start(biasing)
                        else -> Unit
                    }
                }
            }
        }
    }

    /** Keep listening for the wake word in a loop until it's heard or [WAKE_TIMEOUT_MS] runs out. */
    private fun continueWaitingForWakeWord() {
        if (System.currentTimeMillis() < wakeDeadline) {
            listener.start(listOf(BuildConfig.WAKE_WORD))
        } else {
            listener.stop()
            AppAnalytics.logVoiceScheduling("wake_timeout")
            handleReply(conversation.onWakeTimeout())
        }
    }

    private fun stopEverything() {
        turnJob?.cancel()
        listener.stop()
        player.stop()
        setIdle()
    }

    private fun setIdle() {
        _uiState.update { it.copy(phase = VoicePhase.IDLE, partialTranscript = null, level = 0f) }
    }

    private fun publishConversation() {
        _uiState.update { it.copy(items = conversation.items, input = conversation.input) }
    }

    private fun logMilestones(reply: Reply) {
        val outcome = when {
            VoicePrompt.SAVED_ONE in reply.prompts || VoicePrompt.SAVED_MANY in reply.prompts -> "saved"
            VoicePrompt.PARTIAL_SAVE_FAILED in reply.prompts -> "partial_save_failed"
            VoicePrompt.SAVE_FAILED in reply.prompts -> "save_failed"
            VoicePrompt.NAME_CLASH in reply.prompts -> "name_clash"
            VoicePrompt.DUPLICATE_SCHEDULE in reply.prompts -> "duplicate"
            VoicePrompt.CANCELLED in reply.prompts -> "cancelled"
            VoicePrompt.CONTACT_NOT_FOUND_2 in reply.prompts -> "contact_not_found"
            VoicePrompt.LISTENING_TIMEOUT in reply.prompts -> "timeout"
            VoicePrompt.MIC_ERROR in reply.prompts -> "mic_error"
            else -> null
        }
        outcome?.let { AppAnalytics.logVoiceScheduling(it) }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        listener.release()
        player.stop()
    }

    companion object {
        private const val WAKE_TIMEOUT_MS = 60_000L
        private const val RETRY_DELAY_MS = 400L
        private const val MAX_CONSECUTIVE_ERRORS = 3
        private const val MAX_BIASING_NAMES = 300
    }
}
