package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String, val label: String, val icon: ImageVector) {
    data object Today : Destination("today", "Today", Icons.Filled.CheckCircle)
    data object People : Destination("people", "People", Icons.Filled.People)
    data object Settings : Destination("settings", "Settings", Icons.Filled.Settings)

    companion object {
        val bottomBarDestinations = listOf(Today, People, Settings)
    }
}
