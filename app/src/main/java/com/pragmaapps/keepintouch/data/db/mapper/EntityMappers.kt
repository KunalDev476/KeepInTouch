package com.pragmaapps.keepintouch.data.db.mapper

import com.pragmaapps.keepintouch.data.db.entity.CallEventEntity
import com.pragmaapps.keepintouch.data.db.entity.ContactEntity
import com.pragmaapps.keepintouch.data.db.entity.DigestReminderTimeEntity
import com.pragmaapps.keepintouch.data.db.entity.LogEntryEntity
import com.pragmaapps.keepintouch.data.db.entity.PendingMatchEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleCompletionEntity
import com.pragmaapps.keepintouch.data.db.entity.ScheduleEntity
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import com.pragmaapps.keepintouch.domain.model.LogEntry
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion

/** Room entities never leak past the data layer; everything above talks in domain models. */

fun ContactEntity.toDomain() = Contact(
    id = id,
    lookupKey = lookupKey,
    contactId = contactId,
    displayName = displayName,
    normalizedName = normalizedName,
    primaryNumber = primaryNumber,
    hasWhatsApp = hasWhatsApp,
    whatsappRawContactId = whatsappRawContactId,
    learnedAvatarHash = learnedAvatarHash
)

fun Contact.toEntity() = ContactEntity(
    id = id,
    lookupKey = lookupKey,
    contactId = contactId,
    displayName = displayName,
    normalizedName = normalizedName,
    primaryNumber = primaryNumber,
    hasWhatsApp = hasWhatsApp,
    whatsappRawContactId = whatsappRawContactId,
    learnedAvatarHash = learnedAvatarHash
)

fun ScheduleEntity.toDomain() = Schedule(
    id = id,
    contactLookupKey = contactLookupKey,
    displayNameSnapshot = displayNameSnapshot,
    normalizedNameSnapshot = normalizedNameSnapshot,
    numberSnapshot = numberSnapshot,
    recurrence = recurrence,
    startDate = startDate,
    nextDueDate = nextDueDate,
    active = active,
    note = note,
    createdAt = createdAt,
    preferredTimeMinutes = preferredTimeMinutes,
    isImportant = isImportant,
    comfortableTimeEndMinutes = comfortableTimeEndMinutes
)

fun Schedule.toEntity() = ScheduleEntity(
    id = id,
    contactLookupKey = contactLookupKey,
    displayNameSnapshot = displayNameSnapshot,
    normalizedNameSnapshot = normalizedNameSnapshot,
    numberSnapshot = numberSnapshot,
    recurrence = recurrence,
    startDate = startDate,
    nextDueDate = nextDueDate,
    active = active,
    note = note,
    createdAt = createdAt,
    preferredTimeMinutes = preferredTimeMinutes,
    isImportant = isImportant,
    comfortableTimeEndMinutes = comfortableTimeEndMinutes
)

fun ScheduleCompletionEntity.toDomain() = ScheduleCompletion(
    id = id,
    scheduleId = scheduleId,
    dueDate = dueDate,
    completedAt = completedAt,
    callEventId = callEventId,
    matchConfidence = matchConfidence,
    matchMethod = matchMethod
)

fun ScheduleCompletion.toEntity() = ScheduleCompletionEntity(
    id = id,
    scheduleId = scheduleId,
    dueDate = dueDate,
    completedAt = completedAt,
    callEventId = callEventId,
    matchConfidence = matchConfidence,
    matchMethod = matchMethod
)

fun CallEventEntity.toDomain() = CallEvent(
    id = id,
    packageName = packageName,
    notifKey = notifKey,
    statesSeen = statesSeen,
    connected = connected,
    isVideo = isVideo,
    isGroupCall = isGroupCall,
    direction = direction,
    notificationName = notificationName,
    postTime = postTime,
    ongoingWhen = ongoingWhen,
    ongoingPostedElapsedRealtime = ongoingPostedElapsedRealtime,
    endTime = endTime,
    inferredDurationMs = inferredDurationMs,
    qualified = qualified,
    avatarHash = avatarHash,
    matchedScheduleId = matchedScheduleId,
    matchConfidence = matchConfidence,
    rawJson = rawJson
)

fun CallEvent.toEntity() = CallEventEntity(
    id = id,
    packageName = packageName,
    notifKey = notifKey,
    statesSeen = statesSeen,
    connected = connected,
    isVideo = isVideo,
    isGroupCall = isGroupCall,
    direction = direction,
    notificationName = notificationName,
    postTime = postTime,
    ongoingWhen = ongoingWhen,
    ongoingPostedElapsedRealtime = ongoingPostedElapsedRealtime,
    endTime = endTime,
    inferredDurationMs = inferredDurationMs,
    qualified = qualified,
    avatarHash = avatarHash,
    matchedScheduleId = matchedScheduleId,
    matchConfidence = matchConfidence,
    rawJson = rawJson
)

fun PendingMatchEntity.toDomain() = PendingMatch(
    id = id,
    callEventId = callEventId,
    candidateScheduleIds = candidateScheduleIds.toLongList(),
    candidateContactIds = candidateContactIds.toLongList(),
    reason = reason,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    resolvedScheduleId = resolvedScheduleId
)

fun PendingMatch.toEntity() = PendingMatchEntity(
    id = id,
    callEventId = callEventId,
    candidateScheduleIds = candidateScheduleIds.joinToString(","),
    candidateContactIds = candidateContactIds.joinToString(","),
    reason = reason,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    resolvedScheduleId = resolvedScheduleId
)

private fun String.toLongList(): List<Long> =
    if (isBlank()) emptyList() else split(",").mapNotNull { it.trim().toLongOrNull() }

fun LogEntryEntity.toDomain() = LogEntry(
    id = id,
    timestamp = timestamp,
    level = level,
    tag = tag,
    message = message,
    throwable = throwable
)

fun DigestReminderTimeEntity.toDomain() = DigestReminderTime(id = id, hour = hour, minute = minute, label = label, enabled = enabled)

fun DigestReminderTime.toEntity() = DigestReminderTimeEntity(id = id, hour = hour, minute = minute, label = label, enabled = enabled)
