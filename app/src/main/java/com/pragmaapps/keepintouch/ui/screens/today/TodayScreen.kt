@file:OptIn(ExperimentalMaterial3Api::class)

package com.pragmaapps.keepintouch.ui.screens.today

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import com.pragmaapps.keepintouch.domain.model.ScheduleStatus
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.ui.components.EmptyState
import com.pragmaapps.keepintouch.ui.components.SkeletonBlock
import com.pragmaapps.keepintouch.ui.components.SkeletonTextLine
import com.pragmaapps.keepintouch.ui.components.rememberSkeletonColor
import com.pragmaapps.keepintouch.ui.components.ProgressRing
import com.pragmaapps.keepintouch.ui.components.RecurrenceChip
import com.pragmaapps.keepintouch.ui.components.ScheduleStatusIcon
import com.pragmaapps.keepintouch.ui.theme.Spacing

@Composable
fun TodayScreen(onOpenConfirm: () -> Unit = {}, viewModel: TodayViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Today's Calls") }) }) { padding ->
        // Short fade from skeleton to content. To remove the fade, replace this Crossfade with a
        // plain `if (uiState.isLoading) TodaySkeleton(...) else TodayContent(...)`.
        Crossfade(targetState = uiState.isLoading, animationSpec = tween(150), label = "todayLoading") { loading ->
            if (loading) {
                TodaySkeleton(Modifier.fillMaxSize().padding(padding))
            } else {
                TodayContent(
                    uiState = uiState,
                    onOpenConfirm = onOpenConfirm,
                    onMarkDone = viewModel::onMarkDone,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

/** Static, so it's shown for real in [TodaySkeleton] too - nothing above the ring shifts when data arrives. */
@Composable
private fun TodayIntro() {
    Text(
        "See who you need to call today and keep track of completed calls.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = 0.dp, bottom = Spacing.sm)
    )
}

@Composable
private fun TodayContent(
    uiState: TodayUiState,
    onOpenConfirm: () -> Unit,
    onMarkDone: (TodayItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TodayIntro()

        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            ProgressRing(completed = uiState.doneCount, total = uiState.items.size)
            Column {
                Text(
                    if (uiState.toCallCount == 1) "1 call left today" else "${uiState.toCallCount} calls left today",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Complete the call and we'll mark it as done, or swipe right to mark it done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.pendingConfirmationCount > 0) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                    .clickable(onClick = onOpenConfirm),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        if (uiState.pendingConfirmationCount == 1) "1 call needs confirmation" else "${uiState.pendingConfirmationCount} calls need confirmation",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (uiState.items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "Nothing due today",
                subtitle = "Schedule someone from the People tab to see them here.",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                "Your most important calls appear first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                items(uiState.items, key = { it.schedule.id }) { item ->
                    Column(Modifier.animateItem()) {
                        TodaySwipeableRow(
                            item = item,
                            onMarkDone = { onMarkDone(item) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Mirrors [TodayContent]'s layout piece for piece - same intro, a ring the size of ProgressRing
 * with the "calls left" title and hint beside it, the "most important" line, then rows built on
 * the same ListItem as [TodayRow] - so content replaces it without the layout jumping.
 */
@Composable
private fun TodaySkeleton(modifier: Modifier = Modifier) {
    val color = rememberSkeletonColor()
    Column(modifier) {
        TodayIntro()

        Row(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Box(Modifier.size(72.dp).border(6.dp, color, CircleShape)) // ProgressRing's default size/stroke
            Column {
                SkeletonTextLine(color, width = 150.dp, lineHeight = 24.dp, barHeight = 16.dp) // titleMedium
                SkeletonTextLine(color, width = 220.dp, lineHeight = 16.dp, barHeight = 10.dp) // bodySmall, 2 lines
                SkeletonTextLine(color, width = 170.dp, lineHeight = 16.dp, barHeight = 10.dp)
            }
        }

        SkeletonTextLine(
            color,
            width = 200.dp,
            lineHeight = 16.dp,
            barHeight = 10.dp,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
        )

        // Varied name widths, so it reads as a list of different people rather than a pattern.
        for (nameWidth in listOf(140.dp, 110.dp, 160.dp, 120.dp, 130.dp)) {
            ListItem(
                leadingContent = {
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        SkeletonBlock(color, Modifier.size(28.dp), CircleShape) // status icon
                    }
                },
                headlineContent = { SkeletonTextLine(color, width = nameWidth, lineHeight = 24.dp, barHeight = 14.dp) },
                supportingContent = { SkeletonTextLine(color, width = 110.dp, lineHeight = 20.dp, barHeight = 12.dp) },
                trailingContent = { SkeletonBlock(color, Modifier.width(76.dp).height(26.dp), RoundedCornerShape(999.dp)) } // RecurrenceChip
            )
            HorizontalDivider()
        }
    }
}

private const val SWIPE_MAX_WIDTH_FRACTION = 0.4f
private const val SWIPE_CONFIRM_FRACTION = 0.2f

/**
 * Only swipe-right ("mark done") is enabled - there's no more "skip today." A missed call is
 * never dismissed from the list; PriorityEngine's lateness score handles that automatically by
 * climbing the longer a schedule goes uncompleted, so the list itself is the reminder.
 *
 * Hand-rolled drag instead of M3's SwipeToDismissBox: that component always anchors its "swiped"
 * state at the row's full measured width, so even with a low positionalThreshold the row still
 * had to travel nearly its entire width before the release-triggered snap revealed "Mark done"
 * and committed - reading as "swipe a lot" no matter how low the threshold went. This tracks the
 * drag directly, clamped so it can never be dragged past [SWIPE_MAX_WIDTH_FRACTION] of the row's
 * own width, with the "Mark done" reveal and the commit-on-release threshold both at
 * [SWIPE_CONFIRM_FRACTION] - real, visible fractions of the row instead of a threshold over an
 * anchor that stayed invisible until almost fully swiped. Releasing past the confirm fraction
 * marks done; releasing short of it - or dragging back below it before releasing - always springs
 * back with no action, since onDragStopped re-reads the live offset rather than latching a state.
 *
 * Already-done items skip the gesture entirely (no Box/draggable wrapper at all), rather than
 * attaching a gesture that would just no-op on release.
 */
@Composable
private fun TodaySwipeableRow(item: TodayItem, onMarkDone: () -> Unit, modifier: Modifier = Modifier) {
    if (item.done) {
        TodayRow(item)
        return
    }

    var rowWidthPx by remember { mutableFloatStateOf(0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val maxOffsetPx = rowWidthPx * SWIPE_MAX_WIDTH_FRACTION
    val confirmOffsetPx = rowWidthPx * SWIPE_CONFIRM_FRACTION

    val draggableState = rememberDraggableState { delta ->
        offsetX = (offsetX + delta).coerceIn(0f, maxOffsetPx)
    }

    Box(
        modifier
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (offsetX >= confirmOffsetPx) onMarkDone()
                    animate(initialValue = offsetX, targetValue = 0f, animationSpec = spring()) { value, _ ->
                        offsetX = value
                    }
                }
            ),
        propagateMinConstraints = true
    ) {
        Box(Modifier.matchParentSize()) {
            SwipeBackground(revealed = offsetX >= confirmOffsetPx)
        }
        Box(Modifier.offset { IntOffset(offsetX.roundToInt(), 0) }) {
            TodayRow(item)
        }
    }
}

@Composable
private fun SwipeBackground(revealed: Boolean) {
    if (revealed) {
        SwipeBackgroundContent(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Filled.Done,
            label = "Mark done",
            alignment = Alignment.CenterStart
        )
    } else {
        Box(Modifier.fillMaxSize())
    }
}

@Composable
private fun SwipeBackgroundContent(
    color: Color,
    contentColor: Color,
    icon: ImageVector,
    label: String,
    alignment: Alignment
) {
    Box(
        Modifier.fillMaxSize().background(color).padding(horizontal = Spacing.xl),
        contentAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@Composable
private fun TodayRow(item: TodayItem) {
    ListItem(
        leadingContent = {
            AnimatedStatusIcon(status = item.status, lateFraction = item.lateFraction)
        },
        headlineContent = {
            Text(
                item.schedule.displayNameSnapshot,
                textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                if (item.done) {
                    "Done" + (item.completion?.let { " at ${DateMath.formatTime(it.completedAt)}" } ?: "")
                } else {
                    "Due ${DateMath.formatDate(item.schedule.nextDueDate)}"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = { RecurrenceChip(item.schedule.recurrence) }
    )
}

/** Leading, in front of the contact name - the same priority-colored done/pending/missed icon
 *  used on Contact Detail, not a separate plain checked/unchecked mark. Keeps the same
 *  spring-driven overshoot on a status change that the old check-off icon had. */
@Composable
private fun AnimatedStatusIcon(status: ScheduleStatus, lateFraction: Float) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(status) {
        if (status == ScheduleStatus.DONE) {
            scale.snapTo(0.6f)
            scale.animateTo(1.2f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            scale.animateTo(1f, animationSpec = tween(150))
        } else {
            scale.snapTo(1f)
        }
    }
    Box(Modifier.size(40.dp).scale(scale.value), contentAlignment = Alignment.Center) {
        ScheduleStatusIcon(status = status, lateFraction = lateFraction, modifier = Modifier.size(28.dp))
    }
}
