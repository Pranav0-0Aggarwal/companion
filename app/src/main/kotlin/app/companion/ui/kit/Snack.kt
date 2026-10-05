package app.companion.ui.kit

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.pal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Snack(val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun undoable(text: String, act: suspend () -> Boolean, undo: suspend () -> Unit) {
        scope.launch {
            if (act() && host.showSnackbar(text, "Undo", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) undo()
        }
    }
}

val LocalSnack = staticCompositionLocalOf<Snack> { error("Snack not provided") }

@Composable
fun SnackLane(snack: Snack, modifier: Modifier = Modifier, lift: Boolean = false) {
    val p = pal
    SnackbarHost(snack.host, modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp).padding(bottom = if (lift) 88.dp else 16.dp)) { d ->
        Snackbar(
            d,
            shape = RoundedCornerShape(20.dp),
            containerColor = p.ink,
            contentColor = p.bg,
            actionColor = p.accentBox,
        )
    }
}
