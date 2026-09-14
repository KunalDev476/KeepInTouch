package com.pragmaapps.keepintouch.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Generic two-or-more-way toggle, used for People's [Scheduled | All contacts] split. The
 * highlighted pill SLIDES between options (a single shared indicator, target position/width just
 * an even fraction of the control's own measured width via BoxWithConstraints - all options are
 * equal-weight) instead of each option's own background snapping on/off independently.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(999.dp))
            .padding(4.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.CenterStart) {
            val segmentWidth = maxWidth / options.size
            val animatedOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "segmentIndicatorX"
            )

            Box(
                Modifier
                    .offset(x = animatedOffset)
                    .width(segmentWidth)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(999.dp))
            )

            Row(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, label ->
                    val selected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "segmentTextColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(index) }
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
