package com.pragmaapps.keepintouch.ui.screens.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Sheet operates on exactly one of these - a brand-new contact, or an existing schedule to edit. */
sealed class ScheduleSheetArgs {
    data class ForNewContact(val contactLookupKey: String) : ScheduleSheetArgs()
    data class ForExistingSchedule(val scheduleId: Long) : ScheduleSheetArgs()
}

data class ScheduleSheetUiState(
    val loading: Boolean = true,
    val isEditMode: Boolean = false,
    val contact: Contact? = null,
    val recurrence: Recurrence = Recurrence.WEEKLY,
    val nextDueDate: Long = DateMath.startOfDayMillis(System.currentTimeMillis()),
    val note: String = "",
    val saved: Boolean = false,
    val notFound: Boolean = false,
    val nameCollision: NameCollisionInfo? = null,
    /** Minutes since midnight, e.g. 10:00am = 600. Null = no specific call time set. */
    val preferredTimeMinutes: Int? = null,
    /** Window end, minutes since midnight. Only meaningful alongside [preferredTimeMinutes]. */
    val comfortableTimeEndMinutes: Int? = null
) {
    /** "Must not be missed" is derived automatically from having a call time set - not a separate manual toggle. */
    val isImportant: Boolean get() = preferredTimeMinutes != null

    /** A start time plus a window end = "comfortable to call" range rather than a single must-not-miss instant. */
    val isAvailabilityWindow: Boolean get() = preferredTimeMinutes != null && comfortableTimeEndMinutes != null
}

/**
 * Two active schedules can never share a normalized name - a call notification only ever carries
 * a name, never a phone number, so a collision would make future calls for either person
 * unmatchable (see MatchCallToScheduleUseCase). Caught here, at creation time, instead of only
 * after an ambiguous call comes in.
 */
data class NameCollisionInfo(
    val collidingWithScheduleName: String,
    val renameInput: String,
    val renaming: Boolean = false,
    val error: String? = null
)

/**
 * Not a per-destination-args Hilt assisted-injected ViewModel on purpose: the sheet is shown as a
 * dialog over the People screen rather than a separate nav destination, so there's no NavBackStack
 * entry to carry typed args. Instead the caller triggers [load] with the target once, from a
 * LaunchedEffect keyed on those args - simpler and avoids Hilt assisted-injection wiring for a
 * ViewModel with only two real dependencies.
 */
@HiltViewModel
class ScheduleSheetViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private var existingSchedule: Schedule? = null
    private var loadedFor: ScheduleSheetArgs? = null

    private val _uiState = MutableStateFlow(ScheduleSheetUiState())
    val uiState: StateFlow<ScheduleSheetUiState> = _uiState.asStateFlow()

    fun load(args: ScheduleSheetArgs) {
        if (loadedFor == args) return
        loadedFor = args
        existingSchedule = null
        _uiState.value = ScheduleSheetUiState(loading = true)
        viewModelScope.launch {
            when (args) {
                is ScheduleSheetArgs.ForNewContact -> {
                    val contact = contactRepository.findByLookupKey(args.contactLookupKey)
                    _uiState.value = ScheduleSheetUiState(loading = false, isEditMode = false, contact = contact, notFound = contact == null)
                }
                is ScheduleSheetArgs.ForExistingSchedule -> {
                    val schedule = scheduleRepository.findById(args.scheduleId)
                    existingSchedule = schedule
                    val contact = schedule?.let { contactRepository.findByLookupKey(it.contactLookupKey) }
                    _uiState.value = if (schedule == null) {
                        ScheduleSheetUiState(loading = false, notFound = true)
                    } else {
                        ScheduleSheetUiState(
                            loading = false,
                            isEditMode = true,
                            contact = contact,
                            recurrence = schedule.recurrence,
                            nextDueDate = schedule.nextDueDate,
                            note = schedule.note.orEmpty(),
                            preferredTimeMinutes = schedule.preferredTimeMinutes,
                            comfortableTimeEndMinutes = schedule.comfortableTimeEndMinutes
                        )
                    }
                }
            }
        }
    }

    fun onRecurrenceChange(recurrence: Recurrence) {
        _uiState.value = _uiState.value.copy(recurrence = recurrence)
    }

    fun onDueDateChange(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(nextDueDate = DateMath.startOfDayMillis(epochMillis))
    }

    fun onNoteChange(note: String) {
        _uiState.value = _uiState.value.copy(note = note)
    }

    /**
     * The call-time picker dialog owns the single-time/window toggle and its own "end must be
     * after start" validation (see CallTimePicker in ScheduleSheet.kt) and only ever calls this
     * once with an already-consistent pair - [end] is never non-null while [start] is null.
     */
    fun onCallTimeChange(start: Int?, end: Int?) {
        _uiState.value = _uiState.value.copy(
            preferredTimeMinutes = start,
            comfortableTimeEndMinutes = if (start != null) end else null
        )
    }

    fun save() {
        val state = _uiState.value
        val contact = state.contact ?: return

        viewModelScope.launch {
            val existing = existingSchedule
            if (existing != null) {
                scheduleRepository.update(
                    existing.copy(
                        recurrence = state.recurrence,
                        nextDueDate = state.nextDueDate,
                        note = state.note.ifBlank { null },
                        preferredTimeMinutes = state.preferredTimeMinutes,
                        isImportant = state.isImportant,
                        comfortableTimeEndMinutes = state.comfortableTimeEndMinutes
                    )
                )
                _uiState.value = _uiState.value.copy(saved = true)
                return@launch
            }

            val normalizedName = NameNormalizer.normalize(contact.displayName)
            val colliding = scheduleRepository.findActiveByNormalizedName(normalizedName)
            if (colliding.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    nameCollision = NameCollisionInfo(
                        collidingWithScheduleName = colliding.first().displayNameSnapshot,
                        renameInput = contact.displayName
                    )
                )
                return@launch
            }

            scheduleRepository.createSchedule(
                contact = contact,
                recurrence = state.recurrence,
                startDate = state.nextDueDate,
                nextDueDate = state.nextDueDate,
                note = state.note.ifBlank { null },
                preferredTimeMinutes = state.preferredTimeMinutes,
                isImportant = state.isImportant,
                comfortableTimeEndMinutes = state.comfortableTimeEndMinutes
            )
            _uiState.value = _uiState.value.copy(saved = true)
        }
    }

    fun onRenameInputChange(text: String) {
        val collision = _uiState.value.nameCollision ?: return
        _uiState.value = _uiState.value.copy(nameCollision = collision.copy(renameInput = text, error = null))
    }

    fun dismissNameCollision() {
        _uiState.value = _uiState.value.copy(nameCollision = null)
    }

    /** Called once WRITE_CONTACTS is confirmed granted - the Sheet checks/requests permission first. */
    fun confirmRename() {
        val state = _uiState.value
        val contact = state.contact ?: return
        val collision = state.nameCollision ?: return
        val newName = collision.renameInput.trim()

        if (newName.isBlank()) {
            _uiState.value = _uiState.value.copy(nameCollision = collision.copy(error = "Name can't be empty"))
            return
        }
        val normalizedNew = NameNormalizer.normalize(newName)

        _uiState.value = _uiState.value.copy(nameCollision = collision.copy(renaming = true, error = null))
        viewModelScope.launch {
            if (scheduleRepository.findActiveByNormalizedName(normalizedNew).isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    nameCollision = collision.copy(renaming = false, error = "Still not unique - try a different name")
                )
                return@launch
            }

            val renamed = contactRepository.renameContact(contact.contactId, newName)
            if (!renamed) {
                _uiState.value = _uiState.value.copy(
                    nameCollision = collision.copy(renaming = false, error = "Couldn't rename this contact")
                )
                return@launch
            }

            contactRepository.syncContacts()
            val refreshed = contactRepository.findByLookupKey(contact.lookupKey)
                ?: contact.copy(displayName = newName, normalizedName = normalizedNew)
            _uiState.value = _uiState.value.copy(contact = refreshed, nameCollision = null)
            save()
        }
    }

    /** Call after dismissing the sheet so the next open (even for the same args) starts fresh. */
    fun reset() {
        loadedFor = null
        existingSchedule = null
        _uiState.value = ScheduleSheetUiState()
    }
}
