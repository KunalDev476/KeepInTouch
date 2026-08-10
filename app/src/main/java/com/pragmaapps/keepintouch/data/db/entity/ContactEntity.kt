package com.pragmaapps.keepintouch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["lookupKey"], unique = true), Index(value = ["normalizedName"])]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lookupKey: String,
    val contactId: Long,
    val displayName: String,
    val normalizedName: String,
    val primaryNumber: String?,
    val hasWhatsApp: Boolean,
    val whatsappRawContactId: Long?,
    val learnedAvatarHash: String? = null
)
