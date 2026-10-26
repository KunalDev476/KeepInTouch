package com.pragmaapps.keepintouch.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pragmaapps.keepintouch.ui.theme.Spacing
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * One-time announcement of voice scheduling: a lightly see-through wash over the whole app with
 * an arrow to the mic button. Colours come from the mic button's own container colour, so the
 * overlay always matches it - pale in light theme with dark text, deep in dark theme with white
 * text. [micBounds] is the mic button's position in root coordinates; a highlighted copy of the
 * button sits there, and tapping it opens voice scheduling straight away.
 */
@Composable
fun VoiceIntroOverlay(
    micBounds: Rect,
    wakeWord: String,
    onDismiss: () -> Unit,
    onOpenVoice: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val scrim = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.94f else 0.92f)
    val textColor = if (dark) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
    val accent = if (dark) Color.White else MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    BackHandler(onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
            // Swallow taps so nothing underneath reacts while the overlay is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.statusBarsPadding().padding(Spacing.sm)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Close", tint = textColor)
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = Spacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "In Touch just got smarter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                "Create schedules just by talking. Tap the mic, say \"$wakeWord\", and tell it who to call and when.",
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.padding(top = Spacing.md)
            )
        }

        // Arrow from just under the text down to the mic, plus a ring around the mic itself.
        Canvas(Modifier.fillMaxSize()) {
            val target = Offset(micBounds.center.x, micBounds.top - 14.dp.toPx())
            val start = Offset(size.width * 0.5f, size.height * 0.5f + 90.dp.toPx())
            val control = Offset(target.x, start.y)
            val stroke = 3.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(start.x, start.y)
                    quadraticTo(control.x, control.y, target.x, target.y)
                },
                color = accent,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            // Arrowhead along the curve's final direction (control -> target).
            val angle = atan2(target.y - control.y, target.x - control.x)
            val head = 14.dp.toPx()
            for (side in listOf(-0.5f, 0.5f)) {
                val a = angle + Math.PI.toFloat() + side
                drawLine(
                    accent,
                    target,
                    Offset(target.x + head * cos(a), target.y + head * sin(a)),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
            drawCircle(
                accent,
                radius = micBounds.width * 0.75f,
                center = micBounds.center,
                style = Stroke(width = stroke)
            )
        }

        // The scrim washes out the real mic button underneath, so a highlighted copy sits exactly on top of it.
        with(density) {
            Box(
                Modifier
                    .offset { IntOffset(micBounds.left.roundToInt(), micBounds.top.roundToInt()) }
                    .size(micBounds.width.toDp(), micBounds.height.toDp())
                    .background(accent, RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenVoice),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Create a schedule by voice",
                    tint = if (dark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
