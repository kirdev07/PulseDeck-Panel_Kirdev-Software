package com.kirdev.pulsedeckpanel.feature.clock

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Palette
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Current time that changes exactly at minute boundaries: one recomposition per minute. */
@Composable
fun rememberMinuteClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            val current = LocalDateTime.now()
            now = current
            delay(((60 - current.second) * 1000L - current.nano / 1_000_000).coerceAtLeast(200))
        }
    }
    return now
}

/**
 * Large thin time in the KiroX logo style (light system sans, tight tracking) with
 * tabular figures, so «11:11» and «20:08» take the same width and nothing jumps.
 */
@Composable
fun ClockBlock(size: TextUnit, modifier: Modifier = Modifier, use24h: Boolean = true) {
    val now = rememberMinuteClock()
    val locale = Locale.getDefault()
    val date = remember(now.toLocalDate(), locale) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).replaceFirstChar { it.titlecase(locale) }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm")),
            style = TextStyle(
                color = Palette.Text,
                fontSize = size,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Thin,
                letterSpacing = (-0.04f * size.value).sp,
                fontFeatureSettings = "tnum",
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
        )
        Text(date, style = TextStyle(color = Palette.Text2, fontSize = 17.sp, fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp))
    }
}
