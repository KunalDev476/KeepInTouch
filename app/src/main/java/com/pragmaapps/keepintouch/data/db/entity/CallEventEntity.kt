package com.pragmaapps.keepintouch.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.model.MatchConfidence

@Entity(
    tableName = "call_events",
    // notifKey is NOT globally unique: some OEM/WhatsApp builds reuse the same notification key
    // across separate, sequential calls (e.g. a missed call, then a later fresh ring) rather than
    // minting a new key per call. Uniqueness only ever holds among *unfinalized* episodes for a
    // given key - see CallEventDao.findByNotifKey and CallEpisodeProcessor. (v1 -> v2 migration.)
    indices = [Index(value = ["notifKey"]), Index(value = ["notificationName"])]
)
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val notifKey: String,
    val statesSeen: String,
    val connected: Boolean,
    val isVideo: Boolean,
    val isGroupCall: Boolean,
    val direction: Direction,
    val notificationName: String,
    val postTime: Long,
    val ongoingWhen: Long?,
    val ongoingPostedElapsedRealtime: Long?,
    val endTime: Long?,
    val inferredDurationMs: Long?,
    val qualified: Boolean,
    val avatarHash: String?,
    val matchedScheduleId: Long?,
    val matchConfidence: MatchConfidence?,
    val rawJson: String
)
