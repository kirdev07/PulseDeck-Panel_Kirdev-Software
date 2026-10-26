package com.kirdev.pulsedeckpanel.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Colour sets. Token names follow the dark original: [Palette.Black] is the screen background
 * and [Palette.Text] the main ink, whatever their actual colour in the light theme.
 */
enum class PanelColors(
    val title: String, val dark: Boolean,
    val background: Color, val surface: Color, val surface2: Color, val border: Color, val track: Color,
    val text: Color, val text2: Color, val text3: Color, val disabled: Color,
) {
    /** KiroX Fusion «AMOLED Black» (kirdev07/KiroX-Client-TG, ui/theme/FusionTheme.kt). */
    DARK("Тёмная", true, Color(0xFF000000), Color(0xFF101010), Color(0xFF191919), Color(0xFF242424), Color(0xFF292929),
        Color(0xFFFFFFFF), Color(0xFFAAAAAA), Color(0xFF888888), Color(0xFF454545)),
    /** The same monochrome design on white: for a lit room or a white stand. */
    LIGHT("Белая", false, Color(0xFFFFFFFF), Color(0xFFF4F4F5), Color(0xFFEAEAEC), Color(0xFFE0E0E3), Color(0xFFDDDDE0),
        Color(0xFF0A0A0A), Color(0xFF55555A), Color(0xFF77777C), Color(0xFFBDBDC2)),
}

/** Backed by Compose state: switching the theme in Settings recolours the open screen at once. */
object Palette {
    var theme by mutableStateOf(PanelColors.DARK)

    val Black get() = theme.background
    val Surface get() = theme.surface
    val Surface2 get() = theme.surface2
    val Border get() = theme.border
    val Track get() = theme.track
    val Text get() = theme.text
    val Text2 get() = theme.text2
    val Text3 get() = theme.text3
    val Disabled get() = theme.disabled
}

/** Monospace digits keep numbers from jumping while they change. */
val Numbers get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.Text)

@Composable
fun PanelTheme(content: @Composable () -> Unit) {
    val t = Palette.theme
    val colors = if (t.dark) darkColorScheme(
        primary = t.text, onPrimary = t.background, secondary = t.text2, onSecondary = t.background,
        background = t.background, onBackground = t.text, surface = t.background, onSurface = t.text,
        surfaceVariant = t.surface2, onSurfaceVariant = t.text2, outline = t.border, error = t.text, onError = t.background,
    ) else lightColorScheme(
        primary = t.text, onPrimary = t.background, secondary = t.text2, onSecondary = t.background,
        background = t.background, onBackground = t.text, surface = t.background, onSurface = t.text,
        surfaceVariant = t.surface2, onSurfaceVariant = t.text2, outline = t.border, error = t.text, onError = t.background,
    )
    val type = Typography(
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = t.text),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.text),
        bodyMedium = TextStyle(fontSize = 14.sp, color = t.text),
        bodySmall = TextStyle(fontSize = 12.sp, color = t.text2),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, color = t.text3),
    )
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
