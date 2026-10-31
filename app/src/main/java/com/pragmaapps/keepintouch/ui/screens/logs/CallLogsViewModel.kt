package com.pragmaapps.keepintouch.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.data.notification.PhoneCallStateTracker
import com.pragmaapps.keepintouch.data.prefs.DetectionPreferences
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.usecase.CallEventDiagnostics
import com.pragmaapps.keepintouch.notification.WHATSAPP_PACKAGES
import com.pragmaapps.keepintouch.notification.WhatsAppCallListenerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OutgoingDetectionStatus(val outgoingObserved: Int, val totalEvents: Int) {
    val message: String
        get() = if (outgoingObserved == 0) {
            if (totalEvents == 0) {
                "No WhatsApp call notifications observed yet."
            } else {
                "UNCONFIRMED: 0 of $totalEvents observed call notifications looked like an outgoing call. " +
                    "It remains an open question whether WhatsApp posts a notification for a call you place yourself."
            }
        } else {
            "Observed $outgoingObserved outgoing-direction call notification(s) out of $totalEvents total."
        }
}

data class CellularDetectionStatus(
    val dialerPackage: String?,
    val cellularEventCount: Int,
    val totalEvents: Int,
    val phoneStateTracking: Boolean = false
) {
    val message: String
        get() {
            val dialerNote = dialerPackage?.let { "Resolved default dialer: $it (informational only - detection isn't gated on this)." }
                ?: "Couldn't determine this device's default dialer app (informational only - detection doesn't depend on it)."
            val outgoingTip = if (phoneStateTracking) {
                "Call duration tracking is on: an answered phone call gets its duration even if it stays on the full-screen " +
                    "call screen. An outgoing call that never shows a notification is listed as \"(unknown)\" with its " +
                    "duration - it can't be matched to a schedule. Tip: switch away from the call screen during an outgoing " +
                    "call so its notification names the person."
            } else {
                "Call duration tracking is off - allow \"Phone call duration\" in Settings. Without it, a phone call that " +
                    "stays on the full-screen call screen may get no duration. Tip: switch away (press home, or open another " +
                    "app) during the call and its notification will be picked up. WhatsApp calls don't need this."
            }
            return if (cellularEventCount == 0) {
                "$dialerNote UNCONFIRMED: no non-WhatsApp call notification has been observed yet. Regular phone calls are " +
                    "detected only from your dialer's call notification - whether it posts one, and when, varies by phone. " +
                    "Place a real cellular call to test. $outgoingTip"
            } else {
                "$dialerNote Observed $cellularEventCount non-WhatsApp call notification(s) out of $totalEvents total. $outgoingTip"
            }
        }
}

data class CallLogsUiState(
    val events: List<CallEventDiagnostics.Display> = emptyList(),
    val outgoingStatus: OutgoingDetectionStatus = OutgoingDetectionStatus(0, 0),
    val cellularStatus: CellularDetectionStatus = CellularDetectionStatus(null, 0, 0),
    val isLoading: Boolean = true
)

/** Every captured call notification episode, qualified or not, with why - moved out of
 *  Settings' old inline "Diagnostics" section into its own screen, same pattern as App Logs. */
@HiltViewModel
class CallLogsViewModel @Inject constructor(
    private val callEventRepository: CallEventRepository,
    private val scheduleRepository: ScheduleRepository,
    private val pendingMatchRepository: PendingMatchRepository,
    private val detectionPreferences: DetectionPreferences,
    private val callEventDiagnostics: CallEventDiagnostics,
    private val phoneCallStateTracker: PhoneCallStateTracker
) : ViewModel() {

    val uiState: StateFlow<CallLogsUiState> = combine(
        callEventRepository.observeAll(),
        scheduleRepository.observeAll(),
        pendingMatchRepository.observeAll(),
        detectionPreferences.settings
    ) { events, schedules, pendingMatches, detection ->
        val displays = callEventDiagnostics.buildDisplays(events, schedules, detection, pendingMatches)
        val outgoingCount = events.count { it.direction == Direction.OUTGOING }
        val dialerPackage = WhatsAppCallListenerService.resolvedDialerPackage
        // Every non-WhatsApp CallEvent is a dialer/other calling app's call notification, a call
        // seen only via the call state (PHONE_STATE_SOURCE), or a legacy row from the old
        // call-log source (tagged "android.calllog").
        val cellularCount = events.count { it.packageName !in WHATSAPP_PACKAGES }
        CallLogsUiState(
            events = displays,
            outgoingStatus = OutgoingDetectionStatus(outgoingCount, events.size),
            cellularStatus = CellularDetectionStatus(dialerPackage, cellularCount, events.size, phoneCallStateTracker.isTracking),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallLogsUiState())

    /**
     * Debugging aid: deletes every captured call episode. Pending Confirm-screen matches go too -
     * each one points at a call event, and resolving one whose event is gone would silently
     * fulfill nothing. Schedule completions are kept: they're real history, not diagnostics.
     */
    fun clearCallLogs() {
        viewModelScope.launch {
            pendingMatchRepository.clearAll()
            callEventRepository.clearAll()
        }
    }
}
