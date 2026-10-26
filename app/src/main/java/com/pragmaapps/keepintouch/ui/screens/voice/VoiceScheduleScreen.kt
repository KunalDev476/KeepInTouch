@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pragmaapps.keepintouch.ui.screens.voice

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.domain.voice.ChatItem
import com.pragmaapps.keepintouch.domain.voice.InputOptions
import com.pragmaapps.keepintouch.ui.navigation.ForegroundResetGate
import com.pragmaapps.keepintouch.ui.theme.Spacing

/**
 * Voice scheduling: a big mic that reacts to your voice, and - once the conversation starts - the
 * back-and-forth with the assistant as chat bubbles and cards underneath. All on-screen text is
 * English; the assistant itself speaks Hinglish.
 */
@Composable
fun VoiceScheduleScreen(
    onBack: () -> Unit,
    viewModel: VoiceScheduleViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) viewModel.onMicTapped() else viewModel.onMicPermissionDenied()
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        viewModel.onContactsPermissionResult(ok)
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // Same follow-up as the Create Schedule sheet: a timed reminder also needs exact alarms.
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java)
        if (alarmManager?.canScheduleExactAlarms() == false) {
            ForegroundResetGate.suppressNext()
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
        viewModel.onNotificationPermissionHandled()
    }

    fun onMic() {
        if (granted(Manifest.permission.RECORD_AUDIO)) {
            viewModel.onMicTapped()
        } else {
            ForegroundResetGate.suppressNext()
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Opening the screen is the user asking to talk - start listening straight away.
    LaunchedEffect(Unit) { onMic() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onScreenHidden()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenHidden()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create with voice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            val conversationStarted = state.items.isNotEmpty()
            val orbSize by animateDpAsState(if (conversationStarted) 96.dp else 168.dp, tween(400), label = "orbSize")
            // A fixed gap from the top, not vertical centring: the tab bar slides away as this screen
            // opens, and a centred mic would drift down with the growing space while it does.
            val topGap by animateDpAsState(if (conversationStarted) 0.dp else 96.dp, tween(400), label = "orbTopGap")

            Spacer(Modifier.height(topGap))
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MicOrb(phase = state.phase, level = state.level, orbSize = orbSize, onClick = ::onMic)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    statusText(state),
                    style = if (conversationStarted) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                AnimatedVisibility(!conversationStarted, enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        "For example: \"${state.wakeWord}, schedule a weekly call with Rahul on Saturday evening\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = Spacing.xxxl, vertical = Spacing.sm)
                    )
                }
            }

            if (conversationStarted) {
                ConversationList(
                    state = state,
                    onContactPicked = viewModel::onContactPicked,
                    onWrongContact = viewModel::onWrongContact,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            InputArea(
                state = state,
                onYesNo = viewModel::onYesNo,
                onQuickReply = viewModel::onQuickReply,
                onContactTyped = viewModel::onContactTyped,
                onGrantContacts = {
                    ForegroundResetGate.suppressNext()
                    contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                onGrantNotifications = {
                    ForegroundResetGate.suppressNext()
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onOpenAppSettings = {
                    ForegroundResetGate.suppressNext()
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    )
                }
            )
        }
    }
}

private fun statusText(state: VoiceUiState): String = when (state.phase) {
    VoicePhase.IDLE -> if (state.items.isEmpty()) "Tap the mic to start" else "Tap the mic to continue"
    VoicePhase.WAITING_FOR_WAKE_WORD -> "Say \"${state.wakeWord}\" to start"
    VoicePhase.LISTENING -> "Listening..."
    VoicePhase.THINKING -> "One moment..."
    VoicePhase.SPEAKING -> "${state.assistantName} is speaking"
}

@Composable
private fun ConversationList(
    state: VoiceUiState,
    onContactPicked: (com.pragmaapps.keepintouch.domain.model.Contact) -> Unit,
    onWrongContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val lastIndex = state.items.size + (if (state.partialTranscript != null) 1 else 0) - 1
    LaunchedEffect(state.items.size, state.partialTranscript != null) {
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }
    val activeSelectedId = state.items.lastOrNull { it is ChatItem.ContactSelected }?.id
        ?.takeIf { id -> state.items.none { (it is ChatItem.Success || it is ChatItem.ContactSelected || it is ChatItem.ContactConfirm) && it.id > id } }
    // The choices card stays tappable only while it's the question being asked.
    val activeChoicesId = state.items.lastOrNull { it is ChatItem.ContactChoices }?.id
        ?.takeIf { id -> state.items.none { it is ChatItem.UserText && it.id > id } }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(state.items, key = { it.id }) { item ->
            when (item) {
                is ChatItem.UserText -> ChatBubble(text = item.text, fromUser = true, label = "You")
                is ChatItem.AssistantText -> ChatBubble(text = item.prompt.englishText, fromUser = false, label = state.assistantName)
                is ChatItem.ContactConfirm -> ContactCard(contact = item.contact)
                is ChatItem.ContactSelected -> SelectedContactCard(
                    contact = item.contact,
                    // Only while this contact is still the one being scheduled.
                    onWrongContact = onWrongContact.takeIf { item.id == activeSelectedId }
                )
                is ChatItem.ContactChoices -> ContactChoicesCard(
                    contacts = item.contacts,
                    enabled = item.id == activeChoicesId,
                    onPick = onContactPicked
                )
                is ChatItem.Summary -> SummaryCard(summary = item.summary)
                is ChatItem.Success -> SuccessCard(summary = item.summary)
            }
        }
        state.partialTranscript?.let { partial ->
            item(key = "partial") { ChatBubble(text = partial, fromUser = true, label = "You", pending = true) }
        }
    }
}

@Composable
private fun InputArea(
    state: VoiceUiState,
    onYesNo: (Boolean) -> Unit,
    onQuickReply: (com.pragmaapps.keepintouch.domain.voice.QuickReply) -> Unit,
    onContactTyped: (String) -> Unit,
    onGrantContacts: () -> Unit,
    onGrantNotifications: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (state.micPermissionDenied) {
                Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) { Text("Allow microphone in Settings") }
            }
            when (val input = state.input) {
                InputOptions.None -> Unit
                InputOptions.YesNo -> YesNoRow(onYesNo)
                is InputOptions.Chips -> FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    input.options.forEach { option ->
                        SuggestionChip(onClick = { onQuickReply(option) }, label = { Text(option.label) })
                    }
                }
                InputOptions.ContactSearch -> ContactSearchField(onContactTyped)
                InputOptions.GrantContacts ->
                    Button(onClick = onGrantContacts, modifier = Modifier.fillMaxWidth()) { Text("Allow contacts access") }
                InputOptions.GrantNotifications -> {
                    Button(onClick = onGrantNotifications, modifier = Modifier.fillMaxWidth()) { Text("Allow reminders") }
                    YesNoRow(onYesNo)
                }
            }
        }
    }
}

@Composable
private fun YesNoRow(onYesNo: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        OutlinedButton(onClick = { onYesNo(false) }, modifier = Modifier.weight(1f)) { Text("No") }
        Button(onClick = { onYesNo(true) }, modifier = Modifier.weight(1f)) { Text("Yes") }
    }
}

@Composable
private fun ContactSearchField(onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (query.isNotBlank()) {
            onSearch(query)
            query = ""
        }
    }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("Type a contact's name") },
        trailingIcon = {
            IconButton(onClick = ::submit) { Icon(Icons.Filled.Search, contentDescription = "Search") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() })
    )
}
