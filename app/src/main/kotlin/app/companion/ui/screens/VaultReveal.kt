package app.companion.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.companion.data.Revealed
import app.companion.sl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val HIDE_MS = 30_000L

private fun canAuth(a: FragmentActivity) =
    BiometricManager.from(a).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

private fun ask(a: FragmentActivity, onOk: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Reveal details")
        .setSubtitle("Your vault stays on this phone")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()
    val cb = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    }
    BiometricPrompt(a, ContextCompat.getMainExecutor(a), cb).authenticate(info)
}

@Composable
fun rememberReveal(): (Long, (Revealed?) -> Unit) -> Unit {
    val a = LocalActivity.current as? FragmentActivity
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(a) {
        { id, done ->
            val load = { scope.launch { done(withContext(Dispatchers.IO) { runCatching { c.sl.repo.docs.reveal(id) }.getOrNull() }) }; Unit }
            if (a != null && canAuth(a)) ask(a, load) else load()
        }
    }
}

@Composable
fun AutoHide(active: Boolean, hide: () -> Unit) {
    LaunchedEffect(active) {
        if (active) {
            delay(HIDE_MS)
            hide()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { hide() }
}
