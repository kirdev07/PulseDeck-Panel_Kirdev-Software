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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs

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

/** A named choice with its colour, as the swatches of the web panel. */
data class Swatch(val key: String, val title: String, val color: Color)

/** The choices of «Оформление» and «Часы» (web_panel/app.js and app.css use the same values). */
object Choices {
    /** «white» is not a colour of its own: it is the theme's ink (black in the white theme). */
    val ACCENTS = listOf(
        Swatch("white", "Как текст", Color.Unspecified), Swatch("blue", "Синий", Color(0xFF4C8DFF)),
        Swatch("green", "Зелёный", Color(0xFF34C77B)), Swatch("purple", "Фиолетовый", Color(0xFFA679FF)),
        Swatch("orange", "Оранжевый", Color(0xFFFF9F43)), Swatch("red", "Красный", Color(0xFFFF5C5C)),
        Swatch("pink", "Розовый", Color(0xFFFF6FB5)),
    )
    val BACKGROUNDS_DARK = listOf(
        Swatch("default", "Чёрный", Color(0xFF000000)), Swatch("graphite", "Графит", Color(0xFF121417)),
        Swatch("midnight", "Ночь", Color(0xFF0B1020)), Swatch("forest", "Лес", Color(0xFF0A1712)),
        Swatch("plum", "Слива", Color(0xFF170D1C)), Swatch("wine", "Вино", Color(0xFF1C0A10)),
        Swatch("coffee", "Кофе", Color(0xFF17110C)),
    )
    val BACKGROUNDS_LIGHT = listOf(
        Swatch("default", "Белый", Color(0xFFFFFFFF)), Swatch("cream", "Крем", Color(0xFFFBF6EC)),
        Swatch("mint", "Мята", Color(0xFFEDF8F1)), Swatch("sky", "Небо", Color(0xFFEDF3FF)),
        Swatch("lavender", "Лаванда", Color(0xFFF3EFFF)), Swatch("rose", "Роза", Color(0xFFFFF0F3)),
        Swatch("sand", "Песок", Color(0xFFF6EFE3)),
    )
    /** «ink» = the theme's text, «accent» = the accent; the rest are fixed colours. */
    val CLOCK_COLORS = listOf(
        Swatch("ink", "Как текст", Color.Unspecified), Swatch("accent", "Как акцент", Color.Unspecified),
        Swatch("white", "Белый", Color(0xFFFFFFFF)), Swatch("blue", "Голубой", Color(0xFF7AA7FF)),
        Swatch("green", "Зелёный", Color(0xFF5FD99A)), Swatch("purple", "Сиреневый", Color(0xFFB896FF)),
        Swatch("orange", "Оранжевый", Color(0xFFFFB066)), Swatch("pink", "Розовый", Color(0xFFFF8CC6)),
        Swatch("gold", "Золотой", Color(0xFFFFD166)), Swatch("red", "Красный", Color(0xFFFF7A7A)),
    )

    private fun device(name: String) = FontFamily(
        listOf(FontWeight.Thin, FontWeight.Light, FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
            .map { Font(DeviceFontFamilyName(name), it) },
    )

    /**
     * Fonts the phone itself has (nothing is downloaded or bundled). The web panel's «Округлый»,
     * «Геометрический» and «Элегантный» have no font of their own on Android, so they are not offered.
     */
    val CLOCK_FONTS: List<Pair<String, Pair<String, FontFamily>>> = listOf(
        "system" to ("Системный" to FontFamily.SansSerif),
        "condensed" to ("Узкий" to device("sans-serif-condensed")),
        "serif" to ("С засечками" to FontFamily.Serif),
        "mono" to ("Моноширинный" to FontFamily.Monospace),
        "typewriter" to ("Машинка" to device("serif-monospace")),
        "hand" to ("Рукописный" to FontFamily.Cursive),
        "marker" to ("Маркер" to device("casual")),
    )
    val UI_FONTS: List<Pair<String, Pair<String, FontFamily>>> = listOf(
        "system" to ("Системный" to FontFamily.SansSerif),
        "condensed" to ("Узкий" to device("sans-serif-condensed")),
        "serif" to ("С засечками" to FontFamily.Serif),
        "mono" to ("Моноширинный" to FontFamily.Monospace),
    )

    fun clockFont(key: String): FontFamily = CLOCK_FONTS.firstOrNull { it.first == key }?.second?.second ?: FontFamily.SansSerif
    fun uiFont(key: String): FontFamily = UI_FONTS.firstOrNull { it.first == key }?.second?.second ?: FontFamily.SansSerif
}

/**
 * Backed by Compose state: a change in Settings recolours the open screen at once.
 * [cover] is the colour taken from the playing track's cover («Цвет акцента из обложки»).
 */
object Palette {
    var theme by mutableStateOf(PanelColors.DARK)
    var backgroundKey by mutableStateOf("default")
    var accentKey by mutableStateOf("white")
    var clockKey by mutableStateOf("ink")
    var uiFontKey by mutableStateOf("system")
    var cover by mutableStateOf<Color?>(null)

    /** A custom background: the surfaces are mixed from it (6/10/14/16 % towards the ink, as the web panel). */
    private val custom: Color?
        get() = (if (theme.dark) Choices.BACKGROUNDS_DARK else Choices.BACKGROUNDS_LIGHT)
            .firstOrNull { it.key == backgroundKey && it.key != "default" }?.color
    private fun mixed(fallback: Color, dark: Float, light: Float): Color {
        val bg = custom ?: return fallback
        return if (theme.dark) lerp(bg, Color.White, dark) else lerp(bg, Color.Black, light)
    }

    val Black get() = custom ?: theme.background
    val Surface get() = mixed(theme.surface, .06f, .04f)
    val Surface2 get() = mixed(theme.surface2, .10f, .07f)
    val Border get() = mixed(theme.border, .14f, .10f)
    val Track get() = mixed(theme.track, .16f, .12f)
    val Text get() = theme.text
    val Text2 get() = theme.text2
    val Text3 get() = theme.text3
    val Disabled get() = theme.disabled
    val Warn = Color(0xFFFF5C5C)

    /** Bars, the seek wave, the play ring, sparklines: the accent, or the cover's colour while a track plays. */
    val Accent: Color get() = cover ?: accentOf(accentKey)
    /** The ring of the play button: the accent, but softer while the accent is the ink. */
    val AccentRing: Color get() = if (cover == null && accentKey == "white") Text2 else Accent

    fun accentOf(key: String): Color =
        Choices.ACCENTS.firstOrNull { it.key == key }?.color?.takeIf { it != Color.Unspecified } ?: Text

    val Clock: Color get() = when (clockKey) {
        "ink" -> Text
        "accent" -> Accent
        else -> Choices.CLOCK_COLORS.firstOrNull { it.key == clockKey }?.color ?: Text
    }

    val UiFont: FontFamily get() = Choices.uiFont(uiFontKey)

    /** Applies the saved choices; [minuteOfDay] picks the theme when it follows the time of day. */
    fun apply(prefs: DisplayPrefs, minuteOfDay: Int) {
        theme = if (prefs.themeAt(minuteOfDay) == "LIGHT") PanelColors.LIGHT else PanelColors.DARK
        backgroundKey = if (theme.dark) prefs.bgDark else prefs.bgLight
        accentKey = prefs.accent
        clockKey = prefs.clockColor
        uiFontKey = prefs.uiFont
        if (!prefs.coverColors) cover = null
    }
}

/** Monospace digits keep numbers from jumping while they change. */
val Numbers get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Palette.Text)

@Composable
fun PanelTheme(content: @Composable () -> Unit) {
    val t = Palette.theme
    val bg = Palette.Black
    val colors = if (t.dark) darkColorScheme(
        primary = t.text, onPrimary = bg, secondary = t.text2, onSecondary = bg,
        background = bg, onBackground = t.text, surface = bg, onSurface = t.text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = t.text2, outline = Palette.Border, error = t.text, onError = bg,
    ) else lightColorScheme(
        primary = t.text, onPrimary = bg, secondary = t.text2, onSecondary = bg,
        background = bg, onBackground = t.text, surface = bg, onSurface = t.text,
        surfaceVariant = Palette.Surface2, onSurfaceVariant = t.text2, outline = Palette.Border, error = t.text, onError = bg,
    )
    val font = Palette.UiFont
    val base = Typography()
    val type = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = font),
        displayMedium = base.displayMedium.copy(fontFamily = font),
        displaySmall = base.displaySmall.copy(fontFamily = font),
        headlineLarge = base.headlineLarge.copy(fontFamily = font),
        headlineMedium = base.headlineMedium.copy(fontFamily = font),
        headlineSmall = base.headlineSmall.copy(fontFamily = font),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = t.text, fontFamily = font),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = t.text, fontFamily = font),
        titleSmall = base.titleSmall.copy(fontFamily = font),
        bodyLarge = base.bodyLarge.copy(fontFamily = font),
        bodyMedium = TextStyle(fontSize = 14.sp, color = t.text, fontFamily = font),
        bodySmall = TextStyle(fontSize = 12.sp, color = t.text2, fontFamily = font),
        labelLarge = base.labelLarge.copy(fontFamily = font),
        labelMedium = base.labelMedium.copy(fontFamily = font),
        labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, color = t.text3,
            fontFamily = font),
    )
    MaterialTheme(colorScheme = colors, typography = type, content = content)
}
