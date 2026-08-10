package com.pragmaapps.keepintouch.ui.screens.contactdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetailUiState(
    val loading: Boolean = true,
    val contact: Contact? = null,
    val schedule: Schedule? = null,
    val history: List<ScheduleCompletion> = emptyList(),
    val timeline: List<CallEvent> = emptyList()
) {
    val streak: Int get() = history.size

    /** Completed on or before its own due date. */
    val onTimeCount: Int get() = history.count { DateMath.startOfDayMillis(it.completedAt) <= DateMath.startOfDayMillis(it.dueDate) }

    /** Completed, but after its own due date had already passed. */
    val lateCount: Int get() = history.size - onTimeCount

    /** Expected occurrences with no completion at all - see RecurrenceEngine.missedCount(). */
    val missedCount: Int get() = schedule?.let { RecurrenceEngine.missedCount(it, history.size, System.currentTimeMillis()) } ?: 0
}

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository,
    private val completionRepository: ScheduleCompletionRepository,
    private val callEventRepository: CallEventRepository
) : ViewModel() {

    private val lookupKey = MutableStateFlow<String?>(null)
    private val contact = MutableStateFlow<Contact?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scheduleAndHistoryAndTimeline: Flow<Triple<Schedule?, List<ScheduleCompletion>, List<CallEvent>>> =
        lookupKey.filterNotNull().flatMapLatest { key ->
            scheduleRepository.observeByContactLookupKey(key).flatMapLatest { schedules ->
                val schedule = schedules.firstOrNull { it.active } ?: schedules.firstOrNull()
                if (schedule == null) {
                    flowOf(Triple(null, emptyList(), emptyList()))
                } else {
                    combine(
                        completionRepository.observeForSchedule(schedule.id),
                        callEventRepository.observeForSchedule(schedule.id)
                    ) { history, timeline -> Triple(schedule, history, timeline) }
                }
            }
        }

    val uiState: StateFlow<ContactDetailUiState> = combine(
        contact,
        scheduleAndHistoryAndTimeline
    ) { c, (schedule, history, timeline) ->
        ContactDetailUiState(loading = false, contact = c, schedule = schedule, history = history, timeline = timeline)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactDetailUiState())

    fun load(contactLookupKey: String) {
        if (lookupKey.value == contactLookupKey) return
        lookupKey.value = contactLookupKey
        viewModelScope.launch {
            contact.value = contactRepository.findByLookupKey(contactLookupKey)
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch { scheduleRepository.delete(scheduleId) }
    }

    fun setActive(schedule: Schedule, active: Boolean) {
        viewModelScope.launch { scheduleRepository.update(schedule.copy(active = active)) }
    }
}
