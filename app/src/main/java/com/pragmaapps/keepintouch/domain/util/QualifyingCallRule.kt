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

    private fun directionAllowed(direction: Direction, settings: DetectionSettings): Boolean = when (direction) {
        Direction.INCOMING -> settings.countIncoming
        Direction.OUTGOING -> settings.countOutgoing
        Direction.UNKNOWN -> false
    }
}
