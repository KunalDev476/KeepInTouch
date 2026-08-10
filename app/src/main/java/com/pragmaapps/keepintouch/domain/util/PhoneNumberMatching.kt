package com.pragmaapps.keepintouch.domain.util

/**
 * A call notification usually carries a name, but sometimes the OS's own contact-name resolution
 * fails and it carries a raw phone number instead - confirmed on a real device for a cellular
 * call to a number saved in the address book (the outgoing-call notification showed the number,
 * not the contact's name). This is a fallback match path for exactly that case: when the
 * "caller name" from the notification is really just a number, compare it against schedules'
 * saved numbers instead of names. Not WhatsApp-specific - the same fallback applies there too,
 * on the chance WhatsApp ever shows a raw number for an unresolved caller.
 *
 * Comparison is suffix-based (last 10 digits), not exact-string: the same number can appear as
 * "+91 85329 21158", "8532921158", or "085329-21158" depending on formatting/country-code
 * presence, and this is a lightweight match, not a full phone-number-parsing library.
 */
object PhoneNumberMatching {
    private const val SUFFIX_LENGTH = 10
    private const val MIN_DIGITS_TO_COUNT_AS_A_NUMBER = 7
    private val ALLOWED_NON_DIGIT_CHARS = setOf(' ', '+', '-', '(', ')', '.')

    /** True if this text is plausibly a raw phone number rather than a name - no letters, mostly digits. */
    fun looksLikePhoneNumber(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val digitCount = trimmed.count { it.isDigit() }
        val hasOnlyAllowedChars = trimmed.all { it.isDigit() || it in ALLOWED_NON_DIGIT_CHARS }
        return digitCount >= MIN_DIGITS_TO_COUNT_AS_A_NUMBER && hasOnlyAllowedChars
    }

    /** Digits only, then the last [SUFFIX_LENGTH] of them - absorbs country-code/leading-zero formatting differences. */
    fun normalize(number: String): String {
        val digitsOnly = number.filter { it.isDigit() }
        return if (digitsOnly.length > SUFFIX_LENGTH) digitsOnly.takeLast(SUFFIX_LENGTH) else digitsOnly
    }

    fun matches(a: String, b: String): Boolean {
        val normalizedA = normalize(a)
        val normalizedB = normalize(b)
        return normalizedA.isNotEmpty() && normalizedA == normalizedB
    }
}
