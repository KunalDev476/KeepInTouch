package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialerCallSignalsTest {

    @Test
    fun `a number and a name are the same caller shown two ways`() {
        assertTrue(DialerCallSignals.isNumberNameVariant("+91 85329 21158", "Mummy"))
        assertTrue(DialerCallSignals.isNumberNameVariant("Mummy", "+91 85329 21158"))
    }

    @Test
    fun `two different names or two numbers are not a number-name variant`() {
        assertFalse(DialerCallSignals.isNumberNameVariant("Mummy", "Papa"))
        assertFalse(DialerCallSignals.isNumberNameVariant("+91 85329 21158", "+91 80 3706 6619"))
    }

    @Test
    fun `a resolved name is kept when the notification flips back to the number`() {
        assertEquals("Mummy", DialerCallSignals.preferredName("Mummy", "+91 85329 21158"))
        assertEquals("Mummy", DialerCallSignals.preferredName("+91 85329 21158", "Mummy"))
        assertEquals("Mummy", DialerCallSignals.preferredName("Mummy", null))
        assertEquals("+91 85329 21158", DialerCallSignals.preferredName(DialerCallSignals.UNKNOWN_NAME, "+91 85329 21158"))
        assertEquals("Papa", DialerCallSignals.preferredName("Mummy", "Papa"))
    }

    @Test
    fun `outgoing call is answered when its status text clears after dialing`() {
        // Real device: "Calling" -> "Waiting…" -> "" at answer.
        assertTrue(DialerCallSignals.isOutgoingAnswerTransition("Waiting…", ""))
        assertTrue(DialerCallSignals.isOutgoingAnswerTransition("Calling", null))
    }

    @Test
    fun `a blank text before dialing starts or a busy status is not an answer`() {
        // Real device: the first post of a call is "" before "Calling".
        assertFalse(DialerCallSignals.isOutgoingAnswerTransition(null, ""))
        assertFalse(DialerCallSignals.isOutgoingAnswerTransition("", ""))
        // Unanswered call: "Calling" -> "User busy. Please do not hang up." -> "Calling".
        assertFalse(DialerCallSignals.isOutgoingAnswerTransition("Calling", "User busy. Please do not hang up."))
        assertFalse(DialerCallSignals.isOutgoingAnswerTransition("User busy. Please do not hang up.", "Calling"))
    }

    @Test
    fun `session that rang first is incoming, a waiting call during an outgoing one is not`() {
        assertTrue(CallSession(startedAtMs = 0, ringingAtMs = 0, offhookAtMs = 18_000, idleAtMs = 38_000).beganRinging)
        assertTrue(CallSession(startedAtMs = 0, ringingAtMs = 0, offhookAtMs = null, idleAtMs = 20_000).beganRinging)
        assertFalse(CallSession(startedAtMs = 0, ringingAtMs = 10_000, offhookAtMs = 0, idleAtMs = 40_000).beganRinging)
        assertFalse(CallSession(startedAtMs = 0, ringingAtMs = null, offhookAtMs = 0, idleAtMs = 32_000).beganRinging)
    }
}
