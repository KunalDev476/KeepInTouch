package com.pragmaapps.keepintouch.ui.screens.voice

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.util.DateMath
import com.pragmaapps.keepintouch.domain.util.PhoneMasking
import com.pragmaapps.keepintouch.domain.voice.ScheduleSummary
import com.pragmaapps.keepintouch.domain.voice.shortLabel
import com.pragmaapps.keepintouch.ui.components.AvatarWithWhatsAppBadge
import com.pragmaapps.keepintouch.ui.components.label
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors

/**
 * The big mic. While listening, a ring around it grows with how loud you're speaking; while the
 * assistant talks or thinks, it breathes slowly instead, so it's always clear whose turn it is.
 */
@Composable
fun MicOrb(phase: VoicePhase, level: Float, orbSize: Dp, onClick: () -> Unit) {
    val listening = phase == VoicePhase.LISTENING || phase == VoicePhase.WAITING_FOR_WAKE_WORD
    val animatedLevel by animateFloatAsState(if (listening) level else 0f, tween(120), label = "micLevel")
    val pulse by rememberInfiniteTransition(label = "micPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "micPulseValue"
    )
    val ringScale = when (phase) {
        VoicePhase.LISTENING, VoicePhase.WAITING_FOR_WAKE_WORD -> 1.08f + 0.32f * animatedLevel
        VoicePhase.SPEAKING, VoicePhase.THINKING -> 1.06f + 0.1f * pulse
        VoicePhase.IDLE -> 1f
    }
    val ringColor = MaterialTheme.colorScheme.primary
    val active = phase != VoicePhase.IDLE

    Box(Modifier.size(orbSize * 1.6f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(orbSize * 1.6f)) {
            val radius = orbSize.toPx() / 2f
            if (active) {
                drawCircle(ringColor.copy(alpha = 0.12f), radius = radius * ringScale * 1.18f)
                drawCircle(ringColor.copy(alpha = 0.22f), radius = radius * ringScale)
                drawCircle(ringColor.copy(alpha = 0.6f), radius = radius * ringScale, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Surface(
            shape = CircleShape,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(orbSize).clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (phase == VoicePhase.SPEAKING) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                    contentDescription = if (active) "Stop listening" else "Start listening",
                    tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(orbSize * 0.42f)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(text: String, fromUser: Boolean, label: String, pending: Boolean = false) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromUser) Alignment.End else Alignment.Start
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
        )
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (fromUser) 16.dp else 4.dp,
                bottomEnd = if (fromUser) 4.dp else 16.dp
            ),
            color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 300.dp).alpha(if (pending) 0.6f else 1f)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
            )
        }
    }
}

@Composable
private fun VoiceCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) { content() }
    }
}

@Composable
private fun ContactRow(contact: Contact, modifier: Modifier = Modifier, leading: String? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (leading != null) {
            Text(leading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AvatarWithWhatsAppBadge(displayName = contact.displayName, hasWhatsApp = contact.hasWhatsApp, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                contact.primaryNumber?.let { PhoneMasking.mask(it) } ?: "No phone number",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ContactCard(contact: Contact) {
    VoiceCard {
        Text("Is this the right contact?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ContactRow(contact)
    }
}

/** An exact name match the assistant picked by itself - [onWrongContact] null once it's settled. */
@Composable
fun SelectedContactCard(contact: Contact, onWrongContact: (() -> Unit)?) {
    VoiceCard {
        Text("Contact", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        ContactRow(contact)
        if (onWrongContact != null) {
            TextButton(onClick = onWrongContact, contentPadding = PaddingValues(0.dp)) { Text("Wrong contact?") }
        }
    }
}

@Composable
fun ContactChoicesCard(contacts: List<Contact>, enabled: Boolean, onPick: (Contact) -> Unit) {
    VoiceCard {
        Text(
            "Found ${contacts.size} matching contacts",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Column {
            contacts.forEachIndexed { index, contact ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ContactRow(
                    contact = contact,
                    leading = "${index + 1}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onPick(contact) }
                        .padding(vertical = Spacing.sm)
                )
            }
        }
        if (enabled) {
            Text(
                "Tap a contact, or say its number or full name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SummaryCard(summary: ScheduleSummary) {
    VoiceCard {
        Text("Schedule summary", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AvatarWithWhatsAppBadge(displayName = summary.contactName, hasWhatsApp = summary.hasWhatsApp, size = 40.dp)
            Text(summary.contactName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        SummaryLine("Repeats", summary.recurrence.label())
        when {
            summary.oneTimeDate != null -> SummaryLine("Date", DateMath.formatDate(summary.oneTimeDate))
            summary.weekdays.isNotEmpty() -> SummaryLine("Days", summary.weekdays.joinToString(", ") { it.shortLabel() })
        }
        SummaryLine(if (summary.endMinutes != null) "Available" else "Time", timeText(summary))
        if (summary.scheduleCount > 1) {
            Text(
                "This creates ${summary.scheduleCount} schedules, one for each day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SuccessCard(summary: ScheduleSummary) {
    VoiceCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.successGreen,
                modifier = Modifier.size(36.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    if (summary.scheduleCount > 1) "${summary.scheduleCount} schedules created" else "Schedule created",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(summary.contactName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    compactLine(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun timeText(summary: ScheduleSummary): String {
    val start = summary.startMinutes ?: return "No fixed time"
    val end = summary.endMinutes ?: return DateMath.formatTimeOfDay(start)
    return "${DateMath.formatTimeOfDay(start)} - ${DateMath.formatTimeOfDay(end)}"
}

private fun compactLine(summary: ScheduleSummary): String {
    val whenPart = when {
        summary.oneTimeDate != null -> DateMath.formatDate(summary.oneTimeDate)
        summary.weekdays.isNotEmpty() -> summary.weekdays.joinToString(", ") { it.shortLabel() }
        else -> null
    }
    val recurrence = if (summary.recurrence == Recurrence.ONE_TIME) null else summary.recurrence.label()
    return listOfNotNull(recurrence, whenPart, timeText(summary)).joinToString(" · ")
}
