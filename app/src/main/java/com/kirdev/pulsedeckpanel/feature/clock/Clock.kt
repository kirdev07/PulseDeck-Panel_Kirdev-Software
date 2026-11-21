package com.kirdev.pulsedeckpanel.feature.clock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Choices
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Current time that changes exactly at minute boundaries (one recomposition per minute), or at
 * second boundaries when [seconds] is on.
 */
@Composable
fun rememberMinuteClock(seconds: Boolean = false): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(seconds) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            val ms = current.nano / 1_000_000
            delay(if (seconds) (1000L - ms).coerceAtLeast(50) else ((60 - current.second) * 1000L - ms).coerceAtLeast(200))
        }
    }
    return now
}

/** «Тонкие / Обычные / Жирные» of «Настройки → Часы». Script-like fonts have no thin cut. */
fun clockWeight(prefs: DisplayPrefs): FontWeight = when (prefs.clockWeight) {
    "regular" -> FontWeight.Light
    "bold" -> FontWeight.SemiBold
    else -> if (prefs.clockFont in setOf("hand", "marker", "typewriter")) FontWeight.Normal else FontWeight.Thin
}

/** Base size of «S / M / L» (the web panel's --clock: 96 / 128 / 168). */
fun clockSize(prefs: DisplayPrefs): TextUnit = when (prefs.clockSize) {
    "s" -> 96.sp
    "l" -> 168.sp
    else -> 128.sp
}

/**
 * Large time in the KiroX logo style (light system sans, tight tracking) with tabular figures,
 * so «11:11» and «20:08» take the same width. Font, weight, colour, seconds and date come from
 * «Настройки → Часы»; [showDate] lets a layout hide the date.
 */
@Composable
fun ClockBlock(
    prefs: DisplayPrefs,
    size: TextUnit,
    modifier: Modifier = Modifier,
    showDate: Boolean = prefs.showDate,
    weight: FontWeight = clockWeight(prefs),
    dateSize: TextUnit = 17.sp,
) {
    val now = rememberMinuteClock(prefs.seconds)
    val locale = Locale.getDefault()
    val date = remember(now.toLocalDate(), locale) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    val font = Choices.clockFont(prefs.clockFont)
    val tight = if (prefs.clockFont == "mono" || prefs.clockFont == "typewriter") -0.06f else -0.04f
    val time = now.format(DateTimeFormatter.ofPattern(if (prefs.use24h) "HH:mm" else "h:mm"))
    val suffix = if (prefs.use24h) "" else if (now.hour < 12) " AM" else " PM"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                time + suffix,
                style = TextStyle(
                    color = Palette.Clock,
                    fontSize = size,
                    fontFamily = font,
                    fontWeight = weight,
                    letterSpacing = (tight * size.value).sp,
                    fontFeatureSettings = "tnum",
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
            if (prefs.seconds) {
                Text("%02d".format(now.second), modifier = Modifier.padding(start = (size.value * 0.06f).dp, top = (size.value * 0.1f).dp),
                    style = TextStyle(color = Palette.Text2, fontSize = (size.value * 0.32f).sp, fontFamily = font,
                        fontWeight = weight, fontFeatureSettings = "tnum",
                        platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
        if (showDate) {
            Text(date, style = TextStyle(color = Palette.Text2, fontSize = dateSize, fontWeight = FontWeight.Light,
                letterSpacing = 0.5.sp, fontFamily = Palette.UiFont))
        }
    }
}
