package com.pragmaapps.keepintouch.data.logging

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Global analytics facade, callable from anywhere - same shape as [AppLogger] (safe before
 * [init], never throws into the caller). Distinct purpose from AppLogger/Crashlytics though:
 * this logs meaningful app-domain *events* (a schedule was created, a call fulfilled one, someone
 * finished onboarding) to Firebase Analytics, for usage/behavior insight - which recurrence types
 * actually get used, how often a schedule gets fulfilled vs deleted, whether onboarding is
 * actually being completed - not crash diagnostics, which stay Crashlytics's job.
 */
object AppAnalytics {
    private const val TAG = "AppAnalytics"

    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context.applicationContext)
    }

    fun logScheduleCreated(recurrence: String, isImportant: Boolean) {
        log("schedule_created") {
            putString("recurrence", recurrence)
            putBoolean("is_important", isImportant)
        }
    }

    fun logScheduleFulfilled(recurrence: String, matchConfidence: String, matchMethod: String) {
        log("schedule_fulfilled") {
            putString("recurrence", recurrence)
            putString("match_confidence", matchConfidence)
            putString("match_method", matchMethod)
        }
    }

    fun logScheduleDeleted(recurrence: String) {
        log("schedule_deleted") {
            putString("recurrence", recurrence)
        }
    }

    fun logOnboardingCompleted() {
        log("onboarding_completed")
    }

    /** [name] must be a valid Firebase Analytics event name: <=40 chars, alphanumeric/underscore, not a reserved prefix. */
    private inline fun log(name: String, params: Bundle.() -> Unit = {}) {
        val instance = analytics ?: return
        try {
            instance.logEvent(name, Bundle().apply(params))
        } catch (t: Throwable) {
            // Same philosophy as AppLogger - analytics must never be the thing that crashes.
            AppLogger.e(TAG, "Failed to log analytics event: $name", t)
        }
    }
}
