package com.pragmaapps.keepintouch.domain.usecase

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import com.pragmaapps.keepintouch.fakes.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateScheduleUseCaseTest {

    private fun contact(id: Long, lookupKey: String, name: String) = Contact(
        id = id, lookupKey = lookupKey, contactId = id, displayName = name, normalizedName = NameNormalizer.normalize(name),
        primaryNumber = null, hasWhatsApp = false, whatsappRawContactId = null, learnedAvatarHash = null
    )

    @Test
    fun `same contact can have several schedules`() = runTest {
        val repo = FakeScheduleRepository()
        val useCase = CreateScheduleUseCase(repo)
        val rahul = contact(1, "k1", "Rahul")

        assertTrue(useCase(rahul, Recurrence.WEEKLY, 1000, null, null, null) is CreateScheduleUseCase.Result.Created)
        assertTrue(useCase(rahul, Recurrence.WEEKLY, 2000, null, null, null) is CreateScheduleUseCase.Result.Created)
        assertEquals(2, repo.schedules.value.size)
    }

    @Test
    fun `different contact with the same name still collides`() = runTest {
        val repo = FakeScheduleRepository()
        val useCase = CreateScheduleUseCase(repo)
        useCase(contact(1, "k1", "Rahul"), Recurrence.WEEKLY, 1000, null, null, null)

        val result = useCase(contact(2, "k2", "rahul"), Recurrence.DAILY, 1000, null, null, null)

        assertTrue(result is CreateScheduleUseCase.Result.NameCollision)
        assertEquals(1, repo.schedules.value.size)
    }

    @Test
    fun `a call time makes the schedule important and a window needs a start`() = runTest {
        val repo = FakeScheduleRepository()
        val useCase = CreateScheduleUseCase(repo)
        useCase(contact(1, "k1", "A"), Recurrence.DAILY, 0, null, 600, 660)
        useCase(contact(2, "k2", "B"), Recurrence.DAILY, 0, null, null, 660)

        val (timed, untimed) = repo.schedules.value
        assertTrue(timed.isImportant)
        assertEquals(660, timed.comfortableTimeEndMinutes)
        assertEquals(false, untimed.isImportant)
        assertEquals(null, untimed.comfortableTimeEndMinutes)
    }
}
