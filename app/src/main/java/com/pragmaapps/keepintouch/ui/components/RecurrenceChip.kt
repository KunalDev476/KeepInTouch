package com.pragmaapps.keepintouch.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
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
import com.pragmaapps.keepintouch.domain.model.Importance
import com.pragmaapps.keepintouch.domain.model.Recurrence

fun Recurrence.label(): String = when (this) {
    Recurrence.ONE_TIME -> "One time"
    Recurrence.DAILY -> "Daily"
    Recurrence.WEEKLY -> "Weekly"
}

fun Importance.label(): String = when (this) {
    Importance.LOW -> "Low"
    Importance.MEDIUM -> "Medium"
    Importance.HIGH -> "High"
}

/** A quiet, purely informational badge - not an interactive control, so it never renders "disabled." */
@Composable
fun RecurrenceChip(recurrence: Recurrence, modifier: Modifier = Modifier) {
    val icon = when (recurrence) {
        Recurrence.ONE_TIME -> Icons.Filled.Event
        Recurrence.DAILY -> Icons.Filled.CalendarToday
        Recurrence.WEEKLY -> Icons.Filled.Repeat
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

/** Same quiet badge treatment as [RecurrenceChip], for a schedule's Low/Medium/High priority. */
@Composable
fun ImportanceChip(importance: Importance, modifier: Modifier = Modifier) {
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
                Icons.Filled.Flag,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                importance.label(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
