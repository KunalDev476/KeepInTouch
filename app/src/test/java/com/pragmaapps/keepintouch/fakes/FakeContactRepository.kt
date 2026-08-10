package com.pragmaapps.keepintouch.fakes

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake for pure unit tests - no Room, no Android framework. */
class FakeContactRepository(initial: List<Contact> = emptyList()) : ContactRepository {
    private val state = MutableStateFlow(initial)

    override fun observeAll() = state

    override suspend fun findByLookupKey(lookupKey: String): Contact? = state.value.find { it.lookupKey == lookupKey }
    override suspend fun findById(id: Long): Contact? = state.value.find { it.id == id }
    override suspend fun findByIds(ids: List<Long>): List<Contact> = state.value.filter { it.id in ids }
    override suspend fun findByNormalizedName(normalizedName: String): List<Contact> =
        state.value.filter { it.normalizedName == normalizedName }

    override suspend fun syncContacts(): Int = state.value.size

    override suspend fun learnAvatarHash(contactId: Long, hash: String) {
        state.value = state.value.map { if (it.id == contactId) it.copy(learnedAvatarHash = hash) else it }
    }

    override suspend fun renameContact(contactId: Long, newDisplayName: String): Boolean {
        if (state.value.none { it.contactId == contactId }) return false
        state.value = state.value.map {
            if (it.contactId == contactId) {
                it.copy(
                    displayName = newDisplayName,
                    normalizedName = com.pragmaapps.keepintouch.domain.util.NameNormalizer.normalize(newDisplayName)
                )
            } else it
        }
        return true
    }
}
