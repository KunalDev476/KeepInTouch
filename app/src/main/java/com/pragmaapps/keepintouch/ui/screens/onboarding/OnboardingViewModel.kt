package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.data.prefs.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(val granted: Set<AppPermission> = emptySet())

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
        _uiState.value = OnboardingUiState(granted = AppPermissions.granted(getApplication()))
    }

    fun complete() {
        AppAnalytics.logOnboardingCompleted()
        viewModelScope.launch { onboardingPreferences.markCompleted() }
    }
}
