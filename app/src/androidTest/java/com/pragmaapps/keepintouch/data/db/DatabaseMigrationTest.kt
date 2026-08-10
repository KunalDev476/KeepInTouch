package com.pragmaapps.keepintouch.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB_NAME = "migration-test.db"

/**
 * Real migration coverage starts here, at v1 -> v2 (dropping call_events.notifKey's unique index
 * after real devices were observed reusing the same notification key across separate, sequential
 * calls - see Migrations.MIGRATION_1_2 and AppDatabase's class doc). This is the first schema
 * change since the app shipped v1, so it's also the first point a *real* migration test was
 * possible - earlier there was nothing to migrate from.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun exportedV1SchemaIsLoadable() {
        helper.createDatabase(TEST_DB_NAME, 1).close()
    }

    @Test
    fun migrate1To2_dropsUniqueConstraintOnNotifKey_andPreservesExistingRows() {
        // Start at v1 and insert a row exactly like a finalized (missed) call would leave behind.
        helper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO call_events (
                    id, packageName, notifKey, statesSeen, connected, isVideo, isGroupCall,
                    direction, notificationName, postTime, ongoingWhen, ongoingPostedElapsedRealtime,
                    endTime, inferredDurationMs, qualified, avatarHash, matchedScheduleId,
                    matchConfidence, rawJson
                ) VALUES (
                    1, 'com.whatsapp', 'reused-key', 'INCOMING@post=0;REMOVED@elapsed=0', 0, 0, 0,
                    'INCOMING', 'Mummy', 0, NULL, NULL,
                    100, NULL, 0, NULL, NULL,
                    NULL, '[]'
                )
                """.trimIndent()
            )
            close()
        }

        // Migrate to v2 and confirm the pre-existing row survived (this would previously have
        // been silently deleted by REPLACE-on-conflict semantics once the same key got reused).
        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, Migrations.MIGRATION_1_2)
        val cursor = migrated.query("SELECT COUNT(*) FROM call_events WHERE notifKey = 'reused-key'")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()

        // And confirm a second row with the SAME notifKey can now be inserted without conflict -
        // exactly the "call reuses a finalized episode's key" scenario the migration exists for.
        migrated.execSQL(
            """
            INSERT INTO call_events (
                id, packageName, notifKey, statesSeen, connected, isVideo, isGroupCall,
                direction, notificationName, postTime, ongoingWhen, ongoingPostedElapsedRealtime,
                endTime, inferredDurationMs, qualified, avatarHash, matchedScheduleId,
                matchConfidence, rawJson
            ) VALUES (
                2, 'com.whatsapp', 'reused-key', 'ONGOING@post=200', 1, 0, 0,
                'INCOMING', 'Mummy', 200, 200, 0,
                NULL, NULL, 0, NULL, NULL,
                NULL, '[]'
            )
            """.trimIndent()
        )
        val cursor2 = migrated.query("SELECT COUNT(*) FROM call_events WHERE notifKey = 'reused-key'")
        cursor2.moveToFirst()
        assertEquals(2, cursor2.getInt(0))
        cursor2.close()
        migrated.close()
    }

    @Test
    fun migrate2To3_addsPriorityColumns_defaultingExistingRowsToMediumAndNotImportant() {
        // Start at v2 and insert a schedule exactly as v2 code would have (no priority columns yet).
        helper.createDatabase(TEST_DB_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO schedules (
                    id, contactLookupKey, displayNameSnapshot, normalizedNameSnapshot, numberSnapshot,
                    recurrence, startDate, nextDueDate, active, note, createdAt
                ) VALUES (
                    1, 'lookup-1', 'Mummy', 'mummy', '+911234567890',
                    'DAILY', 0, 0, 1, NULL, 0
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 3, true, Migrations.MIGRATION_2_3)
        val cursor = migrated.query("SELECT importance, preferredTimeMinutes, isImportant FROM schedules WHERE id = 1")
        cursor.moveToFirst()
        assertEquals("MEDIUM", cursor.getString(0))
        assertEquals(true, cursor.isNull(1))
        assertEquals(0, cursor.getInt(2))
        cursor.close()
        migrated.close()
    }

    @Test
    fun migrate3To4_createsLogEntriesTable() {
        helper.createDatabase(TEST_DB_NAME, 3).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 4, true, Migrations.MIGRATION_3_4)
        migrated.execSQL(
            "INSERT INTO log_entries (id, timestamp, level, tag, message, throwable) VALUES (1, 0, 'INFO', 'Test', 'hello', NULL)"
        )
        val cursor = migrated.query("SELECT COUNT(*) FROM log_entries")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        migrated.close()
    }

    @Test
    fun migrate4To5_createsDigestReminderTimesTable() {
        helper.createDatabase(TEST_DB_NAME, 4).close()

        val migrated = helper.runMigrationsAndValidate(TEST_DB_NAME, 5, true, Migrations.MIGRATION_4_5)
        migrated.execSQL(
            "INSERT INTO digest_reminder_times (id, hour, minute, label, enabled) VALUES (1, 20, 0, 'Evening', 1)"
        )
        val cursor = migrated.query("SELECT COUNT(*) FROM digest_reminder_times")
        cursor.moveToFirst()
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        migrated.close()
    }
}
