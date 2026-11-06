@file:OptIn(ExperimentalFoundationApi::class)

package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors
import kotlinx.coroutines.launch

/**
 * Shown on a fresh start once onboarding is finished, if anything's missing. Page 0 is the intro
 * and each page after it is one missing permission. That list is fixed when the screen opens, so
 * granting something doesn't pull its page out from under the user. Unlike onboarding, Next works
 * without granting: the user can always leave, and nothing here is mandatory.
 */
@Composable
fun MissingPermissionsScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val missingNames = rememberSaveable { ArrayList(AppPermissions.missing(context).map { it.name }) }
    val missing = remember(missingNames) { missingNames.map(AppPermission::valueOf) }
    var granted by remember { mutableStateOf(AppPermissions.granted(context)) }
    // Permissions whose prompt Android will no longer show, so their button opens Settings instead.
    var blockedNames by rememberSaveable { mutableStateOf(listOf<String>()) }

    val pageCount = missing.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val showPage: (Int) -> Unit = { page ->
        scope.launch { pagerState.animateScrollToPage(page, animationSpec = tween(300)) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = AppPermissions.granted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        granted = AppPermissions.granted(context)
        val permission = missing.getOrNull(pagerState.currentPage - 1)
        val runtimePermission = permission?.runtimePermission
        val activity = context as? Activity
        if (!isGranted && runtimePermission != null && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, runtimePermission)
        ) {
            blockedNames = blockedNames + permission.name
        }
    }

    BackHandler {
        if (pagerState.currentPage > 0) showPage(pagerState.currentPage - 1) else onDone()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Spacing.xl, vertical = Spacing.lg)) {
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                MissingPermissionsIntro(missing)
            } else {
                val permission = missing[page - 1]
                val blocked = permission.name in blockedNames
                MissingPermissionPage(
                    content = permission.content(),
                    granted = permission in granted,
                    ctaLabel = if (blocked) "Open Settings" else permission.content().ctaLabel,
                    onCta = {
                        val runtimePermission = permission.runtimePermission
                        if (runtimePermission != null && !blocked) {
                            permissionLauncher.launch(runtimePermission)
                        } else {
                            context.startActivity(permission.settingsIntent(context))
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        if (pagerState.currentPage == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(onClick = { showPage(1) }, modifier = Modifier.fillMaxWidth()) { Text("Grant Permissions") }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Grant Later") }
            }
        } else {
            val onLastPage = pagerState.currentPage == pageCount - 1
            val goForward = { if (onLastPage) onDone() else showPage(pagerState.currentPage + 1) }
            val forwardLabel = if (onLastPage) "Done" else "Next"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showPage(pagerState.currentPage - 1) }) { Text("Back") }
                PagerDots(count = missing.size, current = pagerState.currentPage - 1, modifier = Modifier.weight(1f))
                // The grant button is the main action until the permission is granted.
                if (missing[pagerState.currentPage - 1] in granted) {
                    Button(onClick = goForward) { Text(forwardLabel) }
                } else {
                    OutlinedButton(onClick = goForward) { Text(forwardLabel) }
                }
            }
        }
    }
}

@Composable
private fun MissingPermissionsIntro(missing: List<AppPermission>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(Spacing.xxxl))
        Text("Some permissions are missing", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The following permissions are needed for the app to work. Without these permissions, " +
                "the app might not behave properly.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm)
        )
        Card(Modifier.fillMaxWidth().padding(top = Spacing.xxl)) {
            missing.forEachIndexed { index, permission ->
                if (index > 0) HorizontalDivider()
                val content = permission.content()
                Row(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    IconBadge(content.icon, size = 44.dp, iconSize = 24.dp)
                    Column(Modifier.weight(1f)) {
                        Text(content.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            content.purpose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** The explanation scrolls, while what's at stake and the grant button stay put at the bottom. */
@Composable
private fun MissingPermissionPage(content: PermissionContent, granted: Boolean, ctaLabel: String, onCta: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(Spacing.xxxl))
            if (granted) {
                IconBadge(
                    icon = Icons.Filled.CheckCircle,
                    size = 64.dp,
                    iconSize = 32.dp,
                    background = MaterialTheme.extendedColors.successGreen.copy(alpha = 0.15f),
                    tint = MaterialTheme.extendedColors.successGreen
                )
            } else {
                IconBadge(content.icon, size = 64.dp, iconSize = 32.dp)
            }
            Text(content.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = Spacing.lg))
            Text(
                content.why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.lg)
            )
        }

        if (granted) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.extendedColors.successGreen)
                Text("Granted", color = MaterialTheme.extendedColors.successGreen, style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        "Required for the app to work properly",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        content.withoutIt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Button(onClick = onCta, modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) { Text(ctaLabel) }
        }
    }
}

@Composable
private fun IconBadge(
    icon: ImageVector,
    size: Dp,
    iconSize: Dp,
    background: Color = MaterialTheme.colorScheme.secondaryContainer,
    tint: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
