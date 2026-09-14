package com.pragmaapps.keepintouch.domain.util

/**
 * Contact-search matching for the People screen. Deliberately stricter than a plain substring
 * search: a query only matches a name if it's a PREFIX of that name or of one of its space-separated
 * parts (so "ma" matches "Manoj Kumar" and "Raj Mahesh", but not "Aman") - a bare `.contains(...)`
 * would surface "Aman" for "ma" too, which reads as noise once a contact list gets large.
 */
object SearchMatcher {
    private val WHITESPACE_REGEX = Regex("\\s+")

    fun matches(name: String, query: String): Boolean {
        if (query.isBlank()) return true
        val trimmedQuery = query.trim()
        return name.trim().split(WHITESPACE_REGEX).any { it.startsWith(trimmedQuery, ignoreCase = true) }
    }

    /** True when [query] looks like someone typing a phone number rather than a name - digits only. */
    fun isNumberQuery(query: String): Boolean {
        val trimmed = query.trim()
        return trimmed.isNotEmpty() && trimmed.all { it.isDigit() }
    }

    /** Digits-only substring match against a saved number, absorbing spacing/formatting differences. */
    fun matchesNumber(number: String?, query: String): Boolean {
        if (number == null) return false
        val queryDigits = query.filter { it.isDigit() }
        if (queryDigits.isEmpty()) return false
        return number.filter { it.isDigit() }.contains(queryDigits)
    }
}
