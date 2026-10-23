package com.pragmaapps.keepintouch.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.database.getStringOrNull
import com.pragmaapps.keepintouch.data.db.dao.ContactDao
import com.pragmaapps.keepintouch.data.db.entity.ContactEntity
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ContactRepository"

/** WhatsApp's own account type for the raw-contact it creates per synced phone contact. */
private const val WHATSAPP_ACCOUNT_TYPE = "com.whatsapp"

/** Data-row MIME types WhatsApp attaches to a contact's raw-contact (belt-and-suspenders alongside the account-type check). */
private const val MIME_WHATSAPP_PROFILE = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
private const val MIME_WHATSAPP_VOIP_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"

/**
 * Loads the device address book (read-only) into Room, tagging each contact with whether a
 * WhatsApp raw-contact / Data row was found for them. This is presence detection only - it says
 * "this person has WhatsApp linked in the address book," never anything about a specific call.
 */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactDao: ContactDao
) : ContactRepository {

    override fun observeAll(): Flow<List<Contact>> = contactDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun findByLookupKey(lookupKey: String): Contact? =
        contactDao.findByLookupKey(lookupKey)?.toDomain()

    override suspend fun findById(id: Long): Contact? = contactDao.findById(id)?.toDomain()

    override suspend fun findByIds(ids: List<Long>): List<Contact> =
        if (ids.isEmpty()) emptyList() else contactDao.findByIds(ids).map { it.toDomain() }

    override suspend fun findByNormalizedName(normalizedName: String): List<Contact> =
        contactDao.findByNormalizedName(normalizedName).map { it.toDomain() }

    override suspend fun learnAvatarHash(contactId: Long, hash: String) {
        contactDao.learnAvatarHash(contactId, hash)
    }

    private data class WhatsAppPresence(val rawContactId: Long)

    override suspend fun syncContacts(): Int = withContext(Dispatchers.IO) {
        try {
            syncContactsUnsafe()
        } catch (t: Throwable) {
            AppLogger.e(TAG, "syncContacts failed - address book left unchanged", t)
            0
        }
    }

    private suspend fun syncContactsUnsafe(): Int {
        val resolver = context.contentResolver

        // 1) contactId -> WhatsApp raw-contact, from RawContacts.ACCOUNT_TYPE.
        val whatsAppByContactId = HashMap<Long, WhatsAppPresence>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.CONTACT_ID),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(WHATSAPP_ACCOUNT_TYPE),
            null
        )?.use { cursor ->
            val rawIdCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.CONTACT_ID)
            while (cursor.moveToNext()) {
                val rawId = cursor.getLong(rawIdCol)
                val contactId = cursor.getLong(contactIdCol)
                whatsAppByContactId.putIfAbsent(contactId, WhatsAppPresence(rawId))
            }
        }

        // 2) Belt-and-suspenders: contactId -> WhatsApp raw-contact, from Data MIME rows.
        resolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.RAW_CONTACT_ID,
                ContactsContract.Data.MIMETYPE
            ),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            arrayOf(MIME_WHATSAPP_PROFILE, MIME_WHATSAPP_VOIP_CALL),
            null
        )?.use { cursor ->
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val rawContactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.Data.RAW_CONTACT_ID)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdCol)
                val rawId = cursor.getLong(rawContactIdCol)
                whatsAppByContactId.putIfAbsent(contactId, WhatsAppPresence(rawId))
            }
        }

        // 3) contactId -> best phone number (super-primary > primary > first seen).
        data class NumberCandidate(val number: String, val isSuperPrimary: Boolean, val isPrimary: Boolean)
        val numbersByContactId = HashMap<Long, NumberCandidate>()
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val contactIdCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val superPrimaryCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
            val primaryCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(contactIdCol)
                val number = cursor.getStringOrNull(numberCol) ?: continue
                val isSuperPrimary = cursor.getInt(superPrimaryCol) != 0
                val isPrimary = cursor.getInt(primaryCol) != 0
                val existing = numbersByContactId[contactId]
                val candidate = NumberCandidate(number, isSuperPrimary, isPrimary)
                if (existing == null ||
                    (candidate.isSuperPrimary && !existing.isSuperPrimary) ||
                    (candidate.isPrimary && !existing.isSuperPrimary && !existing.isPrimary)
                ) {
                    numbersByContactId[contactId] = candidate
                }
            }
        }

        // 4) The contacts themselves.
        val contacts = mutableListOf<ContactEntity>()
        val seenLookupKeys = mutableListOf<String>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
            ),
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val lookupKeyCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idCol)
                val lookupKey = cursor.getStringOrNull(lookupKeyCol) ?: continue
                val displayName = cursor.getStringOrNull(nameCol) ?: continue
                val whatsApp = whatsAppByContactId[contactId]
                seenLookupKeys.add(lookupKey)
                contacts.add(
                    ContactEntity(
                        lookupKey = lookupKey,
                        contactId = contactId,
                        displayName = displayName,
                        normalizedName = NameNormalizer.normalize(displayName),
                        primaryNumber = numbersByContactId[contactId]?.number,
                        hasWhatsApp = whatsApp != null,
                        whatsappRawContactId = whatsApp?.rawContactId
                    )
                )
            }
        }

        // Preserve any avatar hash already learned for a contact across re-syncs.
        val merged = contacts.map { fresh ->
            val existing = contactDao.findByLookupKey(fresh.lookupKey)
            if (existing != null) fresh.copy(id = existing.id, learnedAvatarHash = existing.learnedAvatarHash) else fresh
        }

        contactDao.insertAll(merged)
        if (seenLookupKeys.isNotEmpty()) {
            contactDao.deleteMissing(seenLookupKeys)
        }
        return merged.size
    }

    override suspend fun resolveCurrentLookupKey(staleLookupKey: String): String? = withContext(Dispatchers.IO) {
        try {
            // Querying CONTENT_LOOKUP_URI by key alone makes the provider fall back to the raw
            // contacts encoded in a stale key, rather than requiring an exact current-key match.
            val lookupUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, Uri.encode(staleLookupKey))
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.Contacts.LOOKUP_KEY),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getStringOrNull(0) else null
            }
        } catch (t: Throwable) {
            AppLogger.e(TAG, "resolveCurrentLookupKey failed", t)
            null
        }
    }

    override suspend fun renameContact(contactId: Long, newDisplayName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver

            // Only the non-WhatsApp raw-contact(s) under this aggregate are safely writable -
            // WhatsApp's own sync adapter owns its raw-contact and edits to it aren't reliably
            // persisted (may be silently reverted on its next sync).
            val editableRawContactIds = mutableListOf<Long>()
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID)
                val accountTypeCol = cursor.getColumnIndexOrThrow(ContactsContract.RawContacts.ACCOUNT_TYPE)
                while (cursor.moveToNext()) {
                    if (cursor.getStringOrNull(accountTypeCol) != WHATSAPP_ACCOUNT_TYPE) {
                        editableRawContactIds.add(cursor.getLong(idCol))
                    }
                }
            }

            if (editableRawContactIds.isEmpty()) {
                AppLogger.e(TAG, "renameContact: no editable (non-WhatsApp) raw-contact found for contactId=$contactId")
                return@withContext false
            }

            val ops = ArrayList<ContentProviderOperation>()
            for (rawContactId in editableRawContactIds) {
                var structuredNameDataId: Long? = null
                resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(ContactsContract.Data._ID),
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(rawContactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        structuredNameDataId = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data._ID))
                    }
                }

                val dataId = structuredNameDataId
                ops.add(
                    if (dataId != null) {
                        ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI)
                            .withSelection("${ContactsContract.Data._ID} = ?", arrayOf(dataId.toString()))
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newDisplayName)
                            .build()
                    } else {
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                            .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                            .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, newDisplayName)
                            .build()
                    }
                )
            }

            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (t: Throwable) {
            AppLogger.e(TAG, "renameContact failed for contactId=$contactId", t)
            false
        }
    }
}
