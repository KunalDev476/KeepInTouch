package com.pragmaapps.keepintouch.domain.util

/**
 * Removes invisible text-direction controls that a dialer may wrap around a caller name or number
 * so it renders correctly in right-to-left layouts. Confirmed on a real device: ColorOS's
 * com.android.incallui titles an outgoing call to an unresolved number as "‎+919015156562" -
 * a LEFT-TO-RIGHT MARK in front of the number, invisible on screen, which made the number fail
 * PhoneNumberMatching.looksLikePhoneNumber() and get matched as a name instead (NoMatch).
 *
 * Only direction controls and the byte-order mark are stripped - never zero-width (non-)joiners,
 * which are meaningful in Indic scripts and change how a name renders. Pure/JVM-only.
 */
object BidiMarks {

    private fun isBidiControl(c: Char): Boolean =
        c == '‎' || c == '‏' || // LRM, RLM
            c == '؜' ||                // ARABIC LETTER MARK
            c in '‪'..'‮' ||      // LRE, RLE, PDF, LRO, RLO
            c in '⁦'..'⁩' ||      // LRI, RLI, FSI, PDI
            c == '﻿'                   // BOM / zero-width no-break space

    fun strip(text: String): String =
        if (text.none(::isBidiControl)) text else text.filterNot(::isBidiControl)
}
