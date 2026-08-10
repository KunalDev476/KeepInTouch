package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NameNormalizerTest {

    @Test
    fun `lowercases and trims`() {
        assertEquals("priya", NameNormalizer.normalize("  Priya  "))
    }

    @Test
    fun `collapses internal whitespace`() {
        assertEquals("priya sharma", NameNormalizer.normalize("Priya   Sharma"))
    }

    @Test
    fun `strips emoji decorations`() {
        assertEquals("priya", NameNormalizer.normalize("Priya ❤️"))
    }

    @Test
    fun `strips supplementary-plane emoji`() {
        // U+1F600 GRINNING FACE - outside the BMP, encoded as a UTF-16 surrogate pair.
        assertEquals("mom", NameNormalizer.normalize("Mom 😀"))
    }

    @Test
    fun `strips diacritics`() {
        assertEquals("jose", NameNormalizer.normalize("José"))
    }

    @Test
    fun `identical after normalization means a match`() {
        assertEquals(NameNormalizer.normalize("Mummy"), NameNormalizer.normalize("mummy "))
    }

    @Test
    fun `does not strip ordinary letters digits or punctuation`() {
        assertEquals("dr. rahul 2", NameNormalizer.normalize("Dr. Rahul 2"))
    }

    @Test
    fun `empty string normalizes to empty string`() {
        assertEquals("", NameNormalizer.normalize(""))
    }
}
