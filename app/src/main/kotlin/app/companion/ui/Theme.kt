package app.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
class Pal(
    val dark: Boolean,
    val cover: Color,
    val cover2: Color,
    val coverInk: Color,
    val coverMute: Color,
    val page: Color,
    val card: Color,
    val rule: Color,
    val ruleSoft: Color,
    val ink: Color,
    val ink2: Color,
    val stamp: Color,
    val settled: Color,
    val foil: Color,
    val foil2: Color,
    val chip: Color,
    val accent: Color,
)

val Light = Pal(
    dark = false,
    cover = Color(0xFF1F3A68), cover2 = Color(0xFF2A4A80), coverInk = Color(0xFFE8EEF8), coverMute = Color(0xFFA9B8D2),
    page = Color(0xFFFAFAF7), card = Color.White, rule = Color(0xFF9CC7D6), ruleSoft = Color(0xFFD7E9EF),
    ink = Color(0xFF2A2A2A), ink2 = Color(0xFF5E6470), stamp = Color(0xFFB3261E), settled = Color(0xFF2E6B4F),
    foil = Color(0xFFD9B95F), foil2 = Color(0xFFE9D28A), chip = Color(0xFFDCE5F3), accent = Color(0xFF1F3A68),
)

val Night = Pal(
    dark = true,
    cover = Color(0xFF0C1424), cover2 = Color(0xFF18233A), coverInk = Color(0xFFE8EEF8), coverMute = Color(0xFF8FA0BC),
    page = Color(0xFF10192B), card = Color(0xFF16223A), rule = Color(0xFF36557A), ruleSoft = Color(0xFF1D2B44),
    ink = Color(0xFFDCE3EE), ink2 = Color(0xFF8FA0BC), stamp = Color(0xFFE5675F), settled = Color(0xFF7FD1A8),
    foil = Color(0xFFD9B95F), foil2 = Color(0xFFE9D28A), chip = Color(0xFF22355A), accent = Color(0xFFB7C9EA),
)

val LocalPal = staticCompositionLocalOf { Light }

val pal: Pal
    @Composable @ReadOnlyComposable get() = LocalPal.current

object Ty {
    fun mono(size: Int, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = size.sp, fontWeight = weight, fontFeatureSettings = "tnum")

    fun ui(size: Int, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontFamily = FontFamily.SansSerif, fontSize = size.sp, fontWeight = weight)
}

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Night else Light
    val scheme = if (p.dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = p.cover, background = p.page, onBackground = p.ink, surface = p.card,
            onSurface = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.ink2, outline = p.rule, error = p.stamp,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerLow = p.page,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White, background = p.page, onBackground = p.ink, surface = p.card,
            onSurface = p.ink, surfaceVariant = p.card, onSurfaceVariant = p.ink2, outline = p.rule, error = p.stamp,
            surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerLow = p.page,
        )
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
