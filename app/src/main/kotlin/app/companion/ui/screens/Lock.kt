package app.companion.ui.screens

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import app.companion.ui.Secure
import app.companion.ui.Ty
import app.companion.ui.kit.Ic
import app.companion.ui.kit.cloth
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
    Secure()
    LaunchedEffect(Unit) { prompt(a, onUnlock) }
    Box(Modifier.fillMaxSize().cloth(p), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp).clickable { prompt(a, onUnlock) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).border(2.dp, p.foil, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Ic.Fingerprint, "Unlock", Modifier.size(34.dp), tint = p.foil)
            }
            Text("Companion is locked", Modifier.padding(top = 14.dp), style = Ty.ui(18, FontWeight.Bold).copy(color = p.coverInk))
            Text(
                "Touch the fingerprint sensor. Your ledger never leaves this phone.",
                Modifier.padding(top = 6.dp),
                style = Ty.ui(13, FontWeight.Medium).copy(color = p.coverMute),
                textAlign = TextAlign.Center,
            )
        }
    }
}
