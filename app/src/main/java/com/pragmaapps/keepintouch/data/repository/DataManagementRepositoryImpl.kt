package com.pragmaapps.keepintouch.data.repository

import com.pragmaapps.keepintouch.data.db.AppDatabase
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.PendingMatch
import com.pragmaapps.keepintouch.domain.model.Schedule
import com.pragmaapps.keepintouch.domain.model.ScheduleCompletion
import com.pragmaapps.keepintouch.domain.repository.CallEventRepository
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.DataManagementRepository
import com.pragmaapps.keepintouch.domain.repository.PendingMatchRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleCompletionRepository
import com.pragmaapps.keepintouch.domain.repository.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataManagementRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val contactRepository: ContactRepository,
    private val scheduleRepository: ScheduleRepository,
    private val completionRepository: ScheduleCompletionRepository,
    private val callEventRepository: CallEventRepository,
    private val pendingMatchRepository: PendingMatchRepository
) : DataManagementRepository {

    override suspend fun exportAllDataAsJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("exportedAt", System.currentTimeMillis())
        root.put("contacts", contactRepository.observeAll().first().toJsonArray(::contactToJson))
        root.put("schedules", scheduleRepository.observeAll().first().toJsonArray(::scheduleToJson))
        root.put("scheduleCompletions", completionRepository.observeAll().first().toJsonArray(::completionToJson))
        root.put("callEvents", callEventRepository.observeAll().first().toJsonArray(::callEventToJson))
        root.put("pendingMatches", pendingMatchRepository.observeAll().first().toJsonArray(::pendingMatchToJson))
        root.toString(2)
    }

    override suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }

    private fun <T> List<T>.toJsonArray(map: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(map(it)) }
        return array
    }

    private fun contactToJson(c: Contact) = JSONObject().apply {
        put("id", c.id)
        put("displayName", c.displayName)
        put("hasWhatsApp", c.hasWhatsApp)
    }

    private fun scheduleToJson(s: Schedule) = JSONObject().apply {
        put("id", s.id)
        put("contactDisplayNameSnapshot", s.displayNameSnapshot)
        put("recurrence", s.recurrence.name)
        put("nextDueDate", s.nextDueDate)
        put("active", s.active)
        put("note", s.note)
    }

    private fun completionToJson(c: ScheduleCompletion) = JSONObject().apply {
        put("id", c.id)
        put("scheduleId", c.scheduleId)
        put("dueDate", c.dueDate)
        put("completedAt", c.completedAt)
        put("callEventId", c.callEventId)
        put("matchConfidence", c.matchConfidence.name)
        put("matchMethod", c.matchMethod)
    }

    private fun callEventToJson(e: CallEvent) = JSONObject().apply {
        put("id", e.id)
        put("notificationName", e.notificationName)
        put("direction", e.direction.name)
        put("connected", e.connected)
        put("isVideo", e.isVideo)
        put("isGroupCall", e.isGroupCall)
        put("postTime", e.postTime)
        put("inferredDurationMs", e.inferredDurationMs)
        put("qualified", e.qualified)
        put("matchedScheduleId", e.matchedScheduleId)
        put("matchConfidence", e.matchConfidence?.name)
    }

    private fun pendingMatchToJson(p: PendingMatch) = JSONObject().apply {
        put("id", p.id)
        put("callEventId", p.callEventId)
        put("reason", p.reason)
        put("resolvedAt", p.resolvedAt)
    }
}
