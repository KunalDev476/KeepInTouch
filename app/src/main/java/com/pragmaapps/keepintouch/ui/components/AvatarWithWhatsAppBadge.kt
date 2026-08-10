package com.pragmaapps.keepintouch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A contact's initial(s) in a colored circle, with a small green chat badge when WhatsApp
 * presence was detected. No WhatsApp logo asset is used - this is a generic "messaging app
 * detected" indicator, not a brand mark.
 */
@Composable
fun AvatarWithWhatsAppBadge(
    displayName: String,
    hasWhatsApp: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val initials = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials.ifEmpty { "?" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        if (hasWhatsApp) {
            Box(
                modifier = Modifier
                    .size(size * 0.4f)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFF3FA24B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = "WhatsApp detected",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.24f)
                )
            }
        }
    }
}
