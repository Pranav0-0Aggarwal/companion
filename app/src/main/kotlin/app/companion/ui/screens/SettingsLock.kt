package app.companion.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.companion.ui.kit.Toggle

@Composable
fun LockRow(on: Boolean, set: (Boolean) -> Unit) {
    val c = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    val sub = if (blocked) "Set up a screen lock or fingerprint in system settings first" else "Ask for fingerprint or screen lock when opening"
    Toggle("Lock Companion", sub, on) { v ->
        blocked = v && BiometricManager.from(c).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
        if (!blocked) set(v)
    }
}
