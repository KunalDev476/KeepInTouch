package com.pragmaapps.keepintouch.domain.util

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.util.UnconfirmedOutgoingCallRule.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnconfirmedOutgoingCallRuleTest {

    /** An outgoing call kept on the full-screen call screen, timed from the call state alone. */
    private fun fullScreenOutgoing(dialToHangUpMs: Long) = CallEvent(
        id = 1,
        packageName = "com.android.incallui",
        notifKey = "0|com.android.incallui|268451840|null|1001",
        statesSeen = "UNKNOWN(no android.callType extra)@post=1000;" +
            "PHONE_RINGING@null;PHONE_OFFHOOK@1000;PHONE_IDLE@${1000 + dialToHangUpMs};PHONE_IDLE_FALLBACK",
        connected = false,
        isVideo = false,
        isGroupCall = false,
        direction = Direction.OUTGOING,
        notificationName = "Mummy",
        postTime = 1000,
        ongoingWhen = null,
        ongoingPostedElapsedRealtime = null,
        endTime = 1000 + dialToHangUpMs,
        inferredDurationMs = null,
        qualified = false,
        avatarHash = null,
        matchedScheduleId = null,
        matchConfidence = null,
        rawJson = "[]"
    )

    @Test
    fun `short full-screen outgoing call asks the user`() {
        assertEquals(Decision.ASK_USER, UnconfirmedOutgoingCallRule.decide(fullScreenOutgoing(13_000), countOutgoing = true))
        assertEquals(Decision.ASK_USER, UnconfirmedOutgoingCallRule.decide(fullScreenOutgoing(29_999), countOutgoing = true))
    }

    @Test
    fun `full-screen outgoing call of 30s or more completes the schedule`() {
        assertEquals(Decision.AUTO_COMPLETE, UnconfirmedOutgoingCallRule.decide(fullScreenOutgoing(30_000), countOutgoing = true))
        assertEquals(Decision.AUTO_COMPLETE, UnconfirmedOutgoingCallRule.decide(fullScreenOutgoing(95_000), countOutgoing = true))
    }

    @Test
    fun `call whose notification showed the answer is left to call detection`() {
        // User left the call screen: the notification carried the answer, so it's connected.
        val answered = fullScreenOutgoing(20_000).copy(connected = true, statesSeen = "UNKNOWN@post=1000;STATUS_CLEARED_ANSWER;PHONE_IDLE@21000")
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(answered, countOutgoing = true))
        // Connected but timed by notifications only - no call-state fallback marker.
        val timedByNotification = fullScreenOutgoing(20_000).copy(statesSeen = "UNKNOWN@post=1000;PHONE_IDLE@elapsed=5")
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(timedByNotification, countOutgoing = true))
    }

    @Test
    fun `qualified, incoming, unnamed, video, group, or excluded outgoing calls are left alone`() {
        val base = fullScreenOutgoing(10_000)
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base.copy(qualified = true), countOutgoing = true))
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base.copy(direction = Direction.INCOMING), countOutgoing = true))
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base.copy(notificationName = "(unknown)"), countOutgoing = true))
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base.copy(isVideo = true), countOutgoing = true))
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base.copy(isGroupCall = true), countOutgoing = true))
        assertEquals(Decision.NONE, UnconfirmedOutgoingCallRule.decide(base, countOutgoing = false))
    }

    @Test
    fun `dial to hang-up time comes from the call-state trace`() {
        assertEquals(13_000L, UnconfirmedOutgoingCallRule.dialToHangUpMs("PHONE_RINGING@null;PHONE_OFFHOOK@1000;PHONE_IDLE@14000;PHONE_IDLE_FALLBACK"))
        assertNull(UnconfirmedOutgoingCallRule.dialToHangUpMs("PHONE_RINGING@null;PHONE_OFFHOOK@null;PHONE_IDLE@14000"))
    }
}
