package com.pragmaapps.keepintouch.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion
import com.pragmaapps.keepintouch.domain.model.ScheduleStatus
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.usecase.ManuallyCompleteScheduleUseCase
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.PriorityEngine
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayItem(
    val schedule: Schedule,
    val contact: Contact?,
    val completion: ScheduleCompletion?,
    val status: ScheduleStatus,
    val lateFraction: Float
) {
    val done: Boolean get() = completion != null
}

data class TodayUiState(
    val items: List<TodayItem> = emptyList(),
    val pendingConfirmationCount: Int = 0,
    val isLoading: Boolean = true
) {
    val toCallCount: Int = items.count { !it.done }
    val doneCount: Int = items.count { it.done }
}

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val completionRepository: ScheduleCompletionRepository,
    private val contactRepository: ContactRepository,
    private val pendingMatchRepository: PendingMatchRepository,
    private val manuallyComplete: ManuallyCompleteScheduleUseCase
) : ViewModel() {

    private val todayStart = DateMath.startOfDayMillis(System.currentTimeMillis())

    /** Re-sorts every 60s while this screen is open, so priority-driven ordering creeps up live
     *  rather than only reordering on next launch (see PriorityEngine - the score itself needs no
     *  background job, but the UI still needs a reason to recompose without new DB writes). */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        scheduleRepository.observeAll(),
        completionRepository.observeAll(),
        contactRepository.observeAll(),
        pendingMatchRepository.observeUnresolved(),
        ticker
    ) { schedules, completions, contacts, pending, now ->
        val contactsByLookupKey = contacts.associateBy { it.lookupKey }
        val completionsToday = completions.filter { DateMath.startOfDayMillis(it.completedAt) == todayStart }.associateBy { it.scheduleId }
        val stillDue = schedules.filter { RecurrenceEngine.isDue(it, now) }
        val completedTodaySchedules = schedules.filter { it.id in completionsToday.keys }
        val relevant = (stillDue + completedTodaySchedules).distinctBy { it.id }

        val items = relevant
            .map { schedule ->
                TodayItem(
                    schedule = schedule,
                    contact = contactsByLookupKey[schedule.contactLookupKey],
                    completion = completionsToday[schedule.id],
                    status = RecurrenceEngine.status(schedule, now),
                    lateFraction = PriorityEngine.escalationFraction(schedule, now)
                )
            }
            .sortedWith(compareBy({ it.done }, { -PriorityEngine.score(it.schedule, now) }))

        TodayUiState(items = items, pendingConfirmationCount = pending.size, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    fun onMarkDone(item: TodayItem) {
        viewModelScope.launch { manuallyComplete(item.schedule) }
    }
}
