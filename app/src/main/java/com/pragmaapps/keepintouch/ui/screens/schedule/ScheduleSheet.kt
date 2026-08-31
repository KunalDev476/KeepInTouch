@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.schedule

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.SegmentedControl
import com.pragmaapps.keepintouch.ui.components.label
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Latest minute-of-day a time picker can express (23:59). */
private const val MAX_MINUTES_OF_DAY = 23 * 60 + 59

/**
 * Tighter than ButtonDefaults.ContentPadding's default 24.dp horizontal - Next due and Call time
 * sit side by side and Call time's label can grow into a full "6:00 AM - 8:00 PM" range, so both
 * give up some of their own internal padding to leave more room for that growth.
 */
private val COMPACT_BUTTON_PADDING = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm)

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
     * it's granted from a Settings screen. Fired once, the moment a call time is set - a schedule
     * becomes "Important" automatically as soon as it has one (see ScheduleSheetUiState.isImportant),
     * and that's the only feature that needs this permission.
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
                        if (uiState.isImportant) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (uiState.isAvailabilityWindow) "Availability window" else "Important",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val importantTooltipState = rememberTooltipState(isPersistent = true)
                                val tooltipScope = rememberCoroutineScope()
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                                    tooltip = {
                                        PlainTooltip {
                                            Text(
                                                if (uiState.isAvailabilityWindow) {
                                                    "A reminder fires 10 minutes before this window starts, suggesting it's " +
                                                        "a good time to call. Switch back to a single time, or clear the call time, to undo this."
                                                } else {
                                                    "Marked important automatically because a call time is set - " +
                                                        "a reminder fires 10 minutes before it. Clear the call time to undo this."
                                                }
                                            )
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

                    // Next due only ever needs as much width as its date label - giving it that
                    // instead of an equal half leaves Call time (which can grow to a full
                    // "6:00 AM - 8:00 PM" range) the rest of the row to expand into on its own left
                    // edge, so growing never pushes its delete button past the screen.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        DueDatePicker(nextDueDate = uiState.nextDueDate, onPick = viewModel::onDueDateChange)
                        Box(Modifier.weight(1f)) {
                            CallTimePicker(
                                startMinutes = uiState.preferredTimeMinutes,
                                endMinutes = uiState.comfortableTimeEndMinutes,
                                onConfirm = { start, end ->
                                    if (start != null) {
                                        requestExactAlarmAccessIfNeeded()
                                        requestNotificationPermissionIfNeeded()
                                    }
                                    viewModel.onCallTimeChange(start, end)
                                }
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

/** Which of the two clocks in [CallTimePickerDialog] the shared TimePicker is currently editing. */
private enum class TimeField { START, END }

/**
 * Minutes since midnight <-> a 12-hour "h:mm a" label, and the entry point for
 * [CallTimePickerDialog]. Setting a call time here is also what makes a schedule "Important" (see
 * ScheduleSheetUiState.isImportant) - clearing it back to "Not set" reverts to Normal.
 *
 * The button's own label animates between "Not set" / "6:00 AM" / "6:00 AM - 8:00 PM": switching
 * modes inside the dialog shouldn't just snap the input to a new width once it closes.
 * animateContentSize() eases the button's own resize, while the AnimatedContent around the text
 * gives the new label a small scale-in "pop" rather than a hard cut - together they read as the
 * input gently inflating to fit a window instead of jumping.
 */
@Composable
private fun CallTimePicker(startMinutes: Int?, endMinutes: Int?, onConfirm: (start: Int?, end: Int?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val displayLabel = when {
        startMinutes == null -> "Not set"
        endMinutes != null -> "${DateMath.formatTimeOfDay(startMinutes)} - ${DateMath.formatTimeOfDay(endMinutes)}"
        else -> DateMath.formatTimeOfDay(startMinutes)
    }

    Column(Modifier.fillMaxWidth()) {
        Text("Call time", style = MaterialTheme.typography.titleSmall)
        // fillMaxWidth + weight(1f, fill = false) on the button reserves the delete icon's space
        // first and caps the button to whatever's left, so growing into "6:00 AM - 8:00 PM" can
        // only expand the button within that bound - never push the icon past the visible edge.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(
                onClick = { showPicker = true },
                contentPadding = COMPACT_BUTTON_PADDING,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(top = Spacing.xs)
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            ) {
                AnimatedContent(
                    targetState = displayLabel,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, animationSpec = tween(120)))
                    },
                    label = "call-time-label"
                ) { label -> Text(label, maxLines = 1) }
            }
            if (startMinutes != null) {
                IconButton(onClick = { onConfirm(null, null) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear call time")
                }
            }
        }
    }

    if (showPicker) {
        CallTimePickerDialog(
            initialStart = startMinutes,
            initialEnd = endMinutes,
            onDismiss = { showPicker = false },
            onConfirm = { start, end ->
                onConfirm(start, end)
                showPicker = false
            }
        )
    }
}

/**
 * One popup covering both modes: the clock sits at top (same spot regardless of mode), a Switch
 * below it turns "Availability window" on/off, and - since M3 ships only a single-time TimePicker
 * widget - a From/Until SegmentedControl beneath the switch lets that same clock be reused for both
 * ends of the window rather than showing two clocks at once. Off stays exactly the plain
 * single-time picker this replaced.
 */
@Composable
private fun CallTimePickerDialog(
    initialStart: Int?,
    initialEnd: Int?,
    onDismiss: () -> Unit,
    onConfirm: (start: Int, end: Int?) -> Unit
) {
    var isWindow by remember { mutableStateOf(initialStart != null && initialEnd != null) }
    var activeField by remember { mutableStateOf(TimeField.START) }
    var error by remember { mutableStateOf<String?>(null) }

    val startDefault = initialStart ?: (9 * 60)
    val endDefault = initialEnd ?: (startDefault + 60).coerceAtMost(MAX_MINUTES_OF_DAY)
    val startState = rememberTimePickerState(initialHour = startDefault / 60, initialMinute = startDefault % 60, is24Hour = false)
    val endState = rememberTimePickerState(initialHour = endDefault / 60, initialMinute = endDefault % 60, is24Hour = false)

    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                // Clock stays first/where it's always been; the window toggle and its From/Until
                // switcher sit below it instead of above, still ahead of Cancel/OK.
                val shownField = if (isWindow) activeField else TimeField.START
                AnimatedContent(
                    targetState = shownField,
                    transitionSpec = {
                        (fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "time-field"
                ) { field ->
                    TimePicker(state = if (field == TimeField.START) startState else endState)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Column {
                        Text("Availability window", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Set a start and end time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Switch(
                        checked = isWindow,
                        onCheckedChange = { checked ->
                            isWindow = checked
                            activeField = TimeField.START
                            error = null
                        }
                    )
                }

                if (isWindow) {
                    SegmentedControl(
                        options = listOf(
                            "From · ${DateMath.formatTimeOfDay(startState.hour * 60 + startState.minute)}",
                            "Until · ${DateMath.formatTimeOfDay(endState.hour * 60 + endState.minute)}"
                        ),
                        selectedIndex = if (activeField == TimeField.START) 0 else 1,
                        onSelect = { index -> activeField = if (index == 0) TimeField.START else TimeField.END }
                    )
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            val start = startState.hour * 60 + startState.minute
                            if (!isWindow) {
                                onConfirm(start, null)
                                return@Button
                            }
                            val end = endState.hour * 60 + endState.minute
                            if (end <= start) {
                                error = "End time must be after the start time"
                                return@Button
                            }
                            onConfirm(start, end)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("OK") }
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
        OutlinedButton(
            onClick = { showPicker = true },
            contentPadding = COMPACT_BUTTON_PADDING,
            modifier = Modifier.padding(top = Spacing.xs)
        ) {
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
