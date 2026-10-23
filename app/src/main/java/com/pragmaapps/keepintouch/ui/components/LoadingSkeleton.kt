package com.pragmaapps.keepintouch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pragmaapps.keepintouch.ui.theme.Spacing

/**
 * The placeholder fill color, pulsing gently via a looping alpha animation to signal "still
 * working" without implying a determinate progress amount. Call once per skeleton and pass the
 * result to every [SkeletonBlock], so all blocks pulse in sync.
 */
@Composable
fun rememberSkeletonColor(): Color {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    return MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.16f)
}

/** One placeholder shape - size it with [modifier]. */
@Composable
fun SkeletonBlock(color: Color, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(4.dp)) {
    Box(modifier.background(color, shape))
}

/**
 * A placeholder bar standing in for one line of text: takes the text's full [lineHeight] so the
 * layout around it doesn't shift when real text replaces it, with a thinner bar centered inside.
 */
@Composable
fun SkeletonTextLine(color: Color, width: Dp, lineHeight: Dp, barHeight: Dp, modifier: Modifier = Modifier) {
    Box(modifier.height(lineHeight), contentAlignment = Alignment.CenterStart) {
        SkeletonBlock(color, Modifier.width(width).height(barHeight))
    }
}

/**
 * A placeholder list, shaped like real rows, shown while a screen's first real data emission is
 * still in flight - so the first frame a user sees is never "empty" (which reads as "nothing
 * here") when it's actually just "not loaded yet".
 */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rowCount: Int = 4) {
    val color = rememberSkeletonColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        repeat(rowCount) {
            SkeletonRow(color)
        }
    }
}

@Composable
private fun SkeletonRow(color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        SkeletonBlock(color, Modifier.size(40.dp), CircleShape)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SkeletonBlock(color, Modifier.width(140.dp).height(14.dp))
            SkeletonBlock(color, Modifier.width(90.dp).height(12.dp))
        }
    }
}
