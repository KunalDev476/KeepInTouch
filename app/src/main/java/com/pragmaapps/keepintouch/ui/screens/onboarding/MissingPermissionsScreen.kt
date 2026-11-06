@file:OptIn(ExperimentalFoundationApi::class)

package com.pragmaapps.keepintouch.ui.screens.onboarding

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.data.permissions.AppPermission
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.ui.theme.Spacing
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

    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        HorizontalPager(state = pagerState, userScrollEnabled = false, modifier = Modifier.weight(1f)) { page ->
            if (page == 0) {
                MissingPermissionsIntro(missing)
            } else {
                val permission = missing[page - 1]
                val content = permission.content()
                val blocked = permission.name in blockedNames
                PermissionPage(
                    icon = content.icon,
                    title = content.title,
                    why = content.why,
                    consequence = content.withoutIt,
                    granted = permission in granted,
                    ctaLabel = if (blocked) "Open Settings" else content.ctaLabel,
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

        if (pagerState.currentPage == 0) {
            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Button(onClick = { showPage(1) }, modifier = Modifier.fillMaxWidth()) { Text("Grant Permissions") }
                OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Grant Later") }
            }
        } else {
            PagerDots(count = missing.size, current = pagerState.currentPage - 1)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showPage(pagerState.currentPage - 1) }) { Text("Back") }
                val onLastPage = pagerState.currentPage == pageCount - 1
                Button(onClick = { if (onLastPage) onDone() else showPage(pagerState.currentPage + 1) }) {
                    Text(if (onLastPage) "Done" else "Next")
                }
            }
        }
    }
}

@Composable
private fun MissingPermissionsIntro(missing: List<AppPermission>) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.padding(bottom = Spacing.lg).size(56.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            "The following permissions are missing and are needed for the app to work. " +
                "Without these permissions, the app might not behave properly.",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Column(Modifier.padding(top = Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            missing.forEach { permission ->
                Text(
                    permission.content().name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
