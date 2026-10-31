package com.pragmaapps.keepintouch.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.notification.CallEpisodeProcessor
import com.pragmaapps.keepintouch.data.notification.ContactsChangeObserver
import com.pragmaapps.keepintouch.data.notification.PhoneCallStateTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WACallListener"

/** WhatsApp's two known packages. Shared with CallLogsViewModel. */
val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Known OEM dialer / in-call UI packages that post the notification for a regular cellular call.
 * The package that posts it isn't necessarily the default dialer role holder - confirmed on a real
 * device: com.android.incallui posts it while the default dialer is a different package - so the
 * resolved default dialer is checked in addition to this list, not instead of it.
 */
val KNOWN_DIALER_PACKAGES = setOf(
    "com.android.incallui",
    "com.google.android.dialer",
    "com.samsung.android.incallui",
    "com.samsung.android.dialer",
    "com.android.dialer",
    "com.android.phone",
    "com.oplus.dialer"
)

/** A package that posts regular cellular call notifications: a known OEM in-call UI, or the resolved default dialer. */
fun isCellularDialerPackage(packageName: String): Boolean =
    packageName in KNOWN_DIALER_PACKAGES || packageName == WhatsAppCallListenerService.resolvedDialerPackage

/**
 * Detects both WhatsApp and regular cellular calls from call notifications - there is no call log
 * access. Android delivers every app's notifications to a connected NotificationListenerService,
 * so [isCallNotification] is what keeps this narrow: only call-shaped notifications are ever
 * processed, everything else (message notifications, missed-call notifications, this app's own)
 * is ignored without being read.
 *
 * On some OEMs the dialer posts nothing while its full-screen call screen is in the foreground
 * (confirmed on a real device: ColorOS's com.android.incallui removes the ringing notification at
 * answer and posts no ongoing-call notification until the call screen is backgrounded). A dialer
 * call's timing therefore also comes from [PhoneCallStateTracker] when READ_PHONE_STATE is granted
 * - its ended sessions are forwarded to [CallEpisodeProcessor.onCallSessionEnded] from here. An
 * outgoing call that never posts any notification still can't be named, only timed.
 *
 * The class keeps its original name on purpose: the user's notification access grant is tied to
 * this component name, so renaming it would silently revoke access on update.
 */
@AndroidEntryPoint
class WhatsAppCallListenerService : NotificationListenerService() {

    @Inject lateinit var processor: CallEpisodeProcessor
    @Inject lateinit var contactsChangeObserver: ContactsChangeObserver
    @Inject lateinit var phoneCallStateTracker: PhoneCallStateTracker

    private lateinit var scope: CoroutineScope
    private var callSessionsJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        resolvedDialerPackage = resolveDefaultDialerPackage()
        AppLogger.i(TAG, "Notification listener connected; default dialer package=$resolvedDialerPackage")
        contactsChangeObserver.register()
        phoneCallStateTracker.register()
        callSessionsJob?.cancel()
        callSessionsJob = scope.launch {
            phoneCallStateTracker.sessions.collect { processor.onCallSessionEnded(it) }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        AppLogger.i(TAG, "Notification listener disconnected")
        contactsChangeObserver.unregister()
        phoneCallStateTracker.unregister()
        callSessionsJob?.cancel()
        callSessionsJob = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isCallNotification(sbn)) return
        AppLogger.d(TAG, "Call-shaped notification posted from ${sbn.packageName} (key=${sbn.key})")
        scope.launch { processor.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Not gated on isCallNotification() for WhatsApp/dialer packages - if an episode was
        // tracked for this key on post, it must still be finalized even if some fields look
        // different on removal. CallEpisodeProcessor.onRemoved() is a no-op for any key it never
        // saw a matching onPosted() for. Any other package only gets through if the removed
        // notification itself is call-shaped, so unrelated apps' removals never reach the processor.
        if (sbn.packageName !in WHATSAPP_PACKAGES && !isCellularDialerPackage(sbn.packageName) && !isCallNotification(sbn)) return
        scope.launch { processor.onRemoved(sbn) }
    }

    /**
     * TelecomManager.getDefaultDialerPackage() is a plain public query (which app currently
     * handles calls) - it needs no dangerous permission. Wrapped defensively anyway: this is a
     * new, unverified path and must never take the listener down if a given OEM's TelecomManager
     * implementation misbehaves.
     */
    private fun resolveDefaultDialerPackage(): String? = try {
        (getSystemService(TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Could not resolve default dialer package", t)
        null
    }

    /**
     * Any package: category=="call" (the documented CallStyle signal) or the android.callType
     * extra - unverified whether every source reliably sets the category, so both are accepted.
     *
     * Dialer packages only ([isCellularDialerPackage]): also a running call timer (showChronometer) or a
     * full-screen intent. Some in-call UIs (confirmed on a real device: com.android.incallui) never
     * set android.callType, and Android's own InCallService sample notification sets neither the
     * category nor CallStyle - but it does use a full-screen intent for ringing, and a connected
     * call shows a chronometer. Kept to dialer packages so an arbitrary app's timer notification
     * (a stopwatch, a recording) can never be mistaken for a call.
     *
     * Missed-call notifications are never a call in progress, and this app's own notifications are
     * never a call at all - both are excluded up front.
     */
    private fun isCallNotification(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName) return false
        val notification = sbn.notification ?: return false
        if (notification.category == Notification.CATEGORY_MISSED_CALL) return false
        val hasCallCategory = notification.category == Notification.CATEGORY_CALL
        val hasCallTypeExtra = notification.extras?.containsKey(Notification.EXTRA_CALL_TYPE) == true
        if (hasCallCategory || hasCallTypeExtra) return true
        if (!isCellularDialerPackage(sbn.packageName)) return false
        val showsChronometer = notification.extras?.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) == true
        return showsChronometer || notification.fullScreenIntent != null
    }

    override fun onDestroy() {
        super.onDestroy()
        contactsChangeObserver.unregister()
        phoneCallStateTracker.unregister()
        scope.cancel()
    }

    companion object {
        /** Cheap in-process signal for Diagnostics; the authoritative grant-state check is NotificationManagerCompat.getEnabledListenerPackages(). */
        @Volatile
        var isConnected: Boolean = false
            private set

        /** The package name resolved as the device's default dialer, for Diagnostics - null until the listener has connected at least once, or if resolution failed. */
        @Volatile
        var resolvedDialerPackage: String? = null
            private set
    }
}
