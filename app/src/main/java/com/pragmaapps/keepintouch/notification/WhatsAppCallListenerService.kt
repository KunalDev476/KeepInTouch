package com.pragmaapps.keepintouch.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.notification.CallEpisodeProcessor
import com.pragmaapps.keepintouch.data.notification.CallLogObserver
import com.pragmaapps.keepintouch.data.notification.ContactsChangeObserver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "WACallListener"

/** WhatsApp's two known packages - the ONLY packages this listener ever acts on. Shared with SettingsViewModel. */
val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")

/**
 * Strictly WhatsApp-scoped: every callback bails immediately for any package outside
 * [WHATSAPP_PACKAGES], before even checking whether a notification looks call-shaped. This
 * listener has system-wide visibility once granted (Android delivers every app's notifications
 * to any connected NotificationListenerService), so the package check is what keeps this app's
 * actual behavior narrow regardless of that visibility - by design, no fallback for other apps'
 * call notifications.
 *
 * Cellular (non-WhatsApp) call detection lives entirely in CallLogObserver instead - reading the
 * OS's own call log content provider once READ_CALL_LOG is granted, not notifications at all.
 * That's also what makes it OEM-agnostic for free: every OEM's dialer/in-call-UI package name is
 * different, but they all write to the same standard call log, so there's no per-OEM package list
 * to maintain. If READ_CALL_LOG isn't granted (or gets revoked later - Android always allows
 * that), cellular detection simply doesn't run; see SettingsScreen's call log permission card for
 * the user-facing warning instead of a silent notification-based fallback.
 */
@AndroidEntryPoint
class WhatsAppCallListenerService : NotificationListenerService() {

    @Inject lateinit var processor: CallEpisodeProcessor
    @Inject lateinit var callLogObserver: CallLogObserver
    @Inject lateinit var contactsChangeObserver: ContactsChangeObserver

    private lateinit var scope: CoroutineScope
    private var defaultDialerPackage: String? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        defaultDialerPackage = resolveDefaultDialerPackage()
        resolvedDialerPackage = defaultDialerPackage
        AppLogger.i(TAG, "Notification listener connected; default dialer package=$defaultDialerPackage")
        callLogObserver.register()
        contactsChangeObserver.register()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        AppLogger.i(TAG, "Notification listener disconnected")
        callLogObserver.unregister()
        contactsChangeObserver.unregister()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        if (!looksLikeCallNotification(sbn)) return
        AppLogger.d(TAG, "Call-shaped notification posted from ${sbn.packageName} (key=${sbn.key})")
        scope.launch { processor.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        // Not gated on looksLikeCallNotification() - if an episode was tracked for this key on
        // post, it must still be finalized even if some fields look different on removal.
        // CallEpisodeProcessor.onRemoved() is a no-op for any key it never saw a matching
        // onPosted() for, so this is safe to call for every WhatsApp notification removal.
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
     * Category=="call" is the documented CallStyle signal, but we also accept the presence of the
     * android.callType extra as a fallback - unverified whether every source reliably sets the
     * category, so both are measured rather than assuming just one.
     */
    private fun looksLikeCallNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val hasCallCategory = notification.category == Notification.CATEGORY_CALL
        val hasCallTypeExtra = notification.extras?.containsKey(Notification.EXTRA_CALL_TYPE) == true
        return hasCallCategory || hasCallTypeExtra
    }

    override fun onDestroy() {
        super.onDestroy()
        callLogObserver.unregister()
        contactsChangeObserver.unregister()
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
