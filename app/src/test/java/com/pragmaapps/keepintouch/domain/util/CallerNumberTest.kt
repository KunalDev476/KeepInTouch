package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerNumberTest {

    @Test
    fun `decodes Samsung's percent-encoded tel uri`() {
        assertEquals("+919015156562", CallerNumber.fromTelUri("tel:%2B919015156562"))
    }

    @Test
    fun `last 10 digits of the decoded number are what schedules match on`() {
        assertEquals("9015156562", PhoneNumberMatching.normalize(CallerNumber.fromTelUri("tel:%2B919015156562")!!))
    }

    @Test
    fun `keeps an unencoded plus sign`() {
        assertEquals("+919015156562", CallerNumber.fromTelUri("tel:+919015156562"))
    }

    @Test
    fun `scheme is case-insensitive`() {
        assertEquals("9015156562", CallerNumber.fromTelUri("TEL:9015156562"))
    }

    @Test
    fun `null, non-tel and contact uris give no number`() {
        assertNull(CallerNumber.fromTelUri(null))
        assertNull(CallerNumber.fromTelUri("mailto:someone@example.com"))
        assertNull(CallerNumber.fromTelUri("content://com.android.contacts/contacts/lookup/abc/1"))
    }

    @Test
    fun `short codes are not phone numbers`() {
        assertNull(CallerNumber.fromTelUri("tel:121"))
        assertNull(CallerNumber.fromTelUri("tel:%2A123%23"))
    }

    @Test
    fun `malformed escape is kept as-is`() {
        assertEquals("9015156562%Z", CallerNumber.fromTelUri("tel:9015156562%Z"))
    }
}
