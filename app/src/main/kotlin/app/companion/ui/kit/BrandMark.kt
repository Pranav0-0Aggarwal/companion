package app.companion.ui.kit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.companion.core.Json
import app.companion.core.Merchant
import app.companion.ui.Light
import app.companion.ui.Pal
import app.companion.ui.Ty
import app.companion.ui.pal
import java.util.concurrent.ConcurrentHashMap

private object Glyphs {
    private val src = HashMap<String, String>()
    private val cache = ConcurrentHashMap<String, Path>()
    @Volatile private var ready = false

    @Suppress("UNCHECKED_CAST")
    private fun load(c: Context) {
        if (ready) return
        synchronized(src) {
            if (ready) return
            val t = c.applicationContext.assets.open("brand_icons.json").use { it.readBytes().decodeToString() }
            (Json.obj(t)["icons"] as List<Map<String, Any?>>).forEach { src[it["slug"] as String] = it["path"] as String }
            ready = true
        }
    }

    fun path(c: Context, slug: String): Path? {
        cache[slug]?.let { return it }
        load(c)
        return src[slug]?.let { PathParser().parsePathString(it).toPath() }?.also { cache[slug] = it }
    }
}

@Immutable
private class Look(val bg: Color, val fg: Color, val glyph: Path?, val mono: String)

private val dark = Color(0xFF111317)

private fun hex(s: String) = Color(0xFF000000L or s.removePrefix("#").toLong(16))

private fun tones(p: Pal) = listOf(p.accentBox to p.onAccentBox, p.greenBox to p.green, p.raised to p.ink2, p.accent to p.onAccent, p.raised to p.amber)

private fun mono(name: String): String {
    val w = name.split(' ').mapNotNull { it.firstOrNull(Char::isLetterOrDigit) }
    return (if (w.size > 1) "${w[0]}${w[1]}" else w.firstOrNull()?.toString() ?: "#").uppercase()
}

private fun look(c: Context, name: String, p: Pal): Look {
    val b = Merchant.brand(name)
    if (b == null) {
        val (bg, fg) = tones(p).let { it[(Merchant.key(name).hashCode() and Int.MAX_VALUE) % it.size] }
        return Look(bg, fg, null, mono(name))
    }
    val bg = hex(b.color)
    return Look(bg, if (bg.luminance() > 0.6f) dark else Color.White, b.icon?.let { Glyphs.path(c, it) }, mono(b.name))
}

@Composable
fun BrandMark(name: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val p = pal
    val c = LocalContext.current
    val l = remember(name, p.dark) { look(c, name, p) }
    Box(modifier.size(size).background(l.bg, CircleShape).border(1.dp, p.line, CircleShape), contentAlignment = Alignment.Center) {
        val g = l.glyph
        if (g != null) {
            Canvas(Modifier.fillMaxSize()) {
                val s = this.size.minDimension * 0.55f / 24f
                val o = (this.size.minDimension - 24f * s) / 2f
                translate(o, o) {
                    scale(s, s, Offset.Zero) { drawPath(g, l.fg) }
                }
            }
        } else {
            Text(l.mono, style = Ty.ui((size.value * if (l.mono.length > 1) 0.34f else 0.42f).toInt(), FontWeight.SemiBold).copy(color = l.fg), maxLines = 1)
        }
    }
}

object BrandMark {
    fun bitmap(c: Context, name: String, px: Int): Bitmap {
        val l = look(c, name, Light)
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = l.bg.toArgb()
        cv.drawCircle(px / 2f, px / 2f, px / 2f, paint)
        paint.color = l.fg.toArgb()
        val g = l.glyph
        if (g != null) {
            val s = px * 0.55f / 24f
            val o = (px - 24f * s) / 2f
            val q = android.graphics.Path()
            g.asAndroidPath().transform(Matrix().apply { setScale(s, s); postTranslate(o, o) }, q)
            cv.drawPath(q, paint)
        } else {
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            paint.textSize = px * if (l.mono.length > 1) 0.34f else 0.42f
            cv.drawText(l.mono, px / 2f, px / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f, paint)
        }
        return out
    }
}
