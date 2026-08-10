@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pragmaapps.keepintouch.ui.screens.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.pragmaapps.keepintouch.domain.model.LogEntry
import com.pragmaapps.keepintouch.domain.model.LogLevel
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.LoadingSkeleton
import com.pragmaapps.keepintouch.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

@Composable
fun AppLogsScreen(onBack: () -> Unit, viewModel: AppLogsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showClearConfirm = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear logs")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                LevelChip("All", selected = uiState.selectedLevel == null, onClick = { viewModel.onLevelSelected(null) })
                LevelChip("Debug", selected = uiState.selectedLevel == LogLevel.DEBUG, onClick = { viewModel.onLevelSelected(LogLevel.DEBUG) })
                LevelChip("Info", selected = uiState.selectedLevel == LogLevel.INFO, onClick = { viewModel.onLevelSelected(LogLevel.INFO) })
                LevelChip("Error", selected = uiState.selectedLevel == LogLevel.ERROR, onClick = { viewModel.onLevelSelected(LogLevel.ERROR) })
            }

            if (uiState.isLoading) {
                LoadingSkeleton()
            } else if (uiState.entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Delete,
                    title = "No logs yet",
                    subtitle = "App activity - call detection, matching, reminders - will show up here as it happens.",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        LogRow(entry, modifier = Modifier.padding(bottom = Spacing.sm))
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all logs?") },
            text = { Text("This deletes every stored log entry. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearLogs(); showClearConfirm = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LevelChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun LogRow(entry: LogEntry, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.INFO -> MaterialTheme.colorScheme.primary
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier.fillMaxWidth().clickable(enabled = entry.throwable != null) { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    entry.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor
                )
                Text(
                    Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(timestampFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(entry.tag, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
            if (entry.throwable != null) {
                Text(
                    if (expanded) "Tap to hide stack trace" else "Tap to show stack trace",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xs)
                )
                if (expanded) {
                    Text(
                        entry.throwable,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(top = Spacing.xs)
                    )
                }
            }
        }
    }
}
