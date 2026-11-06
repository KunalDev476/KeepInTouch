package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How far the floating bottom bar reaches up from the screen's bottom edge. Tab screens draw behind
 * it so their lists stay visible around the island, which means anything that must stay clear of
 * it (a list's last row, a FAB, an empty state) has to add this itself.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }
