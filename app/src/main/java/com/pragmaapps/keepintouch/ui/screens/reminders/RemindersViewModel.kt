package com.pragmaapps.keepintouch.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import com.pragmaapps.keepintouch.domain.repository.DigestReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RemindersUiState(val reminders: List<DigestReminderTime> = emptyList(), val isLoading: Boolean = true)

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: DigestReminderRepository
) : ViewModel() {

    val uiState: StateFlow<RemindersUiState> = repository.observeAll()
        .map { RemindersUiState(reminders = it, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemindersUiState())

    fun addReminder(hour: Int, minute: Int, label: String) {
        viewModelScope.launch { repository.add(hour, minute, label) }
    }

    fun setEnabled(reminder: DigestReminderTime, enabled: Boolean) {
        viewModelScope.launch { repository.update(reminder.copy(enabled = enabled)) }
    }

    fun updateTime(reminder: DigestReminderTime, hour: Int, minute: Int) {
        viewModelScope.launch { repository.update(reminder.copy(hour = hour, minute = minute)) }
    }

    fun delete(reminder: DigestReminderTime) {
        viewModelScope.launch { repository.delete(reminder) }
    }
}
