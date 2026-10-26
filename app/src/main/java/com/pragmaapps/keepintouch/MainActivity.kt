package com.pragmaapps.keepintouch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pragmaapps.keepintouch.data.notification.CallLogObserver
import com.pragmaapps.keepintouch.data.notification.ContactsChangeObserver
import com.pragmaapps.keepintouch.data.prefs.OnboardingPreferences
import com.pragmaapps.keepintouch.data.prefs.VoiceIntroPreferences
import com.pragmaapps.keepintouch.domain.usecase.SyncContactsUseCase
import com.pragmaapps.keepintouch.ui.components.VoiceIntroOverlay
import com.pragmaapps.keepintouch.ui.navigation.Destination
import com.pragmaapps.keepintouch.ui.navigation.ForegroundResetGate
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
import com.pragmaapps.keepintouch.ui.screens.voice.VoiceFeature
import com.pragmaapps.keepintouch.ui.screens.voice.VoiceScheduleScreen
import com.pragmaapps.keepintouch.ui.theme.KeepInTouchTheme
import com.pragmaapps.keepintouch.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_CONFIRM = "confirm"
private const val ROUTE_APP_LOGS = "app_logs"
private const val ROUTE_CALL_LOGS = "call_logs"
private const val ROUTE_REMINDERS = "reminders"
private const val ROUTE_CONTACT_DETAIL = "contact/{lookupKey}"
private const val ROUTE_VOICE = "voice"
private fun contactDetailRoute(lookupKey: String) = "contact/$lookupKey"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var onboardingPreferences: OnboardingPreferences

    @Inject lateinit var voiceIntroPreferences: VoiceIntroPreferences

    // The listener service only ever calls CallLogObserver.register() once, when it (re)connects -
    // if READ_CALL_LOG gets granted while that service is already connected (the common case: the
    // user grants it from onboarding/Settings without restarting anything), nothing re-triggers
    // registration for the rest of that session. Re-attempting it on every resume closes that gap -
    // register() is a no-op if it's already registered, so this is safe to call repeatedly, and it
    // also means returning to the app right after a call (the normal thing to do) immediately
    // catches up on any call log entry that arrived while READ_CALL_LOG had never been checked yet.
    @Inject lateinit var callLogObserver: CallLogObserver

    // Same registration gap as callLogObserver, for READ_CONTACTS.
    @Inject lateinit var contactsChangeObserver: ContactsChangeObserver

    @Inject lateinit var syncContacts: SyncContactsUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val onboardingDone = onboardingPreferences.completed.value
        setContent {
            KeepInTouchTheme {
                KeepInTouchRoot(startAtOnboarding = !onboardingDone, voiceIntroPreferences = voiceIntroPreferences)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        callLogObserver.register()
        contactsChangeObserver.register()
        // Picks up contact renames made while the app was closed and the observer wasn't running
        // (e.g. the listener service isn't enabled), before any screen shows a stale name.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch { syncContacts() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeepInTouchRoot(startAtOnboarding: Boolean, voiceIntroPreferences: VoiceIntroPreferences) {
    val navController = rememberNavController()
    var scheduleSheetArgs by remember { mutableStateOf<ScheduleSheetArgs?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Bumped whenever a NEW schedule (not an edit) is created, regardless of which screen the
    // sheet was opened from - PeopleScreen watches this to jump to "Scheduled" with search
    // cleared, so the result of what you just did is immediately visible.
    var peopleResetSignal by remember { mutableIntStateOf(0) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    // Reminders is reached from Settings but, unlike the other pushed detail screens (App Logs,
    // Call Logs, Confirm, Contact Detail), keeps the tab bar visible - it's frequently revisited
    // alongside the main tabs rather than a one-off drill-down.
    val showBottomBar = currentRoute in Destination.bottomBarDestinations.map { it.route } || currentRoute == ROUTE_REMINDERS
    // Voice scheduling's entry point floats over the everyday screens - but not Settings or
    // anything reached from it (App Logs, Call Logs, Reminders), onboarding, or itself - and only
    // on Android versions voice scheduling supports at all.
    val showVoiceButton = VoiceFeature.isSupported && currentRoute in setOf(
        Destination.Today.route, Destination.People.route, ROUTE_CONFIRM, ROUTE_CONTACT_DETAIL
    )
    // One-time "In Touch just got smarter" overlay pointing at the mic - only once onboarding is
    // done and Today (with the mic) is on screen, and only until it's been dismissed once.
    val voiceIntroSeen by voiceIntroPreferences.seen.collectAsState()
    var micBounds by remember { mutableStateOf<Rect?>(null) }
    val openVoice = { navController.navigate(ROUTE_VOICE) { launchSingleTop = true } }

    // Every fresh return to the app - Home/app-switcher away and back, screen off/on, another app
    // opened over this one - drops back to Today, discarding wherever else you were (People,
    // Settings, a pushed screen, or the schedule sheet). A self-triggered interruption (a runtime
    // permission dialog, or an Android Settings screen this app itself opened) causes a similar
    // stop/start pair but must NOT trigger this - see ForegroundResetGate, which whatever launched
    // that dialog/Settings screen calls right beforehand to suppress it once.
    //
    // Deliberately watching ProcessLifecycleOwner (the whole app's foreground state), not this
    // Activity's own lifecycle: Activity.onStop fires for reasons that aren't really "the user
    // left" - a config change recreating the Activity, or a same-process dialog/transition - and
    // reacting to those was flashing back to Today mid-interaction, effectively locking the tab
    // bar. ProcessLifecycleOwner only reports ON_STOP once nothing in the app has been visible for
    // a short debounce window, i.e. an actual departure and return.
    DisposableEffect(Unit) {
        var wasStopped = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> wasStopped = true
                Lifecycle.Event.ON_START -> {
                    if (wasStopped) {
                        wasStopped = false
                        val suppressed = ForegroundResetGate.consumeSuppress()
                        val onOnboarding = navController.currentDestination?.route == ROUTE_ONBOARDING
                        if (!suppressed && !onOnboarding) {
                            scheduleSheetArgs = null
                            navController.navigate(Destination.Today.route) {
                                popUpTo(navController.graph.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        onDispose { ProcessLifecycleOwner.get().lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                AppBottomBar(navController)
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = showVoiceButton, enter = fadeIn(), exit = fadeOut()) {
                FloatingActionButton(
                    onClick = { openVoice() },
                    modifier = Modifier.onGloballyPositioned { micBounds = it.boundsInRoot() }
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = "Create a schedule by voice")
                }
            }
        }
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
                ROUTE_VOICE,
                enterTransition = { Transitions.pushEnter() },
                exitTransition = { Transitions.pushExit() },
                popEnterTransition = { Transitions.popEnter() },
                popExitTransition = { Transitions.popExit() }
            ) {
                VoiceScheduleScreen(onBack = { navController.popBackStack() })
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

    val introMicBounds = micBounds
    if (VoiceFeature.isSupported && !voiceIntroSeen && currentRoute == Destination.Today.route && introMicBounds != null) {
        VoiceIntroOverlay(
            micBounds = introMicBounds,
            wakeWord = BuildConfig.WAKE_WORD,
            onDismiss = voiceIntroPreferences::markSeen,
            onOpenVoice = {
                voiceIntroPreferences.markSeen()
                openVoice()
            }
        )
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

private val BottomNavMotion = tween<androidx.compose.ui.unit.Dp>(durationMillis = 260, easing = FastOutSlowInEasing)

/**
 * A floating, fully-rounded "island" bar - inset with margin on every side rather than spanning
 * edge to edge - with a single capsule indicator that SLIDES between tabs rather than each item
 * fading its own indicator in independently. Since all three tabs are equal-weight, the
 * indicator's target position/width is just an even fraction of the bar's own measured width (via
 * BoxWithConstraints), so no per-item position tracking is needed.
 *
 * Every motion here is a plain eased [tween], deliberately NOT a bouncy spring: a spring
 * overshoots past its target and settles back, which over a short tab-to-tab travel distance reads
 * as the indicator wobbling left-right instead of just arriving - a tween reaches its target once
 * and stops.
 */
@Composable
private fun AppBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedIndex = Destination.bottomBarDestinations
        .indexOfFirst { it.route == currentRoute }
        .coerceAtLeast(0)

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(28.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .height(60.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                val itemWidth = maxWidth / Destination.bottomBarDestinations.size
                val indicatorInset = Spacing.xs
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex + indicatorInset,
                    animationSpec = BottomNavMotion,
                    label = "bottomNavIndicatorX"
                )

                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth - indicatorInset * 2)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(20.dp))
                )

                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Destination.bottomBarDestinations.forEachIndexed { index, destination ->
                        BottomNavItem(
                            destination = destination,
                            selected = index == selectedIndex,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    destination: Destination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = tween(200, easing = FastOutSlowInEasing),
        label = "bottomNavIconScale"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "bottomNavContentColor"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            if (selected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.scale(iconScale)
        )
        Text(
            destination.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor
        )
    }
}
