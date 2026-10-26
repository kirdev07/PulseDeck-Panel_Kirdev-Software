package com.kirdev.pulsedeckpanel.feature.pulse

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kirdev.pulsedeckpanel.core.theme.Palette
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlin.math.sin

/**
 * KiroX Motion — the mini wave from the KiroX splash screen (kirdev07/KiroX-Client-TG,
 * ui/splash/AnimatedSplashScreen2.kt). Same geometry and timing: 48 steps,
 * y = sin(step · 0.42 − t / 135 ms) · 2.3 dp, 1.15 dp white stroke, 144 × 18 dp.
 *
 * Differences for an always-on display: it loops instead of running for the 3.5 s splash,
 * redraws at ~30 fps (KiroX «light effects» rate) and stands still when the system turned
 * animations off or the phone is in power saving, as KiroX's systemAnimationsEnabled does.
 */
@Composable
fun KiroXMotion(
    modifier: Modifier = Modifier,
    width: Dp = 144.dp,
    height: Dp = 18.dp,
    color: Color = Palette.Text,
) {
    val context = LocalContext.current
    val animate = remember { systemAnimationsEnabled(context) }
    val ms = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        var start = -1L
        while (true) {
            withFrameNanos { now ->                   // the Compose frame clock: always schedules a redraw
                if (start < 0) start = now
                ms.floatValue = (now - start) / 1_000_000f
            }
            delay(FRAME_MS)
        }
    }
    val path = remember { Path() }
    Canvas(modifier.width(width).height(height)) {
        val phase = ms.floatValue / 135f              // read in the draw phase: only this canvas redraws
        val amplitude = 2.3.dp.toPx()
        val centerY = size.height / 2f
        path.reset()
        for (step in 0..STEPS) {
            val x = size.width * step / STEPS
            val y = centerY + sin(step * .42f - phase) * amplitude
            if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = color, style = Stroke(width = 1.15.dp.toPx()))
    }
}

/** From KiroX: no motion in power saving mode or when the animator scale is 0. */
internal fun systemAnimationsEnabled(context: Context): Boolean {
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return !powerSave && scale > 0f
}

private const val STEPS = 48
private const val FRAME_MS = 33L
