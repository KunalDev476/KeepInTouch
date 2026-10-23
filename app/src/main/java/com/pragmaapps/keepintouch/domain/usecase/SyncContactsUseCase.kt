package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Re-reads the address book, then brings every schedule's name/number snapshot in line with its
 * contact. Without the second step, renaming a scheduled contact leaves the schedule showing - and
 * matching calls against - the old name, while WhatsApp's notification already shows the new one.
 *
 * Always use this rather than ContactRepository.syncContacts() directly. Concurrent callers (app
 * resume, People tab resume, the contacts observer, a call that didn't match) share one run: a
 * caller arriving mid-sync waits for it to finish instead of starting another full re-read.
 */
@Singleton
class SyncContactsUseCase @Inject constructor(
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository
) {
    private val mutex = Mutex()

    /** Returns how many schedules had their snapshot refreshed (0 when this call joined another's sync). */
    suspend operator fun invoke(): Int {
        if (!mutex.tryLock()) {
            mutex.withLock { } // someone else is syncing - just wait for their result
            return 0
        }
        return try {
            contactRepository.syncContacts()
            refreshScheduleSnapshots()
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Paused and completed schedules are refreshed too, so every list shows the current name. A
     * schedule whose contact can't be found at all (deleted from the address book) keeps its last
     * snapshot untouched.
     */
    private suspend fun refreshScheduleSnapshots(): Int {
        val contactsByLookupKey = contactRepository.observeAll().first().associateBy { it.lookupKey }
        if (contactsByLookupKey.isEmpty()) return 0 // no permission / nothing synced - nothing to compare against

        var refreshedCount = 0
        for (schedule in scheduleRepository.observeAll().first()) {
            val contact = contactsByLookupKey[schedule.contactLookupKey]
                ?: contactRepository.resolveCurrentLookupKey(schedule.contactLookupKey)?.let { contactsByLookupKey[it] }
                ?: continue
            val refreshed = refreshedSnapshot(schedule, contact)
            if (refreshed != schedule) {
                scheduleRepository.update(refreshed) // also re-arms the reminder, so its text uses the new name
                refreshedCount++
            }
        }
        return refreshedCount
    }

    private fun refreshedSnapshot(schedule: Schedule, contact: Contact): Schedule = schedule.copy(
        contactLookupKey = contact.lookupKey,
        displayNameSnapshot = contact.displayName,
        normalizedNameSnapshot = NameNormalizer.normalize(contact.displayName),
        // A contact that lost its number keeps the last known one - still useful for matching
        // calls that only show a number.
        numberSnapshot = contact.primaryNumber ?: schedule.numberSnapshot
    )
}
