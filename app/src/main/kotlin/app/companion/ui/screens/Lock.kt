package app.companion.ui.screens

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.Ic
import app.companion.ui.pal

private fun prompt(a: FragmentActivity, onOk: () -> Unit) {
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Companion")
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .build()
    val cb = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    }
    BiometricPrompt(a, ContextCompat.getMainExecutor(a), cb).authenticate(info)
}

@Composable
fun LockScreen(a: FragmentActivity, onUnlock: () -> Unit) {
    val p = pal
    LaunchedEffect(Unit) { prompt(a, onUnlock) }
    Box(Modifier.fillMaxSize().background(p.bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).background(p.accentBox, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ic.Fingerprint, null, Modifier.size(38.dp), tint = p.onAccentBox)
            }
            Text("Companion is locked", Modifier.padding(top = 20.dp), style = Ty.ui(22, FontWeight.Bold).copy(color = p.ink))
            Text(
                "Use your fingerprint or screen lock. Nothing leaves this phone.",
                Modifier.padding(top = 8.dp),
                style = Ty.ui(15, FontWeight.Normal).copy(color = p.ink2),
                textAlign = TextAlign.Center,
            )
            Btn("Unlock", Modifier.padding(top = 24.dp), go = true, icon = Ic.Fingerprint) { prompt(a, onUnlock) }
        }
    }
}
