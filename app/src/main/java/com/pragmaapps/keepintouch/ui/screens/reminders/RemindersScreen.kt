@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.reminders

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.model.DigestReminderTime
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.LoadingSkeleton
import com.pragmaapps.keepintouch.ui.theme.Spacing

@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddPicker by remember { mutableStateOf(false) }

    fun notificationsGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var postNotificationsGranted by remember { mutableStateOf(notificationsGranted()) }
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> postNotificationsGranted = granted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddPicker = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add reminder time")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Set reminders for when you want to be told about calls you still need to make. " +
                    "Each one covers everything pending at that moment, normal and important together. " +
                    "Important calls also get their own separate reminder 30 minutes before the call time you set for them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(Spacing.lg)
            )

            if (!postNotificationsGranted) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        Modifier.padding(Spacing.md).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        Text(
                            "Notifications aren't allowed for this app - reminders will fire but can't show anything.",
                            Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = {
                            if (!notificationsGranted()) postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }) { Text("Grant") }
                    }
                }
            }

            if (uiState.isLoading) {
                LoadingSkeleton()
            } else if (uiState.reminders.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Add,
                    title = "No reminder times yet",
                    subtitle = "Tap + to add one.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    items(uiState.reminders, key = { it.id }) { reminder ->
                        ReminderRow(
                            reminder = reminder,
                            onToggle = { enabled -> viewModel.setEnabled(reminder, enabled) },
                            onTimeChange = { hour, minute -> viewModel.updateTime(reminder, hour, minute) },
                            onDelete = { viewModel.delete(reminder) },
                            modifier = Modifier.padding(bottom = Spacing.sm)
                        )
                    }
                }
            }
        }
    }

    if (showAddPicker) {
        SimpleTimePickerDialog(
            initialHour = 9,
            initialMinute = 0,
            onDismiss = { showAddPicker = false },
            onConfirm = { hour, minute ->
                if (!notificationsGranted()) postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                viewModel.addReminder(hour, minute, labelFor(hour))
                showAddPicker = false
            }
        )
    }
}

private fun labelFor(hour: Int): String = when {
    hour < 12 -> "Morning"
    hour < 17 -> "Afternoon"
    else -> "Evening"
}

@Composable
private fun ReminderRow(
    reminder: DigestReminderTime,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Card(modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(Spacing.md).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DateMath.formatTimeOfDay(reminder.hour * 60 + reminder.minute),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = Spacing.xs)
                )
                Text(reminder.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = { showPicker = true }) { Text("Change") }
            Switch(checked = reminder.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete reminder")
            }
        }
    }

    if (showPicker) {
        SimpleTimePickerDialog(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            onDismiss = { showPicker = false },
            onConfirm = { hour, minute ->
                onTimeChange(hour, minute)
                showPicker = false
            }
        )
    }
}

@Composable
private fun SimpleTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TimePicker(state = state)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
                }
            }
        }
    }
}
