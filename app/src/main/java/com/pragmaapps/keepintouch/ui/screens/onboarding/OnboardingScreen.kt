@file:OptIn(ExperimentalFoundationApi::class)

package com.pragmaapps.keepintouch.ui.screens.onboarding

import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.data.logging.Breadcrumbs
import com.pragmaapps.keepintouch.data.logging.Breadcrumb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.pragmaapps.keepintouch.data.permissions.OptionalPermission
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The value-prop page, then one page per required permission. */
private val REQUIRED_PAGE_COUNT = AppPermission.entries.size + 1

/** The optional call-screening page comes right after the last required one, when shown. */
private val CALL_SCREENING_PAGE = REQUIRED_PAGE_COUNT

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // Optional call-screening page last, only on phones outside DeviceVendor's verified list.
    val showCallScreeningPage = viewModel.showCallScreeningPage
    val pageCount = REQUIRED_PAGE_COUNT + if (showCallScreeningPage) 1 else 0
    val pagerState = rememberPagerState(pageCount = { pageCount })
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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        Breadcrumbs.log(
            Breadcrumb.PERMISSION_RESULT,
            "permission" to AppPermission.entries.getOrNull(pagerState.currentPage - 1),
            "granted" to isGranted
        )
        viewModel.refresh()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.refresh()
    }

    val onCallScreeningPage = showCallScreeningPage && pagerState.currentPage == CALL_SCREENING_PAGE
    val isLastPage = pagerState.currentPage == pageCount - 1
    // The optional page never blocks: Next/Get started works whether or not it's granted.
    val canContinue = pagerState.currentPage == 0 || onCallScreeningPage ||
        AppPermission.entries[pagerState.currentPage - 1] in uiState.granted

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        // Scroll is button-driven only (userScrollEnabled = false) - a free swipe would let
        // someone skip past an ungranted permission page the same way the old Skip button did.
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                ValuePropPage()
            } else if (showCallScreeningPage && page == CALL_SCREENING_PAGE) {
                val content = OptionalPermission.CALL_SCREENING.content()
                PermissionPage(
                    icon = content.icon,
                    title = content.title,
                    why = content.why,
                    granted = uiState.callScreeningGranted,
                    ctaLabel = content.ctaLabel,
                    optional = true,
                    footnote = CALL_SCREENING_SETTINGS_NOTE,
                    onCta = { roleLauncher.launch(AppPermissions.callScreeningRequestIntent(context)) }
                )
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

        PagerDots(count = pageCount, current = pagerState.currentPage)

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
                enabled = canContinue,
                onClick = {
                    if (!isLastPage) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1, animationSpec = tween(300)) }
                    } else {
                        viewModel.complete()
                        onDone()
                    }
                }
            ) {
                Text(
                    when {
                        !isLastPage -> "Next"
                        onCallScreeningPage && !uiState.callScreeningGranted -> "Skip & get started"
                        else -> "Get started"
                    }
                )
            }
        }
    }
}

@Composable
internal fun PagerDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.lg)
) {
    Row(
        modifier,
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
private fun PermissionPage(
    icon: ImageVector,
    title: String,
    why: String,
    granted: Boolean,
    ctaLabel: String,
    onCta: () -> Unit,
    optional: Boolean = false,
    footnote: String? = null
) {
    // Scrolls when the text is taller than the page (the call-screening page on small phones),
    // and stays vertically centred otherwise.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            androidx.compose.material3.Icon(
                imageVector = if (granted) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (optional) {
                Text(
                    "Optional",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .padding(top = Spacing.lg)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .padding(horizontal = Spacing.md, vertical = 4.dp)
                )
            }
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
            if (footnote != null) {
                Text(
                    footnote,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.lg)
                )
            }
        }
    }
}
