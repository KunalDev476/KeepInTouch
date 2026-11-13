package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.data.logging.AppAnalytics
import com.pragmaapps.keepintouch.data.logging.Breadcrumb
import com.pragmaapps.keepintouch.data.logging.Breadcrumbs
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.data.permissions.CallScreeningSource
import com.pragmaapps.keepintouch.data.permissions.OptionalPermission
import com.pragmaapps.keepintouch.data.prefs.OnboardingPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val granted: Set<AppPermission> = emptySet(),
    val callScreeningGranted: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val onboardingPreferences: OnboardingPreferences
) : AndroidViewModel(application) {

    /**
     * Only on phones outside DeviceVendor's verified list. Decided once, so the page count can't
     * change mid-onboarding.
     */
    val showCallScreeningPage: Boolean = AppPermissions.shouldOfferCallScreening()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val context = getApplication<Application>()
        _uiState.value = OnboardingUiState(
            granted = AppPermissions.granted(context),
            callScreeningGranted = AppPermissions.isGranted(context, OptionalPermission.CALL_SCREENING)
        )
    }

    fun complete() {
        AppAnalytics.logOnboardingCompleted()
        Breadcrumbs.log(Breadcrumb.ONBOARDING_COMPLETED, "call_screening_page" to showCallScreeningPage)
        if (showCallScreeningPage) {
            val granted = _uiState.value.callScreeningGranted
            AppAnalytics.logCallScreeningOffer(source = CallScreeningSource.ONBOARDING, granted = granted)
            Breadcrumbs.log(Breadcrumb.CALL_SCREENING_RESULT, "source" to CallScreeningSource.ONBOARDING, "granted" to granted)
            onboardingPreferences.markCallScreeningOffered()
        }
        viewModelScope.launch { onboardingPreferences.markCompleted() }
    }
}
