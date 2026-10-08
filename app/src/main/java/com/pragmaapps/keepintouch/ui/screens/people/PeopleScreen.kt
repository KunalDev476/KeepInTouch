@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.people

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.navigation.ForegroundResetGate
import com.pragmaapps.keepintouch.ui.components.AvatarWithWhatsAppBadge
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.LoadingSkeleton
import com.pragmaapps.keepintouch.ui.components.SegmentedControl
import com.pragmaapps.keepintouch.ui.components.label
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors

@Composable
fun PeopleScreen(
    onScheduleContact: (lookupKey: String) -> Unit,
    onOpenContact: (lookupKey: String) -> Unit,
    resetToScheduledSignal: Int = 0,
    viewModel: PeopleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissionState() }

    // Fired after a new schedule is created elsewhere (any entry point) - jump to "Scheduled"
    // with a clear search box, so the thing you just created is immediately visible.
    LaunchedEffect(resetToScheduledSignal) {
        if (resetToScheduledSignal > 0) {
            viewModel.onSegmentChange(PeopleSegment.SCHEDULED)
            viewModel.onQueryChange("")
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Your Contacts") }) },
        floatingActionButton = {
            // Only once there's something to browse in Call Schedules - with zero schedules, the
            // segment's own EmptyState already tells you to switch to All contacts and tap +.
            if (uiState.segment == PeopleSegment.SCHEDULED && uiState.scheduledRows.isNotEmpty()) {
                FloatingActionButton(onClick = { viewModel.onSegmentChange(PeopleSegment.ALL) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add someone to call")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.contactsPermissionGranted) {
                ContactsPermissionPrompt(
                    onGrant = {
                        ForegroundResetGate.suppressNext()
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                )
                return@Scaffold
            }

            Text(
                "Choose who you want to stay in touch with and set how often you want to call them",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = 0.dp, bottom = Spacing.sm)
            )

            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                SegmentedControl(
                    options = listOf("All contacts", "Call Schedules"),
                    selectedIndex = if (uiState.segment == PeopleSegment.ALL) 0 else 1,
                    onSelect = { index ->
                        viewModel.onSegmentChange(if (index == 0) PeopleSegment.ALL else PeopleSegment.SCHEDULED)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Search only applies to All contacts - Call Schedules is short enough to scan by eye.
            if (uiState.segment == PeopleSegment.ALL) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    // Ellipsized rather than wrapped, so narrow screens show "Search people by name or…" on one line.
                    placeholder = { Text("Search people by name or number", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            val scheduledListState = androidx.compose.foundation.lazy.rememberLazyListState()
            val allContactsListState = androidx.compose.foundation.lazy.rememberLazyListState()

            // Typing, or backspacing, a new query while scrolled down should snap back to the top
            // so the best/first match is immediately visible instead of wherever the list happened
            // to be scrolled to before.
            LaunchedEffect(uiState.query, uiState.segment) {
                when (uiState.segment) {
                    PeopleSegment.SCHEDULED -> if (scheduledListState.firstVisibleItemIndex != 0) scheduledListState.scrollToItem(0)
                    PeopleSegment.ALL -> if (allContactsListState.firstVisibleItemIndex != 0) allContactsListState.scrollToItem(0)
                }
            }

            if (uiState.isLoading) {
                LoadingSkeleton()
            } else {
                when (uiState.segment) {
                    PeopleSegment.SCHEDULED -> ScheduledList(
                        uiState.scheduledRows,
                        listState = scheduledListState,
                        onRowClick = { onOpenContact(it.schedule.contactLookupKey) }
                    )
                    PeopleSegment.ALL -> AllContactsList(
                        uiState.allContactRows,
                        listState = allContactsListState,
                        onScheduleContact = onScheduleContact,
                        onOpenContact = onOpenContact
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactsPermissionPrompt(onGrant: () -> Unit) {
    EmptyState(
        icon = Icons.Filled.People,
        title = "Contacts access needed",
        subtitle = "KeepInTouch reads your contacts (read-only) to find who has WhatsApp and let you schedule them.",
        actionLabel = "Grant access",
        onAction = onGrant,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScheduledList(
    rows: List<ScheduledPersonRow>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onRowClick: (ScheduledPersonRow) -> Unit
) {
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = "No one scheduled yet",
            subtitle = "Switch to \"All contacts\" and tap + on someone you want to stay in touch with.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        items(rows, key = { it.schedule.id }) { row ->
            Column(Modifier.animateItem()) {
                ListItem(
                    modifier = Modifier.clickable { onRowClick(row) },
                    leadingContent = {
                        AvatarWithWhatsAppBadge(
                            displayName = row.schedule.displayNameSnapshot,
                            hasWhatsApp = row.contact?.hasWhatsApp ?: false
                        )
                    },
                    headlineContent = {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                row.schedule.displayNameSnapshot,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            ScheduleStateChip(row = row)
                        }
                    },
                    supportingContent = {
                        val callsText = callsStatText(row.callStatCount, row.callStatMonthName, row.callStatIsCurrentMonth)
                        val fullText = if (row.completed || !row.schedule.active) {
                            callsText
                        } else {
                            "$callsText · Next ${DateMath.formatDateShort(row.schedule.nextDueDate)}"
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Icon(
                                if (row.callStatCount == 0) Icons.Filled.PhoneDisabled else Icons.Filled.Call,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(fullText)
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

/** "3 calls this month" while [monthName] is the current month, else "3 calls in {month}" naming the past month it happened in. */
private fun callsStatText(count: Int, monthName: String?, isCurrentMonth: Boolean): String {
    if (monthName == null) return "No calls yet"
    val monthLabel = if (isCurrentMonth) "this month" else "in ${monthName.take(3)}"
    return when (count) {
        0 -> "No calls $monthLabel"
        1 -> "1 call $monthLabel"
        else -> "$count calls $monthLabel"
    }
}

@Composable
private fun ScheduleStateChip(row: ScheduledPersonRow, modifier: Modifier = Modifier) {
    val (label, color) = when {
        row.completed -> "Completed" to MaterialTheme.extendedColors.successGreen
        row.schedule.active -> "Active" to MaterialTheme.extendedColors.successGreen
        else -> "Paused" to MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AllContactsList(
    rows: List<AllContactRow>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onScheduleContact: (String) -> Unit,
    onOpenContact: (String) -> Unit
) {
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = "No contacts synced yet",
            subtitle = "Pull down or reopen this tab once Contacts access is granted.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = Spacing.xxl)) {
        items(rows, key = { it.contact.lookupKey }) { row ->
            Column(Modifier.animateItem()) {
                AllContactRowItem(row, onScheduleContact, onOpenContact)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AllContactRowItem(
    row: AllContactRow,
    onScheduleContact: (String) -> Unit,
    onOpenContact: (String) -> Unit
) {
    val contact = row.contact
    val dimmed = !contact.hasWhatsApp
    ListItem(
        modifier = Modifier.clickable {
            if (row.schedule != null) onOpenContact(contact.lookupKey) else onScheduleContact(contact.lookupKey)
        },
        leadingContent = {
            AvatarWithWhatsAppBadge(
                displayName = contact.displayName,
                hasWhatsApp = contact.hasWhatsApp,
                modifier = if (dimmed) Modifier.alpha(0.5f) else Modifier
            )
        },
        headlineContent = {
            Text(
                contact.displayName,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Text(
                if (dimmed) "WhatsApp calls can't be tracked for this contact" else row.schedule?.let { "Scheduled - ${it.recurrence.label()}" } ?: "Add to call Schedule",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (row.schedule == null) {
                IconButton(onClick = { onScheduleContact(contact.lookupKey) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Schedule ${contact.displayName}")
                }
            }
        }
    )
}
