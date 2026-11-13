package com.pragmaapps.keepintouch.data.logging

import android.util.Log

/**
 * Logcat only, for development. Messages here can include contact names and numbers, so they must
 * never be forwarded off the device: anything meant for Crashlytics goes through [CrashReporter].
 */
object AppLogger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
