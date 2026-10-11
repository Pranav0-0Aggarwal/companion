package app.companion.link.feature

import android.graphics.Bitmap
import app.companion.core.LinkCrypto
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

object Jpeg {
    private const val MAX = 16 * 1024

    fun b64(src: Bitmap, edge: Int, q0: Int): String? {
        var b = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) ?: return null else src
        val k = edge.toFloat() / maxOf(b.width, b.height)
        if (k < 1) b = Bitmap.createScaledBitmap(b, (b.width * k).roundToInt().coerceAtLeast(1), (b.height * k).roundToInt().coerceAtLeast(1), true)
        var q = q0
        while (true) {
            val o = ByteArrayOutputStream()
            b.compress(Bitmap.CompressFormat.JPEG, q, o)
            if (o.size() <= MAX) return LinkCrypto.b64(o.toByteArray())
            if (q > 30) q -= 20 else if (b.width > 8) b = Bitmap.createScaledBitmap(b, b.width * 3 / 4, b.height * 3 / 4, true) else return null
        }
    }
}
