package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pragmaapps.keepintouch.data.db.entity.PendingMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMatchDao {
    @Query("SELECT * FROM pending_matches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PendingMatchEntity>>

    @Query("SELECT * FROM pending_matches WHERE resolvedAt IS NULL ORDER BY createdAt DESC")
    fun observeUnresolved(): Flow<List<PendingMatchEntity>>

    @Query("SELECT * FROM pending_matches WHERE resolvedAt IS NULL ORDER BY createdAt DESC")
    suspend fun getUnresolvedSnapshot(): List<PendingMatchEntity>

    @Query("SELECT * FROM pending_matches WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): PendingMatchEntity?

    @Query("SELECT * FROM pending_matches WHERE callEventId = :callEventId LIMIT 1")
    suspend fun findByCallEventId(callEventId: Long): PendingMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pendingMatch: PendingMatchEntity): Long

    @Update
    suspend fun update(pendingMatch: PendingMatchEntity)

    @Query("DELETE FROM pending_matches")
    suspend fun deleteAll()
}
