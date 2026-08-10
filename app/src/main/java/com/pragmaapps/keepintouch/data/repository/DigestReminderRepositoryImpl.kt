package com.pragmaapps.keepintouch.data.repository

import android.content.Context
import com.pragmaapps.keepintouch.data.db.dao.DigestReminderTimeDao
import com.pragmaapps.keepintouch.data.db.entity.DigestReminderTimeEntity
import com.pragmaapps.keepintouch.data.db.mapper.toDomain
import com.pragmaapps.keepintouch.data.db.mapper.toEntity
import com.pragmaapps.keepintouch.data.reminder.DigestReminderScheduler
import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import com.pragmaapps.keepintouch.domain.repository.DigestReminderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "digest_reminder_prefs"
private const val KEY_DEFAULT_SEEDED = "default_8pm_seeded"

/**
 * Seeds a single default 8pm slot exactly once, ever - tracked via a SharedPreferences flag
 * rather than "table is empty", so a user who deliberately deletes every slot doesn't get one
 * silently re-added the next time the list is read.
 */
@Singleton
class DigestReminderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DigestReminderTimeDao,
    private val scheduler: DigestReminderScheduler
) : DigestReminderRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private suspend fun seedDefaultIfNeeded() {
        if (prefs.getBoolean(KEY_DEFAULT_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_DEFAULT_SEEDED, true).apply()
        dao.insert(DigestReminderTimeEntity(hour = 20, minute = 0, label = "Evening", enabled = true))
        scheduler.rescheduleAll(dao.getAllSnapshot().map { it.toDomain() })
    }

    override fun observeAll(): Flow<List<DigestReminderTime>> =
        dao.observeAll()
            .onStart { seedDefaultIfNeeded() }
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getAllSnapshot(): List<DigestReminderTime> {
        seedDefaultIfNeeded()
        return dao.getAllSnapshot().map { it.toDomain() }
    }

    override suspend fun add(hour: Int, minute: Int, label: String): Long {
        val id = dao.insert(DigestReminderTimeEntity(hour = hour, minute = minute, label = label, enabled = true))
        scheduler.rescheduleAll(dao.getAllSnapshot().map { it.toDomain() })
        return id
    }

    override suspend fun update(reminder: DigestReminderTime) {
        dao.update(reminder.toEntity())
        scheduler.rescheduleAll(dao.getAllSnapshot().map { it.toDomain() })
    }

    override suspend fun delete(reminder: DigestReminderTime) {
        dao.delete(reminder.toEntity())
        scheduler.cancel(reminder.id)
    }
}
