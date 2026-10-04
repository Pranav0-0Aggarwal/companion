package app.companion.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.companion.ui.Ty
import app.companion.ui.kit.Chip
import app.companion.ui.pal

private val keeps = listOf(90 to "90 days", 365 to "1 year", 0 to "Forever")

@Composable
fun KeepRow(days: Int, set: (Int) -> Unit) {
    val p = pal
    Column(Modifier.padding(16.dp)) {
        Text("Keep message text", style = Ty.ui(14).copy(color = p.ink))
        Text(
            "Older text is erased from this phone. Amounts, merchants and dates stay. Codes are never kept.",
            Modifier.padding(top = 2.dp),
            style = Ty.ui(12, FontWeight.Normal).copy(color = p.ink2),
        )
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keeps.forEach { (d, label) -> Chip(label, d == days) { set(d) } }
        }
    }
}
