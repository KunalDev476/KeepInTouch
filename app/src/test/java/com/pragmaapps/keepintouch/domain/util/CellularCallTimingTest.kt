package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CellularCallTimingTest {

    @Test
    fun `incoming call answered on the full-screen call screen gets OFFHOOK to IDLE as its duration`() {
        // Real device timings: RINGING 17:10:59.615, OFFHOOK 17:11:03.572, IDLE 17:11:23.676, and
        // no chronometer notification was ever posted.
        val session = CallSession(startedAtMs = 59_615, ringingAtMs = 59_615, offhookAtMs = 63_572, idleAtMs = 83_676)
        val result = CellularCallTiming.compute(session)
        assertTrue(result.connected)
        assertEquals(63_572L, result.startMs)
        assertEquals(83_676L, result.endMs)
        assertEquals(20_104L, result.durationMs)
    }

    @Test
    fun `missed or rejected incoming call never connects`() {
        val session = CallSession(startedAtMs = 0, ringingAtMs = 0, offhookAtMs = null, idleAtMs = 20_000)
        val result = CellularCallTiming.compute(session)
        assertFalse(result.connected)
        assertNull(result.durationMs)
    }

    @Test
    fun `outgoing call never counts as connected through the fallback`() {
        // OFFHOOK at dial time with no RINGING first - could be unanswered.
        val session = CallSession(startedAtMs = 0, ringingAtMs = null, offhookAtMs = 0, idleAtMs = 40_000)
        val result = CellularCallTiming.compute(session)
        assertFalse(result.connected)
        assertNull(result.durationMs)
    }

    @Test
    fun `a waiting call ringing after an outgoing call went OFFHOOK is not an answered incoming call`() {
        val session = CallSession(startedAtMs = 0, ringingAtMs = 10_000, offhookAtMs = 0, idleAtMs = 40_000)
        val result = CellularCallTiming.compute(session)
        assertFalse(result.connected)
    }

    @Test
    fun `session covers a notification posted just before the first call-state callback`() {
        val session = CallSession(startedAtMs = 10_000, ringingAtMs = 10_000, offhookAtMs = null, idleAtMs = 20_000)
        assertTrue(session.covers(9_000))
        assertTrue(session.covers(20_000))
        assertFalse(session.covers(4_000))
        assertFalse(session.covers(20_001))
    }

    @Test
    fun `offhook duration for an unnamed call`() {
        val session = CallSession(startedAtMs = 0, ringingAtMs = null, offhookAtMs = 2_000, idleAtMs = 30_000)
        assertEquals(28_000L, CellularCallTiming.offhookDurationMs(session))
        assertNull(CellularCallTiming.offhookDurationMs(session.copy(offhookAtMs = null)))
    }
}
