package app.companion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.companion.ui.kit.LocalMotion

@Immutable
class Pal(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val raised: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentBox: Color,
    val onAccentBox: Color,
    val red: Color,
    val redBox: Color,
    val green: Color,
    val greenBox: Color,
    val amber: Color,
    val bar: Color,
)

val Light = Pal(
    dark = false,
    bg = Color(0xFFF4F5F7), card = Color.White, raised = Color(0xFFF0F1F4),
    ink = Color(0xFF111317), ink2 = Color(0xFF5D626B), ink3 = Color(0xFF8A8F98), line = Color(0xFFE6E8EC),
    accent = Color(0xFF2A62DB), onAccent = Color.White, accentBox = Color(0xFFE4ECFD), onAccentBox = Color(0xFF1846B0),
    red = Color(0xFFC0302A), redBox = Color(0xFFFCE9E7), green = Color(0xFF17784A), greenBox = Color(0xFFE2F3EA), amber = Color(0xFFB25E00),
    bar = Color(0xF2FFFFFF),
)

val Night = Pal(
    dark = true,
    bg = Color.Black, card = Color(0xFF17171A), raised = Color(0xFF232327),
    ink = Color(0xFFF2F3F5), ink2 = Color(0xFFA3A8B0), ink3 = Color(0xFF6E737B), line = Color(0xFF2A2B30),
    accent = Color(0xFF7EA6FF), onAccent = Color(0xFF0B1A3A), accentBox = Color(0xFF1D2A47), onAccentBox = Color(0xFFC9D8FF),
    red = Color(0xFFFF6B61), redBox = Color(0xFF3A1614), green = Color(0xFF4CC38A), greenBox = Color(0xFF10301F), amber = Color(0xFFFFB547),
    bar = Color(0xF21E1E22),
)

val LocalPal = staticCompositionLocalOf { Light }

val pal: Pal
    @Composable @ReadOnlyComposable get() = LocalPal.current

object Ty {
    fun ui(size: Int, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, fontWeight = weight)

    fun mono(size: Int, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, fontWeight = weight, fontFeatureSettings = "tnum, lnum")
}

private val shapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CompanionTheme(content: @Composable () -> Unit) {
    val p = if (isSystemInDarkTheme()) Night else Light
    val base = if (p.dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = p.accent, onPrimary = p.onAccent, primaryContainer = p.accentBox, onPrimaryContainer = p.onAccentBox,
        secondary = p.accent, onSecondary = p.onAccent, secondaryContainer = p.accentBox, onSecondaryContainer = p.onAccentBox,
        background = p.bg, onBackground = p.ink, surface = p.card, onSurface = p.ink, surfaceVariant = p.raised, onSurfaceVariant = p.ink2,
        surfaceContainerLowest = p.card, surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card,
        surfaceContainerHighest = p.raised, surfaceBright = p.card, surfaceDim = p.bg,
        outline = p.ink3, outlineVariant = p.line, error = p.red, onError = p.onAccent, errorContainer = p.redBox, onErrorContainer = p.red,
        scrim = Color.Black,
    )
    CompositionLocalProvider(LocalPal provides p, LocalMotion provides animationsOn()) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
    }
}
