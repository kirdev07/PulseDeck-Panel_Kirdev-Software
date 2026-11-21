package com.kirdev.pulsedeckpanel.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Palette
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one slider of the app: a thin line and a round dot. The dot grows with a soft halo while held,
 * a bubble shows the value over the finger, outside changes glide. [steps] > 0: snaps with a light tick per step.
 * [wave]: the played part is a drifting wave while [playing] (Android's media player), flat on pause.
 * [onChange] follows the finger; [onDone] gets the value when it lifts (or on a tap).
 */
@Composable
fun PulseSlider(
    value: Float,
    onChange: (Float) -> Unit,
    onDone: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    steps: Int = 0,
    track: Dp = 16.dp,
    wave: Boolean = false,
    playing: Boolean = false,
    enabled: Boolean = true,
    bubble: ((Float) -> String)? = null,
    minHeight: Dp = 44.dp,
    motion: Boolean = true,
) {
    var held by remember { mutableStateOf<Float?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var widthPx by remember { mutableStateOf(1f) }
    val haptic = LocalHapticFeedback.current
    val changeNow by rememberUpdatedState(onChange)
    val doneNow by rememberUpdatedState(onDone)
    fun snap(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return if (steps > 0) (c * steps).roundToInt() / steps.toFloat() else c
    }
    val outside by animateFloatAsState(value.coerceIn(0f, 1f), if (motion) tween(260, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)) else tween(0),
        label = "slider")
    val shown = held ?: outside
    val grip by animateFloatAsState(if (pressed) 1f else 0f, tween(160), label = "grip")
    val waveHeight = remember { Animatable(if (wave && playing) 1f else 0f) }
    LaunchedEffect(wave, playing) {
        waveHeight.animateTo(if (wave && playing) 1f else 0f,
            tween(if (playing) 800 else 550, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
    }
    val time = rememberFrameTime(wave && playing && motion)
    val active = if (enabled) Palette.Accent else Palette.Disabled
    val rest = if (enabled) Palette.Track else Palette.Disabled.copy(alpha = 0.12f)
    val density = LocalDensity.current
    val tall = 24.dp

    Box(
        modifier.fillMaxWidth().height(maxOf(minHeight, tall)).onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f, steps)
                if (enabled) setProgress { target -> doneNow(snap(target)); true }
            }
            .pointerInput(enabled, steps) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { doneNow(snap((it.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()))) },
                )
            }
            .pointerInput(enabled, steps) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        pressed = true
                        held = snap((it.x - 8.dp.toPx()) / (size.width - 16.dp.toPx())).also { v -> changeNow(v) }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val v = snap((change.position.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()))
                        if (v != held) {
                            if (steps > 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            held = v
                            changeNow(v)
                        }
                    },
                    onDragEnd = { held?.let(doneNow); held = null; pressed = false },
                    onDragCancel = { held?.let(doneNow); held = null; pressed = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(tall)) {
            val line = (if (track < 10.dp) 3.dp else 4.dp).toPx()
            val thumb = (if (track < 10.dp) 6.dp else 8.dp).toPx() * (1f + 0.25f * grip)
            if (wave) drawWaveSlider(shown, active, rest, line, thumb, grip, waveHeight.value, time.value)
            else drawLineSlider(shown, active, rest, line, thumb, grip)
        }
        val text = bubble?.takeIf { held != null }?.invoke(shown)
        if (text != null) {
            val x = with(density) { (8.dp.toPx() + shown * (widthPx - 16.dp.toPx())).toDp() }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Black,
                modifier = Modifier.offset { IntOffset((x - 22.dp).roundToPx(), -(tall / 2 + 20.dp).roundToPx()) }
                    .clip(RoundedCornerShape(12.dp)).background(Palette.Text).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}


/** A thin line and a round dot; while held the dot grows and gets a soft halo. */
private fun DrawScope.drawLineSlider(v: Float, active: Color, rest: Color, line: Float, thumb: Float, halo: Float) {
    val mid = size.height / 2
    val start = thumb
    val end = size.width - thumb
    val x = start + (end - start) * v
    drawLine(rest, Offset(start, mid), Offset(end, mid), line, StrokeCap.Round)
    if (x > start) drawLine(active, Offset(start, mid), Offset(x, mid), line, StrokeCap.Round)
    if (halo > 0f) drawCircle(active.copy(alpha = 0.18f * halo), thumb * (1f + 1.1f * halo), Offset(x, mid))
    drawCircle(active, thumb, Offset(x, mid))
}

private const val WAVE_LENGTH = 20f      // dp
private const val WAVE_HEIGHT = 2.2f     // dp
private const val WAVE_SPEED = 8f        // dp per second

/** The same line and dot; the played part waves while the track plays (flat on pause). */
private fun DrawScope.drawWaveSlider(v: Float, active: Color, rest: Color, line: Float, thumb: Float, halo: Float, height: Float, timeMs: Float) {
    if (height <= 0.01f) {
        drawLineSlider(v, active, rest, line, thumb, halo)
        return
    }
    val dp = density
    val mid = size.height / 2
    val start = thumb
    val end = size.width - thumb
    val x = start + (end - start) * v
    val length = WAVE_LENGTH * dp
    val amp = WAVE_HEIGHT * dp * height
    val taper = 1.5f * length
    fun amplitude(px: Float) = amp * max(0f, min(min(1f, (x - px) / taper * 2f), (px - start) / (length / 2)))
    drawLine(rest, Offset(x, mid), Offset(end, mid), line, StrokeCap.Round)
    val path = Path()
    val phase = (timeMs / 1000f * WAVE_SPEED) % WAVE_LENGTH
    var px = start - phase * dp - length
    var sign = 1f
    var py = sign * amplitude(px)
    path.moveTo(px, mid + py)
    val half = length / 2
    while (px < x) {
        sign = -sign
        val nx = px + half
        val ny = sign * amplitude(nx)
        val m = px + half / 2
        path.cubicTo(m, mid + py, m, mid + ny, nx, mid + ny)
        px = nx
        py = ny
    }
    if (x > start) clipRect(left = start - line, right = x) { drawPath(path, active, style = Stroke(width = line, cap = StrokeCap.Round)) }
    drawCircle(active, line / 2, Offset(start, mid))
    if (halo > 0f) drawCircle(active.copy(alpha = 0.18f * halo), thumb * (1f + 1.1f * halo), Offset(x, mid))
    drawCircle(active, thumb, Offset(x, mid))
}
