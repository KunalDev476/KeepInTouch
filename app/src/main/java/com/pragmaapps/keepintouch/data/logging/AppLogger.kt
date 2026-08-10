package com.pragmaapps.keepintouch.data.logging

import android.content.Context
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pragmaapps.keepintouch.data.db.dao.LogEntryDao
import com.pragmaapps.keepintouch.data.db.entity.LogEntryEntity
import com.pragmaapps.keepintouch.domain.model.LogLevel
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AppLoggerEntryPoint {
    fun logEntryDao(): LogEntryDao
}

/**
 * A global logging facade, callable from anywhere - Hilt-injected classes, plain objects, and
 * BroadcastReceivers that aren't @AndroidEntryPoint - without needing constructor injection
 * everywhere. Every call always mirrors to Logcat first (so `adb logcat` keeps working exactly as
 * before this existed), then persists to Room via Hilt's EntryPointAccessors pattern once
 * [init] has run. Before [init] (or if it's never called, e.g. in a unit test), calls simply
 * fall back to Logcat-only - never crashes, never blocks the caller.
 *
 * This is what backs the in-app "App Logs" screen (Settings -> Diagnostics) - the whole point is
 * that a silent failure (a permission never granted, an alarm that computed a past fire time, a
 * notification that couldn't post) becomes something the user can actually go look at, instead of
 * disappearing into a Logcat stream nobody but a developer with a USB cable can see.
 *
 * Every call also forwards to Crashlytics as a `log()` breadcrumb - [d]/[i]/[e] alike - so the
 * Crashlytics dashboard shows exactly what the app was doing leading up to a crash, not just the
 * stack trace itself. FirebaseCrashlytics.getInstance() is safe to call this early - Firebase
 * auto-inits via its own ContentProvider before Application.onCreate() ever runs.
 *
 * Deliberately NOT auto-reported as Crashlytics non-fatals: [e] is used broadly across the app for
 * anything worth surfacing on the local "App Logs" screen, most of which are expected/handled
 * conditions (a permission not yet granted, an alarm computed in the past) rather than actual bugs
 * - and Crashlytics caps non-fatals at 8 per session, dropping the oldest first, so treating every
 * [e] call as reportable would burn that budget on noise. Use [nonFatal] instead, at the specific
 * call sites that represent a genuine broken code state worth Crashlytics visibility.
 */
object AppLogger {
    private const val MAX_ENTRIES = 1000

    private var dao: LogEntryDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        dao = EntryPoints.get(context.applicationContext, AppLoggerEntryPoint::class.java).logEntryDao()
    }

    /**
     * [crashlyticsMessage] lets a call site send a redacted version to Crashlytics (which leaves
     * the device) while [message] - shown on the local, on-device "App Logs" screen and Logcat -
     * keeps full detail. Defaults to [message] since most log calls contain nothing sensitive;
     * only pass a redacted [crashlyticsMessage] at call sites that would otherwise put a contact
     * name, phone number, or other PII into a Crashlytics breadcrumb.
     */
    fun d(tag: String, message: String, crashlyticsMessage: String = message) =
        log(LogLevel.DEBUG, tag, message, null, crashlyticsMessage)
    fun i(tag: String, message: String, crashlyticsMessage: String = message) =
        log(LogLevel.INFO, tag, message, null, crashlyticsMessage)
    fun e(tag: String, message: String, throwable: Throwable? = null, crashlyticsMessage: String = message) =
        log(LogLevel.ERROR, tag, message, throwable, crashlyticsMessage)

    /**
     * Everything [e] does, plus an explicit Crashlytics non-fatal report. Deliberate opt-in only -
     * see the class doc for why this isn't automatic. Reserve this for a genuine broken code state
     * an engineer would need to fix (a failed encryption/keystore operation, a corrupted read) -
     * not an expected/handled condition a user can just retry or that resolves itself (those stay
     * plain [e]: still on the App Logs screen and still a Crashlytics breadcrumb, just not a report).
     */
    fun nonFatal(tag: String, message: String, throwable: Throwable) {
        e(tag, message, throwable)
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (t: Throwable) {
            Log.e("AppLogger", "Failed to record non-fatal to Crashlytics", t)
        }
    }

    private fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
        crashlyticsMessage: String = message
    ) {
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }

        try {
            FirebaseCrashlytics.getInstance().log("[$tag] $crashlyticsMessage")
        } catch (t: Throwable) {
            // Same philosophy as the DAO write below - logging must never be the thing that crashes.
            Log.e("AppLogger", "Failed to forward log entry to Crashlytics", t)
        }

        val currentDao = dao ?: return
        scope.launch {
            try {
                currentDao.insert(
                    LogEntryEntity(
                        timestamp = System.currentTimeMillis(),
                        level = level,
                        tag = tag,
                        message = message,
                        throwable = throwable?.stackTraceToString()
                    )
                )
                currentDao.pruneKeepingLatest(MAX_ENTRIES)
            } catch (t: Throwable) {
                // The DB write itself failed - fall back to Logcat only, never let logging crash the app.
                Log.e("AppLogger", "Failed to persist log entry", t)
            }
        }
    }
}
