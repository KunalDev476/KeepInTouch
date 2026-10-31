package com.pragmaapps.keepintouch.data.notification

import android.app.Notification
import android.app.Person
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.notification.StatusBarNotification
import com.pragmaapps.keepintouch.domain.util.BidiMarks
import org.json.JSONArray
import org.json.JSONObject

/**
 * android.callType is Android's own [Notification.EXTRA_CALL_TYPE] constant ("android.callType"):
 * CALL_TYPE_INCOMING=1 (ringing), CALL_TYPE_ONGOING=2 (connected), CALL_TYPE_SCREENING=3. This
 * part is documented framework behavior, not a guess.
 *
 * There is, however, NO documented public Notification extra literally named "isGroupCall" -
 * WhatsApp's exact extra key (if any) is unverified. We probe every plausible candidate key,
 * record which (if any) fired, and surface that in Diagnostics instead of silently trusting one
 * guessed key.
 *
 * Some call sources (confirmed on a real device: com.android.incallui, Android's own stock
 * in-call UI) never set android.callType at all - CallStyle is WhatsApp's convention, not a
 * universal one. For those, [showChronometer] and [statusText] are fallback signals, confirmed
 * against a real captured trace: android.showChronometer flips false->true exactly when
 * android.text changes from "Incoming call" to "Current call" (i.e. exactly at answer/connect).
 * showChronometer is Android's own documented [Notification.EXTRA_SHOW_CHRONOMETER] boolean, not
 * a guess - it's what drives the live call-duration counter, so "true" reliably means connected.
 * statusText is free text (locale/OEM-dependent) - only verified against this one English-locale
 * capture, so direction inferred from it is a best-effort fallback, not a guarantee.
 *
 * [actionCount] (notification.actions?.size) is a structural, locale-independent complement to
 * statusText: a ringing call conventionally offers two actions (Answer + Decline), while a
 * connected or actively-dialing call typically offers only one (Hang up). Not documented as a
 * direction signal by the platform - inferred from the standard Answer/Decline/Hang-up UX
 * convention every dialer follows - but it doesn't depend on guessing an exact English string, so
 * it's used as the primary "is this actually ringing" check, with statusText as a secondary cue.
 */
object CallNotificationExtras {
    val GROUP_CALL_CANDIDATE_KEYS = listOf("isGroupCall", "android.isGroupCall", "android.isGroupConversation")

    data class Parsed(
        val callType: Int?,
        val isVideo: Boolean,
        val groupCallCandidateHits: Map<String, Boolean>,
        val peopleListSize: Int,
        val callerName: String?,
        val callerIcon: Icon?,
        val largeIcon: Icon?,
        val notificationWhen: Long,
        val category: String?,
        val showChronometer: Boolean,
        val statusText: String?,
        val actionCount: Int,
        val allExtraKeys: List<String>
    )

    fun parse(sbn: StatusBarNotification): Parsed {
        val notification = sbn.notification
        val extras: Bundle = notification.extras ?: Bundle()

        val callType = if (extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            extras.getInt(Notification.EXTRA_CALL_TYPE, -1).takeIf { it != -1 }
        } else null

        val isVideo = extras.getBoolean(Notification.EXTRA_CALL_IS_VIDEO, false)

        val groupCallHits = GROUP_CALL_CANDIDATE_KEYS.associateWith { key ->
            extras.containsKey(key) && extras.getBoolean(key, false)
        }

        val people = extras.getParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, Person::class.java)
        val firstPerson = people?.firstOrNull()

        // Dialers may wrap a number in invisible direction marks (see BidiMarks) - stripped here so
        // the stored name, Call Logs and matching all see the plain text.
        val callerName = (firstPerson?.name?.toString() ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
            ?.let { BidiMarks.strip(it).trim() }
            ?.takeIf { it.isNotEmpty() }

        return Parsed(
            callType = callType,
            isVideo = isVideo,
            groupCallCandidateHits = groupCallHits,
            peopleListSize = people?.size ?: 0,
            callerName = callerName,
            callerIcon = firstPerson?.icon,
            largeIcon = notification.getLargeIcon(),
            notificationWhen = notification.`when`,
            category = notification.category,
            showChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false),
            statusText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            actionCount = notification.actions?.size ?: 0,
            allExtraKeys = extras.keySet().toList()
        )
    }

    /** Serializes everything readable from this callback into one JSON object for rawJson/Diagnostics. */
    @Suppress("DEPRECATION") // Bundle.get(String) is the only way to read an arbitrary, unknown-typed extra key generically.
    fun toJson(sbn: StatusBarNotification, parsed: Parsed, localElapsedRealtime: Long): JSONObject {
        val notification = sbn.notification
        val extras = notification.extras ?: Bundle()
        val extrasJson = JSONObject()
        for (key in parsed.allExtraKeys) {
            val value = try { extras.get(key) } catch (t: Throwable) { null }
            extrasJson.put(key, stringifyExtraValue(value))
        }
        return JSONObject().apply {
            put("key", sbn.key)
            put("packageName", sbn.packageName)
            put("postTime", sbn.postTime)
            put("notificationWhen", parsed.notificationWhen)
            put("category", parsed.category)
            put("callType", parsed.callType)
            put("showChronometer", parsed.showChronometer)
            put("statusText", parsed.statusText)
            put("actionCount", parsed.actionCount)
            put("isVideo", parsed.isVideo)
            put("groupCallCandidateHits", JSONObject(parsed.groupCallCandidateHits as Map<*, *>))
            put("peopleListSize", parsed.peopleListSize)
            put("callerName", parsed.callerName)
            put("localElapsedRealtime", localElapsedRealtime)
            put("extras", extrasJson)
        }
    }

    private fun stringifyExtraValue(value: Any?): String = when (value) {
        null -> "null"
        is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { it.toString() }
        else -> value.toString()
    }

    /** statusText of the last notification snapshot in [rawJson], or null if there is none. */
    fun lastStatusText(rawJson: String?): String? = try {
        if (rawJson.isNullOrBlank()) null
        else JSONArray(rawJson).let { array ->
            (array.length() - 1 downTo 0)
                .map { array.optJSONObject(it) }
                .firstOrNull { it != null && it.has("key") }
                ?.optString("statusText", "")
        }
    } catch (t: Throwable) {
        null
    }

    fun mergeJsonArray(existingRawJson: String?, newEntry: JSONObject): String {
        val array = try {
            if (existingRawJson.isNullOrBlank()) JSONArray() else JSONArray(existingRawJson)
        } catch (t: Throwable) {
            JSONArray()
        }
        array.put(newEntry)
        return array.toString()
    }
}
