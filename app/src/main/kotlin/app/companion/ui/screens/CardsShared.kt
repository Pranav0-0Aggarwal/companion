package app.companion.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import app.companion.ui.Ty
import app.companion.ui.kit.Btn
import app.companion.ui.kit.TextBtn
import app.companion.ui.pal

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
        title = { Text(title, style = Ty.ui(20, FontWeight.Bold).copy(color = p.ink)) },
        text = { Text(body, style = Ty.ui(15, FontWeight.Normal).copy(color = p.ink2)) },
        confirmButton = { Btn(yes, go = true, onClick = onYes) },
        dismissButton = { TextBtn("Cancel", onClick = onNo) },
    )
}
