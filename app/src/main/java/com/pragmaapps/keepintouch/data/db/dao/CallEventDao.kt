package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pragmaapps.keepintouch.data.db.entity.CallEventEntity
import com.pragmaapps.keepintouch.domain.model.Direction
import kotlinx.coroutines.flow.Flow

@Dao
interface CallEventDao {
    @Query("SELECT * FROM call_events ORDER BY postTime DESC")
    fun observeAll(): Flow<List<CallEventEntity>>

    // Only the in-flight (not-yet-finalized) episode for this key, if any - notifKey can be
    // reused across separate, sequential calls, so uniqueness only holds while unfinalized.
    @Query("SELECT * FROM call_events WHERE notifKey = :notifKey AND endTime IS NULL ORDER BY postTime DESC LIMIT 1")
    suspend fun findByNotifKey(notifKey: String): CallEventEntity?

    @Query("SELECT * FROM call_events WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): CallEventEntity?

    @Query("SELECT * FROM call_events WHERE matchedScheduleId = :scheduleId ORDER BY postTime DESC")
    fun observeForSchedule(scheduleId: Long): Flow<List<CallEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: CallEventEntity): Long

    @Update
    suspend fun update(event: CallEventEntity)

    @Query("SELECT COUNT(*) FROM call_events WHERE direction = :direction")
    suspend fun countByDirection(direction: Direction): Int

    @Query("SELECT * FROM call_events WHERE matchedScheduleId = :scheduleId ORDER BY postTime DESC LIMIT 1")
    suspend fun latestForSchedule(scheduleId: Long): CallEventEntity?

    /**
     * Unlinks (rather than deletes) call events from a schedule being deleted - the raw detected
     * call still happened and stays useful in Settings > Diagnostics even once its match target is
     * gone; only the now-dangling matchedScheduleId reference needs clearing. No FK/CASCADE on this
     * column (see CallEventEntity), so callers must do this explicitly on schedule deletion.
     */
    @Query("UPDATE call_events SET matchedScheduleId = NULL WHERE matchedScheduleId = :scheduleId")
    suspend fun clearScheduleMatch(scheduleId: Long)
}
