package com.pragmaapps.keepintouch.notification

import com.pragmaapps.keepintouch.data.logging.Breadcrumbs
import com.pragmaapps.keepintouch.data.logging.Breadcrumb
import android.telecom.Call
import android.telecom.CallScreeningService
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.logging.CallScreeningFailed
import com.pragmaapps.keepintouch.data.logging.CrashReporter
import com.pragmaapps.keepintouch.data.notification.PhoneCallStateTracker
import com.pragmaapps.keepintouch.data.notification.ScreenedCall
import com.pragmaapps.keepintouch.data.notification.ScreenedCallStore
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.util.CallerNumber
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val TAG = "CallScreeningBackup"

/**
 * Optional backup for phones whose dialer notifications never say who a call was with. Only runs
 * when the user has made this app their call-screening app (RoleManager.ROLE_CALL_SCREENING -
 * offered as an optional onboarding page on phones outside DeviceVendor's verified list, and
 * from Settings on every phone). Android hands every incoming call, and outgoing calls
 * too, to the screening app as it starts; all this does is note the number and direction in
 * [ScreenedCallStore] for CallEpisodeProcessor to use when the call ends.
 *
 * It never blocks, silences, rejects or hides a call: the response is always "allow", and it's
 * sent even if recording fails. Timing still comes from PhoneCallStateTracker, which is registered
 * here too in case nothing else (the notification listener, the app's UI) has registered it yet.
 */
@AndroidEntryPoint
class CallScreeningBackupService : CallScreeningService() {

    @Inject lateinit var screenedCallStore: ScreenedCallStore
    @Inject lateinit var phoneCallStateTracker: PhoneCallStateTracker

    override fun onScreenCall(callDetails: Call.Details) {
        try {
            val number = CallerNumber.fromTelUri(callDetails.handle?.toString())
            val direction = when (callDetails.callDirection) {
                Call.Details.DIRECTION_INCOMING -> Direction.INCOMING
                Call.Details.DIRECTION_OUTGOING -> Direction.OUTGOING
                else -> Direction.UNKNOWN
            }
            Breadcrumbs.log(Breadcrumb.CALL_SCREENED, "direction" to direction, "has_number" to (number != null))
            if (number != null) {
                screenedCallStore.record(ScreenedCall(number, direction, System.currentTimeMillis()))
                AppLogger.i(TAG, "Screened $direction call (number recorded)")
            } else {
                AppLogger.d(TAG, "Screened $direction call with no usable number (private/short code)")
            }
            phoneCallStateTracker.register()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Failed to record screened call - the call itself is unaffected", t)
            CrashReporter.report(CallScreeningFailed(t))
        } finally {
            try {
                respondToCall(callDetails, CallResponse.Builder().build())
            } catch (t: Throwable) {
                AppLogger.e(TAG, "Failed to respond to screened call", t)
                CrashReporter.report(CallScreeningFailed(t))
            }
        }
    }
}
