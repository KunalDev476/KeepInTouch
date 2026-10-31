package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumberMatchingTest {

    @Test
    fun `a raw number is recognized as a phone number`() {
        assertTrue(PhoneNumberMatching.looksLikePhoneNumber("+918532921158"))
    }

    @Test
    fun `a formatted number with spaces and dashes is still recognized`() {
        assertTrue(PhoneNumberMatching.looksLikePhoneNumber("+91 85329-21158"))
    }

    @Test
    fun `a number wrapped in invisible direction marks is still recognized`() {
        assertTrue(PhoneNumberMatching.looksLikePhoneNumber("\u200E+919015156562"))
        assertTrue(PhoneNumberMatching.looksLikePhoneNumber("\u202A+91 90151 56562\u202C"))
        assertTrue(PhoneNumberMatching.looksLikePhoneNumber("\u2068+919015156562\u2069"))
    }

    @Test
    fun `a direction-marked number matches the saved number`() {
        assertTrue(PhoneNumberMatching.matches("\u200E+919015156562", "+91 90151 56562"))
    }

    @Test
    fun `a name is not a phone number`() {
        assertFalse(PhoneNumberMatching.looksLikePhoneNumber("Mummy"))
    }

    @Test
    fun `a name that happens to contain digits is not a phone number`() {
        assertFalse(PhoneNumberMatching.looksLikePhoneNumber("Amar Sir 2"))
    }

    @Test
    fun `too few digits does not count as a phone number`() {
        assertFalse(PhoneNumberMatching.looksLikePhoneNumber("123"))
    }

    @Test
    fun `same number with and without country code matches via suffix comparison`() {
        assertTrue(PhoneNumberMatching.matches("+918532921158", "8532921158"))
    }

    @Test
    fun `same number with different formatting matches`() {
        assertTrue(PhoneNumberMatching.matches("+91 85329 21158", "085329-21158"))
    }

    @Test
    fun `different numbers do not match`() {
        assertFalse(PhoneNumberMatching.matches("+918532921158", "+919999999999"))
    }

    @Test
    fun `normalize keeps only the last 10 digits`() {
        assertEquals("8532921158", PhoneNumberMatching.normalize("+918532921158"))
    }
}
