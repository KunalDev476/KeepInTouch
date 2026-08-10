@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.model.Importance
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.label
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun ScheduleSheet(
    args: ScheduleSheetArgs,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onScheduleCreated: () -> Unit = {},
    viewModel: ScheduleSheetViewModel = hiltViewModel()
) {
    LaunchedEffect(args) { viewModel.load(args) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    fun notificationsGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var postNotificationsGranted by remember { mutableStateOf(notificationsGranted()) }

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) {
            val isNewSchedule = !uiState.isEditMode
            viewModel.reset()
            onDismiss()
            if (isNewSchedule) onScheduleCreated()
        }
    }

    val writeContactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.confirmRename() }

    fun renameWithPermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.confirmRename() else writeContactsLauncher.launch(Manifest.permission.WRITE_CONTACTS)
    }

    /**
     * SCHEDULE_EXACT_ALARM isn't a runtime dialog permission - like notification-listener access,
     * it's granted from a Settings screen. Fired once, the moment "Mark important" is switched on,
     * since that's the only feature that needs it.
     */
    fun requestExactAlarmAccessIfNeeded() {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() == false) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            )
        }
    }

    // POST_NOTIFICATIONS (Android 13+) IS a normal runtime dialog - unlike the two above. Without
    // this, the reminder alarm still fires exactly on time, but posting the notification silently
    // fails - the alarm side working correctly makes this easy to miss without checking App Logs.
    val postNotificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> postNotificationsGranted = granted }

    fun requestNotificationPermissionIfNeeded() {
        if (!notificationsGranted()) postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    ModalBottomSheet(
        onDismissRequest = { viewModel.reset(); onDismiss() },
        sheetState = sheetState
    ) {
        Column(
            Modifier.fillMaxWidth()
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.xs, bottom = Spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            val contact = uiState.contact
            when {
                uiState.loading -> Text("Loading...")
                uiState.notFound || contact == null -> Text("Contact not found.")
                uiState.nameCollision != null -> NameCollisionPrompt(
                    collidingContactName = contact.displayName,
                    collision = uiState.nameCollision!!,
                    onRenameInputChange = viewModel::onRenameInputChange,
                    onConfirmRename = ::renameWithPermission,
                    onCancel = viewModel::dismissNameCollision
                )
                else -> {
                    Text(if (uiState.isEditMode) "Edit schedule" else "New schedule", style = MaterialTheme.typography.headlineSmall)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = uiState.isImportant,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        requestExactAlarmAccessIfNeeded()
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    viewModel.onImportantChange(checked)
                                },
                                modifier = Modifier.scale(0.75f)
                            )
                            Text("Important", style = MaterialTheme.typography.titleSmall)
                            val importantTooltipState = rememberTooltipState(isPersistent = true)
                            val tooltipScope = rememberCoroutineScope()
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                tooltip = {
                                    PlainTooltip {
                                        Text("Must not be missed - a reminder fires 30 minutes before the call time above.")
                                    }
                                },
                                state = importantTooltipState
                            ) {
                                IconButton(
                                    onClick = { tooltipScope.launch { importantTooltipState.show() } },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = "What does Important mean?",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.isImportant && !postNotificationsGranted) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(
                                Modifier.padding(Spacing.md).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                            ) {
                                Text(
                                    "Notifications aren't allowed for this app - the reminder alarm will still fire " +
                                        "on time, but it won't be able to show anything.",
                                    Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = ::requestNotificationPermissionIfNeeded) { Text("Grant") }
                            }
                        }
                    }

                    if (!contact.hasWhatsApp) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Text(
                                "No WhatsApp found for this contact - a schedule can still be created, but it " +
                                    "can't be auto-detected without a WhatsApp call notification to match against.",
                                Modifier.padding(Spacing.md),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    DropdownSelector(
                        label = "* Recurrence",
                        selected = uiState.recurrence,
                        options = Recurrence.entries,
                        optionLabel = { it.label() },
                        onSelect = viewModel::onRecurrenceChange
                    )

                    DropdownSelector(
                        label = "* Priority",
                        selected = uiState.importance,
                        options = Importance.entries,
                        optionLabel = { it.label() },
                        onSelect = viewModel::onImportanceChange
                    )

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        Box(Modifier.weight(1f)) {
                            DueDatePicker(nextDueDate = uiState.nextDueDate, onPick = viewModel::onDueDateChange)
                        }
                        Box(Modifier.weight(1f)) {
                            DueTimePicker(
                                preferredTimeMinutes = uiState.preferredTimeMinutes,
                                onPick = viewModel::onPreferredTimeChange,
                                isError = uiState.importantWithoutTimeError
                            )
                        }
                    }

                    Column {
                        Text("Notes", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = uiState.note,
                            onValueChange = viewModel::onNoteChange,
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                            singleLine = true
                        )
                    }

                    Button(
                        onClick = viewModel::save,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.md)
                    ) {
                        Text(if (uiState.isEditMode) "Save changes" else "Create schedule")
                    }
                }
            }
        }
    }
}

/**
 * Blocks schedule creation when another active schedule already has this exact name: a call
 * notification only ever carries a name, so two active schedules sharing one would make future
 * calls for either person impossible to tell apart. Renaming here writes straight to the address
 * book (WRITE_CONTACTS), since WhatsApp's own call notification name follows the saved contact
 * name - see ScheduleSheetViewModel.
 */
@Composable
private fun NameCollisionPrompt(
    collidingContactName: String,
    collision: NameCollisionInfo,
    onRenameInputChange: (String) -> Unit,
    onConfirmRename: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Name already in use", style = MaterialTheme.typography.headlineSmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
                "\"$collidingContactName\" matches another active schedule (\"${collision.collidingWithScheduleName}\"). " +
                    "A WhatsApp call notification only ever carries a name, so two schedules with the same name " +
                    "can't be told apart. Rename this contact to something unique to continue - this also renames " +
                    "them in your address book.",
                Modifier.padding(Spacing.md),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }

        OutlinedTextField(
            value = collision.renameInput,
            onValueChange = onRenameInputChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("New unique name") },
            singleLine = true,
            isError = collision.error != null,
            supportingText = collision.error?.let { { Text(it) } }
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = onConfirmRename, modifier = Modifier.weight(1f), enabled = !collision.renaming) {
                if (collision.renaming) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save unique name")
                }
            }
        }
    }
}

/** A read-only text field + dropdown menu, shared by the Recurrence and Priority pickers. */
@Composable
private fun <T> DropdownSelector(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

/** Minutes since midnight <-> a 12-hour "h:mm a" label, and a dialog wrapping Material3's TimePicker (M3 ships no TimePickerDialog of its own, unlike DatePickerDialog). */
@Composable
private fun DueTimePicker(preferredTimeMinutes: Int?, onPick: (Int?) -> Unit, isError: Boolean) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text("Call time", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = Spacing.xs)) {
                Text(preferredTimeMinutes?.let { DateMath.formatTimeOfDay(it) } ?: "Not set")
            }
            if (preferredTimeMinutes != null) {
                IconButton(onClick = { onPick(null) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear call time")
                }
            }
        }
        if (isError) {
            Text(
                "Set a call time so the important-call reminder knows when to fire",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showPicker) {
        val initialMinutes = preferredTimeMinutes ?: (9 * 60)
        val state = rememberTimePickerState(
            initialHour = initialMinutes / 60,
            initialMinute = initialMinutes % 60,
            is24Hour = false
        )
        Dialog(onDismissRequest = { showPicker = false }) {
            Card {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    TimePicker(state = state)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        OutlinedButton(onClick = { showPicker = false }) { Text("Cancel") }
                        Button(onClick = {
                            onPick(state.hour * 60 + state.minute)
                            showPicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DueDatePicker(nextDueDate: Long, onPick: (Long) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Text("Next due", style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.padding(top = Spacing.xs)) {
            Text(DateMath.formatDate(nextDueDate))
        }
    }

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = nextDueDate)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                Button(onClick = {
                    state.selectedDateMillis?.let(onPick)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { OutlinedButton(onClick = { showPicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = state)
        }
    }
}
