package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class VoiceConversationTest {

    private val zone: ZoneId = ZoneOffset.UTC
    // Wednesday 28 Oct 2026, 10:00.
    private val nowMillis = LocalDateTime.of(2026, 10, 28, 10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun contact(id: Long, name: String, number: String? = "98765$id") = Contact(
        id = id, lookupKey = "k$id", contactId = id, displayName = name, normalizedName = NameNormalizer.normalize(name),
        primaryNumber = number, hasWhatsApp = false, whatsappRawContactId = null, learnedAvatarHash = null
    )

    private class FakeBackend(
        val contactList: List<Contact>,
        val existing: MutableList<Schedule> = mutableListOf(),
        var notificationsBlocked: Boolean = false
    ) : VoiceScheduleBackend {
        val saved = mutableListOf<ScheduleRequest>()
        override suspend fun contacts() = contactList
        override suspend fun activeSchedulesFor(contact: Contact) = existing.filter { it.contactLookupKey == contact.lookupKey }
        override suspend fun save(request: ScheduleRequest): SaveOutcome { saved += request; return SaveOutcome.CREATED }
        override fun notificationsBlocked() = notificationsBlocked
    }

    private val rahul = contact(1, "Rahul Sharma")
    private val contacts = listOf(
        rahul, contact(2, "Rahul Verma"), contact(3, "Amit Kumar"), contact(4, "Neha", number = null),
        contact(5, "Amar Sir"), contact(6, "Mummy")
    )

    private fun conversation(backend: FakeBackend) = VoiceConversation(backend, now = { nowMillis }, zone = { zone })

    private fun day(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    @Test
    fun `one sentence with an exact name and everything else goes straight to the summary`() = runTest {
        val backend = FakeBackend(contacts)
        val c = conversation(backend)

        val first = c.onWake("Rahul Sharma ke liye har Saturday aur Sunday shaam 6 baje schedule banao")
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), first.prompts)
        assertTrue(c.items.any { it is ChatItem.ContactSelected })
        assertFalse(c.items.any { it is ChatItem.ContactConfirm })

        val saved = c.onSpeech(listOf("haan"))
        assertEquals(listOf(VoicePrompt.SAVED_MANY, VoicePrompt.ASK_ANOTHER), saved.prompts)
        assertEquals(2, backend.saved.size)
        assertTrue(backend.saved.all { it.recurrence == Recurrence.WEEKLY && it.preferredTimeMinutes == 18 * 60 })
        assertEquals(listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), backend.saved.map { day(it.nextDueDate).dayOfWeek })
        assertTrue(c.items.last() is ChatItem.Success || c.items.any { it is ChatItem.Success })
    }

    @Test
    fun `step by step one-time call`() = runTest {
        val backend = FakeBackend(contacts)
        val c = conversation(backend)

        assertEquals(listOf(VoicePrompt.WAKE_ACK), c.onWake("").prompts)
        assertEquals(listOf(VoicePrompt.CONTACT_MULTIPLE), c.onSpeech(listOf("Rahul")).prompts)
        assertEquals(listOf(VoicePrompt.ASK_TYPE), c.onSpeech(listOf("pehla")).prompts)
        assertEquals(listOf(VoicePrompt.ASK_DATE), c.onSpeech(listOf("ek baar")).prompts)
        assertEquals(listOf(VoicePrompt.ASK_TIME), c.onSpeech(listOf("kal")).prompts)
        assertEquals(listOf(VoicePrompt.TIME_AM_PM), c.onSpeech(listOf("6 baje")).prompts)
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), c.onSpeech(listOf("shaam")).prompts)
        c.onYesNoTapped(true)

        val request = backend.saved.single()
        assertEquals(Recurrence.ONE_TIME, request.recurrence)
        assertEquals(day(nowMillis).plusDays(1), day(request.nextDueDate))
        assertEquals(18 * 60, request.preferredTimeMinutes)
        assertEquals(rahul, request.contact)
    }

    @Test
    fun `time window becomes an availability window`() = runTest {
        val backend = FakeBackend(contacts)
        val c = conversation(backend)
        assertEquals(listOf(VoicePrompt.ASK_TIME), c.onWake("Amit ke liye roz").prompts)
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), c.onSpeech(listOf("subah 10 se 11 ke beech")).prompts)
        c.onSpeech(listOf("haan"))
        val request = backend.saved.single()
        assertEquals(Recurrence.DAILY, request.recurrence)
        assertEquals(600, request.preferredTimeMinutes)
        assertEquals(660, request.comfortableTimeEndMinutes)
        // 10am window already started today -> first one is tomorrow.
        assertEquals(day(nowMillis).plusDays(1), day(request.nextDueDate))
    }

    @Test
    fun `saying no to a confirmed contact starts over`() = runTest {
        val c = conversation(FakeBackend(contacts))
        // A prefix isn't an exact match, so it's confirmed first.
        assertEquals(listOf(VoicePrompt.CONTACT_CONFIRM), c.onWake("Ami").prompts)
        assertEquals(listOf(VoicePrompt.CONTACT_RETRY), c.onSpeech(listOf("nahi")).prompts)
        assertEquals(VoiceConversation.Step.ASK_CONTACT, c.step)
    }

    @Test
    fun `two misses offer the search box`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("")
        assertEquals(listOf(VoicePrompt.CONTACT_NOT_FOUND), c.onSpeech(listOf("Deepika")).prompts)
        assertEquals(listOf(VoicePrompt.CONTACT_NOT_FOUND_2), c.onSpeech(listOf("Deepika Padukone")).prompts)
        assertEquals(InputOptions.ContactSearch, c.input)
        assertEquals(listOf(VoicePrompt.ASK_TYPE), c.onContactTyped("Amit").prompts)
    }

    @Test
    fun `unclear answers escalate to tap options`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("Amit")
        assertEquals(listOf(VoicePrompt.NOT_UNDERSTOOD), c.onSpeech(listOf("pata kuch bhi")).prompts.take(1))
        assertEquals(listOf(VoicePrompt.NOT_UNDERSTOOD_2), c.onSpeech(listOf("umm")).prompts)
        assertTrue(c.input is InputOptions.Chips)
        assertEquals(listOf(VoicePrompt.ASK_FREQUENCY), c.onQuickReply(QuickReply("Repeating", "repeat")).prompts)
    }

    @Test
    fun `days a week without names asks which days`() = runTest {
        val c = conversation(FakeBackend(contacts))
        assertEquals(listOf(VoicePrompt.WEEKDAYS_UNCLEAR), c.onWake("Amit ke liye hafte mein do din").prompts)
    }

    @Test
    fun `contact with an existing schedule is asked about first`() = runTest {
        val existing = Schedule(
            id = 9, contactLookupKey = "k3", displayNameSnapshot = "Amit Kumar", normalizedNameSnapshot = "amit kumar",
            numberSnapshot = null, recurrence = Recurrence.MONTHLY, startDate = 0, nextDueDate = 0, active = true,
            note = null, createdAt = 0
        )
        val c = conversation(FakeBackend(contacts, existing = mutableListOf(existing)))
        assertEquals(listOf(VoicePrompt.CONTACT_HAS_SCHEDULE), c.onWake("Amit").prompts)
        assertEquals(listOf(VoicePrompt.ASK_TYPE), c.onSpeech(listOf("haan")).prompts)
    }

    @Test
    fun `identical schedule is not created twice`() = runTest {
        val existing = Schedule(
            id = 9, contactLookupKey = "k3", displayNameSnapshot = "Amit Kumar", normalizedNameSnapshot = "amit kumar",
            numberSnapshot = null, recurrence = Recurrence.DAILY, startDate = 0, nextDueDate = 0, active = true,
            note = null, createdAt = 0
        )
        val backend = FakeBackend(contacts, existing = mutableListOf(existing))
        val c = conversation(backend)
        c.onWake("Amit roz koi time nahi")
        c.onSpeech(listOf("haan"))
        assertEquals(listOf(VoicePrompt.DUPLICATE_SCHEDULE, VoicePrompt.ASK_ANOTHER), c.onSpeech(listOf("haan")).prompts)
        assertTrue(backend.saved.isEmpty())
    }

    @Test
    fun `contact without a number is mentioned but still schedulable`() = runTest {
        val c = conversation(FakeBackend(contacts))
        assertEquals(listOf(VoicePrompt.CONTACT_NO_PHONE, VoicePrompt.ASK_TYPE), c.onWake("Neha").prompts)
    }

    @Test
    fun `timed schedule without notification permission asks for it`() = runTest {
        val c = conversation(FakeBackend(contacts, notificationsBlocked = true))
        c.onWake("Amit roz shaam 7 baje")
        val reply = c.onSpeech(listOf("haan"))
        assertEquals(
            listOf(VoicePrompt.SAVED_ONE, VoicePrompt.NOTIFICATION_PERMISSION_REQUIRED, VoicePrompt.ASK_ANOTHER),
            reply.prompts
        )
        assertEquals(InputOptions.GrantNotifications, c.input)
    }

    @Test
    fun `cancel ends the conversation and summary edits change only the time`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("Amit har Monday")
        c.onSpeech(listOf("koi time nahi"))
        assertEquals(VoiceConversation.Step.CONFIRM_SUMMARY, c.step)
        assertEquals(listOf(VoicePrompt.EDIT_SUMMARY), c.onSpeech(listOf("nahi")).prompts)
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), c.onSpeech(listOf("shaam 5 baje")).prompts)
        val summary = c.items.filterIsInstance<ChatItem.Summary>().last().summary
        assertEquals(17 * 60, summary.startMinutes)
        assertEquals(listOf(DayOfWeek.MONDAY), summary.weekdays)

        val cancelled = c.onSpeech(listOf("rehne do"))
        assertEquals(listOf(VoicePrompt.CANCELLED), cancelled.prompts)
        assertFalse(cancelled.listen)
        assertEquals(VoiceConversation.Step.WAIT_WAKE, c.step)
    }

    @Test
    fun `two silent turns time out`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("")
        assertEquals(listOf(VoicePrompt.NO_SPEECH), c.onSilence().prompts)
        val timeout = c.onSilence()
        assertEquals(listOf(VoicePrompt.LISTENING_TIMEOUT), timeout.prompts)
        assertTrue(timeout.endSession)
    }

    @Test
    fun `no contacts means asking for the permission`() = runTest {
        val c = conversation(FakeBackend(emptyList()))
        val reply = c.onWake("")
        assertEquals(listOf(VoicePrompt.CONTACTS_PERMISSION_REQUIRED), reply.prompts)
        assertEquals(InputOptions.GrantContacts, c.input)
    }

    private fun lastSummary(c: VoiceConversation) = c.items.filterIsInstance<ChatItem.Summary>().last().summary

    @Test
    fun `two contacts in one sentence are handled one after the other with shared details`() = runTest {
        val backend = FakeBackend(contacts)
        val c = conversation(backend)

        val first = c.onWake(
            "amar sir ke liye bna do shaam ke 6 bje aur mummy ke liye bna do shaam ke 8 bje dono schdule rojana repeat honge"
        )
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), first.prompts)
        assertEquals("Amar Sir", lastSummary(c).contactName)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
        assertEquals(18 * 60, lastSummary(c).startMinutes)

        val second = c.onSpeech(listOf("haan"))
        assertEquals(listOf(VoicePrompt.SAVED_ONE, VoicePrompt.SUMMARY_CONFIRM), second.prompts)
        assertEquals("Mummy", lastSummary(c).contactName)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
        assertEquals(20 * 60, lastSummary(c).startMinutes)

        assertEquals(listOf(VoicePrompt.SAVED_ONE, VoicePrompt.ASK_ANOTHER), c.onSpeech(listOf("haan")).prompts)
        assertEquals(listOf("Amar Sir", "Mummy"), backend.saved.map { it.contact.displayName })
        assertEquals(listOf(18 * 60, 20 * 60), backend.saved.map { it.preferredTimeMinutes })
    }

    @Test
    fun `yes with details at contact confirmation keeps the details`() = runTest {
        val c = conversation(FakeBackend(contacts))
        assertEquals(listOf(VoicePrompt.CONTACT_CONFIRM), c.onWake("Ami").prompts)
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), c.onSpeech(listOf("haan shaam ke 6 bje rojana")).prompts)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
        assertEquals(18 * 60, lastSummary(c).startMinutes)
    }

    @Test
    fun `yes with a new command at anyone else starts that schedule`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("Neha roz koi time nahi")
        c.onSpeech(listOf("haan"))
        assertEquals(VoiceConversation.Step.ASK_ANOTHER, c.step)

        val reply = c.onSpeech(listOf("haan amar sir ke liye schedule bna do jo har roj shaam 6 bje repeat kre"))
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), reply.prompts)
        assertEquals("Amar Sir", lastSummary(c).contactName)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
        assertEquals(18 * 60, lastSummary(c).startMinutes)
    }

    @Test
    fun `self corrections keep only the final values`() = runTest {
        val c = conversation(FakeBackend(contacts))
        val reply = c.onWake("amar sir ke liye roj 9 bje nhi 6 bje din me nhi shaam me schedule bna do")
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), reply.prompts)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
        assertEquals(18 * 60, lastSummary(c).startMinutes)
    }

    @Test
    fun `wrong contact keeps the schedule details and confirms the new contact`() = runTest {
        val c = conversation(FakeBackend(contacts))
        assertEquals(listOf(VoicePrompt.ASK_TIME), c.onWake("Rahul Sharma roz").prompts)

        val corrected = c.onSpeech(listOf("ye galat contact hai maine Rahul Verma bola tha"))
        assertEquals(listOf(VoicePrompt.CONTACT_CONFIRM), corrected.prompts)
        assertEquals("Rahul Verma", c.items.filterIsInstance<ChatItem.ContactConfirm>().last().contact.displayName)

        assertEquals(listOf(VoicePrompt.ASK_TIME), c.onSpeech(listOf("haan")).prompts)
        assertEquals(listOf(VoicePrompt.SUMMARY_CONFIRM), c.onSpeech(listOf("koi time nahi")).prompts)
        assertEquals("Rahul Verma", lastSummary(c).contactName)
        assertEquals(Recurrence.DAILY, lastSummary(c).recurrence)
    }

    @Test
    fun `wrong contact tap asks for the name again`() = runTest {
        val c = conversation(FakeBackend(contacts))
        c.onWake("Amit roz")
        assertEquals(listOf(VoicePrompt.CONTACT_NO_NAME), c.onWrongContactTapped().prompts)
        assertEquals(VoiceConversation.Step.ASK_CONTACT, c.step)
    }
}
