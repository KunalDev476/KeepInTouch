package com.pragmaapps.keepintouch.data.logging

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pragmaapps.keepintouch.data.notification.CALL_SCREENING_SOURCE
import com.pragmaapps.keepintouch.data.notification.PHONE_STATE_SOURCE

/**
 * What the app was doing before a crash or non-fatal, sent to Crashlytics' "Logs & Breadcrumbs"
 * tab via FirebaseCrashlytics.log(). Crashlytics keeps the most recent 64 KB per session and only
 * uploads them with a crash or non-fatal report.
 *
 * Off-device, so it must never carry user data. That's enforced by the types rather than by
 * care at each call site: the event is a fixed [Breadcrumb] constant, and detail values may only
 * be Boolean, Int, Long or an enum. Anything else - in particular any String, which could be a
 * name, a number or notification text - is replaced with "?" and never sent.
 * Free text stays on the device in [AppLogger] (Logcat).
 */
object Breadcrumbs {
    private const val TAG = "Breadcrumbs"

    fun log(event: Breadcrumb, vararg details: Pair<String, Any?>) {
        try {
            val line = buildString {
                append(event.key)
                details.forEach { (key, value) ->
                    append(' ').append(key).append('=').append(safe(value))
                }
            }
            FirebaseCrashlytics.getInstance().log(line)
        } catch (t: Throwable) {
            // Breadcrumbs must never be the thing that crashes.
            Log.e(TAG, "Failed to log breadcrumb ${event.key}", t)
        }
    }

    private fun safe(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Int, is Long -> value.toString()
        is Enum<*> -> value.name.lowercase()
        else -> "?"
    }
}

/** Every breadcrumb the app can leave. Keys are fixed, so they're safe to send. */
enum class Breadcrumb(val key: String) {
    // App and permissions
    APP_OPENED("app_opened"),
    MISSING_PERMISSIONS_SHOWN("missing_permissions_shown"),
    PERMISSION_RESULT("permission_result"),
    ONBOARDING_COMPLETED("onboarding_completed"),
    CALL_SCREENING_OFFERED("call_screening_offered"),
    CALL_SCREENING_RESULT("call_screening_result"),

    // Notification listener
    LISTENER_CONNECTED("listener_connected"),
    LISTENER_DISCONNECTED("listener_disconnected"),

    // Call detection
    CALL_NOTIFICATION_SEEN("call_notification_seen"),
    CALL_NOTIFICATION_REMOVED("call_notification_removed"),
    PHONE_STATE_CHANGED("phone_state_changed"),
    PHONE_STATE_TRACKING("phone_state_tracking"),
    CALL_SCREENED("call_screened"),
    CALL_EPISODE_ENDED("call_episode_ended"),
    CALL_MATCH_RESULT("call_match_result"),

    // Contacts
    CONTACTS_CHANGED("contacts_changed"),
    CONTACTS_OBSERVER("contacts_observer"),

    // Reminders
    REMINDER_SCHEDULED("reminder_scheduled"),
    REMINDER_FIRED("reminder_fired"),
    DIGEST_SCHEDULED("digest_scheduled"),
    DIGEST_FIRED("digest_fired"),
    BOOT_RESCHEDULE("boot_reschedule"),
    EXACT_ALARM_PERMISSION_CHANGED("exact_alarm_permission_changed"),

    // Post-call prompt
    POST_CALL_PROMPT_SHOWN("post_call_prompt_shown"),
    POST_CALL_PROMPT_ACTION("post_call_prompt_action")
}

/** Which kind of app a call came from - never the package name itself. */
enum class CallSourceKind {
    WHATSAPP, WHATSAPP_BUSINESS, DIALER, CALL_SCREENING, PHONE_STATE, OTHER;

    companion object {
        fun of(packageName: String, isDialer: Boolean): CallSourceKind = when {
            packageName == "com.whatsapp" -> WHATSAPP
            packageName == "com.whatsapp.w4b" -> WHATSAPP_BUSINESS
            packageName == CALL_SCREENING_SOURCE -> CALL_SCREENING
            packageName == PHONE_STATE_SOURCE -> PHONE_STATE
            isDialer -> DIALER
            else -> OTHER
        }
    }
}

/** Why a call episode ended. Reasons are fixed strings in CallEpisodeProcessor; anything new reads as OTHER. */
enum class EndReason {
    REMOVED, PHONE_IDLE, PHONE_IDLE_FALLBACK, STALE_KEY_REUSE, STALE_AWAITING_IDLE, CALL_SCREENING_FALLBACK, OTHER;

    companion object {
        fun of(reason: String): EndReason = entries.firstOrNull { it.name == reason } ?: OTHER
    }
}

/** Call-state transitions, as reported by the telephony callback. */
enum class PhoneStateKind { RINGING, OFFHOOK, IDLE }

/** A finalized call's match result, by outcome type only. */
enum class MatchKind { UNIQUE_SCHEDULE, DUPLICATE_SCHEDULES, CONTACT_WITHOUT_SCHEDULE, NO_MATCH }

/** Why an important-call reminder wasn't armed. */
enum class ReminderSkip { NOT_ELIGIBLE, IN_PAST, NO_EXACT_ALARM }
