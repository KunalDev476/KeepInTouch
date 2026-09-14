package com.pragmaapps.keepintouch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pragmaapps.keepintouch.ui.theme.Spacing
import com.pragmaapps.keepintouch.ui.theme.extendedColors

/**
 * Shared by Settings and Onboarding - live grant-state, one-sentence why, and a single CTA.
 * Icon badge + stacked title/description, same layout as Settings' own Contacts access card, so
 * the description reads as directly under the title rather than starting further left than it.
 */
@Composable
fun PermissionStatusCard(
    title: String,
    granted: Boolean,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(
                    Modifier.size(44.dp).background(
                        if (granted) MaterialTheme.extendedColors.successGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (granted) MaterialTheme.extendedColors.successGreen else MaterialTheme.colorScheme.error
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md))
            if (granted) {
                OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            } else {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}
