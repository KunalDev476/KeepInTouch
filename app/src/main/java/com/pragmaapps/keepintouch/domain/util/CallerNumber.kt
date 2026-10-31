package com.pragmaapps.keepintouch.domain.util

/**
 * Reads the caller's phone number from a notification Person's uri - the standard "tel:" form a
 * CallStyle notification's caller Person may carry. Confirmed on a real device: Samsung's
 * com.android.incallui shows only a generic "Call" as title, text and Person name, but its
 * android.callPerson / android.people.list Person has uri "tel:%2B919015156562". Nothing here is
 * OEM-specific: any dialer that fills in a tel: uri is handled the same way.
 *
 * Pure - no Android framework types - so it's unit-testable.
 */
object CallerNumber {
    private const val TEL_SCHEME = "tel:"
    private const val MIN_DIGITS = 7

    /**
     * The decoded number from a "tel:" uri ("tel:%2B919015156562" -> "+919015156562"), or null if
     * [uri] isn't a tel: uri or has too few digits to be a real phone number (e.g. a short code).
     */
    fun fromTelUri(uri: String?): String? {
        if (uri == null || !uri.startsWith(TEL_SCHEME, ignoreCase = true)) return null
        val number = percentDecode(uri.substring(TEL_SCHEME.length)).trim()
        return number.takeIf { it.count(Char::isDigit) >= MIN_DIGITS }
    }

    /**
     * Minimal %XX decoding. Not java.net.URLDecoder: that turns a literal '+' into a space, and an
     * unencoded "tel:+91..." uri is just as valid as an encoded one. A malformed escape is kept as-is.
     */
    private fun percentDecode(text: String): String {
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '%' && i + 2 < text.length) {
                val code = text.substring(i + 1, i + 3).toIntOrNull(16)
                if (code != null) {
                    out.append(code.toChar())
                    i += 3
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}
