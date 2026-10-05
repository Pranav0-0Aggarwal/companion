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
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

object Ic {
    val Today = icon("today", "M7 3v3M17 3v3", "M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18", "M8 14h3v3H8z")
    val Ledger = icon("ledger", "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h6M9 16h3")
    val Cards = icon("cards", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 10h18M7 15h3")
    val Bills = icon("bills", "M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z", "M14 3v5h5M9 13h6M9 17h4")
    val Inbox = icon("inbox", "M3 13l3-8h12l3 8v5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z", "M3 13h5l1.5 2.5h5L16 13h5")
    val Search = icon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0", "M20 20l-3.5-3.5")
    val Sparkle = icon("sparkle", "M12 3c.5 4 2.5 6 7 7-4.5 1-6.5 3-7 7-.5-4-2.5-6-7-7 4.5-1 6.5-3 7-7z", "M19 15.5c.2 1.2.8 1.8 2 2-1.2.2-1.8.8-2 2-.2-1.2-.8-1.8-2-2 1.2-.2 1.8-.8 2-2z")
    val Mic = icon("mic", "M12 3a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z", "M5 11a7 7 0 0 0 14 0M12 18v3")
    val Settings = icon("settings", "M4 7h9M17 7h3M4 17h3M11 17h9", "M15 5v4M9 15v4")
    val More = icon("more", "M12 5h.01M12 12h.01M12 19h.01")
    val Add = icon("add", "M12 5v14M5 12h14")
    val Back = icon("back", "M19 12H5M11 6l-6 6 6 6")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Copy = icon("copy", "M9 8h10a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h3")
    val Shield = icon("shield", "M12 3L5 6v5c0 4.5 3 8.5 7 10 4-1.5 7-5.5 7-10V6z")
    val Fingerprint = icon(
        "fingerprint",
        "M12 11v4M8.5 8.8A5 5 0 0 1 17 12v1.5M7 12.5V12a5 5 0 0 1 .4-2M9.5 16.5c.3-1 .5-2.1.5-3.3V12a2 2 0 0 1 4 0v1.5c0 1.6-.3 3.1-.8 4.5M12 21c1.2-1.8 2-4 2.2-6.3",
    )
    val Plan = icon("plan", "M5 4h14v16H5z", "M9 9l2 2 4-4M9 15h6")
    val Left = icon("left", "M15 6l-6 6 6 6")
    val Right = icon("right", "M9 6l6 6-6 6")
    val Down = icon("down", "M6 9l6 6 6-6")
    val Clock = icon("clock", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18z", "M12 7v5l3 2")
    val Send = icon("send", "M12 19V5M6 11l6-6 6 6")
    val Edit = icon("edit", "M4 20h4L19 9l-4-4L4 16z")
    val Bell = icon("bell", "M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z", "M10 20a2 2 0 0 0 4 0")
    val Mail = icon("mail", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M3 7l9 6 9-6")
    val Chat = icon("chat", "M4 20l1.3-3.9A8 8 0 1 1 8 19z")
    val Sms = icon("sms", "M4 6a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H9l-5 4z")
    val Camera = icon("camera", "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4z", "M12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M17.5 6.5h.01")
    val Food = icon("food", "M7 3v18M5 3v5a2 2 0 0 0 4 0V3", "M17 21V3c-2 1-3 4-3 8h3")
    val Cart = icon("cart", "M3 4h2l2.4 11h10.2L20 8H6.2", "M9 20h.01M17 20h.01")
    val Car = icon("car", "M5 17h14v-5l-2-5H7l-2 5z", "M5 12h14M7 17v2M17 17v2")
    val Bag = icon("bag", "M6 7h12l1 14H5z", "M9 7a3 3 0 0 1 6 0")
    val Bolt = icon("bolt", "M13 3L5 14h6l-1 7 8-11h-6z")
    val Film = icon("film", "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M10 9l5 3-5 3z")
    val Heart = icon("heart", "M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z")
    val Case = icon("case", "M5 8h14a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z", "M9 8V5h6v3M9 12v4M15 12v4")
    val Swap = icon("swap", "M4 8h15l-4-4M20 16H5l4 4")
    val In = icon("in", "M17 7L7 17M7 9v8h8")
    val Dots = icon("dots", "M6 12h.01M12 12h.01M18 12h.01")
    val Trash = icon("trash", "M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3")
    val Lock = icon("lock", "M7 11h10a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1v-8a1 1 0 0 1 1-1z", "M8 11V8a4 4 0 0 1 8 0v3")
    val User = icon("user", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4 21a8 8 0 0 1 16 0")
    val Download = icon("download", "M12 4v12M6 10l6 6 6-6M5 20h14")
    val Calendar = Today
    val Money = icon("money", "M4 7h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h11", "M16 13.5h.01")
    val Trip = icon("trip", "M21 3L3 11l7 3 3 7z", "M10 14l11-11")
    val Vault = icon("vault", "M9 15a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M12 11h9M18 11v3")
    val Scale = icon("scale", "M5 4h14a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1z", "M8 10a4 4 0 0 1 8 0M12 10l2-2")

    fun of(category: String?) = when (category) {
        "food" -> Food
        "groceries" -> Cart
        "transport" -> Car
        "shopping" -> Bag
        "bills" -> Bolt
        "entertainment" -> Film
        "health" -> Heart
        "travel" -> Case
        "transfer" -> Swap
        "income" -> In
        else -> Dots
    }

    fun src(s: String) = when (s) {
        "Mail" -> Mail
        "Wa" -> Chat
        "Ig" -> Camera
        "Notif" -> Bell
        else -> Sms
    }
}
