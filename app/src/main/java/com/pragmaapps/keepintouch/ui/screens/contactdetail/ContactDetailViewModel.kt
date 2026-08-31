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
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
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
    val timeline: List<CallEvent> = emptyList(),
    /** Connected/qualified calls matched to one of this contact's schedules (current or past). */
    val scheduledCallCount: Int = 0,
    /** Connected/qualified calls to this contact that never matched a schedule (e.g. before one existed, or after it was deleted). */
    val unscheduledCallCount: Int = 0
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

    /**
     * [schedules] is every schedule this contact ever had (not just the shown one). [allCompletions]
     * spans all of those schedules too - each completion is, by construction, the single call that
     * fulfilled one due occurrence (see ProcessFinalizedCallUseCase.tryFulfill / RecurrenceEngine.isDue:
     * only the first qualifying call while a schedule is still due records a completion), so its size
     * is exactly "how many occurrences were actually kept," independent of how many extra qualifying
     * calls happened around it.
     */
    private data class ScheduleContext(
        val schedule: Schedule?,
        val schedules: List<Schedule>,
        val history: List<ScheduleCompletion>,
        val allCompletions: List<ScheduleCompletion>,
        val timeline: List<CallEvent>
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val scheduleContext: Flow<ScheduleContext> =
        lookupKey.filterNotNull().flatMapLatest { key ->
            scheduleRepository.observeByContactLookupKey(key).flatMapLatest { schedules ->
                val schedule = schedules.firstOrNull { it.active } ?: schedules.firstOrNull()
                if (schedule == null) {
                    flowOf(ScheduleContext(null, schedules, emptyList(), emptyList(), emptyList()))
                } else {
                    combine(
                        completionRepository.observeForSchedule(schedule.id),
                        completionRepository.observeForSchedules(schedules.map { it.id }),
                        callEventRepository.observeForSchedule(schedule.id)
                    ) { history, allCompletions, timeline -> ScheduleContext(schedule, schedules, history, allCompletions, timeline) }
                }
            }
        }

    val uiState: StateFlow<ContactDetailUiState> = combine(
        contact,
        scheduleContext,
        callEventRepository.observeAll()
    ) { c, ctx, allEvents ->
        val counts = c?.let { callCounts(it, ctx.allCompletions, allEvents) }
        ContactDetailUiState(
            loading = false,
            contact = c,
            schedule = ctx.schedule,
            history = ctx.history,
            timeline = ctx.timeline,
            scheduledCallCount = counts?.first ?: 0,
            unscheduledCallCount = counts?.second ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ContactDetailUiState())

    /**
     * "Scheduled" = the one call per due occurrence that actually fulfilled it (a completion's own
     * callEventId) - so on a day with several connected calls to the same contact, only the first
     * one that landed while the schedule was due counts as scheduled; every later call that day is
     * "unscheduled," same as a call outside any due window. "Unscheduled" is otherwise identified
     * the same way live matching works everywhere else in the app: normalized caller name against
     * the contact's own normalized name (see NameNormalizer, MatchCallToScheduleUseCase). Only
     * qualified (connected, duration-threshold-met) calls count - see QualifyingCallRule.
     */
    private fun callCounts(contact: Contact, allCompletions: List<ScheduleCompletion>, allEvents: List<CallEvent>): Pair<Int, Int> {
        val completingEventIds = allCompletions.mapNotNull { it.callEventId }.toSet()
        val qualifiedEvents = allEvents.filter { it.qualified }
        val scheduled = allCompletions.size
        val unscheduled = qualifiedEvents.count { event ->
            event.id !in completingEventIds && NameNormalizer.normalize(event.notificationName) == contact.normalizedName
        }
        return scheduled to unscheduled
    }

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
