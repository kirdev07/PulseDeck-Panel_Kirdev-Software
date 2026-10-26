package com.kirdev.pulsedeckpanel.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons drawn for PulseDeck (24×24 viewBox), the same set as the PC app. No icon font. */
object PanelIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (data in paths) {
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = if (filled) SolidColor(Color.White) else null,
                    stroke = if (filled) null else SolidColor(Color.White),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Settings = icon("settings", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6",
        "M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
    val Back = icon("back", "M14.5 6l-6 6l6 6")
    val Chevron = icon("chevron", "M9.5 6l6 6l-6 6")
    val Monitor = icon("monitor", "M5 4h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M8 20h8M12 16v4")
    val Cpu = icon("cpu", "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M9.5 9.5h5v5h-5z",
        "M9 2.5V6M15 2.5V6M9 18v3.5M15 18v3.5M2.5 9H6M2.5 15H6M18 9h3.5M18 15h3.5")
    val Gpu = icon("gpu", "M4.5 6h15a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-15a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z",
        "M9 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6", "M15 10h3M15 14h3")
    val Ram = icon("ram", "M4 7h16a1.5 1.5 0 0 1 1.5 1.5v7A1.5 1.5 0 0 1 20 17H4a1.5 1.5 0 0 1-1.5-1.5v-7A1.5 1.5 0 0 1 4 7z",
        "M6.5 10.5v3M10.5 10.5v3M14.5 10.5v3M18 10.5v3")
    val Disk = icon("disk", "M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 14h18", "M16.5 17h1")
    val Network = icon("network", "M2.5 9a14 14 0 0 1 19 0", "M5.5 12.5a9.5 9.5 0 0 1 13 0", "M8.7 16a5 5 0 0 1 6.6 0",
        "M12 19.2a0.8 0.8 0 1 0 0 1.6a0.8 0.8 0 1 0 0-1.6")
    val Music = icon("music", "M9 18V5l11-2v13", "M6.5 15.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5",
        "M17.5 13.5a2.5 2.5 0 1 0 0 5a2.5 2.5 0 1 0 0-5")
    val Volume = icon("volume", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11")
    val Play = icon("play", "M8 5v14l11-7z", filled = true)
    val Pause = icon("pause", "M7 5h3.5v14H7zM13.5 5H17v14h-3.5z", filled = true)
    val Next = icon("next", "M5 5v14l10-7z", "M16.5 5h2v14h-2z", filled = true)
    val Prev = icon("prev", "M19 5v14L9 12z", "M5.5 5h2v14h-2z", filled = true)
}
