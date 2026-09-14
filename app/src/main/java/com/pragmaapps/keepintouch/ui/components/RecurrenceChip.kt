package com.pragmaapps.keepintouch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pragmaapps.keepintouch.domain.model.Recurrence
import kotlin.math.roundToInt

fun Recurrence.label(): String = when (this) {
    Recurrence.ONE_TIME -> "One time"
    Recurrence.DAILY -> "Daily"
    Recurrence.WEEKLY -> "Weekly"
    Recurrence.BIWEEKLY -> "Bi-weekly"
    Recurrence.MONTHLY -> "Monthly"
}

/** A quiet, purely informational badge - not an interactive control, so it never renders "disabled." */
@Composable
fun RecurrenceChip(recurrence: Recurrence, modifier: Modifier = Modifier) {
    val icon = when (recurrence) {
        Recurrence.ONE_TIME -> Icons.Filled.Event
        Recurrence.DAILY -> Icons.Filled.CalendarToday
        Recurrence.WEEKLY -> Icons.Filled.Repeat
        Recurrence.BIWEEKLY -> Icons.Filled.DateRange
        Recurrence.MONTHLY -> Icons.Filled.CalendarMonth
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                recurrence.label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Same quiet badge treatment as [RecurrenceChip], for a schedule's automatic priority - the live
 * delay percentage from PriorityEngine.score (1 full cadence overdue = 100%), not a manually-set
 * value. Uses the neutral secondaryContainer treatment when on time and the error container once
 * there's an actual delay to flag, so it reads as informational rather than alarming by default.
 */
@Composable
fun PriorityChip(delayPercent: Double, modifier: Modifier = Modifier) {
    val isDelayed = delayPercent > 0
    val containerColor = if (isDelayed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isDelayed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Flag,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Text(
                if (isDelayed) "${delayPercent.roundToInt()}% delayed" else "On time",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
        }
    }
}

/**
 * Same quiet badge treatment as [RecurrenceChip]/[PriorityChip], for how many qualifying calls -
 * scheduled and unscheduled combined, same two counts as Contact Detail's stat tiles - this
 * contact has had in [monthName]. That's the current month for an active schedule, but the most
 * recent month that actually had a call for a paused one, so the number doesn't just read "0 this
 * month" the instant it's paused - see PeopleViewModel.callStatFor. [monthName] is null only when
 * this contact has never had a single qualifying call ("No calls yet").
 */
@Composable
fun CallsThisMonthChip(count: Int, monthName: String?, modifier: Modifier = Modifier) {
    val label = when {
        monthName == null -> "No calls yet"
        count == 0 -> "No calls made in $monthName"
        count == 1 -> "1 call made in $monthName"
        else -> "$count calls made in $monthName"
    }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Call,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
