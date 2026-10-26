package com.pragmaapps.keepintouch.domain.voice

/** One recognizer hypothesis, split into words plus each word's [PhoneticKey]. */
class Utterance(val raw: String) {
    val words: List<String> = PhoneticKey.tokenize(raw)
    val lowerWords: List<String> = words.map { it.lowercase() }
    val keys: List<String> = words.map { PhoneticKey.of(it) }

    val size: Int get() = words.size
    fun isEmpty(): Boolean = words.isEmpty()
}

/**
 * A keyword or short phrase in either script ("har hafte", "हर हफ्ते"). Matched word-by-word on
 * phonetic keys, so spelling variants don't each need listing - only genuinely different
 * words/scripts do. Short keys (under 5 letters) must match exactly: "do"/"de"/"di" or "ka"/"ki"
 * are different words, and fuzzy-matching them would make everything match everything.
 */
class Phrase(text: String) {
    val keys: List<String> = PhoneticKey.tokenize(text).map { PhoneticKey.of(it) }

    fun matchesAt(utterance: Utterance, start: Int): Boolean {
        if (keys.isEmpty() || start < 0 || start + keys.size > utterance.size) return false
        return keys.indices.all { i -> keyMatches(keys[i], utterance.keys[start + i]) }
    }

    private fun keyMatches(expected: String, actual: String): Boolean =
        if (expected.length < 5) expected == actual else PhoneticKey.isClose(expected, actual)
}

/** A set of interchangeable [Phrase]s that all mean the same thing. */
class Vocabulary(vararg phrases: String) {
    val phrases: List<Phrase> = phrases.map { Phrase(it) }.sortedByDescending { it.keys.size }

    /** Every non-overlapping place any phrase occurs, longest phrase preferred at each position. */
    fun findAll(utterance: Utterance): List<IntRange> {
        val hits = mutableListOf<IntRange>()
        var i = 0
        while (i < utterance.size) {
            val phrase = phrases.firstOrNull { it.matchesAt(utterance, i) }
            if (phrase != null) {
                hits += i until i + phrase.keys.size
                i += phrase.keys.size
            } else {
                i++
            }
        }
        return hits
    }

    fun occursIn(utterance: Utterance): Boolean = findAll(utterance).isNotEmpty()

    fun matchesWord(utterance: Utterance, index: Int): Boolean =
        phrases.any { it.keys.size == 1 && it.matchesAt(utterance, index) }
}
