package com.pragmaapps.keepintouch.data.notification

import android.content.Context
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.Direction
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ScreenedCallStore"
private const val PREFS = "screened_calls"
private const val KEY_CALLS = "calls"
private const val MAX_ENTRIES = 10
private const val MAX_AGE_MS = 12 * 60 * 60 * 1000L

/** How long before a call-state session (or a dialer notification) a screened call may have started. */
const val SCREENED_CALL_GRACE_MS = 30_000L

/** One call the system handed to CallScreeningBackupService: who it was with, and when it started. */
data class ScreenedCall(val number: String, val direction: Direction, val atMs: Long)

/**
 * The last few calls seen by CallScreeningBackupService, so CallEpisodeProcessor can put a number
 * to a call whose notification never named the caller. Kept in private SharedPreferences rather
 * than memory: the screening service is unbound as soon as it answers, and the process may be
 * restarted before the call ends. Never leaves the device; entries expire after [MAX_AGE_MS].
 */
@Singleton
class ScreenedCallStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @Synchronized
    fun record(call: ScreenedCall) {
        val calls = (readAll() + call).sortedBy { it.atMs }.takeLast(MAX_ENTRIES)
        write(calls)
    }

    /**
     * The first screened call that started between [fromMs] - [SCREENED_CALL_GRACE_MS] and [toMs],
     * or null. The first, not the latest: with call waiting the second call starts mid-session,
     * and the session started with the first one.
     */
    @Synchronized
    fun findBetween(fromMs: Long, toMs: Long): ScreenedCall? =
        readAll().firstOrNull { it.atMs in (fromMs - SCREENED_CALL_GRACE_MS)..toMs }

    private fun readAll(): List<ScreenedCall> = try {
        val now = System.currentTimeMillis()
        val array = JSONArray(prefs.getString(KEY_CALLS, null) ?: "[]")
        (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .map { ScreenedCall(it.getString("number"), Direction.valueOf(it.getString("direction")), it.getLong("atMs")) }
            .filter { now - it.atMs <= MAX_AGE_MS }
            .sortedBy { it.atMs }
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Could not read screened calls; starting fresh", t)
        emptyList()
    }

    private fun write(calls: List<ScreenedCall>) {
        val array = JSONArray()
        calls.forEach {
            array.put(JSONObject().put("number", it.number).put("direction", it.direction.name).put("atMs", it.atMs))
        }
        prefs.edit().putString(KEY_CALLS, array.toString()).apply()
    }
}
