package com.pragmaapps.keepintouch.domain.util

/** Masks a phone number for display, keeping only the leading "+" (if any) and the last 2 digits visible. */
object PhoneMasking {
    private const val VISIBLE_SUFFIX_LENGTH = 2

    fun mask(number: String): String {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return trimmed
        val hasPlus = trimmed.startsWith("+")
        val prefix = if (hasPlus) "+" else ""
        val digits = if (hasPlus) trimmed.substring(1) else trimmed
        if (digits.length <= VISIBLE_SUFFIX_LENGTH) return prefix + "•".repeat(digits.length)
        val maskedCount = digits.length - VISIBLE_SUFFIX_LENGTH
        return prefix + "•".repeat(maskedCount) + digits.takeLast(VISIBLE_SUFFIX_LENGTH)
    }
}
