package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Schedule

/** Asks the user, after a call, whether it should complete [schedule] - see UnconfirmedOutgoingCallRule. */
interface PostCallConfirmationNotifier {
    fun show(event: CallEvent, schedule: Schedule)
}
