package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * [selectedIcon]/[unselectedIcon] are the filled/outlined pair used by [com.pragmaapps.keepintouch.MainActivity]'s
 * bottom bar so the active tab reads clearly at a glance instead of relying on color/label weight alone.
 */
sealed class Destination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Today : Destination("today", "Today", Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle)
    data object People : Destination("people", "People", Icons.Filled.People, Icons.Outlined.People)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val bottomBarDestinations = listOf(Today, People, Settings)
    }
}
