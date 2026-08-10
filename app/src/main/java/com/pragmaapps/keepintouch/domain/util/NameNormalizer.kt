package com.pragmaapps.keepintouch.domain.util

import java.text.Normalizer

/**
 * Normalizes a display name into the canonical form used as the match key between WhatsApp call
 * notification names, Contact rows, and Schedule snapshots. Deliberately conservative: it only
 * removes characters that are typically invisible/decorative in a name (emoji, diacritics,
 * redundant whitespace), never letters, digits or punctuation that could disambiguate two people.
 *
 * Emoji are filtered by numeric code-point range rather than by embedding literal emoji glyphs in
 * a regex character class, since several emoji blocks are outside the Basic Multilingual Plane
 * (represented as UTF-16 surrogate pairs) and are error-prone to author correctly as literal
 * source characters. Pure/JVM-only: no Android framework dependency, fully unit-testable.
 */
object NameNormalizer {

    private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    // Code-point ranges (inclusive) treated as decorative/emoji and stripped before matching.
    private val EMOJI_RANGES: List<IntRange> = listOf(
        0x200D..0x200D,     // zero-width joiner
        0x2190..0x21FF,     // arrows
        0x2300..0x23FF,     // misc technical (includes clock/hourglass emoji)
        0x25A0..0x25FF,     // geometric shapes
        0x2600..0x27BF,     // misc symbols & dingbats
        0x2B00..0x2BFF,     // misc symbols and arrows
        0xFE0F..0xFE0F,     // variation selector-16 (emoji presentation)
        0x1F1E6..0x1F1FF,   // regional indicator symbols (flag letters)
        0x1F300..0x1FAFF    // misc symbols/pictographs, emoticons, transport, supplemental symbols
    )

    private fun isEmojiCodePoint(cp: Int): Boolean = EMOJI_RANGES.any { cp in it }

    private fun stripEmoji(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val cp = input.codePointAt(i)
            val charCount = Character.charCount(cp)
            if (!isEmojiCodePoint(cp)) {
                sb.appendCodePoint(cp)
            }
            i += charCount
        }
        return sb.toString()
    }

    fun normalize(rawName: String): String {
        val emojiStripped = stripEmoji(rawName)
        val decomposed = Normalizer.normalize(emojiStripped, Normalizer.Form.NFD)
        val diacriticsStripped = COMBINING_MARKS_REGEX.replace(decomposed, "")
        val collapsedWhitespace = WHITESPACE_REGEX.replace(diacriticsStripped, " ").trim()
        return collapsedWhitespace.lowercase()
    }
}
