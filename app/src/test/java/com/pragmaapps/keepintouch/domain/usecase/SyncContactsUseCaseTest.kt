package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.fakes.FakeContactRepository
import com.pragmaapps.keepintouch.fakes.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncContactsUseCaseTest {

    private fun contact(id: Long, lookupKey: String, name: String, number: String? = null) = Contact(
        id = id,
        lookupKey = lookupKey,
        contactId = id,
        displayName = name,
        normalizedName = NameNormalizer.normalize(name),
        primaryNumber = number,
        hasWhatsApp = true,
        whatsappRawContactId = null,
        learnedAvatarHash = null
    )

    private fun schedule(id: Long, lookupKey: String, name: String, number: String? = null, active: Boolean = true) = Schedule(
        id = id,
        contactLookupKey = lookupKey,
        displayNameSnapshot = name,
        normalizedNameSnapshot = NameNormalizer.normalize(name),
        numberSnapshot = number,
        recurrence = Recurrence.WEEKLY,
        startDate = 0,
        nextDueDate = 0,
        active = active,
        note = null,
        createdAt = 0
    )

    @Test
    fun `renamed contact updates the schedule's name snapshot`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Amar Sir")))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Amar")))

        SyncContactsUseCase(contacts, schedules)()

        val updated = schedules.schedules.value.single()
        assertEquals("Amar Sir", updated.displayNameSnapshot)
        assertEquals(NameNormalizer.normalize("Amar Sir"), updated.normalizedNameSnapshot)
    }

    @Test
    fun `after a rename, a call under the new name matches the schedule`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Amar Sir")))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Amar")))

        SyncContactsUseCase(contacts, schedules)()
        val outcome = MatchCallToScheduleUseCase(schedules, contacts)("Amar Sir")

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        assertEquals(1L, (outcome as MatchOutcome.UniqueSchedule).scheduleId)
    }

    @Test
    fun `changed lookup key is resolved and the schedule relinked`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "r5-amarsir", "Amar Sir")))
        contacts.lookupKeyChanges["r5-amar"] = "r5-amarsir"
        val schedules = FakeScheduleRepository(listOf(schedule(1, "r5-amar", "Amar")))

        SyncContactsUseCase(contacts, schedules)()

        val updated = schedules.schedules.value.single()
        assertEquals("r5-amarsir", updated.contactLookupKey)
        assertEquals("Amar Sir", updated.displayNameSnapshot)
    }

    @Test
    fun `changed number updates the number snapshot`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Mummy", number = "+91 98765 43210")))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy", number = "+91 91234 56789")))

        SyncContactsUseCase(contacts, schedules)()

        assertEquals("+91 98765 43210", schedules.schedules.value.single().numberSnapshot)
    }

    @Test
    fun `contact that lost its number keeps the last known one`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Mummy", number = null)))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy", number = "+91 91234 56789")))

        SyncContactsUseCase(contacts, schedules)()

        assertEquals("+91 91234 56789", schedules.schedules.value.single().numberSnapshot)
    }

    @Test
    fun `unchanged contact leaves the schedule untouched`() = runTest {
        val original = schedule(1, "k1", "Mummy", number = "123")
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Mummy", number = "123")))
        val schedules = FakeScheduleRepository(listOf(original))

        SyncContactsUseCase(contacts, schedules)()

        assertEquals(original, schedules.schedules.value.single())
    }

    @Test
    fun `deleted contact keeps the schedule's last snapshot`() = runTest {
        val original = schedule(1, "k1", "Mummy")
        val contacts = FakeContactRepository(listOf(contact(2, "k2", "Someone Else")))
        val schedules = FakeScheduleRepository(listOf(original))

        SyncContactsUseCase(contacts, schedules)()

        assertEquals(original, schedules.schedules.value.single())
    }

    @Test
    fun `paused schedules are refreshed too`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Amar Sir")))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Amar", active = false)))

        SyncContactsUseCase(contacts, schedules)()

        assertEquals("Amar Sir", schedules.schedules.value.single().displayNameSnapshot)
    }

    @Test
    fun `rename into another schedule's name goes to the duplicate prompt`() = runTest {
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Rahul"), contact(2, "k2", "Rahul")))
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Rahul"), schedule(2, "k2", "Rahul Office")))

        SyncContactsUseCase(contacts, schedules)()
        val outcome = MatchCallToScheduleUseCase(schedules, contacts)("Rahul")

        assertTrue(outcome is MatchOutcome.DuplicateSchedules)
        assertEquals(setOf(1L, 2L), (outcome as MatchOutcome.DuplicateSchedules).scheduleIds.toSet())
    }
}
