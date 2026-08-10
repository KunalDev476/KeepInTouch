package com.pragmaapps.keepintouch.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    /**
     * Drops the unique index on call_events.notifKey and replaces it with a plain (non-unique)
     * index. Real devices were observed reusing the same WhatsApp notification key across
     * separate, sequential calls (e.g. a missed call, then a later fresh ring) rather than
     * minting a new key per call; the unique constraint made every call after the first on a
     * reused key un-processable (see CallEpisodeProcessor/CallEventDao.findByNotifKey, which now
     * only ever matches the in-flight, unfinalized episode for a key).
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS `index_call_events_notifKey`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_events_notifKey` ON `call_events` (`notifKey`)")
        }
    }

    /**
     * Adds priority-sorting fields to schedules: importance (Low/Medium/High, mandatory - existing
     * rows default to MEDIUM), preferredTimeMinutes (nullable - existing schedules have no known
     * call time), and isImportant (the "must not be missed" reminder flag, defaults to false so no
     * existing schedule silently starts firing reminders). See PriorityEngine and
     * ImportantCallReminderScheduler.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `importance` TEXT NOT NULL DEFAULT 'MEDIUM'")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `preferredTimeMinutes` INTEGER")
            db.execSQL("ALTER TABLE `schedules` ADD COLUMN `isImportant` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Backs the new in-app App Logs screen (Settings -> Diagnostics) - see AppLogger. */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `log_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `level` TEXT NOT NULL,
                    `tag` TEXT NOT NULL,
                    `message` TEXT NOT NULL,
                    `throwable` TEXT
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_entries_timestamp` ON `log_entries` (`timestamp`)")
        }
    }

    /**
     * Backs the collective "N calls pending" digest reminder (Settings -> Reminders) - a daily
     * time-of-day slot, separate from the per-schedule 30-min-before "Important" alarm. The
     * default 8pm row is seeded by DigestReminderRepositoryImpl on first read, not here - Room
     * migrations shouldn't depend on domain/default-data logic living outside the DB layer.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `digest_reminder_times` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }
}
