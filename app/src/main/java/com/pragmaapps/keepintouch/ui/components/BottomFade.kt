package com.pragmaapps.keepintouch.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Fades whatever scrolls behind this element into [color], starting [fadeHeight] above it. Drawn
 * rather than laid out, so it doesn't change the element's size or take touches from the list.
 */
fun Modifier.bottomFade(color: Color, fadeHeight: Dp): Modifier = drawBehind {
    val fade = fadeHeight.toPx()
    drawRect(
        brush = Brush.verticalGradient(
            0f to color.copy(alpha = 0f),
            0.15f to color.copy(alpha = 0.4f),
            0.35f to color.copy(alpha = 0.82f),
            0.5f to color.copy(alpha = 0.95f),
            0.65f to color,
            startY = -fade,
            endY = size.height
        ),
        topLeft = Offset(0f, -fade),
        size = Size(size.width, size.height + fade)
    )
}
