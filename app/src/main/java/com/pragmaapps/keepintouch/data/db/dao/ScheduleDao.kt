package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.pragmaapps.keepintouch.data.db.entity.ScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY nextDueDate ASC")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE active = 1 ORDER BY nextDueDate ASC")
    fun observeActive(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE active = 1")
    suspend fun getActiveSnapshot(): List<ScheduleEntity>

    /** Any schedule at all - active, paused, or a completed one-time - not just currently-active ones. */
    @Query("SELECT EXISTS(SELECT 1 FROM schedules LIMIT 1)")
    suspend fun hasAny(): Boolean

    @Query("SELECT * FROM schedules WHERE active = 1 AND normalizedNameSnapshot = :normalizedName")
    suspend fun findActiveByNormalizedName(normalizedName: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ScheduleEntity?

    @Query("SELECT * FROM schedules WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE contactLookupKey = :lookupKey")
    suspend fun findByContactLookupKey(lookupKey: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE contactLookupKey = :lookupKey")
    fun observeByContactLookupKey(lookupKey: String): Flow<List<ScheduleEntity>>

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)
}
