@file:OptIn(ExperimentalFoundationApi::class)

package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 6

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    val postNotificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }
    val phoneStateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    val currentPageGranted = when (pagerState.currentPage) {
        0 -> true
        1 -> uiState.contactsGranted
        2 -> uiState.notificationListenerGranted
        3 -> uiState.phoneStateGranted
        4 -> uiState.postNotificationsGranted
        else -> uiState.exactAlarmGranted
    }

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        // Scroll is button-driven only (userScrollEnabled = false) - a free swipe would let
        // someone skip past an ungranted permission page the same way the old Skip button did.
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> ValuePropPage()
                1 -> PermissionPage(
                    icon = Icons.Filled.Contacts,
                    title = "Find who has WhatsApp",
                    why = "We read your contacts, read-only, to see who's saved and who has WhatsApp linked. " +
                        "Contact names and numbers are only ever used inside this app, to match them to your " +
                        "schedules - nothing is sent off your device.",
                    granted = uiState.contactsGranted,
                    ctaLabel = "Grant Contacts access",
                    onCta = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }
                )
                2 -> PermissionPage(
                    icon = Icons.Filled.Notifications,
                    title = "Detect WhatsApp and phone calls",
                    why = "Android requires notification access to let an app see call notifications - granted " +
                        "from Settings, not a normal prompt. We only ever look at call notifications - from " +
                        "WhatsApp, your phone's dialer, or another calling app; every other notification is " +
                        "ignored immediately, unread. From a call notification we only read the caller's name (or " +
                        "number) and whether the call is ringing, ongoing, or ended - never message text or chat " +
                        "content. That's used only to mark a call as complete on a schedule you created; nothing " +
                        "is sent off your device, and nothing is shared with us, WhatsApp, or anyone else. You can " +
                        "revoke this any time from Settings > Apps > Special app access > Notification access.",
                    granted = uiState.notificationListenerGranted,
                    ctaLabel = "Open Settings",
                    onCta = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                )
                3 -> PermissionPage(
                    icon = Icons.Filled.Timer,
                    title = "Measure phone call duration",
                    why = "Some phones show no call notification while the full-screen call screen is open, so we " +
                        "also check whether a phone call is ringing, in progress, or has ended - only to measure " +
                        "how long it lasted. This never gives us the phone number, your call history, or anything " +
                        "else, and nothing leaves your device.",
                    granted = uiState.phoneStateGranted,
                    ctaLabel = "Allow phone call status",
                    onCta = { phoneStateLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
                )
                4 -> PermissionPage(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Show you reminders",
                    why = "Needed to actually display reminder notifications - the important-call warning and the " +
                        "daily pending-calls summary. These are notifications this app creates and shows only to " +
                        "you; nothing is read from any other app here.",
                    granted = uiState.postNotificationsGranted,
                    ctaLabel = "Allow notifications",
                    onCta = { postNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
                else -> PermissionPage(
                    icon = Icons.Filled.Alarm,
                    title = "Fire reminders exactly on time",
                    why = "Lets an important call's 10-minutes-before warning fire at the exact moment, even in " +
                        "Doze - granted from Settings, not a normal prompt. Only used to schedule this app's own " +
                        "reminders; it doesn't give access to anything else on your device.",
                    granted = uiState.exactAlarmGranted,
                    ctaLabel = "Open Settings",
                    onCta = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        )
                    }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PAGE_COUNT) { index ->
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                        .background(
                            if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1, animationSpec = tween(300)) } },
                enabled = pagerState.currentPage > 0
            ) { Text("Back") }
            Button(
                enabled = currentPageGranted,
                onClick = {
                    if (pagerState.currentPage < PAGE_COUNT - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(300)) }
                    } else {
                        viewModel.complete()
                        onDone()
                    }
                }
            ) {
                Text(if (pagerState.currentPage < PAGE_COUNT - 1) "Next" else "Get started")
            }
        }
    }
}

@Composable
private fun ValuePropPage() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("In Touch", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Schedule the people you want to stay close to. We'll quietly check them off when a real WhatsApp call happens - no logging it yourself.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.md)
        )
    }
}

@Composable
private fun PermissionPage(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    ctaLabel: String,
    onCta: () -> Unit
) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.lg), textAlign = TextAlign.Center)
        Text(
            why,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
        )
        if (!granted) {
            Button(onClick = onCta) { Text(ctaLabel) }
        } else {
            Text("Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}
