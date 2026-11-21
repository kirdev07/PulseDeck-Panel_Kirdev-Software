package com.kirdev.pulsedeckpanel.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
 * The one slider of the app (Material 3 Expressive): a thick track split by an upright handle with
 * small gaps, inner corners 2 dp, a stop dot at the end. The handle thins while held, a bubble shows
 * the value over the finger, outside changes glide. [steps] > 0: snaps with a light tick per step.
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
    val handleW by animateDpAsState(if (pressed) 2.dp else 4.dp, tween(150), label = "handle")
    val waveHeight = remember { Animatable(if (wave && playing) 1f else 0f) }
    LaunchedEffect(wave, playing) {
        waveHeight.animateTo(if (wave && playing) 1f else 0f,
            tween(if (playing) 800 else 550, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
    }
    val time = rememberFrameTime(wave && playing && motion)
    val active = if (enabled) Palette.Accent else Palette.Disabled
    val rest = if (enabled) Palette.Track else Palette.Disabled.copy(alpha = 0.12f)
    val dot = if (enabled) Palette.Accent else Palette.Disabled
    val density = LocalDensity.current
    val tall = if (wave) 24.dp else track + 20.dp

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
                    onTap = { doneNow(snap(it.x / size.width)) },
                )
            }
            .pointerInput(enabled, steps) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        pressed = true
                        held = snap(it.x / size.width).also { v -> changeNow(v) }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val v = snap(change.position.x / size.width)
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
            if (wave) drawWave(shown, active, rest, handleW.toPx(), waveHeight.value, time.value)
            else drawThick(shown, active, rest, dot, track.toPx(), handleW.toPx())
        }
        val text = bubble?.takeIf { held != null }?.invoke(shown)
        if (text != null) {
            val x = with(density) { (shown * widthPx).toDp() }
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Palette.Black,
                modifier = Modifier.offset { IntOffset((x - 22.dp).roundToPx(), -(tall / 2 + 20.dp).roundToPx()) }
                    .clip(RoundedCornerShape(12.dp)).background(Palette.Text).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }
}


/** Track with a gap on each side of the handle; inner corners small, outer corners round. */
private fun DrawScope.drawThick(v: Float, active: Color, rest: Color, dot: Color, track: Float, handle: Float) {
    val gap = 6.dp.toPx()
    val inner = CornerRadius(2.dp.toPx())
    val outer = CornerRadius(track / 2)
    val top = (size.height - track) / 2
    val x = (size.width - handle) * v
    val leftEnd = x - gap
    if (leftEnd > 0f) {
        drawPath(Path().apply {
            addRoundRect(RoundRect(0f, top, max(leftEnd, track / 2), top + track, outer, inner, inner, outer))
        }, active)
    }
    val rightStart = x + handle + gap
    if (rightStart < size.width) {
        drawPath(Path().apply {
            addRoundRect(RoundRect(min(rightStart, size.width - track / 2), top, size.width, top + track, inner, outer, outer, inner))
        }, rest)
        val r = 2.dp.toPx()
        if (size.width - rightStart > track) drawCircle(dot, r, Offset(size.width - track / 2, size.height / 2))
    }
    drawRoundRect(active, Offset(x, (size.height - (track + 20.dp.toPx())) / 2), Size(handle, track + 20.dp.toPx()),
        CornerRadius(handle / 2))
}

private const val WAVE_LENGTH = 20f      // dp
private const val WAVE_HEIGHT = 2.2f     // dp
private const val WAVE_SPEED = 8f        // dp per second
private const val WAVE_STROKE = 4f       // dp

/** Played part as a drifting wave that flattens near the handle; the rest a flat line of the same width. */
private fun DrawScope.drawWave(v: Float, active: Color, rest: Color, handle: Float, height: Float, timeMs: Float) {
    val dp = density
    val stroke = WAVE_STROKE * dp
    val gap = 5 * dp
    val x = (size.width - handle) * v
    val mid = size.height / 2
    val length = WAVE_LENGTH * dp
    val amp = WAVE_HEIGHT * dp * height
    val taper = 1.5f * length
    fun amplitude(px: Float) = amp * max(0f, min(min(1f, (x - gap - px) / taper * 2f), px / (length / 2)))
    val path = Path()
    val phase = (timeMs / 1000f * WAVE_SPEED) % WAVE_LENGTH
    var px = -phase * dp
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
    val s = Stroke(width = stroke, cap = StrokeCap.Round)
    if (x - gap > stroke / 2) clipRect(right = x - gap) { drawPath(path, active, style = s) }
    if (x + handle + gap < size.width - stroke / 2) {
        drawLine(rest, Offset(x + handle + gap + stroke / 2, mid), Offset(size.width - stroke / 2, mid), stroke, StrokeCap.Round)
    }
    drawRoundRect(active, Offset(x, mid - 10 * dp), Size(handle, 20 * dp), CornerRadius(handle / 2))
}

