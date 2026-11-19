package com.sortquest.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sortquest.app.domain.model.FileCategory
import com.sortquest.app.domain.model.SwipeDirection

/** Palette "néon doux" sur fond très sombre. */
object Neon {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF141A22)
    val SurfaceHigh = Color(0xFF1C2430)
    val Outline = Color(0xFF2A3442)
    val TextPrimary = Color(0xFFE8EEF5)
    val TextMuted = Color(0xFF8A97A8)

    val Cyan = Color(0xFF3DF5E0)
    val Lime = Color(0xFFB6FF3B)
    val Magenta = Color(0xFFFF4FD8)
    val Amber = Color(0xFFFFC24B)
    val Red = Color(0xFFFF5370)
    val Violet = Color(0xFF9D7CFF)

    fun forDirection(d: SwipeDirection): Color = when (d) {
        SwipeDirection.RIGHT -> Lime
        SwipeDirection.LEFT -> Red
        SwipeDirection.UP -> Magenta
        SwipeDirection.DOWN -> Amber
    }

    fun forCategory(c: FileCategory): Color = when (c) {
        FileCategory.ADMINISTRATIF -> Cyan
        FileCategory.MEDIAS -> Magenta
        FileCategory.TELECHARGEMENTS -> Amber
        FileCategory.INSTALLATEURS -> Lime
        FileCategory.ARCHIVES -> Violet
        FileCategory.DOUBLONS_LOURDS -> Red
    }
}

private val DarkScheme = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.Background,
    secondary = Neon.Magenta,
    tertiary = Neon.Lime,
    background = Neon.Background,
    onBackground = Neon.TextPrimary,
    surface = Neon.Surface,
    onSurface = Neon.TextPrimary,
    surfaceVariant = Neon.SurfaceHigh,
    onSurfaceVariant = Neon.TextMuted,
    outline = Neon.Outline,
    error = Neon.Red,
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

/** Dark Mode par défaut (choix produit) : pas de thème clair en v1. */
@Composable
fun SortQuestTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography, content = content)
}
