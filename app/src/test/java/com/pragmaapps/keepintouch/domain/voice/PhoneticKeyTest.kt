package com.pragmaapps.keepintouch.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticKeyTest {

    @Test
    fun `devanagari names transliterate to how they're usually typed`() {
        assertEquals("raahul", Devanagari.toLatin("राहुल"))
        assertEquals("shanivaar", Devanagari.toLatin("शनिवार"))
        assertEquals("kamal", Devanagari.toLatin("कमल"))
        assertEquals("sanjay", Devanagari.toLatin("संजय"))
        assertEquals("amarnaath", Devanagari.toLatin("अमरनाथ"))
        assertEquals("sharmaa", Devanagari.toLatin("शर्मा"))
    }

    @Test
    fun `latin text passes through untouched`() {
        assertEquals("Rahul 6:30", Devanagari.toLatin("Rahul 6:30"))
    }

    @Test
    fun `spelling and script variants of one name share a key`() {
        val rahul = PhoneticKey.of("Rahul")
        assertEquals(rahul, PhoneticKey.of("Raahul"))
        assertEquals(rahul, PhoneticKey.of("राहुल"))
        assertTrue(PhoneticKey.isClose(rahul, PhoneticKey.of("Rahool")))
        assertEquals(PhoneticKey.of("Shaniwar"), PhoneticKey.of("शनिवार"))
        assertEquals(PhoneticKey.of("Sharma"), PhoneticKey.of("शर्मा"))
        assertEquals(PhoneticKey.of("Siddharth"), PhoneticKey.of("Sidharth"))
    }

    @Test
    fun `different short names are never treated as close`() {
        assertNotEquals(PhoneticKey.of("Ravi"), PhoneticKey.of("Ramu"))
        assertFalse(PhoneticKey.isClose(PhoneticKey.of("Raj"), PhoneticKey.of("Ram")))
    }

    @Test
    fun `tokenize keeps clock times together`() {
        assertEquals(listOf("shaam", "6:30", "baje"), PhoneticKey.tokenize("shaam 6:30 baje."))
    }

    @Test
    fun `wake word is found in either script and returns the rest of the sentence`() {
        val wake = WakeWord("Jarvis")
        assertEquals("Rahul ke liye schedule banao", wake.find("Jarvis, Rahul ke liye schedule banao"))
        assertEquals("", wake.find("जार्विस"))
        assertEquals("", wake.find("ok jarvi"))
        assertEquals(null, wake.find("kya haal hai"))
    }
}
