package com.pragmaapps.keepintouch.data.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Every permission the app needs in order to work properly, in the order they're asked for.
 * [runtimePermission] is null for the two granted from an Android Settings page instead of a
 * prompt. WRITE_CONTACTS isn't here on purpose: it's only requested when renaming a contact.
 */
enum class AppPermission(val runtimePermission: String?) {
    CONTACTS(Manifest.permission.READ_CONTACTS),
    NOTIFICATION_ACCESS(null),
    PHONE_STATE(Manifest.permission.READ_PHONE_STATE),
    POST_NOTIFICATIONS(Manifest.permission.POST_NOTIFICATIONS),
    EXACT_ALARM(null)
}

object AppPermissions {
    fun isGranted(context: Context, permission: AppPermission): Boolean = when (permission) {
        AppPermission.NOTIFICATION_ACCESS ->
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        AppPermission.EXACT_ALARM ->
            ContextCompat.getSystemService(context, AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
        else -> permission.runtimePermission?.let {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        } ?: false
    }

    fun granted(context: Context): Set<AppPermission> =
        AppPermission.entries.filterTo(mutableSetOf()) { isGranted(context, it) }

    fun missing(context: Context): List<AppPermission> =
        AppPermission.entries.filterNot { isGranted(context, it) }
}
