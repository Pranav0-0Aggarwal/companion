package app.companion.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.pal

internal fun LazyListScope.emptyPage(title: String, body: String) {
    item(key = "empty") {
        val p = pal
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 36.dp)) {
            Text(title, style = Ty.ui(18, FontWeight.Bold).copy(color = p.ink))
            Text(body, Modifier.padding(top = 6.dp), style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2))
        }
    }
}

internal fun srcWord(src: String) = when (src) {
    "Sms" -> "SMS"
    "Notif" -> "notification"
    "Mail" -> "mail"
    "Wa" -> "WhatsApp"
    else -> "Instagram"
}

@Composable
internal fun PassConfirm(title: String, body: String, yes: String, onYes: () -> Unit, onNo: () -> Unit) {
    val p = pal
    AlertDialog(
        onDismissRequest = onNo,
        containerColor = p.card,
        title = { Text(title, style = Ty.ui(18, FontWeight.Bold).copy(color = p.ink)) },
        text = { Text(body, style = Ty.ui(14, FontWeight.Normal).copy(color = p.ink2)) },
        confirmButton = { Btn(yes, go = true, onClick = onYes) },
        dismissButton = { Btn("Cancel", onClick = onNo) },
    )
}
