package app.companion.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.Ty
import app.companion.ui.pal

private val DATE = 52.dp
private val AMT = 82.dp

@Composable
fun LedgerHead(first: String = "DATE") {
    val p = pal
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(Modifier.padding(vertical = 6.dp)) {
            val s = Ty.mono(10, FontWeight.Bold).copy(color = p.ink2, letterSpacing = 0.8.sp)
            Text(first, Modifier.width(DATE).padding(start = 6.dp), style = s)
            Text("PARTICULARS", Modifier.weight(1f), style = s)
            Text("DR", Modifier.width(AMT).padding(end = 6.dp), style = s, textAlign = TextAlign.End)
            Text("CR", Modifier.width(AMT).padding(end = 6.dp), style = s, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(p.rule))
    }
}

@Composable
fun LedgerRow(
    date: String,
    name: String,
    src: String,
    dr: String,
    cr: String,
    modifier: Modifier = Modifier,
    printing: Boolean = false,
    onClick: (() -> Unit)? = null,
    tags: List<String> = emptyList(),
    stamp: String? = null,
) {
    val p = pal
    Column(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 9.dp)) {
            Text(date, Modifier.width(DATE).padding(start = 6.dp), style = Ty.mono(12).copy(color = p.ink))
            Box(Modifier.width(1.dp).fillMaxHeight().background(p.stamp.copy(alpha = 0.28f)))
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Printed(name, Ty.mono(12).copy(color = p.ink), printing)
                Text(src, Modifier.padding(top = 2.dp), style = Ty.mono(10).copy(color = p.ink2), maxLines = 1)
                if (stamp != null || tags.isNotEmpty()) {
                    Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        stamp?.let { Stamp(it, size = 9) }
                        Tags(tags)
                    }
                }
            }
            Text(dr, Modifier.width(AMT).padding(end = 6.dp), style = Ty.mono(12).copy(color = p.ink), textAlign = TextAlign.End)
            Text(cr, Modifier.width(AMT).padding(end = 6.dp), style = Ty.mono(12).copy(color = p.settled), textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.ruleSoft))
    }
}

@Composable
fun LedgerTotal(label: String, dr: String, cr: String) {
    val p = pal
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(start = DATE + 14.dp), style = Ty.mono(12, FontWeight.ExtraBold).copy(color = p.ink))
        Text(dr, Modifier.width(AMT).padding(end = 6.dp), style = Ty.mono(12, FontWeight.ExtraBold).copy(color = p.ink), textAlign = TextAlign.End)
        Text(cr, Modifier.width(AMT).padding(end = 6.dp), style = Ty.mono(12, FontWeight.ExtraBold).copy(color = p.settled), textAlign = TextAlign.End)
    }
}
