package com.pragmaapps.keepintouch.ui.screens.people

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PeopleSegment { SCHEDULED, ALL }

data class ScheduledPersonRow(val schedule: Schedule, val contact: Contact?)
data class AllContactRow(val contact: Contact, val schedule: Schedule?)

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
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {

    private val segment = MutableStateFlow(PeopleSegment.SCHEDULED)
    private val query = MutableStateFlow("")
    private val syncing = MutableStateFlow(false)
    private val permissionGranted = MutableStateFlow(checkPermission())

    val uiState: StateFlow<PeopleUiState> = combine(
        contactRepository.observeAll(),
        scheduleRepository.observeAll(),
        segment,
        query
    ) { contacts, schedules, seg, q ->
        buildState(contacts, schedules, seg, q)
    }
        .combine(syncing) { state, isSyncing -> state.copy(syncing = isSyncing) }
        .combine(permissionGranted) { state, granted -> state.copy(contactsPermissionGranted = granted) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PeopleUiState())

    private fun buildState(
        contacts: List<Contact>,
        schedules: List<Schedule>,
        segment: PeopleSegment,
        query: String
    ): PeopleUiState {
        val contactsByLookupKey = contacts.associateBy { it.lookupKey }
        val activeSchedulesByLookupKey = schedules.filter { it.active }.associateBy { it.contactLookupKey }

        val filteredContacts = if (query.isBlank()) contacts else contacts.filter { it.displayName.contains(query, ignoreCase = true) }

        val scheduledRows = schedules
            .filter { it.active }
            .filter { query.isBlank() || it.displayNameSnapshot.contains(query, ignoreCase = true) }
            .map { schedule ->
                ScheduledPersonRow(
                    schedule = schedule,
                    contact = contactsByLookupKey[schedule.contactLookupKey]
                )
            }
            .sortedBy { it.schedule.nextDueDate }

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
