package com.pragmaapps.keepintouch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

private val ThumbWidth = 4.dp
private val ThumbWidthActive = 6.dp
private val EdgeInset = 4.dp
private val MinThumbHeight = 36.dp

/**
 * Always-visible scrollbar for a LazyColumn, so it's obvious there's more list below without
 * having to try scrolling first. Material 3 has no built-in one; this draws a slim brand-colored
 * thumb on a faint track along the end edge, which widens and darkens while scrolling, then
 * settles back. Hidden entirely when the whole list fits on screen.
 *
 * Item heights aren't known until they're laid out, so the thumb's size and position are
 * estimated from the average height of the visible items - accurate for lists of similar rows.
 * Indicator only: it doesn't take touches, so it never steals taps from row trailing buttons.
 */
@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier {
    val thumbColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val activity by animateFloatAsState(
        targetValue = if (state.isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = if (state.isScrollInProgress) 150 else 600),
        label = "scrollbarActivity"
    )

    return drawWithContent {
        drawContent()

        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty() || !(state.canScrollForward || state.canScrollBackward)) return@drawWithContent

        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        val averageItemHeight = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
        val contentHeight = averageItemHeight * layoutInfo.totalItemsCount +
            layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
        val scrolled = averageItemHeight * state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset
        val maxScroll = (contentHeight - viewportHeight).coerceAtLeast(1f)

        val inset = EdgeInset.toPx()
        val trackHeight = size.height - inset * 2
        val thumbHeight = (trackHeight * viewportHeight / contentHeight).coerceIn(MinThumbHeight.toPx(), trackHeight)
        // Snap to the very bottom once the list can't scroll further, since the estimate may not land exactly there.
        val progress = if (!state.canScrollForward) 1f else (scrolled / maxScroll).coerceIn(0f, 1f)
        val thumbTop = inset + (trackHeight - thumbHeight) * progress

        val width = ThumbWidth.toPx() + (ThumbWidthActive.toPx() - ThumbWidth.toPx()) * activity
        val left = size.width - inset - width
        val radius = CornerRadius(width / 2, width / 2)

        drawRoundRect(trackColor, Offset(left, inset), Size(width, trackHeight), radius)
        drawRoundRect(
            thumbColor.copy(alpha = 0.7f + 0.3f * activity),
            Offset(left, thumbTop),
            Size(width, thumbHeight),
            radius
        )
    }
}
