package app.companion.ui.kit

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun icon(name: String, vararg d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
    d.forEach {
        addPath(
            PathParser().parsePathString(it).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

object Ic {
    val Today = icon("today", "M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M8 8h8M8 12h8M8 16h5")
    val Ledger = icon("ledger", "M4 5h16v14H4z", "M4 10h16M9 5v14")
    val Cards = icon("cards", "M5 6h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M3 10h18M7 15h4")
    val Bills = icon("bills", "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 3v4M16 3v4M4 10h16")
    val Inbox = icon("inbox", "M4 6h16v12H4z", "M4 7l8 6 8-6")
    val Search = icon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M20 20l-3.5-3.5")
    val More = icon("more", "M12 5h.01M12 12h.01M12 19h.01")
    val Add = icon("add", "M12 5v14M5 12h14")
    val Back = icon("back", "M15 5l-7 7 7 7")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Copy = icon("copy", "M9 9h10v10H9z", "M5 15V5h10")
    val Shield = icon("shield", "M12 3L5 6v5c0 4.5 3 8.5 7 10 4-1.5 7-5.5 7-10V6z")
    val Print = icon("print", "M7 9V4h10v5M7 17H5v-6h14v6h-2M7 14h10v6H7z")
    val Fingerprint = icon(
        "fingerprint",
        "M12 11v4M8.5 8.8A5 5 0 0 1 17 12v1.5M7 12.5V12a5 5 0 0 1 .4-2M9.5 16.5c.3-1 .5-2.1.5-3.3V12a2 2 0 0 1 4 0v1.5c0 1.6-.3 3.1-.8 4.5M12 21c1.2-1.8 2-4 2.2-6.3",
    )
    val Plan = icon("plan", "M5 4h14v16H5z", "M9 9l2 2 4-4M9 15h6")
}
