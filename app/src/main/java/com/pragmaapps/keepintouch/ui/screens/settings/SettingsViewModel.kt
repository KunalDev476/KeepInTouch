package com.pragmaapps.keepintouch.ui.screens.settings

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pragmaapps.keepintouch.data.prefs.DetectionPreferences
import com.pragmaapps.keepintouch.domain.repository.ContactRepository
import com.pragmaapps.keepintouch.domain.repository.DataManagementRepository
import com.pragmaapps.keepintouch.domain.usecase.SyncContactsUseCase
import com.pragmaapps.keepintouch.domain.util.DetectionSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val contactsPermissionGranted: Boolean = false,
    val syncedContactCount: Int = 0,
    val whatsappContactCount: Int = 0,
    val notificationListenerGranted: Boolean = false,
    val phoneStatePermissionGranted: Boolean = false,
    val detectionSettings: DetectionSettings = DetectionSettings()
)

private data class PermissionSnapshot(
    val contactsGranted: Boolean,
    val notificationListenerGranted: Boolean,
    val phoneStateGranted: Boolean
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val detectionPreferences: DetectionPreferences,
    private val contactRepository: ContactRepository,
    private val dataManagementRepository: DataManagementRepository,
    private val syncContacts: SyncContactsUseCase
) : AndroidViewModel(application) {

    private val permissionState = MutableStateFlow(checkPermissions())

    val uiState: StateFlow<SettingsUiState> = combine(
        permissionState,
        detectionPreferences.settings
    ) { permissions, detection ->
        SettingsUiState(
            contactsPermissionGranted = permissions.contactsGranted,
            notificationListenerGranted = permissions.notificationListenerGranted,
            phoneStatePermissionGranted = permissions.phoneStateGranted,
            detectionSettings = detection
        )
    }
        .combine(contactRepository.observeAll()) { state, contacts ->
            state.copy(syncedContactCount = contacts.size, whatsappContactCount = contacts.count { it.hasWhatsApp })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    private fun checkPermissions(): PermissionSnapshot {
        val app = getApplication<Application>()
        val contacts = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val listener = NotificationManagerCompat.getEnabledListenerPackages(app).contains(app.packageName)
        val phoneState = ContextCompat.checkSelfPermission(app, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        return PermissionSnapshot(contacts, listener, phoneState)
    }

    fun refreshPermissionState() {
        permissionState.value = checkPermissions()
    }

    fun resyncContacts() {
        viewModelScope.launch { syncContacts() }
    }

    fun setMinDurationSeconds(seconds: Int) = detectionPreferences.setMinDurationMs(seconds * 1000L)
    fun setCountIncoming(value: Boolean) = detectionPreferences.setCountIncoming(value)
    fun setCountOutgoing(value: Boolean) = detectionPreferences.setCountOutgoing(value)

    fun exportData(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = dataManagementRepository.exportAllDataAsJson()
            onReady(json)
        }
    }

    fun clearAllData(onDone: () -> Unit) {
        viewModelScope.launch {
            dataManagementRepository.clearAllData()
            onDone()
        }
    }
}
