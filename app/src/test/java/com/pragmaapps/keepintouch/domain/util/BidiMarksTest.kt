package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class BidiMarksTest {

    @Test
    fun `strips a left-to-right mark in front of a number`() {
        assertEquals("+919015156562", BidiMarks.strip("‎+919015156562"))
    }

    @Test
    fun `strips embeddings, isolates and the byte-order mark`() {
        assertEquals("+91 90151 56562", BidiMarks.strip("‪+91 90151 56562‬"))
        assertEquals("+919015156562", BidiMarks.strip("⁨+919015156562⁩"))
        assertEquals("Mummy", BidiMarks.strip("﻿Mummy‏"))
    }

    @Test
    fun `keeps zero-width joiners, which change how Indic names render`() {
        val name = "क्‍ष" // क्‍ष with a zero-width joiner
        assertEquals(name, BidiMarks.strip(name))
    }

    @Test
    fun `returns the same instance when there is nothing to strip`() {
        val name = "Abhiranjan Sir Pragmaapps"
        assertSame(name, BidiMarks.strip(name))
    }
}
