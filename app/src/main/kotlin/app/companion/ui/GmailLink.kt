package app.companion.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.companion.BuildConfig
import app.companion.sl
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

private const val SCOPE = "https://www.googleapis.com/auth/gmail.readonly"

private suspend fun link(c: Context, token: String) = runCatching {
    c.sl.repo.edit { it.copy(mail = true) }
    c.sl.gmail.sync(token)
}.isSuccess

@Composable
fun rememberGmailLink(onResult: (Boolean) -> Unit): () -> Unit {
    val c = LocalContext.current
    val done by rememberUpdatedState(onResult)
    val scope = rememberCoroutineScope()
    val grant: (AuthorizationResult?) -> Unit = { r ->
        val token = r?.accessToken
        if (token == null) done(false) else scope.launch { done(link(c, token)) }
    }
    val resolve = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        grant(res.data?.let { runCatching { Identity.getAuthorizationClient(c).getAuthorizationResultFromIntent(it) }.getOrNull() })
    }
    if (BuildConfig.GMAIL_CLIENT_ID.isBlank()) return { done(false) }
    return {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()
        Identity.getAuthorizationClient(c).authorize(request)
            .addOnSuccessListener { r ->
                val pending = r.pendingIntent
                if (r.hasResolution() && pending != null) resolve.launch(IntentSenderRequest.Builder(pending.intentSender).build()) else grant(r)
            }
            .addOnFailureListener { done(false) }
    }
}
