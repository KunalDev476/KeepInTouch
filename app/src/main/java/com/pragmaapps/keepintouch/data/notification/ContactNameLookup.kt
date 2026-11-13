package com.pragmaapps.keepintouch.data.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.pragmaapps.keepintouch.data.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContactNameLookup"

/**
 * The saved contact name for a phone number, via the contacts provider's own number matching
 * (PhoneLookup). Used only to give a call known just by its number a readable name; matching to a
 * schedule still goes by the number itself. Read-only, READ_CONTACTS only, null on any failure.
 */
@Singleton
class ContactNameLookup @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun displayNameFor(number: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() } else null }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "Contact lookup by number failed", t)
            null
        }
    }
}
