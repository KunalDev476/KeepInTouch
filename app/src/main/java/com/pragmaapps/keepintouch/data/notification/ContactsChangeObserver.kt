package com.pragmaapps.keepintouch.data.notification

import com.pragmaapps.keepintouch.data.logging.Breadcrumbs
import com.pragmaapps.keepintouch.data.logging.Breadcrumb
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.logging.ContactsObserverFailed
import com.pragmaapps.keepintouch.data.logging.CrashReporter
import com.pragmaapps.keepintouch.domain.usecase.SyncContactsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContactsChangeObserver"

/** Edits arrive as bursts of provider notifications (one per raw contact/data row) - sync once they settle. */
private const val DEBOUNCE_MS = 3_000L

/**
 * Re-syncs contacts (and with them, schedule name snapshots - see SyncContactsUseCase) whenever
 * the address book changes, so renaming a scheduled contact in the phone's Contacts app takes
 * effect even while this app isn't open. Registered by
 * WhatsAppCallListenerService, which keeps the process alive in the background, and by
 * MainActivity.onResume() in case READ_CONTACTS was granted after the service connected.
 */
@Singleton
class ContactsChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncContacts: SyncContactsUseCase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null
    private var pendingSync: Job? = null

    fun register() {
        try {
            registerUnsafe()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to register contacts observer - renames sync on next app open instead", t)
            CrashReporter.report(ContactsObserverFailed("register", t))
        }
    }

    private fun registerUnsafe() {
        if (observer != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            AppLogger.d(TAG, "READ_CONTACTS not granted; not observing contact changes")
            return
        }
        val newObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pendingSync?.cancel()
                pendingSync = scope.launch {
                    delay(DEBOUNCE_MS)
                    try {
                        val refreshed = syncContacts()
                        Breadcrumbs.log(Breadcrumb.CONTACTS_CHANGED, "snapshots_refreshed" to refreshed)
                        if (refreshed > 0) AppLogger.i(TAG, "Contacts changed; refreshed $refreshed schedule snapshot(s)")
                    } catch (t: Throwable) {
                        AppLogger.e(TAG, "Failed to sync after contacts change", t)
                        CrashReporter.report(ContactsObserverFailed("sync", t))
                    }
                }
            }
        }
        context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, newObserver)
        observer = newObserver
        AppLogger.i(TAG, "Contacts observer registered")
        Breadcrumbs.log(Breadcrumb.CONTACTS_OBSERVER, "registered" to true)
    }

    fun unregister() {
        try {
            observer?.let { context.contentResolver.unregisterContentObserver(it) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to unregister contacts observer", t)
            CrashReporter.report(ContactsObserverFailed("unregister", t))
        }
        if (observer != null) Breadcrumbs.log(Breadcrumb.CONTACTS_OBSERVER, "registered" to false)
        observer = null
        pendingSync?.cancel()
        pendingSync = null
    }
}
