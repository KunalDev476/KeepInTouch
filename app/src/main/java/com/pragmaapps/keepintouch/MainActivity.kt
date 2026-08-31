package com.pragmaapps.keepintouch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import com.pragmaapps.keepintouch.data.notification.CallLogObserver
import com.pragmaapps.keepintouch.data.prefs.OnboardingPreferences
import com.pragmaapps.keepintouch.ui.navigation.Destination
import com.pragmaapps.keepintouch.ui.navigation.Transitions
import com.pragmaapps.keepintouch.ui.screens.confirm.ConfirmScreen
import com.pragmaapps.keepintouch.ui.screens.contactdetail.ContactDetailScreen
import com.pragmaapps.keepintouch.ui.screens.logs.AppLogsScreen
import com.pragmaapps.keepintouch.ui.screens.logs.CallLogsScreen
import com.pragmaapps.keepintouch.ui.screens.onboarding.OnboardingScreen
import com.pragmaapps.keepintouch.ui.screens.people.PeopleScreen
import com.pragmaapps.keepintouch.ui.screens.reminders.RemindersScreen
import com.pragmaapps.keepintouch.ui.screens.schedule.ScheduleSheet
import com.pragmaapps.keepintouch.ui.screens.schedule.ScheduleSheetArgs
import com.pragmaapps.keepintouch.ui.screens.settings.SettingsScreen
import com.pragmaapps.keepintouch.ui.screens.today.TodayScreen
import com.pragmaapps.keepintouch.ui.theme.KeepInTouchTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_CONFIRM = "confirm"
private const val ROUTE_APP_LOGS = "app_logs"
private const val ROUTE_CALL_LOGS = "call_logs"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_CONTACT_DETAIL = "contact/{lookupKey}"
private fun contactDetailRoute(lookupKey: String) = "contact/$lookupKey"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    // The listener service only ever calls CallLogObserver.register() once, when it (re)connects -
    // if READ_CALL_LOG gets granted while that service is already connected (the common case: the
    // user grants it from onboarding/Settings without restarting anything), nothing re-triggers
    // registration for the rest of that session. Re-attempting it on every resume closes that gap -
    // register() is a no-op if it's already registered, so this is safe to call repeatedly, and it
    // also means returning to the app right after a call (the normal thing to do) immediately
    // catches up on any call log entry that arrived while READ_CALL_LOG had never been checked yet.
    @Inject lateinit var callLogObserver: CallLogObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingDone = onboardingPreferences.completed.value
        setContent {
            KeepInTouchTheme {
                KeepInTouchRoot(startAtOnboarding = !onboardingDone)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        callLogObserver.register()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeepInTouchRoot(startAtOnboarding: Boolean) {
    val navController = rememberNavController()
    var scheduleSheetArgs by remember { mutableStateOf<ScheduleSheetArgs?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Bumped whenever a NEW schedule (not an edit) is created, regardless of which screen the
    // sheet was opened from - PeopleScreen watches this to jump to "Scheduled" with search
    // cleared, so the result of what you just did is immediately visible.
    var peopleResetSignal by remember { mutableIntStateOf(0) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Destination.bottomBarDestinations.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { if (showBottomBar) AppBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startAtOnboarding) ROUTE_ONBOARDING else Destination.Today.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(
                ROUTE_ONBOARDING,
                enterTransition = { fadeIn(androidx.compose.animation.core.tween(260)) },
                exitTransition = { fadeOut(androidx.compose.animation.core.tween(260)) }
            ) {
                OnboardingScreen(onDone = {
                    navController.navigate(Destination.Today.route) {
                        popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(
                Destination.Today.route,
                enterTransition = { Transitions.tabEnter() },
                exitTransition = { Transitions.tabExit() },
                popEnterTransition = { Transitions.tabEnter() },
                popExitTransition = { Transitions.tabExit() }
            ) {
                TodayScreen(onOpenConfirm = { navController.navigate(ROUTE_CONFIRM) })
            }
            composable(
                Destination.People.route,
                enterTransition = { Transitions.tabEnter() },
                exitTransition = { Transitions.tabExit() },
                popEnterTransition = { Transitions.tabEnter() },
                popExitTransition = { Transitions.tabExit() }
            ) {
                PeopleScreen(
                    onScheduleContact = { lookupKey -> scheduleSheetArgs = ScheduleSheetArgs.ForNewContact(lookupKey) },
                    onOpenContact = { lookupKey -> navController.navigate(contactDetailRoute(lookupKey)) },
                    resetToScheduledSignal = peopleResetSignal
                )
            }
            composable(
                Destination.Settings.route,
                enterTransition = { Transitions.tabEnter() },
                exitTransition = { Transitions.tabExit() },
                popEnterTransition = { Transitions.tabEnter() },
                popExitTransition = { Transitions.tabExit() }
            ) {
                SettingsScreen(
                    onOpenAppLogs = { navController.navigate(ROUTE_APP_LOGS) },
                    onOpenCallLogs = { navController.navigate(ROUTE_CALL_LOGS) },
                    onOpenReminders = { navController.navigate(ROUTE_REMINDERS) }
                )
            }
            composable(
                ROUTE_APP_LOGS,
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) {
                AppLogsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                ROUTE_CALL_LOGS,
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) {
                CallLogsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                ROUTE_REMINDERS,
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) {
                RemindersScreen(onBack = { navController.popBackStack() })
            }
            composable(
                ROUTE_CONFIRM,
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) {
                ConfirmScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = ROUTE_CONTACT_DETAIL,
                arguments = listOf(navArgument("lookupKey") { type = NavType.StringType }),
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) { entry ->
                val lookupKey = entry.arguments?.getString("lookupKey").orEmpty()
                ContactDetailScreen(
                    lookupKey = lookupKey,
                    onBack = { navController.popBackStack() },
                    onEditSchedule = { scheduleId -> scheduleSheetArgs = ScheduleSheetArgs.ForExistingSchedule(scheduleId) }
                )
            }
        }
    }

    scheduleSheetArgs?.let { args ->
        ScheduleSheet(
            args = args,
            sheetState = sheetState,
            onDismiss = { scheduleSheetArgs = null },
            onScheduleCreated = {
                peopleResetSignal++
                navController.navigate(Destination.People.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}

/**
 * Filled/outlined icon pairing (see [Destination]) plus a small selected-state scale pop makes the
 * active tab unambiguous without leaning on color alone; a top divider gives the bar a crisp edge
 * against scrollable content instead of relying purely on tonal elevation to read as "above" it.
 */
@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Destination.bottomBarDestinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.9f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "bottomNavIconScale"
                )

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = destination.label,
                            modifier = Modifier.scale(iconScale)
                        )
                    },
                    label = {
                        Text(
                            destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}
