@file:OptIn(ExperimentalFoundationApi::class)

package com.pragmaapps.keepintouch.ui.screens.onboarding

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
import androidx.compose.material.icons.filled.CheckCircle
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
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The value-prop page, then one page per permission. */
private val PAGE_COUNT = AppPermission.entries.size + 1

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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    val currentPageGranted = pagerState.currentPage == 0 ||
        AppPermission.entries[pagerState.currentPage - 1] in uiState.granted

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        // Scroll is button-driven only (userScrollEnabled = false) - a free swipe would let
        // someone skip past an ungranted permission page the same way the old Skip button did.
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                ValuePropPage()
            } else {
                val permission = AppPermission.entries[page - 1]
                val content = permission.content()
                PermissionPage(
                    icon = content.icon,
                    title = content.title,
                    why = content.why,
                    granted = permission in uiState.granted,
                    ctaLabel = content.ctaLabel,
                    onCta = {
                        permission.runtimePermission?.let(permissionLauncher::launch)
                            ?: context.startActivity(permission.settingsIntent(context))
                    }
                )
            }
        }

        PagerDots(count = PAGE_COUNT, current = pagerState.currentPage)

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
internal fun PagerDots(count: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (current == index) 10.dp else 8.dp)
                    .background(
                        if (current == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        CircleShape
                    )
            )
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
internal fun PermissionPage(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    ctaLabel: String,
    onCta: () -> Unit,
    consequence: String? = null
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
        if (consequence != null && !granted) {
            Text(
                consequence,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = Spacing.lg)
            )
        }
        if (!granted) {
            Button(onClick = onCta) { Text(ctaLabel) }
        } else {
            Text("Granted", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
    }
}
