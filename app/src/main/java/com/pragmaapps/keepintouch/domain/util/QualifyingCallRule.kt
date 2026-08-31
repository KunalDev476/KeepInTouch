package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Direction

/**
 * User-configurable detection knobs (Settings screen). Network "stability" is deliberately not a
 * field here and never can be: WhatsApp's call notification exposes no signal/quality/reconnection
 * data, so a sustained ONGOING state is only ever a proxy for a real conversation, never proof of
 * a stable connection.
 */
data class DetectionSettings(
    val minDurationMs: Long = QualifyingCallRule.DEFAULT_MIN_DURATION_MS,
    val countIncoming: Boolean = true,
    val countOutgoing: Boolean = true
)

/**
 * The one rule that decides whether a detected call is allowed to fulfill a schedule. Pure -
 * never touches Android framework types or the database directly - so it's fully unit-testable.
 */
object QualifyingCallRule {
    const val DEFAULT_MIN_DURATION_MS = 15_000L

    data class Signals(
        val connected: Boolean,
        val direction: Direction,
        val isGroupCall: Boolean,
        val isVideo: Boolean,
        val inferredDurationMs: Long?
    )

    fun isQualified(signals: Signals, settings: DetectionSettings = DetectionSettings()): Boolean =
        signals.connected &&
            directionAllowed(signals.direction, settings) &&
            !signals.isGroupCall &&
            !signals.isVideo &&
            signals.inferredDurationMs != null &&
            signals.inferredDurationMs >= settings.minDurationMs

    /** Human-readable reasons a non-qualifying call didn't qualify, for the Diagnostics screen. */
    fun disqualifyReasons(signals: Signals, settings: DetectionSettings = DetectionSettings()): List<String> {
        val reasons = mutableListOf<String>()
        if (!signals.connected) reasons += "Never reached ONGOING (not answered/connected)."
        if (signals.direction == Direction.UNKNOWN) reasons += "Direction could not be determined."
        if (signals.direction == Direction.INCOMING && !settings.countIncoming) reasons += "Incoming calls are excluded in Settings."
        if (signals.direction == Direction.OUTGOING && !settings.countOutgoing) reasons += "Outgoing calls are excluded in Settings."
        if (signals.isGroupCall) reasons += "Flagged as a group call - cannot fulfill a 1:1 schedule."
        if (signals.isVideo) reasons += "Video calls are excluded - only voice calls fulfill a schedule."
        when (val duration = signals.inferredDurationMs) {
            null -> reasons += "No duration basis available (never observed an ONGOING timestamp)."
            else -> if (duration < settings.minDurationMs) {
                reasons += "Inferred duration ${duration}ms is below the ${settings.minDurationMs}ms threshold."
            }
        }
        return reasons
    }

    private fun directionAllowed(direction: Direction, settings: DetectionSettings): Boolean = when (direction) {
        Direction.INCOMING -> settings.countIncoming
        Direction.OUTGOING -> settings.countOutgoing
        Direction.UNKNOWN -> false
    }
}
