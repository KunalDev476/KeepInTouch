@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.confirm

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.theme.Spacing
import androidx.compose.material.icons.filled.CheckCircle

@Composable
fun ConfirmScreen(
    onBack: () -> Unit,
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
                        onDismiss = { viewModel.dismiss(display.pendingMatch.id) }
                    )
                }
            }
        }
    }
}

/**
 * Steps through [PendingMatchDisplay.candidateSchedules] one at a time (best guess first - see
 * ConfirmViewModel.buildDisplay): No moves to the next candidate, Yes confirms the one on screen.
 * Once every candidate has been said no to, the whole prompt dismisses.
 */
@Composable
private fun PendingMatchCard(
    display: PendingMatchDisplay,
    onConfirmSchedule: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    var candidateIndex by remember(display.pendingMatch.id) { mutableIntStateOf(0) }
    val candidate = display.candidateSchedules.getOrNull(candidateIndex)

    Card(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val event = display.callEvent
            Text(
                "Call from \"${event?.notificationName ?: "?"}\"" +
                    (event?.postTime?.let { " on ${DateMath.formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.titleSmall
            )
            Text(display.pendingMatch.reason, style = MaterialTheme.typography.bodySmall)

            if (candidate != null) {
                Text("Was this call with ${candidate.displayNameSnapshot}?", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = {
                            if (candidateIndex + 1 >= display.candidateSchedules.size) onDismiss() else candidateIndex++
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("No") }
                    Button(
                        onClick = { onConfirmSchedule(candidate.id) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Yes") }
                }
            } else {
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
