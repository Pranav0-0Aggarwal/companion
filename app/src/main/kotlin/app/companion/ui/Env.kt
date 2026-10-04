package app.companion.ui

import androidx.compose.runtime.getValue
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.util.WeakHashMap
import kotlinx.coroutines.delay


private val secured = WeakHashMap<Window, Int>()

@Composable
fun Secure() {
    val w = LocalActivity.current?.window
    DisposableEffect(w) {
        if (w != null) {
            val n = secured[w] ?: 0
            if (n == 0) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            secured[w] = n + 1
        }
        onDispose {
            if (w != null) {
                val n = (secured[w] ?: 1) - 1
                if (n <= 0) {
                    secured.remove(w)
                    w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    secured[w] = n
                }
            }
        }
    }
}

@Composable
fun rememberNow(step: Long = 1000, active: Boolean = true): State<Long> {
    val owner = LocalLifecycleOwner.current
    return produceState(System.currentTimeMillis(), step, active, owner) {
        if (!active) return@produceState
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(step - value % step)
            }
        }
    }
}

@Composable
fun animationsOn(): Boolean {
    val c = LocalContext.current
    return remember { Settings.Global.getFloat(c.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

fun copy(c: Context, text: String) {
    val cm = c.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("code", text)
    clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    cm.setPrimaryClip(clip)
}

fun Context.has(perm: String) = ContextCompat.checkSelfPermission(this, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED

fun Context.listenerOn() = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

fun Context.openListenerSettings() =
    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

@Composable
fun rememberPerms(vararg perms: String, onResult: (Boolean) -> Unit): () -> Unit {
    val done by rememberUpdatedState(onResult)
    val l = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> done(r.values.all { it }) }
    val list = remember { arrayOf(*perms) }
    return { l.launch(list) }
}


@Composable
fun rememberVoice(onText: (String?) -> Unit): (() -> Unit)? {
    val c = LocalContext.current
    val done by rememberUpdatedState(onText)
    val l = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        done(r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() })
    }
    val i = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Companion")
    }
    val ok = remember { i.resolveActivity(c.packageManager) != null }
    return if (ok) ({ runCatching { l.launch(i) } }) else null
}
