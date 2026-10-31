package com.pragmaapps.keepintouch.data.notification

import android.content.Context
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.prefs.DetectionPreferences
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.usecase.ProcessFinalizedCallUseCase
import com.pragmaapps.keepintouch.domain.util.CallSession
import com.pragmaapps.keepintouch.domain.util.CellularCallTiming
import com.pragmaapps.keepintouch.domain.util.DialerCallSignals
import com.pragmaapps.keepintouch.domain.util.POST_CALL_REPOST_WINDOW_MS
import com.pragmaapps.keepintouch.domain.util.QualifyingCallRule
import com.pragmaapps.keepintouch.domain.util.SESSION_POST_TIME_GRACE_MS
import com.pragmaapps.keepintouch.notification.isCellularDialerPackage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallEpisodeProcessor"

/** Synthetic "packageName" tag for a call only the call state saw - no dialer notification ever named it. */
const val PHONE_STATE_SOURCE = "android.phonestate"

private const val AWAITING_IDLE_MARKER = "REMOVED_AWAITING_IDLE"
private const val STATUS_CLEARED_MARKER = "STATUS_CLEARED_ANSWER"

/**
 * Rebuilds call "episodes" (WhatsApp and regular cellular calls alike) from raw
 * NotificationListenerService callbacks, grouping by notification key. Every public entry point is
 * wrapped so a single malformed/unexpected notification can never crash listener processing or
 * drop the episode silently.
 *
 * Timing comes from notifications. For dialer calls, [PhoneCallStateTracker] (READ_PHONE_STATE)
 * supplies the end (IDLE) and, for a call that never posts a chronometer notification, the answer
 * time too - see handleRemoved, handleSessionEnded and CellularCallTiming. Every entry point runs under one [mutex]: a
 * notification removal and the call-state IDLE for the same call arrive within milliseconds of
 * each other, and both read-modify-write the same row.
 */
@Singleton
class CallEpisodeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callEventRepository: CallEventRepository,
    private val processFinalizedCall: ProcessFinalizedCallUseCase,
    private val detectionPreferences: DetectionPreferences,
    private val phoneCallStateTracker: PhoneCallStateTracker
) {
    private val mutex = Mutex()

    suspend fun onPosted(sbn: StatusBarNotification) {
        try {
            mutex.withLock { handlePosted(sbn) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onPosted failed for key=${sbn.key}; episode processing continues for others", t)
        }
    }

    suspend fun onRemoved(sbn: StatusBarNotification) {
        try {
            mutex.withLock { handleRemoved(sbn) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onRemoved failed for key=${sbn.key}", t)
        }
    }

    /** Called for every call-state session that ends (IDLE) - see PhoneCallStateTracker.sessions. */
    suspend fun onCallSessionEnded(session: CallSession) {
        try {
            mutex.withLock { handleSessionEnded(session) }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onCallSessionEnded failed", t)
        }
    }

    private suspend fun handlePosted(sbn: StatusBarNotification) {
        val parsed = CallNotificationExtras.parse(sbn)
        if (parsed.callType == null) {
            AppLogger.d(
                TAG,
                "Tracking call-shaped notification without android.callType from ${sbn.packageName} " +
                    "(key=${sbn.key}) - falling back to showChronometer=${parsed.showChronometer}/statusText=\"${parsed.statusText}\""
            )
        }

        val elapsedNow = SystemClock.elapsedRealtime()
        val stateLabel = stateLabelFor(parsed.callType)
        val jsonEntry = CallNotificationExtras.toJson(sbn, parsed, elapsedNow)
        // Only ever returns an in-flight (unfinalized) episode for this key - notifKey can be
        // reused across separate, sequential calls (e.g. a missed call, then a later fresh ring),
        // so a previously-finalized row for the same key must never be merged into; it's history.
        var existing = callEventRepository.findByNotifKey(sbn.key)

        // A dialer re-posts its notification right after hang-up ("Call ended 00:20" - confirmed on
        // ColorOS's com.android.incallui), after handleSessionEnded already finalized the call.
        // That's the same call's leftover, not a new one - starting an episode for it would record
        // and match the call a second time.
        if (existing == null && isPostCallRepost(sbn)) {
            AppLogger.d(TAG, "Ignoring dialer re-post after its call ended (key=${sbn.key}, statusText=\"${parsed.statusText}\")")
            return
        }

        // Some sources (confirmed on a real device: com.android.incallui) reuse the exact same
        // notifKey across entirely separate, unrelated calls WITHOUT ever firing
        // onNotificationRemoved in between - observed two different callers, ~8 minutes apart,
        // sharing one key. A caller's identity can't change mid-call, so a known, resolved name
        // that doesn't match the in-flight episode's already-known name is a reliable "this is
        // actually a different call" signal - finalize the stale episode with whatever data it
        // has rather than silently merging two different people's calls into one row. The one
        // exception is the same call showing its caller as a number and then as a name (or back)
        // - see isSameCallerShownDifferently.
        if (existing != null && parsed.callerName != null &&
            existing.notificationName != DialerCallSignals.UNKNOWN_NAME && parsed.callerName != existing.notificationName &&
            !isSameCallerShownDifferently(existing, parsed.callerName)
        ) {
            AppLogger.d(
                TAG,
                "notifKey=${sbn.key} reused for a different caller (\"${existing.notificationName}\" -> " +
                    "\"${parsed.callerName}\") - finalizing the stale episode instead of merging into it",
                crashlyticsMessage = "notifKey=${sbn.key} reused for a different caller (existing.id=${existing.id}) - " +
                    "finalizing the stale episode instead of merging into it"
            )
            finalizeEpisode(existing, System.currentTimeMillis(), elapsedNow, reason = "STALE_KEY_REUSE")
            existing = null
        }

        // An episode left waiting for the call-state fallback whose IDLE never arrived (tracking
        // stopped, process killed mid-call) must not absorb a later call that reuses its key - it
        // only merges with a post from the same, still-ongoing call (e.g. "Current call" posted
        // once the user leaves the full-screen call screen).
        if (existing != null && AWAITING_IDLE_MARKER in existing.statesSeen && !belongsToCurrentCall(existing)) {
            finalizeEpisode(existing, System.currentTimeMillis(), elapsedNow, reason = "STALE_AWAITING_IDLE")
            existing = null
        }

        val isGroupHitNow = parsed.groupCallCandidateHits.values.any { it }
        val avatarHashNow by lazy { AvatarHasher.hash(context, parsed.largeIcon ?: parsed.callerIcon) }
        // showChronometer, not android.callType, is the authoritative "connected" signal - it's
        // literally the flag that starts the live call-duration counter, so "true" means the call
        // is actually answered, not just attempted. Confirmed this distinction matters on a real
        // capture: for a WhatsApp OUTGOING call, android.callType flips to ONGOING(2) the moment
        // the *other* phone starts ringing - before anyone has answered - and only showChronometer
        // flips true once someone actually picks up. Treating callType==ONGOING alone as
        // "connected" (the old logic) would anchor the duration timer at ring-start, overcounting
        // by however long it rang. showChronometer isn't CallStyle-specific either - it's present
        // (and reliable) on every source captured so far, WhatsApp and cellular alike.
        val isOngoingNow = parsed.showChronometer

        if (existing == null) {
            val direction = computeDirection(Direction.UNKNOWN, parsed)
            val event = CallEvent(
                id = 0,
                packageName = sbn.packageName,
                notifKey = sbn.key,
                statesSeen = "$stateLabel@post=${sbn.postTime}",
                connected = isOngoingNow,
                isVideo = parsed.isVideo,
                isGroupCall = isGroupHitNow,
                direction = direction,
                notificationName = parsed.callerName ?: "(unknown)",
                postTime = sbn.postTime,
                ongoingWhen = if (isOngoingNow) parsed.notificationWhen else null,
                ongoingPostedElapsedRealtime = if (isOngoingNow) elapsedNow else null,
                endTime = null,
                inferredDurationMs = null,
                qualified = false,
                avatarHash = avatarHashNow,
                matchedScheduleId = null,
                matchConfidence = null,
                rawJson = CallNotificationExtras.mergeJsonArray(null, jsonEntry)
            )
            callEventRepository.upsert(event)
        } else {
            val direction = computeDirection(existing.direction, parsed)
            // An outgoing dialer call that never shows a chronometer: its status text clearing
            // after "Calling"/"Waiting…" is the only sign of answer (see DialerCallSignals). Timed
            // from now to the call state's IDLE in handleSessionEnded.
            val answeredByStatus = !existing.connected && !isOngoingNow &&
                direction == Direction.OUTGOING &&
                isCellularDialerPackage(sbn.packageName) &&
                phoneCallStateTracker.isTracking && phoneCallStateTracker.isCallActive &&
                DialerCallSignals.isOutgoingAnswerTransition(CallNotificationExtras.lastStatusText(existing.rawJson), parsed.statusText)
            if (answeredByStatus) {
                AppLogger.d(TAG, "Outgoing dialer call answered - status text cleared (key=${sbn.key})")
            }
            val updated = existing.copy(
                statesSeen = "${existing.statesSeen};$stateLabel@post=${sbn.postTime}" +
                    if (answeredByStatus) ";$STATUS_CLEARED_MARKER" else "",
                connected = existing.connected || isOngoingNow || answeredByStatus,
                isVideo = existing.isVideo || parsed.isVideo,
                isGroupCall = existing.isGroupCall || isGroupHitNow,
                direction = direction,
                notificationName = DialerCallSignals.preferredName(existing.notificationName, parsed.callerName),
                ongoingWhen = existing.ongoingWhen ?: when {
                    isOngoingNow -> parsed.notificationWhen
                    answeredByStatus -> System.currentTimeMillis()
                    else -> null
                },
                ongoingPostedElapsedRealtime = existing.ongoingPostedElapsedRealtime
                    ?: if (isOngoingNow || answeredByStatus) elapsedNow else null,
                avatarHash = existing.avatarHash ?: avatarHashNow,
                rawJson = CallNotificationExtras.mergeJsonArray(existing.rawJson, jsonEntry)
            )
            callEventRepository.update(updated)
        }
    }

    private suspend fun handleRemoved(sbn: StatusBarNotification) {
        val existing = callEventRepository.findByNotifKey(sbn.key) ?: run {
            AppLogger.d(TAG, "onNotificationRemoved for unknown key=${sbn.key}; no episode to finalize")
            return
        }
        if (existing.endTime != null) return // already finalized (duplicate removal callback)

        // Fallback for one case only: a dialer call answered and kept on the full-screen call
        // screen. There (confirmed on a real device: ColorOS's com.android.incallui) the ringing
        // notification is removed at ANSWER and no "Current call" notification with a chronometer
        // is ever posted, so this removal isn't the end of the call and nothing here says when it
        // will end. The removal even lands ~50ms before the call state goes OFFHOOK, so nothing can
        // be decided yet: wait for the call state's IDLE (handleSessionEnded) instead.
        //
        // Everything else stays notification-only: WhatsApp, a dialer call whose notification
        // already showed a chronometer (connected), and any removal once the phone is idle again.
        if (isAwaitingCallStateFallback(existing)) {
            if (AWAITING_IDLE_MARKER !in existing.statesSeen) {
                callEventRepository.update(
                    existing.copy(statesSeen = "${existing.statesSeen};$AWAITING_IDLE_MARKER@elapsed=${SystemClock.elapsedRealtime()}")
                )
            }
            AppLogger.d(TAG, "Dialer notification removed mid-call (key=${sbn.key}); waiting for call-state IDLE")
            return
        }
        finalizeEpisode(existing, System.currentTimeMillis(), SystemClock.elapsedRealtime(), reason = "REMOVED")
    }

    private fun belongsToCurrentCall(episode: CallEvent): Boolean =
        phoneCallStateTracker.currentSessionStartedAtMs?.let { episode.postTime >= it - SESSION_POST_TIME_GRACE_MS } ?: false

    /**
     * The number <-> name flip only counts as the same caller within one call session (or when the
     * call state isn't tracked, so there's nothing to tell sessions apart) - a key reused by a
     * later call from someone else must still split off.
     */
    private fun isSameCallerShownDifferently(existing: CallEvent, callerName: String): Boolean =
        DialerCallSignals.isNumberNameVariant(existing.notificationName, callerName) &&
            (!phoneCallStateTracker.isTracking || belongsToCurrentCall(existing))

    private suspend fun isPostCallRepost(sbn: StatusBarNotification): Boolean {
        if (!isCellularDialerPackage(sbn.packageName)) return false
        if (!phoneCallStateTracker.isTracking || phoneCallStateTracker.isCallActive) return false
        val endTime = callEventRepository.findLatestFinalizedByNotifKey(sbn.key)?.endTime ?: return false
        return System.currentTimeMillis() - endTime in 0..POST_CALL_REPOST_WINDOW_MS
    }

    /**
     * A dialer notification removed mid-call isn't the end of the call (see handleRemoved) - with
     * no chronometer it's the ringing notification removed at answer, and even a connected
     * episode is better ended by the call state's IDLE than by whenever the notification goes away.
     */
    private fun isAwaitingCallStateFallback(existing: CallEvent): Boolean =
        isCellularDialerPackage(existing.packageName) &&
            phoneCallStateTracker.isTracking &&
            phoneCallStateTracker.isCallActive

    /**
     * Finalizes every still-open dialer episode of this call at IDLE - the moment the call actually
     * ended - whether or not its notification is still showing (an answered incoming call's
     * notification can stay up until after hang-up; confirmed on ColorOS). An episode the
     * notifications already timed (chronometer, or an outgoing call's status text clearing) ends
     * at IDLE with that timing; any other one gets its timing from the call state.
     */
    private suspend fun handleSessionEnded(session: CallSession) {
        val sessionDialerEpisodes = callEventRepository.findPostedBetween(session.startedAtMs - SESSION_POST_TIME_GRACE_MS, session.idleAtMs)
            .filter { isCellularDialerPackage(it.packageName) && session.covers(it.postTime) }
        if (sessionDialerEpisodes.isEmpty()) {
            recordUnnamedCall(session)
            return
        }
        // If some notification for this call did show a chronometer (e.g. the in-call UI used a
        // separate key for "Current call"), the notifications already timed it - the fallback
        // would only double-count the same call.
        val notificationsTimedIt = sessionDialerEpisodes.any { it.connected }
        val idleElapsed = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - session.idleAtMs).coerceAtLeast(0)
        sessionDialerEpisodes
            .filter { it.endTime == null }
            .map { if (session.beganRinging) it.copy(direction = Direction.INCOMING) else it }
            .forEach { episode ->
                if (episode.connected || notificationsTimedIt) {
                    finalizeEpisode(episode, session.idleAtMs, idleElapsed, reason = "PHONE_IDLE")
                } else {
                    finalizeWithSession(episode, session, reason = "PHONE_IDLE_FALLBACK")
                }
            }
    }

    /** Finalizes a dialer episode with its call-state session supplying the timing. */
    private suspend fun finalizeWithSession(existing: CallEvent, session: CallSession, reason: String) {
        val timing = CellularCallTiming.compute(session)
        finalize(
            existing = existing.copy(
                connected = timing.connected,
                ongoingWhen = timing.startMs,
                rawJson = CallNotificationExtras.mergeJsonArray(existing.rawJson, sessionJson(session))
            ),
            endTime = timing.endMs,
            inferredDuration = timing.durationMs,
            stateTrace = "${phoneStateTrace(session)};$reason",
            reason = reason
        )
    }

    /**
     * A call the call state saw end with no dialer notification ever posted for it - typically an
     * outgoing call that stayed on the full-screen call screen until hang-up. There's no name to
     * match, so it's recorded for Call Logs diagnostics only: never qualified, never matched.
     */
    private suspend fun recordUnnamedCall(session: CallSession) {
        val offhookDuration = CellularCallTiming.offhookDurationMs(session) ?: return // rang and stopped - nothing worth recording
        val event = CallEvent(
            id = 0,
            packageName = PHONE_STATE_SOURCE,
            notifKey = "phonestate:${session.startedAtMs}",
            statesSeen = "${phoneStateTrace(session)};PHONE_STATE_ONLY(no call notification - caller unknown)",
            connected = true,
            isVideo = false,
            isGroupCall = false,
            direction = Direction.UNKNOWN,
            notificationName = "(unknown)",
            postTime = session.startedAtMs,
            ongoingWhen = session.offhookAtMs,
            ongoingPostedElapsedRealtime = null,
            endTime = session.idleAtMs,
            inferredDurationMs = offhookDuration,
            qualified = false,
            avatarHash = null,
            matchedScheduleId = null,
            matchConfidence = null,
            rawJson = CallNotificationExtras.mergeJsonArray(null, sessionJson(session))
        )
        callEventRepository.upsert(event)
        AppLogger.i(TAG, "Call seen only via call state (no notification): offhook duration=${offhookDuration}ms - recorded for diagnostics only")
    }

    private fun phoneStateTrace(session: CallSession): String =
        "PHONE_RINGING@${session.ringingAtMs};PHONE_OFFHOOK@${session.offhookAtMs};PHONE_IDLE@${session.idleAtMs}"

    private fun sessionJson(session: CallSession): JSONObject = JSONObject().apply {
        put("source", PHONE_STATE_SOURCE)
        put("sessionStartedAt", session.startedAtMs)
        put("ringingAt", session.ringingAtMs)
        put("offhookAt", session.offhookAtMs)
        put("idleAt", session.idleAtMs)
    }

    /** Shared by a real onNotificationRemoved callback and the stale-key-reuse override above. */
    private suspend fun finalizeEpisode(existing: CallEvent, endTime: Long, elapsedNow: Long, reason: String) {
        finalize(
            existing = existing,
            endTime = endTime,
            inferredDuration = computeDurationMs(existing, endTime, elapsedNow),
            stateTrace = "$reason@elapsed=$elapsedNow",
            reason = reason
        )
    }

    private suspend fun finalize(existing: CallEvent, endTime: Long, inferredDuration: Long?, stateTrace: String, reason: String) {
        val qualified = QualifyingCallRule.isQualified(
            QualifyingCallRule.Signals(
                connected = existing.connected,
                direction = existing.direction,
                isGroupCall = existing.isGroupCall,
                isVideo = existing.isVideo,
                inferredDurationMs = inferredDuration
            ),
            detectionPreferences.settings.value
        )

        val finalized = existing.copy(
            statesSeen = "${existing.statesSeen};$stateTrace",
            endTime = endTime,
            inferredDurationMs = inferredDuration,
            qualified = qualified
        )
        callEventRepository.update(finalized)
        AppLogger.i(
            TAG,
            "Call finalized ($reason): \"${finalized.notificationName}\" from ${finalized.packageName}, " +
                "direction=${finalized.direction}, durationMs=$inferredDuration, qualified=$qualified",
            crashlyticsMessage = "Call finalized ($reason): id=${finalized.id} from ${finalized.packageName}, " +
                "direction=${finalized.direction}, durationMs=$inferredDuration, qualified=$qualified"
        )
        processFinalizedCall(finalized)
    }

    private fun computeDurationMs(event: CallEvent, endTime: Long, endElapsed: Long): Long? {
        val ongoingWhen = event.ongoingWhen
        if (ongoingWhen != null) {
            return (endTime - ongoingWhen).coerceAtLeast(0)
        }
        val ongoingElapsed = event.ongoingPostedElapsedRealtime
        if (ongoingElapsed != null) {
            return (endElapsed - ongoingElapsed).coerceAtLeast(0)
        }
        return null // never reached ONGOING - no basis for a duration, so it cannot qualify.
    }

    /**
     * Prefers the documented android.callType extra. When that's absent (e.g. com.android.incallui),
     * falls back to two signals, checked in order:
     *  1. actionCount - structural, locale-independent: a ringing call conventionally offers two
     *     actions (Answer + Decline), a connected/dialing one offers one (Hang up). This is the
     *     primary fallback specifically so direction doesn't depend on guessing an exact English
     *     string that may not exist on other OEMs/locales.
     *  2. statusText - free text, confirmed only against one English-locale capture ("Incoming
     *     call" / "Current call"), kept as a secondary cue for whichever case actionCount can't
     *     distinguish (e.g. it's already >=2 actions but doesn't scream "ringing").
     * A miss on both just leaves direction UNKNOWN (never qualifies) - it can't cause a false
     * qualification either way.
     */
    private fun computeDirection(previous: Direction, parsed: CallNotificationExtras.Parsed): Direction {
        if (previous != Direction.UNKNOWN) return previous
        parsed.callType?.let {
            return when (it) {
                CALL_TYPE_INCOMING -> Direction.INCOMING
                // ONGOING observed with NO prior state at all for this key: tentatively OUTGOING.
                CALL_TYPE_ONGOING -> Direction.OUTGOING
                else -> Direction.UNKNOWN
            }
        }

        // The call state saw this call ring first - incoming, whatever the notification looks like
        // (confirmed on ColorOS: an incoming call's notification showed only "India" as its text).
        if (phoneCallStateTracker.currentSessionBeganRinging) return Direction.INCOMING

        val text = parsed.statusText?.lowercase()
        val looksLikeRinging = parsed.actionCount >= 2 || text?.contains("incoming") == true
        if (looksLikeRinging) return Direction.INCOMING

        // Not ringing (fewer than two actions, and the text doesn't say "incoming" either) with
        // no prior state at all for this key: a call already past the ringing stage that was
        // never seen ringing has to have been placed by the user - tentatively OUTGOING, whether
        // or not the text happens to confirm it ("outgoing"/"current"/"dialing"/"calling").
        return Direction.OUTGOING
    }

    private fun stateLabelFor(callType: Int?): String = when (callType) {
        CALL_TYPE_INCOMING -> "INCOMING"
        CALL_TYPE_ONGOING -> "ONGOING"
        CALL_TYPE_SCREENING -> "SCREENING"
        null -> "UNKNOWN(no android.callType extra)"
        else -> "UNKNOWN($callType)"
    }

    companion object {
        const val CALL_TYPE_INCOMING = 1
        const val CALL_TYPE_ONGOING = 2
        const val CALL_TYPE_SCREENING = 3
    }
}
