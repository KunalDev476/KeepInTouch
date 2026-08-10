package com.pragmaapps.keepintouch.domain.model

/**
 * One WhatsApp call "episode" rebuilt from NotificationListenerService callbacks that share the
 * same notification key. See [com.pragmaapps.keepintouch.domain.util.QualifyingCallRule] for how
 * [qualified] is derived.
 */
data class CallEvent(
    val id: Long,
    val packageName: String,
    val notifKey: String,
    /** Human-readable trace of every state transition observed for this episode. */
    val statesSeen: String,
    /** True iff an ONGOING (android.callType == 2) state was ever observed - answered/connected. */
    val connected: Boolean,
    val isVideo: Boolean,
    val isGroupCall: Boolean,
    val direction: Direction,
    /** Caller display name as WhatsApp rendered it in the notification - no phone number is ever available. */
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
