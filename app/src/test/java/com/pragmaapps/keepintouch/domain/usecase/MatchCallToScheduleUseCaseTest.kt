package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.fakes.FakeContactRepository
import com.pragmaapps.keepintouch.fakes.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchCallToScheduleUseCaseTest {

    private fun contact(id: Long, lookupKey: String, name: String, hasWhatsApp: Boolean = true) = Contact(
        id = id,
        lookupKey = lookupKey,
        contactId = id,
        displayName = name,
        normalizedName = NameNormalizer.normalize(name),
        primaryNumber = null,
        hasWhatsApp = hasWhatsApp,
        whatsappRawContactId = null,
        learnedAvatarHash = null
    )

    private fun schedule(id: Long, lookupKey: String, name: String, active: Boolean = true, numberSnapshot: String? = null) = Schedule(
        id = id,
        contactLookupKey = lookupKey,
        displayNameSnapshot = name,
        normalizedNameSnapshot = NameNormalizer.normalize(name),
        numberSnapshot = numberSnapshot,
        recurrence = Recurrence.WEEKLY,
        startDate = 0,
        nextDueDate = 0,
        active = active,
        note = null,
        createdAt = 0
    )

    @Test
    fun `exact raw-string match is EXACT confidence`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Mummy")

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        outcome as MatchOutcome.UniqueSchedule
        assertEquals(1L, outcome.scheduleId)
        assertEquals(MatchConfidence.EXACT, outcome.confidence)
    }

    @Test
    fun `case and emoji differences match as NORMALIZED not EXACT`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("mummy ❤️") // "mummy ❤️"

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        assertEquals(MatchConfidence.NORMALIZED, (outcome as MatchOutcome.UniqueSchedule).confidence)
    }

    @Test
    fun `a confident unique fuzzy match auto-attaches as FUZZY`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Sharma")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Sharmaa") // one-character insertion typo

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        assertEquals(MatchConfidence.FUZZY, (outcome as MatchOutcome.UniqueSchedule).confidence)
    }

    @Test
    fun `duplicate active schedules with the same name need confirmation`() = runTest {
        val schedules = FakeScheduleRepository(
            listOf(schedule(1, "k1", "Rahul"), schedule(2, "k2", "Rahul"))
        )
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Rahul")

        assertTrue(outcome is MatchOutcome.DuplicateSchedules)
        assertEquals(setOf(1L, 2L), (outcome as MatchOutcome.DuplicateSchedules).scheduleIds.toSet())
    }

    @Test
    fun `an inactive schedule with a matching name is not counted as a duplicate`() = runTest {
        val schedules = FakeScheduleRepository(
            listOf(schedule(1, "k1", "Rahul"), schedule(2, "k2", "Rahul", active = false))
        )
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Rahul")

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        assertEquals(1L, (outcome as MatchOutcome.UniqueSchedule).scheduleId)
    }

    @Test
    fun `name matching a synced contact with no schedule offers to create one, once the user has ever created any schedule`() = runTest {
        // A schedule for someone else entirely - just enough to prove the user has used scheduling before.
        val schedules = FakeScheduleRepository(listOf(schedule(9, "k9", "Someone Else")))
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Priya")))
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Priya")

        assertTrue(outcome is MatchOutcome.ContactWithoutSchedule)
        assertEquals(listOf(1L), (outcome as MatchOutcome.ContactWithoutSchedule).contactIds)
    }

    @Test
    fun `with zero schedules ever created, a synced-contact match does not prompt to create one`() = runTest {
        val schedules = FakeScheduleRepository()
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "Priya")))
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Priya")

        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `a low-confidence fuzzy match needs confirmation rather than auto-attaching`() = runTest {
        // "Rahul" vs "Rahool" - similarity is below the 0.85 auto-attach threshold but above the 0.6 floor.
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Rahul")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Rahool")

        assertTrue("expected UncertainSchedules but was $outcome", outcome is MatchOutcome.UncertainSchedules)
    }

    @Test
    fun `completely unrelated caller name with no contact match is NoMatch`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("Totally Unrelated Person")

        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `a raw phone number matches an active schedule's saved number as PHONE_NUMBER confidence`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy", numberSnapshot = "8532921158")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("+918532921158")

        assertTrue(outcome is MatchOutcome.UniqueSchedule)
        outcome as MatchOutcome.UniqueSchedule
        assertEquals(1L, outcome.scheduleId)
        assertEquals(MatchConfidence.PHONE_NUMBER, outcome.confidence)
    }

    @Test
    fun `a phone number matching two active schedules needs confirmation`() = runTest {
        val schedules = FakeScheduleRepository(
            listOf(
                schedule(1, "k1", "Mummy", numberSnapshot = "8532921158"),
                schedule(2, "k2", "Mom Duplicate", numberSnapshot = "8532921158")
            )
        )
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("+918532921158")

        assertTrue(outcome is MatchOutcome.DuplicateSchedules)
        assertEquals(setOf(1L, 2L), (outcome as MatchOutcome.DuplicateSchedules).scheduleIds.toSet())
    }

    @Test
    fun `a phone number never matches an inactive schedule`() = runTest {
        val schedules = FakeScheduleRepository(listOf(schedule(1, "k1", "Mummy", active = false, numberSnapshot = "8532921158")))
        val contacts = FakeContactRepository()
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("+918532921158")

        assertEquals(MatchOutcome.NoMatch, outcome)
    }

    @Test
    fun `a phone number does not trigger a ContactWithoutSchedule prompt`() = runTest {
        // Number-matching is deliberately narrower than name-matching - it never expands into
        // "want to schedule this contact?" suggestions, even if schedules exist elsewhere.
        val schedules = FakeScheduleRepository(listOf(schedule(9, "k9", "Someone Else")))
        val contacts = FakeContactRepository(listOf(contact(1, "k1", "+918532921158")))
        val useCase = MatchCallToScheduleUseCase(schedules, contacts)

        val outcome = useCase("+918532921158")

        assertEquals(MatchOutcome.NoMatch, outcome)
    }
}
