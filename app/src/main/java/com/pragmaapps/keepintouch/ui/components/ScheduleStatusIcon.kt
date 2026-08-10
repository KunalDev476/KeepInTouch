package com.pragmaapps.keepintouch.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pragmaapps.keepintouch.domain.model.Importance
import com.pragmaapps.keepintouch.domain.model.ScheduleStatus
import com.pragmaapps.keepintouch.ui.theme.extendedColors

/**
 * Status of THIS particular scheduled occurrence (today's, or whichever is next/overdue) - not an
 * aggregate "health of the schedule over time". See RecurrenceEngine.status(). Tint shifts toward
 * alarm red as priority rises (Low = base amber, Medium = part-way, High = closest to red) - same
 * base color per status, just shifted along one red axis, not a second color scale to learn.
 *
 * [lateFraction] (0..1, only meaningful when [status] is MISSED - see PriorityEngine.lateRatio,
 * the source of truth for "how overdue") ramps the MISSED color from PENDING's amber toward full
 * alarm red as a schedule stays missed longer, so lateness reads visually instead of a flat color
 * regardless of whether it was missed yesterday or a month ago. [importance] then pushes further
 * toward red on top of that (see importanceRedShift below) - both axes feed the same red ramp
 * since both mean "more urgent," just measured differently (how late vs. how important).
 */
@Composable
fun ScheduleStatusIcon(
    status: ScheduleStatus,
    importance: Importance,
    lateFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val (icon, baseColor, description) = when (status) {
        ScheduleStatus.DONE -> Triple(Icons.Filled.CheckCircle, MaterialTheme.extendedColors.successGreen, "Attended")
        ScheduleStatus.PENDING -> Triple(Icons.Filled.AccessTimeFilled, MaterialTheme.extendedColors.streakAmber, "Pending")
        ScheduleStatus.MISSED -> Triple(
            Icons.Filled.Error,
            lerp(MaterialTheme.extendedColors.streakAmber, MaterialTheme.colorScheme.error, lateFraction.coerceIn(0f, 1f)),
            "Missed"
        )
    }
    val tint = if (status == ScheduleStatus.DONE) {
        baseColor
    } else {
        lerp(baseColor, MaterialTheme.colorScheme.error, importanceRedShift(importance))
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = modifier)
}

/** How far a PENDING/MISSED icon shifts from its base amber toward full alarm red purely from
 *  [importance] - the same alarm color MISSED already ramps toward via [lateFraction], so a
 *  HIGH-importance item reads urgent even before it's actually late. */
private fun importanceRedShift(importance: Importance): Float = when (importance) {
    Importance.LOW -> 0f
    Importance.MEDIUM -> 0.3f
    Importance.HIGH -> 0.65f
}
