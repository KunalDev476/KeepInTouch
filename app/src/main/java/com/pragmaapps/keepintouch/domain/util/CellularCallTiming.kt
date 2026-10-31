package com.pragmaapps.keepintouch.domain.util

/** Grace for a notification posted a moment before the call-state callback that opened its session. */
const val SESSION_POST_TIME_GRACE_MS = 5_000L

/**
 * One telephony call "session" as seen through READ_PHONE_STATE call-state callbacks: from the
 * first non-IDLE state until the phone is IDLE again. Wall-clock millis. No phone number - the call
 * state API never exposes one; who the call was with always comes from the dialer's notification.
 */
data class CallSession(
    val startedAtMs: Long,
    /** First CALL_STATE_RINGING in this session, if any - only an incoming call rings. */
    val ringingAtMs: Long?,
    /** First CALL_STATE_OFFHOOK in this session, if any - an answered incoming call, or an outgoing call from the moment it's dialed. */
    val offhookAtMs: Long?,
    val idleAtMs: Long
) {
    fun covers(postTimeMs: Long): Boolean = postTimeMs >= startedAtMs - SESSION_POST_TIME_GRACE_MS && postTimeMs <= idleAtMs

    /** Rang before (or without) going OFFHOOK - an incoming call. A waiting call ringing during an outgoing one doesn't count. */
    val beganRinging: Boolean
        get() = ringingAtMs != null && (offhookAtMs == null || ringingAtMs <= offhookAtMs)
}

/**
 * Fallback timing for a dialer call whose notifications never showed a chronometer: works out
 * whether it connected, and for how long, from the call state. Pure - no Android framework types -
 * so it's unit-testable. Calls the notifications already timed never come through here.
 *
 * Why this is needed: on some OEMs (confirmed on a real device: ColorOS's com.android.incallui), a
 * call answered on the full-screen call screen never gets a "Current call" notification with a
 * running chronometer - the ringing notification is just removed at answer and nothing else is
 * posted until hang-up. The call state still goes RINGING -> OFFHOOK -> IDLE, so that's the only
 * remaining record of when the call was answered and when it ended.
 *
 * The call state is used for timing only:
 *  - Start: OFFHOOK, but only after RINGING - i.e. an answered incoming call. An outgoing call goes
 *    OFFHOOK the moment it's dialed, before anyone answers, so OFFHOOK alone would count an
 *    unanswered outgoing call as connected; an outgoing call only counts as connected through its
 *    notification's chronometer, never through this fallback.
 *  - End: IDLE, the moment the call actually ended - not when the notification happened to go away.
 */
object CellularCallTiming {

    data class Result(val connected: Boolean, val startMs: Long?, val endMs: Long, val durationMs: Long?)

    fun compute(session: CallSession): Result {
        val start = session.offhookAtMs?.takeIf { offhook ->
            session.ringingAtMs != null && offhook >= session.ringingAtMs
        }
        return Result(
            connected = start != null,
            startMs = start,
            endMs = session.idleAtMs,
            durationMs = start?.let { (session.idleAtMs - it).coerceAtLeast(0) }
        )
    }

    /**
     * For a call the call state saw but no dialer notification ever named (an outgoing call that
     * stayed on the full-screen call screen until hang-up) - diagnostics only. OFFHOOK -> IDLE, so
     * for an outgoing call this includes dialing/ringing time.
     */
    fun offhookDurationMs(session: CallSession): Long? =
        session.offhookAtMs?.let { (session.idleAtMs - it).coerceAtLeast(0) }
}
