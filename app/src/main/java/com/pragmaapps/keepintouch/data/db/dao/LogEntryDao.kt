package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.pragmaapps.keepintouch.data.db.entity.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogEntryDao {
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insert(entry: LogEntryEntity)

    /** Keeps only the most recent [maxEntries] rows, so this table can't grow unbounded on a device that's never opened the app to clear it. */
    @Query(
        """
        DELETE FROM log_entries WHERE id NOT IN (
            SELECT id FROM log_entries ORDER BY timestamp DESC LIMIT :maxEntries
        )
        """
    )
    suspend fun pruneKeepingLatest(maxEntries: Int)

    @Query("DELETE FROM log_entries")
    suspend fun clearAll()
}
