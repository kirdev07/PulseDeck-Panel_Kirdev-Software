package com.kirdev.pulsedeckpanel.feature.music

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import com.kirdev.pulsedeckpanel.core.theme.Palette
import kotlin.math.max
import kotlin.math.min

private const val WAVE_LENGTH = 20f      // dp
private const val WAVE_HEIGHT = 1.9f     // dp
private const val WAVE_SPEED = 8f        // dp per second
private const val WAVE_STROKE = 2.5f     // dp
private const val WAVE_TAPER = 1.5f      // wavelengths

/**
 * The seek bar of the Android / One UI media player (SystemUI «SquigglyProgress»), as in the
 * web panel: the played part is a wave of half-wavelength Bézier arcs that drifts slowly and
 * flattens over 1.5 wavelengths around the thumb; the rest is a thin line of the same colour at
 * 30 %. Play: the wave grows in 0.8 s (emphasized decelerate, 60 ms delay); pause: it flattens in 0.55 s.
 */
@Composable
fun SquigglyProgress(fraction: Float, playing: Boolean, motion: Boolean, modifier: Modifier = Modifier) {
    val height = remember { Animatable(if (playing) 1f else 0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            kotlinx.coroutines.delay(60)
            height.animateTo(1f, tween(800, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
        } else {
            height.animateTo(0f, tween(550, easing = CubicBezierEasing(0f, 0f, 0f, 1f)))
        }
    }
    LaunchedEffect(playing, motion) {
        if (!playing || !motion) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last > 0) phase.floatValue = (phase.floatValue + (now - last) / 1e9f * WAVE_SPEED) % WAVE_LENGTH
                last = now
            }
        }
    }
    val accent = Palette.Accent
    val path = remember { Path() }
    Canvas(modifier) {
        val dp = density
        val left = 6 * dp
        val span = size.width - 12 * dp
        val x = fraction.coerceIn(0f, 1f) * span
        val length = WAVE_LENGTH * dp
        val taper = WAVE_TAPER * length
        val amp = WAVE_HEIGHT * dp * height.value
        fun amplitude(px: Float): Float {
            val k = max(0f, min(min(1f, (x + taper / 2 - px) / taper), px / (length / 2)))
            return amp * k
        }
        path.reset()
        var prevX = -phase.floatValue * dp - length / 2
        var sign = 1f
        var prevY = sign * amplitude(prevX)
        path.moveTo(prevX, prevY)
        val half = length / 2
        while (prevX < span) {
            sign = -sign
            val nextX = prevX + half
            val nextY = sign * amplitude(nextX)
            val mid = prevX + half / 2
            path.cubicTo(mid, prevY, mid, nextY, nextX, nextY)
            prevX = nextX
            prevY = nextY
        }
        val stroke = Stroke(width = WAVE_STROKE * dp, cap = StrokeCap.Round)
        translate(left, size.height / 2) {
            clipRect(0f, -size.height, x, size.height) { drawPath(path, accent, style = stroke) }
            clipRect(x, -size.height, span + 1, size.height) { drawPath(path, accent.copy(alpha = 0.3f), style = stroke) }
            drawCircle(accent, WAVE_STROKE * dp / 2, Offset.Zero)                           // round start
            drawRoundRect(accent, Offset(x - 2 * dp, -8 * dp), Size(4 * dp, 16 * dp), CornerRadius(2 * dp))   // the thumb
        }
    }
}

/**
 * The colour of a cover («Цвет акцента из обложки»): the hue that is both common and vivid,
 * made bright enough on a dark screen and dark enough on a white one. Same as the web panel.
 */
fun coverColor(bitmap: Bitmap, dark: Boolean): Color? = runCatching {
    val small = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
    val buckets = HashMap<Int, FloatArray>()          // r, g, b, n, saturation
    var sr = 0f; var sg = 0f; var sb = 0f; var count = 0
    for (y in 0 until 24) for (x in 0 until 24) {
        val p = small.getPixel(x, y)
        val r = (p shr 16 and 255).toFloat(); val g = (p shr 8 and 255).toFloat(); val b = (p and 255).toFloat()
        val hi = maxOf(r, g, b); val lo = minOf(r, g, b)
        val light = (hi + lo) / 510f
        val sat = if (hi > 0) (hi - lo) / hi else 0f
        sr += r; sg += g; sb += b; count++
        if (sat < 0.22f || light < 0.18f || light > 0.92f) continue
        val hue = Math.round(hue(r, g, b) / 24f)
        val bucket = buckets.getOrPut(hue) { FloatArray(5) }
        bucket[0] += r; bucket[1] += g; bucket[2] += b; bucket[3] += 1f; bucket[4] += sat
    }
    if (small !== bitmap) small.recycle()
    val best = buckets.values.maxByOrNull { it[3] * (it[4] / it[3]) }
    val (r, g, b) = if (best != null) Triple(best[0] / best[3], best[1] / best[3], best[2] / best[3])
        else Triple(sr / count, sg / count, sb / count)
    readable(r, g, b, dark)
}.getOrNull()

private fun hue(r: Float, g: Float, b: Float): Float {
    val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val d = hi - lo
    if (d == 0f) return 0f
    val h = when (hi) {
        r -> ((g - b) / d) % 6
        g -> (b - r) / d + 2
        else -> (r - g) / d + 4
    }
    return (h * 60 + 360) % 360
}

private fun readable(r: Float, g: Float, b: Float, dark: Boolean): Color {
    val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
    val k = if (dark) max(1f, 0.55f / max(lum, 0.01f)).coerceAtMost(3f) else min(1f, 0.45f / max(lum, 0.01f))
    fun f(v: Float) = (v * k).coerceIn(0f, 255f) / 255f
    return Color(f(r), f(g), f(b))
}
