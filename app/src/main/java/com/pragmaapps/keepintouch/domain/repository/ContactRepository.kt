package com.pragmaapps.keepintouch.domain.repository

import com.pragmaapps.keepintouch.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    fun observeAll(): Flow<List<Contact>>
    suspend fun findByLookupKey(lookupKey: String): Contact?
    suspend fun findById(id: Long): Contact?
    suspend fun findByIds(ids: List<Long>): List<Contact>
    suspend fun findByNormalizedName(normalizedName: String): List<Contact>

    /** Re-reads the address book via ContentResolver and upserts into Room. Returns the synced count. */
    suspend fun syncContacts(): Int

    suspend fun learnAvatarHash(contactId: Long, hash: String)

    /**
     * Renames this contact's editable raw-contact(s) in the system address book (never the
     * WhatsApp-owned raw-contact, which isn't reliably writable from another app). Requires
     * WRITE_CONTACTS - only ever called after the user explicitly opts into a rename to resolve a
     * name collision between two schedules. Returns false on any failure (permission denied,
     * no editable raw-contact found, provider error) rather than throwing.
     */
    suspend fun renameContact(contactId: Long, newDisplayName: String): Boolean
}
