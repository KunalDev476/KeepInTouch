package com.pragmaapps.keepintouch.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameSimilarityTest {

    @Test
    fun `identical strings score 1`() {
        assertEquals(1.0, NameSimilarity.similarity("priya", "priya"), 0.0001)
    }

    @Test
    fun `completely different strings score below the min threshold`() {
        val score = NameSimilarity.similarity("priya", "xyzxyzxyz")
        assertTrue(score < NameSimilarity.MIN_CANDIDATE_THRESHOLD)
    }

    @Test
    fun `one-character typo stays above the auto-match threshold for longer names`() {
        // "sharmaa" vs "sharma" - a single trailing insertion on a 6-char name.
        val score = NameSimilarity.similarity("sharma", "sharmaa")
        assertTrue("expected >= ${NameSimilarity.AUTO_MATCH_THRESHOLD} but was $score", score >= NameSimilarity.AUTO_MATCH_THRESHOLD)
    }

    @Test
    fun `both empty scores 1`() {
        assertEquals(1.0, NameSimilarity.similarity("", ""), 0.0001)
    }

    @Test
    fun `similarity is symmetric`() {
        val a = NameSimilarity.similarity("rahul", "raul")
        val b = NameSimilarity.similarity("raul", "rahul")
        assertEquals(a, b, 0.0001)
    }
}
