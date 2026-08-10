package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneMaskingTest {

    @Test
    fun `masks a long international number keeping the leading plus and last two digits`() {
        assertEquals("+••••••••••10", PhoneMasking.mask("+919876543210"))
    }

    @Test
    fun `masks a number with no leading plus`() {
        assertEquals("••••••••10", PhoneMasking.mask("9876543210"))
    }

    @Test
    fun `very short numbers are fully masked`() {
        assertEquals("••", PhoneMasking.mask("12"))
    }

    @Test
    fun `empty string stays empty`() {
        assertEquals("", PhoneMasking.mask(""))
    }

    @Test
    fun `trims surrounding whitespace before masking`() {
        assertEquals("+••••••••••10", PhoneMasking.mask("  +919876543210  "))
    }
}
