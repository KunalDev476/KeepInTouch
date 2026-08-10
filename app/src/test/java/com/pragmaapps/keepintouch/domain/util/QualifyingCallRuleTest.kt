package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.Direction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualifyingCallRuleTest {

    private fun signals(
        connected: Boolean = true,
        direction: Direction = Direction.INCOMING,
        isGroupCall: Boolean = false,
        isVideo: Boolean = false,
        inferredDurationMs: Long? = 16_000L
    ) = QualifyingCallRule.Signals(connected, direction, isGroupCall, isVideo, inferredDurationMs)

    @Test
    fun `a connected incoming call over the duration threshold qualifies`() {
        assertTrue(QualifyingCallRule.isQualified(signals()))
    }

    @Test
    fun `a connected outgoing call over the duration threshold qualifies`() {
        assertTrue(QualifyingCallRule.isQualified(signals(direction = Direction.OUTGOING)))
    }

    @Test
    fun `never connected does not qualify`() {
        assertFalse(QualifyingCallRule.isQualified(signals(connected = false)))
    }

    @Test
    fun `unknown direction never qualifies`() {
        assertFalse(QualifyingCallRule.isQualified(signals(direction = Direction.UNKNOWN)))
    }

    @Test
    fun `group call never qualifies even if everything else is fine`() {
        assertFalse(QualifyingCallRule.isQualified(signals(isGroupCall = true)))
    }

    @Test
    fun `duration under the 15s default threshold does not qualify`() {
        assertFalse(QualifyingCallRule.isQualified(signals(inferredDurationMs = 14_999L)))
    }

    @Test
    fun `duration exactly at the threshold qualifies (boundary is inclusive)`() {
        assertTrue(QualifyingCallRule.isQualified(signals(inferredDurationMs = 15_000L)))
    }

    @Test
    fun `null duration (no basis) never qualifies`() {
        assertFalse(QualifyingCallRule.isQualified(signals(inferredDurationMs = null)))
    }

    @Test
    fun `custom minimum duration setting is respected`() {
        val settings = DetectionSettings(minDurationMs = 10_000L)
        assertFalse(QualifyingCallRule.isQualified(signals(inferredDurationMs = 6_000L), settings))
        assertTrue(QualifyingCallRule.isQualified(signals(inferredDurationMs = 10_000L), settings))
    }

    @Test
    fun `excluding incoming calls in settings disqualifies an incoming call`() {
        val settings = DetectionSettings(countIncoming = false)
        assertFalse(QualifyingCallRule.isQualified(signals(direction = Direction.INCOMING), settings))
        assertTrue(QualifyingCallRule.isQualified(signals(direction = Direction.OUTGOING), settings))
    }

    @Test
    fun `excluding outgoing calls in settings disqualifies an outgoing call`() {
        val settings = DetectionSettings(countOutgoing = false)
        assertFalse(QualifyingCallRule.isQualified(signals(direction = Direction.OUTGOING), settings))
        assertTrue(QualifyingCallRule.isQualified(signals(direction = Direction.INCOMING), settings))
    }

    @Test
    fun `video calls qualify by default`() {
        assertTrue(QualifyingCallRule.isQualified(signals(isVideo = true)))
    }

    @Test
    fun `video calls excluded when videoCallsCount is false`() {
        val settings = DetectionSettings(videoCallsCount = false)
        assertFalse(QualifyingCallRule.isQualified(signals(isVideo = true), settings))
        assertTrue(QualifyingCallRule.isQualified(signals(isVideo = false), settings))
    }

    @Test
    fun `disqualifyReasons explains every failing criterion`() {
        val reasons = QualifyingCallRule.disqualifyReasons(
            signals(connected = false, direction = Direction.UNKNOWN, isGroupCall = true, inferredDurationMs = null)
        )
        assertTrue(reasons.any { it.contains("ONGOING") })
        assertTrue(reasons.any { it.contains("Direction") })
        assertTrue(reasons.any { it.contains("group call") })
        assertTrue(reasons.any { it.contains("duration basis") })
    }

    @Test
    fun `disqualifyReasons is empty for a qualifying call`() {
        assertTrue(QualifyingCallRule.disqualifyReasons(signals()).isEmpty())
    }
}
