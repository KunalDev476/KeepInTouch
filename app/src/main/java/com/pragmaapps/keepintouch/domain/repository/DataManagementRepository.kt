package com.pragmaapps.keepintouch.domain.repository

interface DataManagementRepository {
    /** Serializes contacts, schedules, completions, call events, and pending matches as one JSON document. */
    suspend fun exportAllDataAsJson(): String

    /** Wipes every table. Irreversible - the caller is responsible for confirming with the user first. */
    suspend fun clearAllData()
}
