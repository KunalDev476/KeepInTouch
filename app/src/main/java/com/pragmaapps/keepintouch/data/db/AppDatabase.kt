package com.pragmaapps.keepintouch.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pragmaapps.keepintouch.data.db.dao.CallEventDao
import com.pragmaapps.keepintouch.data.db.dao.ContactDao
import com.pragmaapps.keepintouch.data.db.dao.DigestReminderTimeDao
import com.pragmaapps.keepintouch.data.db.dao.LogEntryDao
import com.pragmaapps.keepintouch.data.db.dao.PendingMatchDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleCompletionDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleDao
import com.pragmaapps.keepintouch.data.db.entity.CallEventEntity
import com.pragmaapps.keepintouch.data.db.entity.ContactEntity
import com.pragmaapps.keepintouch.data.db.entity.DigestReminderTimeEntity
import com.pragmaapps.keepintouch.data.db.entity.LogEntryEntity
import com.pragmaapps.keepintouch.data.db.entity.PendingMatchEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleCompletionEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleEntity

/**
 * Encrypted at rest via SQLCipher (see [com.pragmaapps.keepintouch.di.DatabaseModule] for the
 * openHelperFactory wiring and [com.pragmaapps.keepintouch.data.security.PassphraseManager] for
 * the Keystore-backed passphrase).
 *
 * v1 -> v2: call_events.notifKey's unique index was dropped (real devices were observed reusing
 * the same notification key across separate, sequential calls - e.g. a missed call followed by a
 * fresh ring - which made every call after the first on a reused key silently unprocessable). See
 * [com.pragmaapps.keepintouch.data.db.Migrations.MIGRATION_1_2].
 *
 * v2 -> v3: adds schedules.importance/preferredTimeMinutes/isImportant for priority sorting and
 * the "must not be missed" reminder notification. See
 * [com.pragmaapps.keepintouch.data.db.Migrations.MIGRATION_2_3].
 *
 * v3 -> v4: adds log_entries, backing the in-app App Logs screen. See
 * [com.pragmaapps.keepintouch.data.db.Migrations.MIGRATION_3_4].
 *
 * v4 -> v5: adds digest_reminder_times, backing the collective "N calls pending" reminder. See
 * [com.pragmaapps.keepintouch.data.db.Migrations.MIGRATION_4_5].
 */
@Database(
    entities = [
        ContactEntity::class,
        ScheduleEntity::class,
        ScheduleCompletionEntity::class,
        CallEventEntity::class,
        PendingMatchEntity::class,
        LogEntryEntity::class,
        DigestReminderTimeEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun scheduleCompletionDao(): ScheduleCompletionDao
    abstract fun callEventDao(): CallEventDao
    abstract fun pendingMatchDao(): PendingMatchDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun digestReminderTimeDao(): DigestReminderTimeDao

    companion object {
        const val DATABASE_NAME = "keep_in_touch.db"
    }
}
