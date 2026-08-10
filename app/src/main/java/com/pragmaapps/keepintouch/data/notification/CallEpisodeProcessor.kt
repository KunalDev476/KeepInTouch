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
import com.pragmaapps.keepintouch.domain.util.QualifyingCallRule
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CallEpisodeProcessor"

/**
 * Rebuilds WhatsApp call "episodes" from raw NotificationListenerService callbacks, grouping by
 * notification key. Every public entry point is wrapped so a single malformed/unexpected
 * notification can never crash listener processing or drop the episode silently.
 */
@Singleton
class CallEpisodeProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callEventRepository: CallEventRepository,
    private val processFinalizedCall: ProcessFinalizedCallUseCase,
    private val detectionPreferences: DetectionPreferences
) {
    suspend fun onPosted(sbn: StatusBarNotification) {
        try {
            handlePosted(sbn)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onPosted failed for key=${sbn.key}; episode processing continues for others", t)
        }
    }

    suspend fun onRemoved(sbn: StatusBarNotification) {
        try {
            handleRemoved(sbn)
        } catch (t: Throwable) {
            AppLogger.e(TAG, "onRemoved failed for key=${sbn.key}", t)
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

        // Some sources (confirmed on a real device: com.android.incallui) reuse the exact same
        // notifKey across entirely separate, unrelated calls WITHOUT ever firing
        // onNotificationRemoved in between - observed two different callers, ~8 minutes apart,
        // sharing one key. A caller's identity can't change mid-call, so a known, resolved name
        // that doesn't match the in-flight episode's already-known name is a reliable "this is
        // actually a different call" signal - finalize the stale episode with whatever data it
        // has rather than silently merging two different people's calls into one row.
        if (existing != null && parsed.callerName != null &&
            existing.notificationName != "(unknown)" && parsed.callerName != existing.notificationName
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
            val updated = existing.copy(
                statesSeen = "${existing.statesSeen};$stateLabel@post=${sbn.postTime}",
                connected = existing.connected || isOngoingNow,
                isVideo = existing.isVideo || parsed.isVideo,
                isGroupCall = existing.isGroupCall || isGroupHitNow,
                direction = computeDirection(existing.direction, parsed),
                notificationName = parsed.callerName ?: existing.notificationName,
                ongoingWhen = existing.ongoingWhen ?: if (isOngoingNow) parsed.notificationWhen else null,
                ongoingPostedElapsedRealtime = existing.ongoingPostedElapsedRealtime
                    ?: if (isOngoingNow) elapsedNow else null,
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
        finalizeEpisode(existing, System.currentTimeMillis(), SystemClock.elapsedRealtime(), reason = "REMOVED")
    }

    /** Shared by a real onNotificationRemoved callback and the stale-key-reuse override above. */
    private suspend fun finalizeEpisode(existing: CallEvent, endTime: Long, elapsedNow: Long, reason: String) {
        val inferredDuration = computeDurationMs(existing, endTime, elapsedNow)
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
            statesSeen = "${existing.statesSeen};$reason@elapsed=$elapsedNow",
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
