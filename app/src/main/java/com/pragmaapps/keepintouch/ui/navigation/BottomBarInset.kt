package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How far above the floating bottom bar its fade starts - see AppBottomBar. */
val BottomBarFadeHeight = 56.dp

/**
 * How far the floating bottom bar and its fade reach up from the screen's bottom edge. Tab screens
 * draw behind both so their lists stay visible around the island, which means anything that must
 * stay clear of them (a list's last row, a FAB, an empty state) has to add this itself.
 */
val LocalBottomBarInset = compositionLocalOf { 0.dp }
