package com.pragmaapps.keepintouch.domain.voice

import java.text.Normalizer

/**
 * Collapses the many ways one spoken Indian name or Hinglish word gets spelled into a single
 * comparison key - "Rahul", "Raahul", "Rahool", "राहुल" all become "rahul"; "Shaniwar" and
 * "शनिवार" both become "sanivar". Only ever used by voice scheduling (contact search, keyword
 * spotting, wake word). Caller -> schedule matching deliberately stays exact-name only - see
 * MatchCallToScheduleUseCase - so none of this tolerance leaks into deciding which call
 * fulfils which schedule.
 */
object PhoneticKey {

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^a-z0-9]")
    private val TOKEN_SPLIT = Regex("[^\\p{L}\\p{M}\\p{N}:.]+")
    private const val VOWELS = "aeiou"

    /** Splits raw recognizer output into words, keeping "6:30"-style times together. */
    fun tokenize(text: String): List<String> =
        text.split(TOKEN_SPLIT).map { it.trim('.') }.filter { it.isNotEmpty() }

    fun of(word: String): String {
        val latin = Devanagari.toLatin(word)
        val stripped = COMBINING_MARKS.replace(Normalizer.normalize(latin, Normalizer.Form.NFD), "")
        var s = NON_ALNUM.replace(stripped.lowercase(), "")
        if (s.isEmpty() || s.all { it.isDigit() }) return s

        s = s.replace("ph", "f")
            .replace("ck", "k")
            .replace('w', 'v')
            .replace('z', 'j')
            .replace('q', 'k')
            .replace("x", "ks")
            .replace('y', 'i')

        // Aspiration is the most common spelling/transliteration disagreement (bh/b, th/t,
        // sh/s, chh/ch...), so an "h" straight after a consonant is dropped entirely.
        s = buildString {
            for ((i, c) in s.withIndex()) {
                val prev = s.getOrNull(i - 1)
                if (c == 'h' && prev != null && prev !in VOWELS && !prev.isDigit()) continue
                append(c)
            }
        }

        s = s.replace("ee", "i").replace("oo", "u").replace("ou", "u")
        s = collapseRepeats(s)
        if (s.length > 1 && s.endsWith('h') && s[s.length - 2] in VOWELS) s = s.dropLast(1)
        if (s.length > 3 && s.endsWith('a')) s = s.dropLast(1)
        return s
    }

    private fun collapseRepeats(s: String): String = buildString {
        for (c in s) if (isEmpty() || last() != c) append(c)
    }

    /** How many single-letter edits two keys may differ by and still count as the same word. */
    fun allowedDistance(key: String): Int = when {
        key.length >= 8 -> 2
        key.length >= 4 -> 1
        else -> 0
    }

    fun isClose(a: String, b: String): Boolean {
        if (a == b) return true
        val allowed = minOf(allowedDistance(a), allowedDistance(b))
        return allowed > 0 && levenshtein(a, b, allowed) <= allowed
    }

    /** Edit distance, bailing out early once it's certain to exceed [limit]. */
    fun levenshtein(a: String, b: String, limit: Int = Int.MAX_VALUE): Int {
        if (kotlin.math.abs(a.length - b.length) > limit) return limit + 1
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            var rowMin = curr[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost)
                rowMin = minOf(rowMin, curr[j])
            }
            if (rowMin > limit) return limit + 1
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }
}
