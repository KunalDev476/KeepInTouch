package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.pragmaapps.keepintouch.data.permissions.AppPermission

/** What a permission's screen says, shared by first-time onboarding and the missing-permissions flow. */
internal data class PermissionContent(
    val icon: ImageVector,
    /** Short name and what it's for, for the list on the missing-permissions intro. */
    val name: String,
    val purpose: String,
    val title: String,
    val why: String,
    val ctaLabel: String,
    /** What actually stops working without it - only shown in the missing-permissions flow. */
    val withoutIt: String
)

internal fun AppPermission.content(): PermissionContent = when (this) {
    AppPermission.CONTACTS -> PermissionContent(
        icon = Icons.Filled.Contacts,
        name = "Contacts",
        purpose = "Lets you choose who to schedule",
        title = "Find who has WhatsApp",
        why = "We read your contacts, read-only, to see who's saved and who has WhatsApp linked. " +
            "Contact names and numbers are only ever used inside this app, to match them to your " +
            "schedules - nothing is sent off your device.",
        ctaLabel = "Grant Contacts access",
        withoutIt = "Without it, you can't add people to schedules, and contact name changes won't update."
    )
    AppPermission.NOTIFICATION_ACCESS -> PermissionContent(
        icon = Icons.Filled.Notifications,
        name = "Notification access",
        purpose = "Detects calls to complete your schedules",
        title = "Detect WhatsApp and phone calls",
        why = "Android requires notification access to let an app see call notifications - granted " +
            "from Settings, not a normal prompt. We only ever look at call notifications - from " +
            "WhatsApp, your phone's dialer, or another calling app; every other notification is " +
            "ignored immediately, unread. From a call notification we only read the caller's name (or " +
            "number) and whether the call is ringing, ongoing, or ended - never message text or chat " +
            "content. That's used only to mark a call as complete on a schedule you created; nothing " +
            "is sent off your device, and nothing is shared with us, WhatsApp, or anyone else. You can " +
            "revoke this any time from Settings > Apps > Special app access > Notification access.",
        ctaLabel = "Open Settings",
        withoutIt = "Without it, calls can't be detected, so schedules won't be marked completed automatically."
    )
    AppPermission.PHONE_STATE -> PermissionContent(
        icon = Icons.Filled.Timer,
        name = "Phone call status",
        purpose = "Measures how long phone calls last",
        title = "Measure phone call duration",
        why = "Some phones show no call notification while the full-screen call screen is open, so we " +
            "also check whether a phone call is ringing, in progress, or has ended - only to measure " +
            "how long it lasted. This never gives us the phone number, your call history, or anything " +
            "else, and nothing leaves your device.",
        ctaLabel = "Allow phone call status",
        withoutIt = "Without it, phone calls on some phones may not be marked completed automatically."
    )
    AppPermission.POST_NOTIFICATIONS -> PermissionContent(
        icon = Icons.Filled.NotificationsActive,
        name = "Notifications",
        purpose = "Shows your reminders",
        title = "Show you reminders",
        why = "Needed to actually display reminder notifications - the important-call warning and the " +
            "daily pending-calls summary. These are notifications this app creates and shows only to " +
            "you; nothing is read from any other app here.",
        ctaLabel = "Allow notifications",
        withoutIt = "Without it, you won't get reminders or the prompt to mark a call complete."
    )
    AppPermission.EXACT_ALARM -> PermissionContent(
        icon = Icons.Filled.Alarm,
        name = "Exact alarms",
        purpose = "Sends reminders right on time",
        title = "Fire reminders exactly on time",
        why = "Lets an important call's 10-minutes-before warning fire at the exact moment, even in " +
            "Doze - granted from Settings, not a normal prompt. Only used to schedule this app's own " +
            "reminders; it doesn't give access to anything else on your device.",
        ctaLabel = "Open Settings",
        withoutIt = "Without it, important-call reminders won't be set."
    )
}

/**
 * Where this permission is granted from Android Settings. For a runtime permission that's the
 * app's own settings page, the only way left once Android stops showing its prompt.
 */
internal fun AppPermission.settingsIntent(context: Context): Intent = when (this) {
    AppPermission.NOTIFICATION_ACCESS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    AppPermission.EXACT_ALARM ->
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
    else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
