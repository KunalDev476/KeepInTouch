package com.pragmaapps.keepintouch.data.permissions

import android.provider.Settings
import android.content.Intent
import android.Manifest
import android.app.AlarmManager
import android.app.role.RoleManager
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

/**
 * Permissions the user may skip: they never count as "missing" on a fresh start. Call screening is
 * shown on phones outside [DeviceVendor]'s verified list - as the optional last onboarding page,
 * or once on startup for existing users - and is always available from Settings on every phone,
 * or via MissingPermissionsGate.showCallScreening().
 */
enum class OptionalPermission {
    /** The call-screening role - a backup way to learn a call's number; see CallScreeningBackupService. */
    CALL_SCREENING
}

/** Where the optional call-screening page was shown from - fixed values for diagnostics. */
enum class CallScreeningSource { ONBOARDING, STARTUP, SETTINGS }

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

    fun isGranted(context: Context, permission: OptionalPermission): Boolean = when (permission) {
        OptionalPermission.CALL_SCREENING -> try {
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) ?: false
        } catch (t: Throwable) {
            false
        }
    }

    /** Whether Android lets this app ask for the call-screening role at all (some builds don't offer it). */
    fun isCallScreeningAvailable(context: Context): Boolean = try {
        context.getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) ?: false
    } catch (t: Throwable) {
        false
    }

    /** Whether this phone gets the optional call-screening page during setup / at startup. */
    fun shouldOfferCallScreening(): Boolean = !DeviceVendor.isVerified

    /**
     * How to ask for call screening: Android's role dialog when the phone offers it, otherwise the
     * Default apps settings page, where the user can pick this app as "Caller ID & spam app" (some
     * OEM builds, e.g. MIUI, report the role as unavailable to the dialog but still list it there).
     */
    fun callScreeningRequestIntent(context: Context): Intent {
        if (isCallScreeningAvailable(context)) {
            context.getSystemService(RoleManager::class.java)?.let {
                return it.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }
}
