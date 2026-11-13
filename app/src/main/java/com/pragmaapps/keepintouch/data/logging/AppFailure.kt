package com.pragmaapps.keepintouch.data.logging

import com.pragmaapps.keepintouch.domain.model.Recurrence

/**
 * Everything sent to Crashlytics. Each kind of failure is its own class, created where it happens,
 * because Crashlytics groups reports by exception type and top stack frame.
 *
 * This is what keeps user data off Crashlytics: a report's message is a fixed string from this
 * file, [details] are built from typed non-personal values, and a cause keeps only its exception
 * type and stack - see [SanitizedCause]. Never add a parameter here that could hold a name, a
 * number, or text from a notification.
 */
sealed class AppFailure(
    description: String,
    cause: Throwable? = null,
    val details: Map<String, String> = emptyMap()
) : Exception(description, cause?.let(SanitizedCause::from))

/**
 * Stands in for a real exception: same type name and stack, without its message. System exception
 * messages can quote whatever they were working on, such as a contact or a query.
 */
class SanitizedCause private constructor(
    originalType: String,
    originalStack: Array<StackTraceElement>,
    cause: Throwable?
) : Exception(originalType, cause) {
    init {
        stackTrace = originalStack
    }

    companion object {
        private const val MAX_DEPTH = 5

        fun from(throwable: Throwable): SanitizedCause = from(throwable, 0)

        private fun from(throwable: Throwable, depth: Int): SanitizedCause {
            val next = throwable.cause?.takeIf { it !== throwable && depth < MAX_DEPTH }
            return SanitizedCause(throwable.javaClass.name, throwable.stackTrace, next?.let { from(it, depth + 1) })
        }
    }
}

private fun reminderDetails(recurrence: Recurrence, isWindow: Boolean, minutesUntilDue: Long) = mapOf(
    "recurrence" to recurrence.name,
    "is_window" to isWindow.toString(),
    "minutes_until_due" to minutesUntilDue.toString()
)

// Reminders
class ExactAlarmNotAllowed(recurrence: Recurrence, isWindow: Boolean, minutesUntilDue: Long) :
    AppFailure("Reminder not set: exact alarms aren't allowed", details = reminderDetails(recurrence, isWindow, minutesUntilDue))
class ReminderArmFailed(recurrence: Recurrence, isWindow: Boolean, minutesUntilDue: Long, cause: Throwable) :
    AppFailure("Reminder could not be set", cause, reminderDetails(recurrence, isWindow, minutesUntilDue))
class ReminderCancelFailed(cause: Throwable) : AppFailure("Reminder could not be cancelled", cause)
class DigestReminderArmFailed(cause: Throwable) : AppFailure("Daily reminder could not be set", cause)
class DigestReminderCancelFailed(cause: Throwable) : AppFailure("Daily reminder could not be cancelled", cause)
class BootRearmFailed(cause: Throwable) : AppFailure("Reminders could not be set again after a restart", cause)
class ExactAlarmRearmFailed(cause: Throwable) : AppFailure("Reminders could not be set after exact alarms were allowed", cause)

// Notifications
class ImportantReminderBlocked(pending: Int) :
    AppFailure("Reminder dropped: notifications aren't allowed", details = mapOf("pending" to pending.toString()))
class DigestReminderBlocked(pending: Int) :
    AppFailure("Daily reminder dropped: notifications aren't allowed", details = mapOf("pending" to pending.toString()))
class PostCallPromptBlocked : AppFailure("Post-call prompt dropped: notifications aren't allowed")
class ImportantReminderPostFailed(cause: Throwable) : AppFailure("Reminder notification could not be shown", cause)
class DigestReminderPostFailed(cause: Throwable) : AppFailure("Daily reminder notification could not be shown", cause)
class PostCallPromptFailed(cause: Throwable) : AppFailure("Post-call prompt could not be shown", cause)
class NotificationChannelsFailed(cause: Throwable) : AppFailure("Notification channels could not be created", cause)

// Call detection
class ListenerLost : AppFailure("Call listener disconnected while notification access is still granted")
class CallNotificationPostedFailed(cause: Throwable) : AppFailure("A call notification could not be processed", cause)
class CallNotificationRemovedFailed(cause: Throwable) : AppFailure("A call notification's removal could not be processed", cause)
class CallSessionEndFailed(cause: Throwable) : AppFailure("The end of a phone call could not be processed", cause)
class PhoneStateTrackingFailed(stage: String, cause: Throwable) :
    AppFailure("Phone call status tracking failed", cause, mapOf("stage" to stage))
class DialerLookupFailed(cause: Throwable) : AppFailure("The default dialer could not be found", cause)
class CallScreeningFailed(cause: Throwable) : AppFailure("A screened call could not be recorded", cause)
class AvatarHashFailed(cause: Throwable) : AppFailure("A caller's avatar could not be read", cause)

// Schedules and contacts
class ScheduleCompleteFailed(cause: Throwable) : AppFailure("A schedule could not be completed from the post-call prompt", cause)
class ContactSyncFailed(cause: Throwable) : AppFailure("Contacts could not be synced", cause)
class ContactLookupFailed(cause: Throwable) : AppFailure("A contact's current key could not be found", cause)
class ContactRenameFailed(cause: Throwable? = null) : AppFailure("A contact could not be renamed", cause)
class ContactsObserverFailed(stage: String, cause: Throwable) :
    AppFailure("Watching for contact changes failed", cause, mapOf("stage" to stage))
