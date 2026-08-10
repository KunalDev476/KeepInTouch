package com.pragmaapps.keepintouch.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pragmaapps.keepintouch.data.db.entity.CallEventEntity
import com.pragmaapps.keepintouch.data.db.entity.ContactEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleCompletionEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleEntity
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Recurrence
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real DAOs against an actual (in-memory, unencrypted) SQLite instance - not just
 * the schema shape. Deliberately skips SQLCipher's openHelperFactory: encryption-at-rest is a
 * storage concern orthogonal to whether the schema/queries themselves are correct, and the app's
 * own runtime path already proves the encrypted factory wires up (see README).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSmokeTest {
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun contactRoundTrip() = runTest {
        val contact = ContactEntity(
            lookupKey = "lookup-1",
            contactId = 1,
            displayName = "Mummy",
            normalizedName = "mummy",
            primaryNumber = "+15551234",
            hasWhatsApp = true,
            whatsappRawContactId = 99
        )
        val id = db.contactDao().insert(contact)
        val loaded = db.contactDao().findById(id)
        assertEquals("Mummy", loaded?.displayName)
        assertEquals(true, loaded?.hasWhatsApp)
    }

    @Test
    fun scheduleFulfillmentRoundTrip() = runTest {
        val scheduleId = db.scheduleDao().insert(
            ScheduleEntity(
                contactLookupKey = "lookup-1",
                displayNameSnapshot = "Mummy",
                normalizedNameSnapshot = "mummy",
                numberSnapshot = null,
                recurrence = Recurrence.WEEKLY,
                startDate = 0,
                nextDueDate = 0
            )
        )

        val callEventId = db.callEventDao().upsert(
            CallEventEntity(
                packageName = "com.whatsapp",
                notifKey = "key-1",
                statesSeen = "ONGOING;REMOVED",
                connected = true,
                isVideo = false,
                isGroupCall = false,
                direction = Direction.INCOMING,
                notificationName = "Mummy",
                postTime = 0,
                ongoingWhen = 0,
                ongoingPostedElapsedRealtime = 0,
                endTime = 6000,
                inferredDurationMs = 6000,
                qualified = true,
                avatarHash = null,
                matchedScheduleId = scheduleId,
                matchConfidence = MatchConfidence.EXACT,
                rawJson = "[]"
            )
        )

        db.scheduleCompletionDao().insert(
            ScheduleCompletionEntity(
                scheduleId = scheduleId,
                dueDate = 0,
                completedAt = 6000,
                callEventId = callEventId,
                matchConfidence = MatchConfidence.EXACT,
                matchMethod = "test"
            )
        )

        val completions = db.scheduleCompletionDao().getForScheduleSnapshot(scheduleId)
        assertEquals(1, completions.size)
        assertEquals(callEventId, completions.first().callEventId)
    }

    @Test
    fun manualCompletionAllowsNullCallEventId() = runTest {
        val scheduleId = db.scheduleDao().insert(
            ScheduleEntity(
                contactLookupKey = "lookup-2",
                displayNameSnapshot = "Dad",
                normalizedNameSnapshot = "dad",
                numberSnapshot = null,
                recurrence = Recurrence.ONE_TIME,
                startDate = 0,
                nextDueDate = 0
            )
        )

        db.scheduleCompletionDao().insert(
            ScheduleCompletionEntity(
                scheduleId = scheduleId,
                dueDate = 0,
                completedAt = 0,
                callEventId = null, // manual "mark done" from Today - no detected call
                matchConfidence = MatchConfidence.CONFIRMED_BY_USER,
                matchMethod = "Manually marked done from Today"
            )
        )

        val completions = db.scheduleCompletionDao().getForScheduleSnapshot(scheduleId)
        assertEquals(1, completions.size)
        assertEquals(null, completions.first().callEventId)
    }
}
