package com.pragmaapps.keepintouch.data.logging

import com.pragmaapps.keepintouch.data.permissions.DeviceVendor
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.PowerManager
import android.util.Log
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.data.permissions.OptionalPermission
import com.pragmaapps.keepintouch.notification.WhatsAppCallListenerService
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends an [AppFailure] to Crashlytics. Crashlytics adds the device model, manufacturer and Android
 * version itself; this adds the state of the phone, which is usually what explains a failure.
 * Only fixed values go out, never user data - see [AppFailure].
 *
 * Crashlytics keeps only 8 non-fatals per session, so one failure hitting every schedule at once
 * would crowd out everything else. Each type reports at most once per [REPEAT_WINDOW_MS].
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val REPEAT_WINDOW_MS = 6 * 60 * 60 * 1000L

    private var appContext: Context? = null
    private val lastReportedAt = ConcurrentHashMap<Class<*>, Long>()

    fun init(context: Context) {
        appContext = context.applicationContext
        refreshDeviceState()
    }

    /** Keeps the keys current for crashes too, which have no chance to set them first. */
    fun refreshDeviceState() {
        val context = appContext ?: return
        try {
            FirebaseCrashlytics.getInstance().setCustomKeys(deviceState(context))
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update device state", t)
        }
    }

    fun report(failure: AppFailure) {
        val now = System.currentTimeMillis()
        val last = lastReportedAt[failure.javaClass]
        if (last != null && now - last < REPEAT_WINDOW_MS) return
        lastReportedAt[failure.javaClass] = now

        try {
            refreshDeviceState()
            val keys = CustomKeysAndValues.Builder()
            failure.details.forEach { (key, value) -> keys.putString(key, value) }
            FirebaseCrashlytics.getInstance().recordException(failure, keys.build())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to report ${failure.javaClass.simpleName}", t)
        }
    }

    private fun deviceState(context: Context): CustomKeysAndValues {
        val power = context.getSystemService(PowerManager::class.java)
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val keys = CustomKeysAndValues.Builder()
            .putString("build_type", if (debuggable) "debug" else "release")
            .putBoolean("listener_connected", WhatsAppCallListenerService.isConnected)
            .putBoolean("battery_saver", power.isPowerSaveMode)
            .putBoolean("battery_optimization_exempt", power.isIgnoringBatteryOptimizations(context.packageName))
            .putString("standby_bucket", standbyBucketName(usage.appStandbyBucket))
        AppPermission.entries.forEach { permission ->
            keys.putBoolean("perm_${permission.name.lowercase()}", AppPermissions.isGranted(context, permission))
        }
        keys.putBoolean("call_screening_role_available", AppPermissions.isCallScreeningAvailable(context))
        // Maker/brand describe the phone model line, not the user. Brand catches sub-brands
        // (Redmi, POCO) that Crashlytics' own manufacturer field doesn't show.
        keys.putString("device_maker", DeviceVendor.maker)
        keys.putString("device_brand", DeviceVendor.brand)
        keys.putBoolean("device_verified_vendor", DeviceVendor.isVerified)
        keys.putBoolean("perm_call_screening", AppPermissions.isGranted(context, OptionalPermission.CALL_SCREENING))
        return keys.build()
    }

    private fun standbyBucketName(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
        UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
        else -> "unknown_$bucket"
    }
}
