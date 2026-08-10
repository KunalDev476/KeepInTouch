package com.pragmaapps.keepintouch.domain.model

/** One entry in the in-app App Logs screen - see [com.pragmaapps.keepintouch.data.logging.AppLogger]. */
data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: String?
)
