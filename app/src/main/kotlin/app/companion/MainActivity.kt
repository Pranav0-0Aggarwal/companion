package app.companion

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.companion.ui.LocalFold
import app.companion.ui.kit.FloatNav
import app.companion.ui.kit.Tab
import app.companion.ui.kit.cloth
import app.companion.ui.pal
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
import app.companion.ui.screens.SearchScreen
import app.companion.ui.screens.SettingsScreen
import app.companion.ui.screens.TodayScreen

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var capture by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        take(intent)
        setContent { CompanionTheme { Root(this, unlocked, capture, { capture = it }) { unlocked = true } } }
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
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    companion object {
        const val NEW = "app.companion.NEW"
    }
}

@Composable
private fun Root(a: FragmentActivity, unlocked: Boolean, capture: String?, setCapture: (String?) -> Unit, onUnlock: () -> Unit) {
    val p by a.sl.repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val pr = p
    when {
        pr == null -> Box(Modifier.fillMaxSize().cloth(pal))
        !pr.done -> OnboardingScreen()
        pr.lock && !unlocked -> LockScreen(a, onUnlock)
        else -> Shell(a, capture, setCapture)
    }
}

@Composable
private fun Shell(a: FragmentActivity, capture: String?, setCapture: (String?) -> Unit) {
    val info by remember { WindowInfoTracker.getOrCreate(a).windowLayoutInfo(a) }.collectAsStateWithLifecycle(null)
    val fold = info?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull()
    val tabletop = fold != null && fold.state == FoldingFeature.State.HALF_OPENED && fold.orientation == FoldingFeature.Orientation.HORIZONTAL
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val top = Tab.entries.any { it.route == route }
    val go: (String) -> Unit = { r ->
        nav.navigate(r) {
            if (Tab.entries.any { it.route == r }) {
                popUpTo("today") { saveState = true }
                restoreState = true
            }
            launchSingleTop = true
        }
    }
    CompositionLocalProvider(LocalFold provides fold, LocalCapture provides { setCapture(it) }) {
        Box(Modifier.fillMaxSize()) {
            if (tabletop && top) {
                FlexScreen(go)
            } else {
                NavHost(nav, startDestination = "today") {
                    composable("today") { TodayScreen(go) }
                    composable("ledger") { LedgerScreen(go) }
                    composable("cards") { CardsScreen(go) }
                    composable("bills") { BillsScreen(go) }
                    composable("inbox") { InboxScreen(go) }
                    composable("search") { SearchScreen { nav.popBackStack() } }
                    composable("settings") { SettingsScreen { nav.popBackStack() } }
                    composable("plan") { PlanScreen({ nav.popBackStack() }) }
                }
                if (top) FloatNav(route, Modifier.align(Alignment.BottomCenter), go)
            }
            capture?.let { CaptureSheet(it) { setCapture(null) } }
        }
    }
}
