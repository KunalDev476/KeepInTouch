package com.pragmaapps.keepintouch.domain.model

/**
 * One call "episode" (WhatsApp or a regular cellular call) rebuilt from
 * NotificationListenerService callbacks that share the same notification key. See [com.pragmaapps.keepintouch.domain.util.QualifyingCallRule] for how
 * [qualified] is derived.
 */
data class CallEvent(
    val id: Long,
    val packageName: String,
    val notifKey: String,
    /** Human-readable trace of every state transition observed for this episode. */
    val statesSeen: String,
    /**
     * Answered/connected: android.showChronometer was observed true, or - for a dialer call with
     * call-state tracking - the call state showed an answered incoming call (see CellularCallTiming),
     * or an outgoing call's status text cleared after dialing (see DialerCallSignals).
     */
    val connected: Boolean,
    val isVideo: Boolean,
    val isGroupCall: Boolean,
    val direction: Direction,
    /**
     * Caller display name as the notification rendered it. A dialer may show a raw phone number
     * here instead of a name (see PhoneNumberMatching); WhatsApp never does in practice.
     */
    val notificationName: String,
    val postTime: Long,
    val ongoingWhen: Long?,
    /**
     * SystemClock.elapsedRealtime() captured locally when ONGOING was first observed - required
     * to implement the fallback duration formula when the notification's own timestamp basis is
     * unavailable. Not part of the literal field list, but necessary to compute [inferredDurationMs].
     */
    val ongoingPostedElapsedRealtime: Long?,
    val endTime: Long?,
    val inferredDurationMs: Long?,
    val qualified: Boolean,
    val avatarHash: String?,
    val matchedScheduleId: Long?,
    val matchConfidence: MatchConfidence?,
    val rawJson: String
)
