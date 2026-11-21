package com.kirdev.pulsedeckpanel.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Container = Color(0xFF4F378B)
private val Ink = Color(0xFFEADDFF)
private val Accent = Color(0xFFD0BCFF)
private val EMPHASIZED = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * Launch animation over the first screen: the logo «PC and phone» in its purple circle (as on the
 * system splash screen) — a pulse runs across the monitor, the phone bounces — then the logo grows
 * and fades while the app shows through. About 0.8 s, once per cold start; a tap skips it.
 */
@Composable
fun LaunchIntro(enabled: Boolean, background: Color, onDone: () -> Unit) {
    if (!enabled) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    val pulse = remember { Animatable(0f) }
    val bounce = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    var skipped by remember { mutableStateOf(false) }
    LaunchedEffect(skipped) {
        if (skipped) {
            leave.animateTo(1f, tween(160, easing = LinearEasing))
            onDone()
            return@LaunchedEffect
        }
        launch { bounce.animateTo(1f, tween(420, delayMillis = 120, easing = LinearEasing)) }
        pulse.animateTo(1f, tween(480, easing = EMPHASIZED))
        leave.animateTo(1f, tween(320, easing = EMPHASIZED))
        onDone()
    }
    val logo = remember { LogoPaths() }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = 1f - leave.value }
            .background(background)
            .pointerInput(Unit) { detectTapGestures { skipped = true } },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(160.dp).graphicsLayer {
            val k = 1f + 0.18f * leave.value
            scaleX = k
            scaleY = k
        }) {
            // the circle, then the logo on the 48-unit grid of the icon
            drawCircle(Container)
            val unit = size.minDimension / 48f
            scale(unit, unit, Offset.Zero) {
                val stroke = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(logo.monitor, Ink, style = stroke)
                drawLine(Ink, Offset(16f, 35f), Offset(26f, 35f), 3f, StrokeCap.Round)
                // the pulse is drawn again from left to right
                val drawn = logo.pulsePart(pulse.value)
                drawPath(drawn, Ink, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // the phone hops once: up and back
                val hop = kotlin.math.sin(bounce.value * Math.PI).toFloat() * -2.2f
                translate(0f, hop) {
                    drawRoundRect(Accent, Offset(29f, 19f), Size(12f, 20f), CornerRadius(3.5f))
                }
            }
        }
    }
}

private class LogoPaths {
    val monitor = Path().apply {
        addRoundRect(androidx.compose.ui.geometry.RoundRect(8f, 12f, 34f, 30f, CornerRadius(4f)))
    }
    private val pulse = Path().apply {
        moveTo(12f, 22f); lineTo(16f, 22f); lineTo(18f, 18f); lineTo(21f, 26f); lineTo(23f, 22f); lineTo(26f, 22f)
    }
    private val measure = PathMeasure().apply { setPath(pulse, false) }
    private val part = Path()

    fun pulsePart(fraction: Float): Path {
        part.reset()
        measure.getSegment(0f, measure.length * fraction.coerceIn(0f, 1f), part, true)
        return part
    }
}
