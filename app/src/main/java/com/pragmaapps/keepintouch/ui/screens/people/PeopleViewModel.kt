package com.pragmaapps.keepintouch.ui.screens.people

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.domain.util.SearchMatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PeopleSegment { SCHEDULED, ALL }

/**
 * [callStatCount]/[callStatMonthName] = every qualifying call to this contact - scheduled and
 * unscheduled combined, same two counts Contact Detail shows as separate stat tiles, added
 * together here - in whichever month the chip is reporting on: the current month while the
 * schedule is active, or the most recent month that actually had a call once it's paused (so the
 * number doesn't just go stale at "0 this month" the instant you pause it - see
 * PeopleViewModel.callStatFor). [callStatMonthName] is null only when this contact has never had
 * a single qualifying call - "No calls yet" rather than referencing any specific month.
 * [completed] = a ONE_TIME schedule that was fulfilled (shown as "Completed").
 * Every schedule shows here regardless of [Schedule.active] - Scheduled is a browsable list of
 * every contact you've ever set up, active, paused, or completed; only the Today screen filters
 * to what's actually due. A schedule that's inactive and NOT [completed] reads as "Paused" - see
 * PeopleScreen.ScheduledList.
 */
data class ScheduledPersonRow(
    val schedule: Schedule,
    val contact: Contact?,
    val callStatCount: Int,
    val callStatMonthName: String?,
    val callStatIsCurrentMonth: Boolean,
    val completed: Boolean
)
data class AllContactRow(val contact: Contact, val schedule: Schedule?)

/**
 * Pure, so it's unit-testable without a ViewModel/Hilt harness - see PeopleViewModelCallStatTest.
 * [qualifiedEvents] is already pre-filtered to this one schedule's normalized name.
 *
 * An active schedule reports the current calendar month, even if that's 0 so far - it's the
 * live, ongoing window. A paused/completed one instead reports whichever past month most recently
 * had an actual call, so the number doesn't just read "0 this month" forever the moment you pause
 * it - see PeopleViewModel.buildState. Returns (count, monthName), monthName null only when
 * [qualifiedEvents] is empty (no calls ever, nothing to report a month for).
 */
fun callStatFor(schedule: Schedule, qualifiedEvents: List<CallEvent>, now: Long): Pair<Int, String?> {
    if (schedule.active) {
        val currentMonthStart = DateMath.startOfMonthMillis(now)
        return qualifiedEvents.count { it.postTime >= currentMonthStart } to DateMath.monthName(now)
    }

    val lastCallAt = qualifiedEvents.maxOfOrNull { it.postTime } ?: return 0 to null
    val lastMonthStart = DateMath.startOfMonthMillis(lastCallAt)
    val nextMonthStart = DateMath.plusMonths(lastMonthStart, 1)
    val count = qualifiedEvents.count { it.postTime >= lastMonthStart && it.postTime < nextMonthStart }
    return count to DateMath.monthName(lastCallAt)
}

data class PeopleUiState(
    val segment: PeopleSegment = PeopleSegment.SCHEDULED,
    val query: String = "",
    val contactsPermissionGranted: Boolean = false,
    val syncing: Boolean = false,
    val scheduledRows: List<ScheduledPersonRow> = emptyList(),
    val allContactRows: List<AllContactRow> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository,
    private val callEventRepository: CallEventRepository,
    private val completionRepository: ScheduleCompletionRepository
) : ViewModel() {

    private val segment = MutableStateFlow(PeopleSegment.SCHEDULED)
    private val query = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(checkPermission())

    // Folded together so the main combine() below stays within the 5-flow typed overload.
    private val segmentAndQuery: Flow<Pair<PeopleSegment, String>> = combine(segment, query) { s, q -> s to q }

    val uiState: StateFlow<PeopleUiState> = combine(
        contactRepository.observeAll(),
        scheduleRepository.observeAll(),
        callEventRepository.observeAll(),
        completionRepository.observeAll(),
        segmentAndQuery
    ) { contacts, schedules, callEvents, completions, (seg, q) ->
        val completedScheduleIds = completions.mapTo(mutableSetOf()) { it.scheduleId }
        buildState(contacts, schedules, callEvents, completedScheduleIds, seg, q)
    }
        .combine(syncing) { state, isSyncing -> state.copy(syncing = isSyncing) }
        .combine(permissionGranted) { state, granted -> state.copy(contactsPermissionGranted = granted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleUiState())

    private fun buildState(
        contacts: List<Contact>,
        schedules: List<Schedule>,
        callEvents: List<CallEvent>,
        completedScheduleIds: Set<Long>,
        segment: PeopleSegment,
        query: String
    ): PeopleUiState {
        val contactsByLookupKey = contacts.associateBy { it.lookupKey }
        val activeSchedulesByLookupKey = schedules.filter { it.active }.associateBy { it.contactLookupKey }

        // Pure digits => the user is typing a phone number, so search saved numbers instead of
        // names; anything with a letter (including a letters+digits mix) keeps the existing
        // name-prefix search untouched.
        val isNumberQuery = SearchMatcher.isNumberQuery(query)
        val filteredContacts = contacts.filter {
            if (isNumberQuery) SearchMatcher.matchesNumber(it.primaryNumber, query) else SearchMatcher.matches(it.displayName, query)
        }

        // By normalized caller name, not matchedScheduleId - matchedScheduleId is only set for
        // calls MatchCallToScheduleUseCase could confidently pin to this exact schedule, which
        // undercounts relative to Contact Detail's own "Scheduled calls" + "Unscheduled calls"
        // tiles (the latter counts every qualifying call matching the contact by name, whether or
        // not it ever got attributed to a schedule id - see ContactDetailViewModel.callCounts).
        // Matching on the schedule's own normalizedNameSnapshot instead reproduces that same
        // combined total without needing a live Contact lookup.
        val qualifiedEventsByNormalizedName = callEvents
            .filter { it.qualified }
            .groupBy { NameNormalizer.normalize(it.notificationName) }
        val now = System.currentTimeMillis()

        // Never filtered by active - pausing a schedule (Contact Detail's Pause/Resume toggle)
        // should only hide it from Today (which does filter on active, via RecurrenceEngine.isDue),
        // not remove it from this browsable list; resuming it just needs to flip active back, no
        // special-casing here. A fulfilled ONE_TIME schedule also deactivates (nothing left to
        // repeat - see RecurrenceEngine.advanceAfterCompletion) but reads as "Completed" rather
        // than "Paused" since it has a real completion behind it.
        // Not filtered by the search query - the search bar is only shown on All contacts.
        val scheduledRows = schedules
            .map { schedule ->
                val (statCount, statMonthName) = callStatFor(
                    schedule = schedule,
                    qualifiedEvents = qualifiedEventsByNormalizedName[schedule.normalizedNameSnapshot].orEmpty(),
                    now = now
                )
                ScheduledPersonRow(
                    schedule = schedule,
                    contact = contactsByLookupKey[schedule.contactLookupKey],
                    callStatCount = statCount,
                    callStatMonthName = statMonthName,
                    callStatIsCurrentMonth = statMonthName != null && statMonthName == DateMath.monthName(now),
                    completed = schedule.recurrence == Recurrence.ONE_TIME && !schedule.active && schedule.id in completedScheduleIds
                )
            }
            .sortedWith(compareBy({ !it.schedule.active }, { it.schedule.nextDueDate }))

        val allContactRows = filteredContacts
            .map { contact -> AllContactRow(contact, activeSchedulesByLookupKey[contact.lookupKey]) }
            .sortedBy { it.contact.displayName.lowercase() }

        return PeopleUiState(
            segment = segment,
            query = query,
            scheduledRows = scheduledRows,
            allContactRows = allContactRows,
            isLoading = false
        )
    }

    private fun checkPermission(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    fun onSegmentChange(newSegment: PeopleSegment) {
        // Drop any typed search on switch, so returning to All contacts starts unfiltered
        // rather than silently keeping a query whose search bar was hidden.
        if (newSegment != segment.value) query.value = ""
        segment.value = newSegment
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /** Call from onResume so returning from the permission dialog/Settings refreshes state immediately. */
    fun refreshPermissionState() {
        val granted = checkPermission()
        permissionGranted.value = granted
        if (granted) {
            syncContacts()
        }
    }

    fun syncContacts() {
        viewModelScope.launch {
            syncing.value = true
            try {
                contactRepository.syncContacts()
            } finally {
                syncing.value = false
            }
        }
    }
}
