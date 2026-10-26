package com.pragmaapps.keepintouch.domain.voice

/**
 * Spots the assistant's wake word (BuildConfig.WAKE_WORD, "Jarvis" by default) in a recognizer
 * hypothesis, tolerating script and spelling ("जार्विस", "Jaarvis"), and hands back whatever was
 * said after it - "Jarvis, Rahul ke liye schedule banao" starts the flow with that command
 * instead of making the user repeat it.
 */
class WakeWord(wakeWord: String) {

    private val keys: List<String> = PhoneticKey.tokenize(wakeWord).map { PhoneticKey.of(it) }.filter { it.isNotEmpty() }

    /** Null when the wake word isn't in [text]; otherwise the (possibly empty) text that followed it. */
    fun find(text: String): String? {
        if (keys.isEmpty()) return null
        val words = PhoneticKey.tokenize(text)
        val wordKeys = words.map { PhoneticKey.of(it) }
        for (start in 0..wordKeys.size - keys.size) {
            val hit = keys.indices.all { i -> matches(keys[i], wordKeys[start + i]) }
            if (hit) return words.drop(start + keys.size).joinToString(" ")
        }
        return null
    }

    // A one-letter slip is allowed even on short wake words - "Jarvis" heard as "Jarvi"/"Jervis"
    // is the common case, and nothing else said in this screen is likely to land that close.
    private fun matches(expected: String, actual: String): Boolean =
        expected == actual || (expected.length >= 4 && PhoneticKey.levenshtein(expected, actual, 1) <= 1)
}
