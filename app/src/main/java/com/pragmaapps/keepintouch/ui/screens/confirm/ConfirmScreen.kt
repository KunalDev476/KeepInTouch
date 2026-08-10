@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.theme.Spacing
import androidx.compose.material.icons.filled.CheckCircle

@Composable
fun ConfirmScreen(
    onBack: () -> Unit,
    onCreateScheduleForContact: (lookupKey: String) -> Unit,
    viewModel: ConfirmViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "Nothing needs confirmation",
                subtitle = "Ambiguous calls will show up here for you to resolve.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(Spacing.lg)
            ) {
                items(items, key = { it.pendingMatch.id }) { display ->
                    PendingMatchCard(
                        display = display,
                        onConfirmSchedule = { scheduleId -> viewModel.confirmSchedule(display.pendingMatch.id, scheduleId) },
                        onCreateScheduleForContact = onCreateScheduleForContact,
                        onDismiss = { viewModel.dismiss(display.pendingMatch.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingMatchCard(
    display: PendingMatchDisplay,
    onConfirmSchedule: (Long?) -> Unit,
    onCreateScheduleForContact: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val event = display.callEvent
            Text(
                "Call from \"${event?.notificationName ?: "?"}\"" +
                    (event?.postTime?.let { " on ${DateMath.formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.titleSmall
            )
            Text(display.pendingMatch.reason, style = MaterialTheme.typography.bodySmall)

            if (display.candidateSchedules.isNotEmpty()) {
                Text("Which contact was this call with?", style = MaterialTheme.typography.labelMedium)
                display.candidateSchedules.forEach { schedule ->
                    val isSuggested = schedule.id == display.suggestedScheduleId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSuggested) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Column(Modifier.padding(Spacing.md)) {
                            Text(schedule.displayNameSnapshot + if (isSuggested) " (avatar match)" else "")
                            Button(onClick = { onConfirmSchedule(schedule.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                                Text("This is the one")
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { onConfirmSchedule(null) }) { Text("None of these") }
            } else if (display.candidateContacts.isNotEmpty()) {
                Text("This contact has no schedule yet:", style = MaterialTheme.typography.labelMedium)
                display.candidateContacts.forEach { contact ->
                    Button(
                        onClick = { onCreateScheduleForContact(contact.lookupKey) },
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("Create schedule for ${contact.displayName}")
                    }
                }
                OutlinedButton(onClick = onDismiss) { Text("Not now") }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
