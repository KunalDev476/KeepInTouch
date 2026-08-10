package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.Manifest
import android.app.Application
import android.app.AlarmManager
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.prefs.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val contactsGranted: Boolean = false,
    val notificationListenerGranted: Boolean = false,
    val postNotificationsGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val callLogGranted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val onboardingPreferences: OnboardingPreferences
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val app = getApplication<Application>()
        val contacts = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val listener = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        val postNotifications = ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exactAlarm = ContextCompat.getSystemService(app, AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        val callLog = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        _uiState.value = OnboardingUiState(
            contactsGranted = contacts,
            notificationListenerGranted = listener,
            postNotificationsGranted = postNotifications,
            exactAlarmGranted = exactAlarm,
            callLogGranted = callLog
        )
    }

    fun complete() {
        AppAnalytics.logOnboardingCompleted()
        viewModelScope.launch { onboardingPreferences.markCompleted() }
    }
}
