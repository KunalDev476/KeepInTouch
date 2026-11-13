package com.pragmaapps.keepintouch.data.logging

import com.pragmaapps.keepintouch.domain.model.Recurrence
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AppFailureTest {
    private val privateText = "Mummy +91 98765 43210"

    private fun chain(start: Throwable): List<Throwable> = generateSequence(start) { it.cause }.toList()

    @Test
    fun `no message from a cause reaches the report`() {
        val cause = IllegalStateException("query failed for $privateText", SecurityException(privateText))
        val failure = ContactSyncFailed(cause)

        assertFalse(chain(failure).any { it.message.orEmpty().contains("Mummy") || it.toString().contains("98765") })
    }

    @Test
    fun `a cause keeps its type and stack so the report shows where it failed`() {
        val cause = SecurityException(privateText)
        val sanitized = ContactSyncFailed(cause).cause!!

        assertEquals("java.lang.SecurityException", sanitized.message)
        assertArrayEquals(cause.stackTrace, sanitized.stackTrace)
    }

    @Test
    fun `the whole cause chain is sanitized`() {
        val failure = ContactSyncFailed(IllegalStateException("a", IllegalArgumentException("b", SecurityException("c"))))

        assertEquals(
            listOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException", "java.lang.SecurityException"),
            chain(failure).drop(1).map { it.message }
        )
    }

    @Test
    fun `a self-referencing cause can't loop forever`() {
        class Looping : Exception("loop") {
            override val cause: Throwable get() = this
        }

        assertNull(ContactSyncFailed(Looping()).cause!!.cause)
    }

    @Test
    fun `reminder failures describe the schedule without identifying anyone`() {
        val failure = ExactAlarmNotAllowed(Recurrence.WEEKLY, isWindow = true, minutesUntilDue = 45)

        assertEquals(
            mapOf("recurrence" to "WEEKLY", "is_window" to "true", "minutes_until_due" to "45"),
            failure.details
        )
        assertNull(failure.cause)
    }
}
