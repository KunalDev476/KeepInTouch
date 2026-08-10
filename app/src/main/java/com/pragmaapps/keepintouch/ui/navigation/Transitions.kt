package com.pragmaapps.keepintouch.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/**
 * Two motion "languages" used throughout the app, kept deliberately simple:
 * - Peer/lateral moves (bottom-nav tabs) fade + settle with a hair of scale - nothing slides,
 *   since none of these screens are "above" or "below" each other.
 * - Hierarchical moves (list -> detail, e.g. People -> Contact, Today -> Confirm) slide along X
 *   like a real push/pop, so back-navigation reads as literally retracing your steps.
 */
private const val TAB_DURATION_MS = 220
private const val PUSH_DURATION_MS = 320

object Transitions {
    fun tabEnter(): EnterTransition =
        fadeIn(tween(TAB_DURATION_MS)) + scaleIn(initialScale = 0.97f, animationSpec = tween(TAB_DURATION_MS))

    fun tabExit(): ExitTransition = fadeOut(tween(TAB_DURATION_MS))

    fun pushEnter(): EnterTransition =
        slideInHorizontally(animationSpec = tween(PUSH_DURATION_MS)) { fullWidth -> fullWidth / 3 } +
            fadeIn(tween(PUSH_DURATION_MS))

    fun pushExit(): ExitTransition =
        slideOutHorizontally(animationSpec = tween(PUSH_DURATION_MS)) { fullWidth -> -fullWidth / 4 } +
            fadeOut(tween(PUSH_DURATION_MS))

    fun popEnter(): EnterTransition =
        slideInHorizontally(animationSpec = tween(PUSH_DURATION_MS)) { fullWidth -> -fullWidth / 4 } +
            fadeIn(tween(PUSH_DURATION_MS))

    fun popExit(): ExitTransition =
        slideOutHorizontally(animationSpec = tween(PUSH_DURATION_MS)) { fullWidth -> fullWidth / 3 } +
            fadeOut(tween(PUSH_DURATION_MS))
}
