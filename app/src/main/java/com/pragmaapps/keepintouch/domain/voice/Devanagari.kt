package com.pragmaapps.keepintouch.domain.voice

/**
 * Devanagari -> plain Latin letters, the way the same word is usually typed in Hinglish
 * ("राहुल" -> "raahul", "शनिवार" -> "shanivaar"). The Hindi speech recognizer writes names and
 * even English words in Devanagari, while contacts are almost always saved in Latin script - so
 * this runs before any comparison (see PhoneticKey for the spelling tolerance on top of it).
 *
 * Hand-rolled rather than android.icu.text.Transliterator so it stays pure/JVM-only and unit
 * testable, and because ICU's output keeps scholarly diacritics and every inherent vowel
 * ("rāhula"), which is further from how people actually spell names than this is.
 *
 * Inherent-vowel ("schwa") deletion follows the usual Hindi rule: a consonant's implicit "a" is
 * dropped at the end of a word, and in the middle of one when it sits between a vowel on its left
 * and a consonant that keeps its own vowel on its right ("अमरनाथ" -> "amarnaath", not "amaranaatha").
 */
object Devanagari {

    private val INDEPENDENT_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o", 'ऍ' to "e"
    )

    private val VOWEL_SIGNS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e"
    )

    private val CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'ळ' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        // Precomposed nukta letters.
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y"
    )

    /** Consonant + combining nukta (U+093C), for text that spells nukta letters decomposed. */
    private val NUKTA_FORMS = mapOf(
        'क' to "q", 'ख' to "kh", 'ग' to "g", 'ज' to "z", 'ड' to "r", 'ढ' to "rh", 'फ' to "f", 'य' to "y"
    )

    private const val NUKTA = '़'
    private const val VIRAMA = '्'
    private const val ANUSVARA = 'ं'
    private const val CHANDRABINDU = 'ँ'
    private const val VISARGA = 'ः'

    fun containsDevanagari(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' }

    /** Converts every Devanagari run in [text]; anything else (Latin, digits, spaces) passes through. */
    fun toLatin(text: String): String {
        if (!containsDevanagari(text)) return text
        val out = StringBuilder()
        val word = StringBuilder()
        for (ch in text) {
            if (ch in 'ऀ'..'ॿ' && ch != '।' && ch != '॥') {
                word.append(ch)
            } else {
                if (word.isNotEmpty()) { out.append(transliterateWord(word.toString())); word.clear() }
                out.append(if (ch == '।' || ch == '॥') ' ' else ch)
            }
        }
        if (word.isNotEmpty()) out.append(transliterateWord(word.toString()))
        return out.toString()
    }

    private sealed class Glyph {
        data class Vowel(val latin: String, var nasal: String = "") : Glyph()
        data class Consonant(
            val latin: String,
            var vowel: String? = INHERENT,
            var nasal: String = ""
        ) : Glyph()
        data class Other(val latin: String) : Glyph()
    }

    private const val INHERENT = "\u0000inherent"

    private fun transliterateWord(word: String): String {
        val units = mutableListOf<Glyph>()
        for (ch in word) {
            val last = units.lastOrNull()
            when {
                ch == NUKTA -> if (last is Glyph.Consonant) {
                    val base = CONSONANTS.entries.firstOrNull { it.value == last.latin }?.key
                    NUKTA_FORMS[base]?.let { units[units.lastIndex] = last.copy(latin = it) }
                }
                ch in CONSONANTS -> units += Glyph.Consonant(CONSONANTS.getValue(ch))
                ch in INDEPENDENT_VOWELS -> units += Glyph.Vowel(INDEPENDENT_VOWELS.getValue(ch))
                ch in VOWEL_SIGNS -> if (last is Glyph.Consonant) last.vowel = VOWEL_SIGNS.getValue(ch)
                    else units += Glyph.Vowel(VOWEL_SIGNS.getValue(ch))
                ch == VIRAMA -> if (last is Glyph.Consonant) last.vowel = null
                ch == ANUSVARA || ch == CHANDRABINDU -> when (last) {
                    is Glyph.Consonant -> last.nasal = "n"
                    is Glyph.Vowel -> last.nasal = "n"
                    else -> units += Glyph.Other("n")
                }
                ch == VISARGA -> units += Glyph.Other("h")
                ch in '०'..'९' -> units += Glyph.Other(('0' + (ch - '०')).toString())
                ch == 'ॐ' -> units += Glyph.Other("om")
                else -> Unit
            }
        }

        // Right-to-left so each consonant's right-hand neighbour has already been decided.
        for (i in units.indices.reversed()) {
            val unit = units[i] as? Glyph.Consonant ?: continue
            if (unit.vowel != INHERENT || unit.nasal.isNotEmpty()) continue
            val isLast = units.drop(i + 1).none { it is Glyph.Consonant || it is Glyph.Vowel }
            if (isLast) {
                // A lone consonant word (e.g. "न") keeps its vowel.
                if (i > 0) unit.vowel = null
                continue
            }
            val left = units.getOrNull(i - 1)
            val right = units.getOrNull(i + 1)
            val leftHasVowel = left is Glyph.Vowel || (left is Glyph.Consonant && left.vowel != null)
            val rightKeepsVowel = right is Glyph.Consonant && right.vowel != null
            if (i > 0 && leftHasVowel && rightKeepsVowel) unit.vowel = null
        }

        return buildString {
            for (unit in units) {
                when (unit) {
                    is Glyph.Vowel -> append(unit.latin).append(unit.nasal)
                    is Glyph.Consonant -> {
                        append(unit.latin)
                        when (unit.vowel) {
                            null -> Unit
                            INHERENT -> append('a')
                            else -> append(unit.vowel)
                        }
                        append(unit.nasal)
                    }
                    is Glyph.Other -> append(unit.latin)
                }
            }
        }
    }
}
