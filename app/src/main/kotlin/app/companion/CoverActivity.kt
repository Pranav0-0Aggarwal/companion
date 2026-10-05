package app.companion

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.core.Reveal
import app.companion.data.Profile
import app.companion.system.Prefs
import app.companion.ui.CompanionTheme
import app.companion.ui.Ty
import app.companion.ui.cover.CoverPager
import app.companion.ui.pal
import app.companion.ui.screens.prompt

internal class CoverReq(val page: Int, val n: Long)

class CoverActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var locked by mutableStateOf(false)
    private var req by mutableStateOf(CoverReq(0, 0))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        take(intent)
        setContent { CompanionTheme(dark = true) { Root(this, unlocked, locked, req, { unlocked = true }) { locked = false } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    override fun onResume() {
        super.onResume()
        locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
    }

    private fun take(i: Intent?) {
        req = CoverReq(i?.getIntExtra(PAGE, 0) ?: 0, System.nanoTime())
    }

    companion object {
        const val PAGE = "page"
        private const val FLEX = 1

        fun intent(c: Context, page: Int): Intent =
            Intent(c, CoverActivity::class.java).setData(Uri.parse("app.companion://cover/$page")).putExtra(PAGE, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun options(c: Context): Bundle? = c.getSystemService(DisplayManager::class.java).getDisplay(FLEX)?.let { ActivityOptions.makeBasic().setLaunchDisplayId(FLEX).toBundle() }

        fun open(c: Context, page: Int = 0) = c.startActivity(intent(c, page), options(c))
    }
}

@Composable
private fun Root(a: FragmentActivity, unlocked: Boolean, locked: Boolean, req: CoverReq, onUnlocked: () -> Unit, onOpened: () -> Unit) {
    val p = pal
    val prof by a.sl.repo.profile.collectAsStateWithLifecycle<Profile?>(null)
    val opts = remember { Prefs.get(a) }
    val pr = prof
    when {
        pr == null -> Box(Modifier.fillMaxSize().background(p.bg))
        !pr.done -> Box(Modifier.fillMaxSize().background(p.bg).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Set up Companion", style = Ty.ui(24, FontWeight.Bold).copy(color = p.ink), textAlign = TextAlign.Center)
                Text("Open it on the main screen to finish.", Modifier.padding(top = 8.dp), style = Ty.ui(16, FontWeight.Normal).copy(color = p.ink2), textAlign = TextAlign.Center)
            }
        }
        else -> {
            val unlock: (() -> Unit)? = when {
                pr.lock && !unlocked -> { { prompt(a, onUnlocked) } }
                locked && !opts.lock -> {
                    {
                        a.getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
                            a,
                            object : KeyguardManager.KeyguardDismissCallback() {
                                override fun onDismissSucceeded() = onOpened()
                            },
                        )
                    }
                }
                else -> null
            }
            key(req.n) { CoverPager(Reveal.of(pr.lock, unlocked, locked, opts.lock), unlock, req.page) }
        }
    }
}
