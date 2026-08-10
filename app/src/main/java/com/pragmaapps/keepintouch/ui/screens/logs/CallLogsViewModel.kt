package com.pragmaapps.keepintouch.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class CellularDetectionStatus(val dialerPackage: String?, val cellularEventCount: Int, val totalEvents: Int) {
    val message: String
        get() {
            val dialerNote = dialerPackage?.let { "Resolved default dialer: $it (informational only - detection isn't gated on this)." }
                ?: "Couldn't determine this device's default dialer app (informational only - detection doesn't depend on it)."
            val outgoingTip = "Tip: on some phones, an outgoing call has no notification at all while you're still on the " +
                "call screen - switch away (press home, or open another app) while it's ringing or connected and it'll be " +
                "picked up. Incoming calls and WhatsApp calls don't need this."
            return if (cellularEventCount == 0) {
                "$dialerNote UNCONFIRMED: no non-WhatsApp call notification has been observed yet. Regular phone calls are " +
                    "mainly shown via the OS's own in-call screen, not necessarily a notification - whether your dialer " +
                    "posts one at all is unverified. Place a real cellular call to test; any package's call-shaped " +
                    "notification is now picked up, not just the resolved default dialer's. $outgoingTip"
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

/** Every captured call notification/call-log entry, qualified or not, with why - moved out of
 *  Settings' old inline "Diagnostics" section into its own screen, same pattern as App Logs. */
@HiltViewModel
class CallLogsViewModel @Inject constructor(
    private val callEventRepository: CallEventRepository,
    private val scheduleRepository: ScheduleRepository,
    private val pendingMatchRepository: PendingMatchRepository,
    private val detectionPreferences: DetectionPreferences,
    private val callEventDiagnostics: CallEventDiagnostics
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
        // WhatsAppCallListenerService is strictly package-gated to WHATSAPP_PACKAGES, so every
        // non-WhatsApp CallEvent is sourced from CallLogObserver (tagged CALL_LOG_SOURCE) instead.
        val cellularCount = events.count { it.packageName !in WHATSAPP_PACKAGES }
        CallLogsUiState(
            events = displays,
            outgoingStatus = OutgoingDetectionStatus(outgoingCount, events.size),
            cellularStatus = CellularDetectionStatus(dialerPackage, cellularCount, events.size),
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CallLogsUiState())
}
