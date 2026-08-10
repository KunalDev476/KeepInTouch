@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.people

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.AvatarWithWhatsAppBadge
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.LoadingSkeleton
import com.pragmaapps.keepintouch.ui.components.SegmentedControl
import com.pragmaapps.keepintouch.ui.theme.Spacing

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

    Scaffold(topBar = { TopAppBar(title = { Text("People") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!uiState.contactsPermissionGranted) {
                ContactsPermissionPrompt(
                    onGrant = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                )
                return@Scaffold
            }

            Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
                SegmentedControl(
                    options = listOf("Scheduled", "All contacts"),
                    selectedIndex = if (uiState.segment == PeopleSegment.SCHEDULED) 0 else 1,
                    onSelect = { index ->
                        viewModel.onSegmentChange(if (index == 0) PeopleSegment.SCHEDULED else PeopleSegment.ALL)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                placeholder = { Text("Search contacts") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true
            )

            if (uiState.isLoading) {
                LoadingSkeleton()
            } else {
                when (uiState.segment) {
                    PeopleSegment.SCHEDULED -> ScheduledList(uiState.scheduledRows, onRowClick = { onOpenContact(it.schedule.contactLookupKey) })
                    PeopleSegment.ALL -> AllContactsList(
                        uiState.allContactRows,
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
private fun ScheduledList(rows: List<ScheduledPersonRow>, onRowClick: (ScheduledPersonRow) -> Unit) {
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.People,
            title = "No one scheduled yet",
            subtitle = "Switch to \"All contacts\" and tap + on someone you want to stay in touch with.",
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
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
                    headlineContent = { Text(row.schedule.displayNameSnapshot) },
                    supportingContent = { Text("Next due ${DateMath.formatDate(row.schedule.nextDueDate)}") }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AllContactsList(
    rows: List<AllContactRow>,
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
    LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
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
                if (dimmed) "Can't auto-detect - no WhatsApp found" else row.schedule?.let { "Scheduled - ${it.recurrence.name.lowercase()}" } ?: "Not scheduled",
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
