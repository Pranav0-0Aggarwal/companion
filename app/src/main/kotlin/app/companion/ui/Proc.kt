package app.companion.ui

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.companion.ai.Job
import app.companion.ai.Active
import app.companion.ai.Processing
import app.companion.ai.Run
import app.companion.data.Profile
import app.companion.sl
import app.companion.ai.Dl
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Fin(val job: Job, val n: Int)

object Proc {
    private const val PREFS = "processing"
    private const val CHARGE = "charge"
    private const val LATER = "later"
    private const val FIN = "fin"

    private val fin = MutableStateFlow<Fin?>(null)
    val done: StateFlow<Fin?> = fin

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun charge(c: Context) = prefs(c).getBoolean(CHARGE, true)

    fun charge(c: Context, v: Boolean) = prefs(c).edit().putBoolean(CHARGE, v).apply()

    fun later(c: Context): String? = prefs(c).getString(LATER, null)

    fun later(c: Context, sha: String) = prefs(c).edit().putString(LATER, sha).apply()

    fun dismiss(c: Context) {
        prefs(c).edit().remove(FIN).apply()
        fin.value = null
    }

    fun watch(app: Application) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            fin.value = runCatching { prefs(app).getString(FIN, null)?.split(':')?.let { (j, n) -> Fin(Job.valueOf(j), n.toInt()) } }.getOrNull()
            var last: Run? = null
            Processing.state.collect { r ->
                val l = last
                if (r != null) {
                    if (fin.value != null) dismiss(app)
                } else if (l != null && app.sl.repo.mark(l.job.name) == null) {
                    prefs(app).edit().putString(FIN, "${l.job.name}:${l.done}").apply()
                    fin.value = Fin(l.job, l.done)
                }
                last = r
            }
        }
    }

    fun first(c: Context, p: Profile) = !p.imported && p.sms && c.has(Manifest.permission.READ_SMS)
}

@Composable
fun rememberCharge(): Pair<Boolean, (Boolean) -> Unit> {
    val c = LocalContext.current
    var on by remember { mutableStateOf(Proc.charge(c)) }
    return on to { v ->
        on = v
        Proc.charge(c, v)
    }
}

@Composable
fun rememberStart(): (Job) -> Unit {
    val c = LocalContext.current
    val ask = rememberPerms(Manifest.permission.POST_NOTIFICATIONS) {}
    return { j ->
        Processing.start(c, j, !Proc.charge(c))
        if (!c.has(Manifest.permission.POST_NOTIFICATIONS)) ask()
    }
}

@Composable
fun rememberBanner(first: Boolean, count: Int?): Pair<Boolean, () -> Unit> {
    val c = LocalContext.current
    val mode by remember { Dl.state.map { it.mode }.distinctUntilChanged() }.collectAsStateWithLifecycle(Dl.state.value.mode)
    val changed by Processing.modelChanged.collectAsStateWithLifecycle()
    val run by Processing.state.collectAsStateWithLifecycle()
    var later by remember { mutableStateOf(Proc.later(c)) }
    val sha by produceState<String?>(null, changed, mode) { value = withContext(Dispatchers.IO) { Active.sha(c) } }
    val now = sha
    return (changed && !first && (count ?: 0) > 0 && run == null && now != null && now != later) to {
        if (now != null) {
            later = now
            Proc.later(c, now)
        }
    }
}
