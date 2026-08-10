package com.pragmaapps.keepintouch.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pragmaapps.keepintouch.data.db.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY displayName COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE lookupKey = :lookupKey LIMIT 1")
    suspend fun findByLookupKey(lookupKey: String): ContactEntity?

    @Query("SELECT * FROM contacts WHERE normalizedName = :normalizedName")
    suspend fun findByNormalizedName(normalizedName: String): List<ContactEntity>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): ContactEntity?

    @Query("SELECT * FROM contacts WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(contacts: List<ContactEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactEntity): Long

    @Update
    suspend fun update(contact: ContactEntity)

    @Query("UPDATE contacts SET learnedAvatarHash = :hash WHERE id = :contactId")
    suspend fun learnAvatarHash(contactId: Long, hash: String)

    @Query("DELETE FROM contacts WHERE lookupKey NOT IN (:keepLookupKeys)")
    suspend fun deleteMissing(keepLookupKeys: List<String>)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int
}
