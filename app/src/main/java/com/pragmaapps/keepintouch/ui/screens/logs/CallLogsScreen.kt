@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.usecase.CallEventDiagnostics
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.LoadingSkeleton
import com.pragmaapps.keepintouch.ui.theme.Spacing

@Composable
fun CallLogsScreen(onBack: () -> Unit, viewModel: CallLogsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingSkeleton(modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }

        if (uiState.events.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.Phone,
                title = "No calls captured yet",
                subtitle = "Every detected WhatsApp call and regular phone call will show up here, qualified or not.",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(Spacing.lg)
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(uiState.outgoingStatus.message, Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = Spacing.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(uiState.cellularStatus.message, Modifier.padding(Spacing.md), style = MaterialTheme.typography.bodySmall)
                }
            }
            items(uiState.events, key = { it.event.id }) { display ->
                CallLogCard(display, modifier = Modifier.padding(bottom = Spacing.sm))
            }
        }
    }
}

@Composable
private fun CallLogCard(display: CallEventDiagnostics.Display, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val event = display.event
    Card(
        modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (event.qualified) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Text("\"${event.notificationName}\" - ${event.direction} - ${if (event.qualified) "QUALIFIED" else "not qualified"}")
            Text(event.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "connected=${event.connected} video=${event.isVideo} group=${event.isGroupCall} " +
                    "duration=${event.inferredDurationMs?.let { "${it}ms" } ?: "n/a"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text("posted ${DateMath.formatDate(event.postTime)}", style = MaterialTheme.typography.bodySmall)
            display.matchedScheduleName?.let {
                Text("Matched schedule: $it (${event.matchConfidence})", style = MaterialTheme.typography.bodySmall)
            }
            display.pendingReason?.let {
                Text("Needs confirmation: $it", style = MaterialTheme.typography.bodySmall)
            }
            if (display.disqualifyReasons.isNotEmpty()) {
                Text("Why not qualified:", style = MaterialTheme.typography.labelMedium)
                display.disqualifyReasons.forEach { reason -> Text("- $reason", style = MaterialTheme.typography.bodySmall) }
            }
            Text(if (expanded) "Tap to hide raw JSON" else "Tap to show raw JSON", style = MaterialTheme.typography.labelSmall)
            if (expanded) {
                Text(
                    event.rawJson,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            }
        }
    }
}
