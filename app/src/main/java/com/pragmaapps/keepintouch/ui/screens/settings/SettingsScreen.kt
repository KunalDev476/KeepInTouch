@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.ui.components.PermissionStatusCard
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors

@Composable
fun SettingsScreen(
    onOpenAppLogs: () -> Unit = {},
    onOpenCallLogs: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionState() }

    val callLogPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionState() }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            item { SectionHeader("Permissions") }
            item {
                ContactsPermissionCard(
                    granted = uiState.contactsPermissionGranted,
                    syncedCount = uiState.syncedContactCount,
                    whatsappCount = uiState.whatsappContactCount,
                    onGrant = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) },
                    onResync = { viewModel.resyncContacts() },
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }
            item {
                PermissionStatusCard(
                    title = "Notification permissions",
                    granted = uiState.notificationListenerGranted,
                    description = if (uiState.notificationListenerGranted) {
                        "Granted - this app can read WhatsApp call notifications."
                    } else {
                        "Required to detect WhatsApp calls. Android requires this to be granted from Settings, not a normal prompt."
                    },
                    actionLabel = "Open Settings",
                    onAction = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }
            item {
                // WhatsAppCallListenerService is strictly WhatsApp-scoped with no cross-app
                // fallback - this is the only path for cellular (non-WhatsApp) call detection, so
                // its absence is surfaced here rather than silently degrading.
                PermissionStatusCard(
                    title = "Call log permission",
                    granted = uiState.callLogPermissionGranted,
                    description = if (uiState.callLogPermissionGranted) {
                        "Granted - this app can detect regular (non-WhatsApp) phone calls."
                    } else {
                        "Required to detect regular phone calls. Without it, only WhatsApp calls will be detected - " +
                            "schedules fulfilled only by a WhatsApp call, never a normal call."
                    },
                    actionLabel = "Grant call log access",
                    onAction = { callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
                    modifier = Modifier.padding(bottom = Spacing.lg)
                )
            }

            item { SectionHeader("Detection") }
            item { DetectionSection(uiState, viewModel) }

            item { SectionHeader("Reminders") }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.lg).clickable(onClick = onOpenReminders)
                ) {
                    Row(
                        Modifier.padding(Spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Call reminders", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Set when you'd like to be reminded about pending calls - on by default at 8pm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }

            item { SectionHeader("Diagnostics") }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm).clickable(onClick = onOpenAppLogs)
                ) {
                    Row(
                        Modifier.padding(Spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("App Logs", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "See exactly what the app has been doing - call detection, matching, reminders",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                    }
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm).clickable(onClick = onOpenCallLogs)
                ) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text("Call Logs", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Every detected call notification, qualified or not, with the exact reason why",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Spacing.xxl)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.sm)
    )
}

@Composable
private fun ContactsPermissionCard(
    granted: Boolean,
    syncedCount: Int,
    whatsappCount: Int,
    onGrant: () -> Unit,
    onResync: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    Modifier.size(44.dp).background(
                        if (granted) MaterialTheme.extendedColors.successGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.extendedColors.successGreen else MaterialTheme.colorScheme.error
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("Contacts access", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (granted) "Read-only - used to find who has WhatsApp" else "Needed to find saved contacts and detect WhatsApp presence",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (granted) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Granted", tint = MaterialTheme.extendedColors.successGreen)
                }
            }

            if (granted) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StatChip(label = "synced", value = syncedCount, modifier = Modifier.weight(1f))
                    StatChip(label = "with WhatsApp", value = whatsappCount, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(Spacing.md))
            if (granted) {
                OutlinedButton(onClick = onResync, modifier = Modifier.fillMaxWidth()) { Text("Re-sync now") }
            } else {
                Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) { Text("Grant access") }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text("$value", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetectionSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val settings = uiState.detectionSettings
    Card(Modifier.fillMaxWidth().padding(bottom = Spacing.lg)) {
        Column(Modifier.padding(Spacing.lg)) {
            Text("Minimum call duration: ${settings.minDurationMs / 1000}s", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = (settings.minDurationMs / 1000L).toFloat(),
                onValueChange = { viewModel.setMinDurationSeconds(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28
            )
            ToggleRow("Count incoming calls", settings.countIncoming, viewModel::setCountIncoming)
            ToggleRow("Count outgoing calls", settings.countOutgoing, viewModel::setCountOutgoing)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

