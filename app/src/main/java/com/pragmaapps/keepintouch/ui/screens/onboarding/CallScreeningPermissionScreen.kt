package com.pragmaapps.keepintouch.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pragmaapps.keepintouch.data.logging.AppLogger
import com.pragmaapps.keepintouch.data.permissions.AppPermissions
import com.pragmaapps.keepintouch.data.permissions.OptionalPermission
import com.pragmaapps.keepintouch.ui.navigation.ForegroundResetGate
import com.pragmaapps.keepintouch.ui.theme.Spacing

private const val TAG = "CallScreeningScreen"

/**
 * The optional call-screening permission on a page of its own - same layout as a page of the
 * missing-permissions flow, but marked optional. Never on the fresh-start missing-permissions
 * list. Shown once at startup to existing users on phones outside DeviceVendor's verified list, and
 * from Settings or MissingPermissionsGate.showCallScreening(). New users see the same content as
 * the last onboarding page instead. Granting shows Android's own role dialog.
 *
 * [onDone] receives whether the role is held when the screen closes.
 */
@Composable
fun CallScreeningPermissionScreen(onDone: (granted: Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permission = OptionalPermission.CALL_SCREENING
    var granted by remember { mutableStateOf(AppPermissions.isGranted(context, permission)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = AppPermissions.isGranted(context, permission)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        granted = AppPermissions.isGranted(context, permission)
        AppLogger.i(TAG, "Call-screening role request finished: granted=$granted")
    }

    BackHandler { onDone(granted) }

    val content = permission.content()
    Column(Modifier.fillMaxSize().padding(Spacing.lg)) {
        Column(Modifier.weight(1f)) {
            MissingPermissionPage(
                content = content,
                granted = granted,
                ctaLabel = content.ctaLabel,
                optional = true,
                onCta = {
                    ForegroundResetGate.suppressNext()
                    roleLauncher.launch(AppPermissions.callScreeningRequestIntent(context))
                }
            )
        }
        Text(
            CALL_SCREENING_SETTINGS_NOTE,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onDone(granted) }) { Text(if (granted) "Close" else "Skip") }
            if (granted) Button(onClick = { onDone(granted) }) { Text("Done") }
        }
    }
}
