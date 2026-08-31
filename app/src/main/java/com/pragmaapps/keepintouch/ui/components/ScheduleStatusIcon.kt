package com.pragmaapps.keepintouch.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTimeFilled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import com.pragmaapps.keepintouch.domain.model.ScheduleStatus
import com.pragmaapps.keepintouch.ui.theme.extendedColors

/**
 * Status of THIS particular scheduled occurrence (today's, or whichever is next/overdue) - not an
 * aggregate "health of the schedule over time". See RecurrenceEngine.status().
 *
 * [lateFraction] (0..1, only meaningful when [status] is MISSED - see PriorityEngine.lateRatio,
 * the source of truth for "how overdue") ramps the MISSED color from PENDING's amber toward full
 * alarm red as a schedule stays missed longer, so lateness reads visually instead of a flat color
 * regardless of whether it was missed yesterday or a month ago - the only urgency signal now that
 * priority is fully automatic (there's no separate manual importance to blend in on top).
 */
@Composable
fun ScheduleStatusIcon(
    status: ScheduleStatus,
    lateFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val (icon, tint, description) = when (status) {
        ScheduleStatus.DONE -> Triple(Icons.Filled.CheckCircle, MaterialTheme.extendedColors.successGreen, "Attended")
        ScheduleStatus.PENDING -> Triple(Icons.Filled.AccessTimeFilled, MaterialTheme.extendedColors.streakAmber, "Pending")
        ScheduleStatus.MISSED -> Triple(
            Icons.Filled.Error,
            lerp(MaterialTheme.extendedColors.streakAmber, MaterialTheme.colorScheme.error, lateFraction.coerceIn(0f, 1f)),
            "Missed"
        )
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = modifier)
}
