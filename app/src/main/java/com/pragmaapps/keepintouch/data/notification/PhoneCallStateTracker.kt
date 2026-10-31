package com.pragmaapps.keepintouch.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.util.CallSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PhoneCallState"

/**
 * Tracks the telephony call state (RINGING / OFFHOOK / IDLE) via READ_PHONE_STATE, purely as a
 * duration fallback for one case: a dialer call kept on the full-screen call screen, whose
 * notifications never say when it was answered or ended (see CallEpisodeProcessor.handleRemoved).
 * Every other call is timed from notifications only. Never sees a phone number:
 * the call-state API doesn't expose one.
 *
 * One callback per active SIM subscription (a default-subscription TelephonyManager only reports
 * that one SIM's calls); the per-SIM states are folded into one aggregate, and a [CallSession] is
 * emitted on [sessions] each time the aggregate returns to IDLE.
 *
 * Registered alongside ContactsChangeObserver: by WhatsAppCallListenerService, which keeps the
 * process alive in the background, and by MainActivity.onResume() in case READ_PHONE_STATE was
 * granted after the service connected. Every framework call is wrapped - a misbehaving OEM
 * telephony implementation must never take the listener or the app down.
 */
@Singleton
class PhoneCallStateTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _sessions = MutableSharedFlow<CallSession>(extraBufferCapacity = 8)
    val sessions: SharedFlow<CallSession> = _sessions.asSharedFlow()

    private val registrations = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()
    private val stateBySubscription = mutableMapOf<Int, Int>()
    private var aggregateState = TelephonyManager.CALL_STATE_IDLE

    private var sessionStartedAtMs: Long? = null
    private var ringingAtMs: Long? = null
    private var offhookAtMs: Long? = null

    /** True while call-state callbacks are registered - only then can a dialer episode wait for IDLE. */
    val isTracking: Boolean
        get() = synchronized(this) { registrations.isNotEmpty() }

    /** When the current (not yet IDLE) call session started, or null while the phone is idle. */
    val currentSessionStartedAtMs: Long?
        get() = synchronized(this) { sessionStartedAtMs }

    /** True when the current (not yet IDLE) call session started by ringing - an incoming call. */
    val currentSessionBeganRinging: Boolean
        get() = synchronized(this) {
            val ringing = ringingAtMs
            val offhook = offhookAtMs
            ringing != null && (offhook == null || ringing <= offhook)
        }

    /** True while any call is ringing or in progress. */
    val isCallActive: Boolean
        get() = synchronized(this) { aggregateState != TelephonyManager.CALL_STATE_IDLE }

    fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    fun register() {
        try {
            registerUnsafe()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to register call-state tracking - dialer call durations fall back to notifications only", t)
            unregister()
        }
    }

    @Synchronized
    private fun registerUnsafe() {
        if (registrations.isNotEmpty()) return
        if (!isPermissionGranted()) {
            AppLogger.d(TAG, "READ_PHONE_STATE not granted; dialer call durations come from notifications only")
            return
        }
        val baseManager = context.getSystemService(TelephonyManager::class.java) ?: return
        val subscriptionIds = context.getSystemService(SubscriptionManager::class.java)
            ?.activeSubscriptionInfoList
            ?.map { it.subscriptionId }
            .orEmpty()

        if (subscriptionIds.isEmpty()) {
            registerFor(baseManager, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        } else {
            subscriptionIds.forEach { subId -> registerFor(baseManager.createForSubscriptionId(subId), subId) }
        }
        AppLogger.i(TAG, "Call-state tracking registered for ${registrations.size} subscription(s)")
    }

    private fun registerFor(manager: TelephonyManager, subscriptionId: Int) {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                try {
                    onSubscriptionState(subscriptionId, state)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "Failed to handle call state $state", t)
                }
            }
        }
        manager.registerTelephonyCallback(context.mainExecutor, callback)
        registrations += manager to callback
    }

    fun unregister() {
        synchronized(this) {
            registrations.forEach { (manager, callback) ->
                try {
                    manager.unregisterTelephonyCallback(callback)
                } catch (t: Throwable) {
                    AppLogger.e(TAG, "Failed to unregister call-state callback", t)
                }
            }
            registrations.clear()
            stateBySubscription.clear()
            aggregateState = TelephonyManager.CALL_STATE_IDLE
            resetSession()
        }
    }

    @Synchronized
    private fun onSubscriptionState(subscriptionId: Int, state: Int) {
        stateBySubscription[subscriptionId] = state
        val states = stateBySubscription.values
        val aggregate = when {
            TelephonyManager.CALL_STATE_RINGING in states -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.CALL_STATE_OFFHOOK in states -> TelephonyManager.CALL_STATE_OFFHOOK
            else -> TelephonyManager.CALL_STATE_IDLE
        }
        if (aggregate == aggregateState) return
        aggregateState = aggregate

        val now = System.currentTimeMillis()
        AppLogger.d(TAG, "Call state -> ${labelFor(aggregate)} at $now")
        when (aggregate) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (sessionStartedAtMs == null) sessionStartedAtMs = now
                if (ringingAtMs == null) ringingAtMs = now
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (sessionStartedAtMs == null) sessionStartedAtMs = now
                if (offhookAtMs == null) offhookAtMs = now
            }
            else -> {
                val started = sessionStartedAtMs ?: return
                val session = CallSession(started, ringingAtMs, offhookAtMs, idleAtMs = now)
                resetSession()
                AppLogger.i(TAG, "Call session ended: ringing=${session.ringingAtMs} offhook=${session.offhookAtMs} idle=${session.idleAtMs}")
                _sessions.tryEmit(session)
            }
        }
    }

    private fun resetSession() {
        sessionStartedAtMs = null
        ringingAtMs = null
        offhookAtMs = null
    }

    private fun labelFor(state: Int): String = when (state) {
        TelephonyManager.CALL_STATE_RINGING -> "RINGING"
        TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
        else -> "IDLE"
    }
}
