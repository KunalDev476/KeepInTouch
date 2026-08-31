package com.pragmaapps.keepintouch.di

import android.content.Context
import androidx.room.Room
import com.pragmaapps.keepintouch.data.db.AppDatabase
import com.pragmaapps.keepintouch.data.db.Migrations
import com.pragmaapps.keepintouch.data.db.dao.CallEventDao
import com.pragmaapps.keepintouch.data.db.dao.ContactDao
import com.pragmaapps.keepintouch.data.db.dao.DigestReminderTimeDao
import com.pragmaapps.keepintouch.data.db.dao.LogEntryDao
import com.pragmaapps.keepintouch.data.db.dao.PendingMatchDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleCompletionDao
import com.pragmaapps.keepintouch.data.db.dao.ScheduleDao
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.security.PassphraseManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

private const val TAG = "DatabaseModule"

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * No safe fallback if this fails (SQLCipher native lib missing, passphrase decryption
     * failing, a corrupt DB file) - the app cannot function without its database, so this must
     * still crash. The try/catch only adds a log (mirrors to Logcat even before AppLogger's Room
     * sink is available, since this can run as part of AppLogger.init() itself resolving
     * LogEntryDao) before rethrowing, for diagnosability.
     */
    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphraseManager: PassphraseManager
    ): AppDatabase = try {
        System.loadLibrary("sqlcipher")
        val passphrase = passphraseManager.getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase)
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .openHelperFactory(factory)
            .addMigrations(
                Migrations.MIGRATION_1_2,
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
                Migrations.MIGRATION_6_7
            )
            .build()
    } catch (t: Throwable) {
        AppLogger.e(TAG, "Failed to open the app database - app cannot start without it", t)
        throw t
    }

    @Provides
    fun provideContactDao(db: AppDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideScheduleCompletionDao(db: AppDatabase): ScheduleCompletionDao = db.scheduleCompletionDao()

    @Provides
    fun provideCallEventDao(db: AppDatabase): CallEventDao = db.callEventDao()

    @Provides
    fun providePendingMatchDao(db: AppDatabase): PendingMatchDao = db.pendingMatchDao()

    @Provides
    fun provideLogEntryDao(db: AppDatabase): LogEntryDao = db.logEntryDao()

    @Provides
    fun provideDigestReminderTimeDao(db: AppDatabase): DigestReminderTimeDao = db.digestReminderTimeDao()
}
