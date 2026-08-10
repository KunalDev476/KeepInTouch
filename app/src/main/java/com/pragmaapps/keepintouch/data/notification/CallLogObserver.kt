package com.pragmaapps.keepintouch.data.notification

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.core.database.getStringOrNull
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.prefs.DetectionPreferences
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.usecase.ProcessFinalizedCallUseCase
import com.pragmaapps.keepintouch.domain.util.QualifyingCallRule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallLogObserver"
private const val PREFS_NAME = "call_log_observer_prefs"
private const val KEY_LAST_PROCESSED_ID = "last_processed_call_log_id"

/**
 * ColorOS/Oplus devices write their own proprietary CallLog.Calls.TYPE codes instead of (or in
 * addition to) the standard AOSP ones - confirmed on a real device via CallLogObserver's own
 * logging: an outgoing call logged type=100, an incoming call logged type=101. Not documented
 * anywhere; these are empirical, not from any Oplus API surface.
 */
private const val OPLUS_OUTGOING_TYPE = 100
private const val OPLUS_INCOMING_TYPE = 101

/** Synthetic "packageName" tag for CallEvent rows sourced from the call log rather than a notification. */
const val CALL_LOG_SOURCE = "android.calllog"

private data class CallLogRow(
    val id: Long,
    val number: String?,
    val cachedName: String?,
    val type: Int,
    val date: Long,
    val durationSeconds: Long
)

/**
 * Authoritative source for non-WhatsApp call detection: type (incoming/outgoing/missed) and
 * duration come straight from the OS's own call log entry, written the instant a call ends -
 * regardless of whether any notification for it ever existed, or what was foregrounded. This is
 * what actually fixes outgoing cellular calls: on some OEMs, no notification is posted for an
 * outgoing call at all until the call screen is backgrounded (confirmed on a real device), so
 * notification-based detection has nothing to observe in that window. The call log has no such
 * gap - it's written after the call regardless.
 *
 * Requires READ_CALL_LOG. When it isn't granted, WhatsAppCallListenerService's notification-
 * heuristic path (showChronometer/actionCount/statusText) is used as a fallback for cellular
 * calls instead - see its isTrackedForFallback logic. WhatsApp itself never appears in the call
 * log, so its detection is completely unaffected either way.
 */
@Singleton
class CallLogObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callEventRepository: CallEventRepository,
    private val processFinalizedCall: ProcessFinalizedCallUseCase,
    private val detectionPreferences: DetectionPreferences
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: ContentObserver? = null

    fun isPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    /**
     * Called from MainActivity.onResume() on every single app open (see its doc for why) - a
     * misbehaving OEM CallLog provider throwing anything uncaught here would crash the app on
     * every launch, which is exactly as bad as it sounds. Every framework call in this function
     * is wrapped defensively for that reason, same as everything else in this app that touches an
     * OEM-controlled content provider.
     */
    fun register() {
        try {
            registerUnsafe()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to register call log observer - cellular calls fall back to notification-based detection", t)
        }
    }

    private fun registerUnsafe() {
        if (observer != null) return
        if (!isPermissionGranted()) {
            AppLogger.d(TAG, "READ_CALL_LOG not granted; cellular calls fall back to notification-based detection")
            return
        }

        // First-ever run: baseline to whatever's already in the log, never retroactively process
        // call history from before the app started watching.
        if (!prefs.contains(KEY_LAST_PROCESSED_ID)) {
            prefs.edit().putLong(KEY_LAST_PROCESSED_ID, latestRowId() ?: 0L).apply()
        }

        val newObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scope.launch {
                    try {
                        processNewEntries()
                    } catch (t: Throwable) {
                        AppLogger.e(TAG, "Failed to process call log change", t)
                    }
                }
            }
        }
        context.contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, newObserver)
        observer = newObserver
        AppLogger.i(TAG, "Call log observer registered")
        scope.launch {
            try {
                processNewEntries() // catch anything logged between app starts
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed initial call log catch-up scan", t)
            }
        }
    }

    fun unregister() {
        try {
            observer?.let { context.contentResolver.unregisterContentObserver(it) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to unregister call log observer", t)
        }
        observer = null
    }

    /**
     * Appending "LIMIT 1" onto a sortOrder string is an old, undocumented trick that only ever
     * worked by accident on a few providers - confirmed on a real device that CallLog's provider
     * validates sort order properly and throws IllegalArgumentException("Invalid token LIMIT") on
     * it. QUERY_ARG_SQL_SORT_ORDER + QUERY_ARG_LIMIT (the Bundle-based query overload, API 26+) is
     * the actual, documented way to do this - every standard content provider must honor it.
     */
    private fun latestRowId(): Long? {
        val queryArgs = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${CallLog.Calls._ID} DESC")
            putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
        }
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID),
            queryArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private suspend fun processNewEntries() {
        if (!isPermissionGranted()) return
        val lastProcessed = prefs.getLong(KEY_LAST_PROCESSED_ID, 0L)
        val newRows = queryRowsNewerThan(lastProcessed)
        for (row in newRows) {
            try {
                processRow(row)
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to process call log row id=${row.id}", t)
            }
            prefs.edit().putLong(KEY_LAST_PROCESSED_ID, row.id).apply()
        }
    }

    private fun queryRowsNewerThan(lastId: Long): List<CallLogRow> {
        val rows = mutableListOf<CallLogRow>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            "${CallLog.Calls._ID} > ?",
            arrayOf(lastId.toString()),
            "${CallLog.Calls._ID} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val numberCol = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val nameCol = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationCol = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            while (cursor.moveToNext()) {
                rows.add(
                    CallLogRow(
                        id = cursor.getLong(idCol),
                        number = cursor.getStringOrNull(numberCol),
                        cachedName = cursor.getStringOrNull(nameCol),
                        type = cursor.getInt(typeCol),
                        date = cursor.getLong(dateCol),
                        durationSeconds = cursor.getLong(durationCol)
                    )
                )
            }
        }
        return rows
    }

    private suspend fun processRow(row: CallLogRow) {
        val direction = when (row.type) {
            CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE, OPLUS_INCOMING_TYPE -> Direction.INCOMING
            CallLog.Calls.OUTGOING_TYPE, OPLUS_OUTGOING_TYPE -> Direction.OUTGOING
            // MISSED/REJECTED/BLOCKED/VOICEMAIL (standard or this OEM's own codes, whichever they
            // turn out to be): direction is moot either way, duration=0 means it can't qualify
            // regardless (see QualifyingCallRule - connected requires a durationMs basis).
            else -> Direction.UNKNOWN
        }
        val durationMs = row.durationSeconds * 1000L
        val connected = row.durationSeconds > 0
        val qualified = QualifyingCallRule.isQualified(
            QualifyingCallRule.Signals(
                connected = connected,
                direction = direction,
                isGroupCall = false,
                isVideo = false,
                inferredDurationMs = if (connected) durationMs else null
            ),
            detectionPreferences.settings.value
        )
        val callerName = row.cachedName?.takeIf { it.isNotBlank() } ?: row.number?.takeIf { it.isNotBlank() } ?: "(unknown)"

        val event = CallEvent(
            id = 0,
            packageName = CALL_LOG_SOURCE,
            notifKey = "calllog:${row.id}",
            statesSeen = "CALL_LOG_ENTRY@date=${row.date}",
            connected = connected,
            isVideo = false,
            isGroupCall = false,
            direction = direction,
            notificationName = callerName,
            postTime = row.date,
            ongoingWhen = if (connected) row.date else null,
            ongoingPostedElapsedRealtime = null,
            endTime = row.date + durationMs,
            inferredDurationMs = if (connected) durationMs else null,
            qualified = qualified,
            avatarHash = null,
            matchedScheduleId = null,
            matchConfidence = null,
            rawJson = rowToJson(row)
        )
        val id = callEventRepository.upsert(event)
        val saved = event.copy(id = id)
        AppLogger.i(
            TAG,
            "Call log entry processed: \"$callerName\" direction=$direction durationMs=${saved.inferredDurationMs} qualified=$qualified",
            crashlyticsMessage = "Call log entry processed: id=${saved.id} direction=$direction " +
                "durationMs=${saved.inferredDurationMs} qualified=$qualified"
        )
        processFinalizedCall(saved)
    }

    private fun rowToJson(row: CallLogRow): String = JSONObject().apply {
        put("source", CALL_LOG_SOURCE)
        put("id", row.id)
        put("number", row.number)
        put("cachedName", row.cachedName)
        put("type", row.type)
        put("date", row.date)
        put("durationSeconds", row.durationSeconds)
    }.let { "[$it]" }
}
