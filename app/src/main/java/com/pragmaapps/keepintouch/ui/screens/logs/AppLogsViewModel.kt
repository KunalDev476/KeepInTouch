package com.pragmaapps.keepintouch.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.domain.model.LogEntry
import com.pragmaapps.keepintouch.domain.model.LogLevel
import com.pragmaapps.keepintouch.domain.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLogsUiState(
    val entries: List<LogEntry> = emptyList(),
    val selectedLevel: LogLevel? = null, // null = "All"
    val isLoading: Boolean = true
)

@HiltViewModel
class AppLogsViewModel @Inject constructor(
    private val logRepository: LogRepository
) : ViewModel() {

    private val selectedLevel = MutableStateFlow<LogLevel?>(null)

    val uiState: StateFlow<AppLogsUiState> = combine(
        logRepository.observeRecent(),
        selectedLevel
    ) { entries, level ->
        AppLogsUiState(
            entries = if (level == null) entries else entries.filter { it.level == level },
            selectedLevel = level,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLogsUiState())

    fun onLevelSelected(level: LogLevel?) {
        selectedLevel.value = level
    }

    fun clearLogs() {
        viewModelScope.launch { logRepository.clearAll() }
    }
}
