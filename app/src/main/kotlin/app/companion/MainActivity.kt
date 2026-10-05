package app.companion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import app.companion.data.Profile
import app.companion.ui.CompanionTheme
import app.companion.ui.Pick
import app.companion.ui.kit.FloatNav
import app.companion.ui.kit.LocalSnack
import app.companion.ui.kit.Motion
import app.companion.ui.kit.Snack
import app.companion.ui.kit.SnackLane
import app.companion.ui.kit.Tab
import app.companion.ui.kit.motion
import app.companion.ui.pal
import app.companion.ui.screens.AskHost
import app.companion.ui.screens.AskReq
import app.companion.ui.screens.LocalAsk
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.navigation.NavBackStackEntry
import app.companion.ui.screens.BillsScreen
import app.companion.ui.screens.CaptureSheet
import app.companion.ui.screens.CardsScreen
import app.companion.ui.screens.FlexScreen
import app.companion.ui.screens.InboxScreen
import app.companion.ui.screens.LedgerScreen
import app.companion.ui.screens.LocalCapture
import app.companion.ui.screens.LockScreen
import app.companion.ui.screens.OnboardingScreen
import app.companion.ui.screens.PlanScreen
import app.companion.ui.screens.SettingsScreen
import app.companion.ui.screens.TodayScreen

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var capture by mutableStateOf<String?>(null)
    private var dest by mutableStateOf<Dest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        take(intent)
        setContent { CompanionTheme { Root(this, unlocked, capture, { capture = it }, dest, { dest = null }) { unlocked = true } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(i: Intent?) {
        capture = when (i?.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            NEW -> ""
            else -> capture
        }
        if (i?.action == OPEN) {
            val route = i.getStringExtra(ROUTE)?.takeIf { it in tabs }
            if (route != null) dest = Dest(route, i.getLongExtra(ITEM, 0), System.nanoTime())
        }
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    companion object {
        const val NEW = "app.companion.NEW"
        const val OPEN = "app.companion.OPEN"
        const val ROUTE = "route"
        const val ITEM = "item"
    }
}

private class Dest(val route: String, val item: Long, val n: Long)

@Composable
private fun Root(a: FragmentActivity, unlocked: Boolean, capture: String?, setCapture: (String?) -> Unit, dest: Dest?, took: () -> Unit, onUnlock: () -> Unit) {
    val p by a.sl.repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val pr = p
    when {
        pr == null -> Box(Modifier.fillMaxSize().background(pal.bg))
        !pr.done -> OnboardingScreen()
        pr.lock && !unlocked -> LockScreen(a, onUnlock)
        else -> Shell(a, capture, setCapture, dest, took)
    }
}

private val tabs = Tab.entries.map { it.route }.toSet()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.push() =
    initialState.destination.route in tabs && targetState.destination.route !in tabs

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pop() =
    initialState.destination.route !in tabs && targetState.destination.route in tabs

@Composable
private fun Shell(a: FragmentActivity, capture: String?, setCapture: (String?) -> Unit, dest: Dest?, took: () -> Unit) {
    val info by remember { WindowInfoTracker.getOrCreate(a).windowLayoutInfo(a) }.collectAsStateWithLifecycle(null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
        ?.takeIf { it.state == FoldingFeature.State.HALF_OPENED && it.orientation == FoldingFeature.Orientation.HORIZONTAL }
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val top = route == null || route in tabs
    var ask by remember { mutableStateOf<AskReq?>(null) }
    val on = motion()
    val snack = remember { SnackbarHostState() }.let { h -> Snack(h, rememberCoroutineScope()) }
    val go: (String) -> Unit = { r ->
        nav.navigate(r) {
            if (r in tabs) {
                popUpTo("today") { saveState = true }
                restoreState = true
            }
            launchSingleTop = true
        }
    }
    LaunchedEffect(route) { ask = null }
    LaunchedEffect(dest?.n) {
        dest?.let {
            if (it.item > 0) Pick.item.value = it.item
            go(it.route)
            took()
        }
    }
    val enter = if (on) fadeIn(tween(210, 90)) + scaleIn(tween(210, 90), 0.97f) else fadeIn(tween(120))
    val exit = fadeOut(tween(90))
    val slideIn = if (on) slideInHorizontally(Motion.soft()) { it / 6 } + fadeIn(tween(200)) else fadeIn(tween(120))
    val slideOut = if (on) slideOutHorizontally(Motion.soft()) { it / 6 } + fadeOut(tween(150)) else fadeOut(tween(90))
    CompositionLocalProvider(LocalCapture provides { setCapture(it) }, LocalAsk provides { ask = it }, LocalSnack provides snack) {
        Box(Modifier.fillMaxSize().background(pal.bg)) {
            AnimatedContent(
                fold?.takeIf { top },
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                contentKey = { it != null },
                label = "posture",
            ) { f ->
                if (f != null) {
                    FlexScreen(go, f)
                } else {
                    Box(Modifier.fillMaxSize()) {
                        NavHost(
                            nav,
                            startDestination = "today",
                            enterTransition = { if (push()) slideIn else enter },
                            exitTransition = { if (push()) fadeOut(tween(150)) else exit },
                            popEnterTransition = { if (pop()) fadeIn(tween(200)) else enter },
                            popExitTransition = { if (pop()) slideOut else exit },
                        ) {
                            composable("today") { TodayScreen(go) }
                            composable("ledger") { LedgerScreen(go) }
                            composable("cards") { CardsScreen(go) }
                            composable("bills") { BillsScreen(go) }
                            composable("inbox") { InboxScreen(go) }
                            composable("settings") { SettingsScreen { nav.popBackStack() } }
                            composable("plan") { PlanScreen({ nav.popBackStack() }) }
                        }
                        if (top) FloatNav(route, Modifier.align(Alignment.BottomCenter), go) { ask = AskReq(it) }
                    }
                }
            }
            SnackLane(snack, Modifier.align(Alignment.BottomCenter), top && fold == null)
            AskHost(ask, { ask = null }, go)
            capture?.let { CaptureSheet(it) { setCapture(null) } }
        }
    }
}
