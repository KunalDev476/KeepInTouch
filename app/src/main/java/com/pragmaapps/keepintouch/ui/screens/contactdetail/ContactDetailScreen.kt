@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.contactdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.model.CallEvent
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.PhoneMasking
import com.pragmaapps.keepintouch.domain.util.PriorityEngine
import com.pragmaapps.keepintouch.domain.util.RecurrenceEngine
import com.pragmaapps.keepintouch.ui.components.AvatarWithWhatsAppBadge
import com.pragmaapps.keepintouch.ui.components.PriorityChip
import com.pragmaapps.keepintouch.ui.components.RecurrenceChip
import com.pragmaapps.keepintouch.ui.components.ScheduleStatusIcon
import com.pragmaapps.keepintouch.ui.components.label
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors

@Composable
fun ContactDetailScreen(
    lookupKey: String,
    onBack: () -> Unit,
    onEditSchedule: (scheduleId: Long) -> Unit,
    viewModel: ContactDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(lookupKey) { viewModel.load(lookupKey) }
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.contact?.displayName ?: "Contact") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val contact = uiState.contact
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            item {
                if (contact != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        AvatarWithWhatsAppBadge(displayName = contact.displayName, hasWhatsApp = contact.hasWhatsApp, size = 64.dp)
                        Column {
                            Text(contact.displayName, style = MaterialTheme.typography.headlineSmall)
                            contact.primaryNumber?.let {
                                Text(PhoneMasking.mask(it), style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                if (contact.hasWhatsApp) "WhatsApp detected" else "No WhatsApp found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        StatTile(label = "Scheduled calls", value = "${uiState.scheduledCallCount}", modifier = Modifier.weight(1f))
                        StatTile(label = "Unscheduled calls", value = "${uiState.unscheduledCallCount}", modifier = Modifier.weight(1f))
                    }
                    Column(Modifier.padding(top = Spacing.lg)) {
                        uiState.schedule?.let { schedule ->
                            ScheduleCard(
                                schedule = schedule,
                                onEdit = { onEditSchedule(schedule.id) },
                                onTogglePause = { viewModel.setActive(schedule, active = !schedule.active) },
                                onDelete = { showDeleteConfirm = true }
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = Spacing.md),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                StatTile(label = "Successful", value = "${uiState.onTimeCount}", modifier = Modifier.weight(1f))
                                StatTile(label = "Late", value = "${uiState.lateCount}", modifier = Modifier.weight(1f))
                                StatTile(label = "Missed", value = "${uiState.missedCount}", modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        "Call history",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.sm)
                    )
                }
            }
            if (contact != null && uiState.timeline.isEmpty()) {
                item { Text("No calls detected yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(uiState.timeline, key = { it.id }) { event ->
                TimelineRow(event)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this schedule?") },
            text = { Text("This removes the schedule and its history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    uiState.schedule?.let { viewModel.deleteSchedule(it.id) }
                    showDeleteConfirm = false
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: com.pragmaapps.keepintouch.domain.model.Schedule,
    onEdit: () -> Unit,
    onTogglePause: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                RecurrenceChip(schedule.recurrence)
                val now = System.currentTimeMillis()
                PriorityChip(PriorityEngine.score(schedule, now))
                if (!schedule.active) {
                    Text("Paused", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                ScheduleStatusIcon(
                    status = RecurrenceEngine.status(schedule, now),
                    lateFraction = PriorityEngine.escalationFraction(schedule, now)
                )
            }
            Row(
                Modifier.padding(top = Spacing.xs).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Text("Next due ${DateMath.formatDate(schedule.nextDueDate)}", style = MaterialTheme.typography.bodyMedium)
                schedule.preferredTimeMinutes?.let { start ->
                    val label = schedule.comfortableTimeEndMinutes?.let { end ->
                        "${DateMath.formatTimeOfDay(start)} - ${DateMath.formatTimeOfDay(end)}"
                    } ?: "at ${DateMath.formatTimeOfDay(start)}"
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            schedule.note?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.xs))
            }
            Row(Modifier.padding(top = Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit schedule") }
                IconButton(onClick = onTogglePause) {
                    Icon(
                        if (schedule.active) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (schedule.active) "Pause" else "Resume"
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete schedule") }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(Spacing.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TimelineRow(event: CallEvent) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Icon(
            when (event.direction) {
                Direction.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                Direction.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                Direction.UNKNOWN -> Icons.AutoMirrored.Filled.HelpOutline
            },
            contentDescription = event.direction.name,
            tint = if (event.qualified) MaterialTheme.extendedColors.successGreen else MaterialTheme.colorScheme.outline
        )
        if (event.isVideo) {
            Icon(Icons.Filled.Videocam, contentDescription = "Video", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(DateMath.formatDate(event.postTime), style = MaterialTheme.typography.bodyMedium)
            Text(
                event.inferredDurationMs?.let { "${it / 1000}s" } ?: "no duration",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ConfidenceDot(qualified = event.qualified)
    }
}

@Composable
private fun ConfidenceDot(qualified: Boolean) {
    val color = if (qualified) MaterialTheme.extendedColors.successGreen else MaterialTheme.colorScheme.outlineVariant
    androidx.compose.foundation.layout.Box(
        Modifier.size(10.dp).background(color, CircleShape)
    )
}
