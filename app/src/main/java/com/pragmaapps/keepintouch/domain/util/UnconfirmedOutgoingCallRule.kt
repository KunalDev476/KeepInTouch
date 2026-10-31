package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction

/**
 * What to do with an outgoing dialer call kept on the full-screen call screen the whole time: its
 * notification never showed an answer (no chronometer, no status text clearing), so call detection
 * timed it from the call state alone and - since an outgoing call goes OFFHOOK at dial, not at
 * answer - couldn't tell whether anyone picked up. It's left not qualified; this rule decides
 * what happens next, from the dial-to-hang-up time:
 *  - under [ASK_USER_BELOW_MS]: could just as well have rung out - ask the user (post-call notification).
 *  - [ASK_USER_BELOW_MS] or longer: long enough to have been a conversation - complete the schedule.
 *
 * Only calls whose timing came from the call-state fallback qualify: a call whose notification
 * showed the answer (the user left the call screen, so the in-call notification carried the
 * chronometer or status change) was already judged by call detection, and a call no notification
 * ever named has no caller to match - both are left alone. Pure, so it's unit-testable.
 */
object UnconfirmedOutgoingCallRule {
    const val ASK_USER_BELOW_MS = 30_000L

    /** Written into statesSeen by call detection when a dialer call is timed from the call state alone. */
    private const val CALL_STATE_FALLBACK_MARKER = "PHONE_IDLE_FALLBACK"
    private const val UNKNOWN_NAME = "(unknown)"
    private val OFFHOOK = Regex("""PHONE_OFFHOOK@(\d+)""")
    private val IDLE = Regex("""PHONE_IDLE@(\d+)""")

    enum class Decision { NONE, ASK_USER, AUTO_COMPLETE }

    fun decide(event: CallEvent, countOutgoing: Boolean): Decision {
        if (!countOutgoing) return Decision.NONE
        if (event.qualified || event.connected) return Decision.NONE
        if (event.direction != Direction.OUTGOING || event.isGroupCall || event.isVideo) return Decision.NONE
        if (CALL_STATE_FALLBACK_MARKER !in event.statesSeen || event.notificationName == UNKNOWN_NAME) return Decision.NONE
        val duration = dialToHangUpMs(event.statesSeen) ?: return Decision.NONE
        return if (duration < ASK_USER_BELOW_MS) Decision.ASK_USER else Decision.AUTO_COMPLETE
    }

    /** OFFHOOK (dial) to IDLE (hang-up), from the call-state trace in statesSeen; null if either is missing. */
    fun dialToHangUpMs(statesSeen: String): Long? {
        val offhook = OFFHOOK.find(statesSeen)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        val idle = IDLE.find(statesSeen)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        return (idle - offhook).coerceAtLeast(0)
    }
}
