package com.pragmaapps.keepintouch.ui.screens.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.usecase.ResolvePendingMatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PendingMatchDisplay(
    val pendingMatch: PendingMatch,
    val callEvent: CallEvent?,
    val candidateSchedules: List<Schedule>,
    val candidateContacts: List<Contact>,
    /** Pre-highlighted candidate whose learned avatar hash matches this call's - still requires an explicit confirm tap. */
    val suggestedScheduleId: Long?
)

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val pendingMatchRepository: PendingMatchRepository,
    private val callEventRepository: CallEventRepository,
    private val scheduleRepository: ScheduleRepository,
    private val contactRepository: ContactRepository,
    private val resolvePendingMatch: ResolvePendingMatchUseCase
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: StateFlow<List<PendingMatchDisplay>> = pendingMatchRepository.observeUnresolved()
        .mapLatest { pendingList -> pendingList.map { buildDisplay(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private suspend fun buildDisplay(pending: PendingMatch): PendingMatchDisplay {
        val callEvent = callEventRepository.findById(pending.callEventId)
        val schedules = if (pending.candidateScheduleIds.isNotEmpty()) scheduleRepository.findByIds(pending.candidateScheduleIds) else emptyList()
        val contacts = if (pending.candidateContactIds.isNotEmpty()) contactRepository.findByIds(pending.candidateContactIds) else emptyList()

        var suggestedScheduleId: Long? = null
        if (callEvent?.avatarHash != null && schedules.isNotEmpty()) {
            for (schedule in schedules) {
                val contact = contactRepository.findByLookupKey(schedule.contactLookupKey)
                if (contact?.learnedAvatarHash != null && contact.learnedAvatarHash == callEvent.avatarHash) {
                    suggestedScheduleId = schedule.id
                    break
                }
            }
        }

        return PendingMatchDisplay(pending, callEvent, schedules, contacts, suggestedScheduleId)
    }

    fun confirmSchedule(pendingMatchId: Long, chosenScheduleId: Long?) {
        viewModelScope.launch { resolvePendingMatch(pendingMatchId, chosenScheduleId) }
    }

    /** Case (b): user declined to create a schedule right now for the recognized contact. */
    fun dismiss(pendingMatchId: Long) {
        viewModelScope.launch {
            val pending = pendingMatchRepository.findById(pendingMatchId) ?: return@launch
            pendingMatchRepository.update(pending.copy(resolvedAt = System.currentTimeMillis(), resolvedScheduleId = null))
        }
    }
}
