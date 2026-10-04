package app.companion.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.delay

val LocalFold = compositionLocalOf<FoldingFeature?> { null }

@Composable
fun Secure() {
    val w = LocalActivity.current?.window
    DisposableEffect(w) {
        w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
fun rememberNow(step: Long = 1000): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(step)
        value = System.currentTimeMillis()
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

