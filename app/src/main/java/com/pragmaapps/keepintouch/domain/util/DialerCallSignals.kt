package com.pragmaapps.keepintouch.domain.util

/** How long after a dialer episode is finalized a re-post of the same key is still treated as its "Call ended" leftover. */
const val POST_CALL_REPOST_WINDOW_MS = 10_000L

/**
 * Pure helpers for dialer (cellular) call notifications that carry no android.callType and no
 * chronometer - confirmed on a real device: ColorOS's com.android.incallui. No Android framework
 * types, so they're unit-testable.
 */
object DialerCallSignals {

    /**
     * True when two caller names on the same notification are the same caller shown two ways: one
     * is a raw phone number, the other a name. Confirmed on a real device: an outgoing call's
     * notification first showed "+91 85329 21158", then "Mummy" 22ms later, and flipped back and
     * forth several times within the first second of the call.
     */
    fun isNumberNameVariant(a: String, b: String): Boolean =
        PhoneNumberMatching.looksLikePhoneNumber(a) != PhoneNumberMatching.looksLikePhoneNumber(b)

    /** Keeps a resolved name over a raw number, so a flip back to the number never overwrites it. */
    fun preferredName(existing: String, incoming: String?): String {
        if (incoming == null) return existing
        if (existing == UNKNOWN_NAME) return incoming
        val incomingIsNumber = PhoneNumberMatching.looksLikePhoneNumber(incoming)
        val existingIsNumber = PhoneNumberMatching.looksLikePhoneNumber(existing)
        return if (incomingIsNumber && !existingIsNumber) existing else incoming
    }

    /**
     * An outgoing dialer call with no chronometer is answered when its status text clears after a
     * dialing status. Confirmed on a real device: "Calling" -> "Waiting…" -> "" exactly when the
     * other side picked up, while an unanswered call went "Calling" -> "User busy…" and never
     * cleared. Only "non-blank -> blank" is checked, never the words themselves, so it doesn't
     * depend on the device language. A call can start with a blank text before "Calling" is shown,
     * which is why a previous non-blank status is required.
     */
    fun isOutgoingAnswerTransition(previousStatus: String?, currentStatus: String?): Boolean =
        !previousStatus.isNullOrBlank() && currentStatus.isNullOrBlank()

    const val UNKNOWN_NAME = "(unknown)"
}
