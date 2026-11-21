package com.kirdev.pulsedeckpanel.feature.pulse

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What runs between the clock and the tiles when nothing plays («Настройки → Анимации»). «Волна»
 * is KiroX Motion; the others are decorative and show no data. They are the web panel's canvas
 * animations (web_panel/app.js, drawIdle / drawMore) drawn the same way, in dp, in the accent colour.
 */
object Idle {
    /** Key, name, height in dp at the «Обычный» size. */
    val KINDS: List<Triple<String, String, Int>> = listOf(
        Triple("wave", "Волна", 28), Triple("waves", "Волны", 70), Triple("pulse", "Пульс", 80),
        Triple("eq", "Эквалайзер", 90), Triple("breathe", "Дыхание", 120), Triple("orbit", "Орбиты", 120),
        Triple("fireflies", "Светлячки", 110), Triple("ripples", "Рябь", 120), Triple("aurora", "Сияние", 110),
        Triple("starfield", "Звёздное небо", 120), Triple("matrix", "Матрица", 110),
        Triple("spectrum", "Круговой спектр", 150), Triple("helix", "ДНК", 90), Triple("pendulum", "Маятники", 120),
        Triple("rain", "Дождь", 120), Triple("snow", "Снег", 130), Triple("bubbles", "Пузырьки", 130),
        Triple("voice", "Голосовая волна", 70), Triple("galaxy", "Галактика", 140), Triple("grid", "Сетка точек", 120),
        Triple("ring", "Неоновое кольцо", 140), Triple("dots", "Три точки", 60), Triple("infinity", "Бесконечность", 100),
        Triple("heart", "Сердце", 120), Triple("seconds", "Секундное кольцо", 150), Triple("runner", "Бегущие точки", 80),
        Triple("ocean", "Океан", 110), Triple("lissajous", "Лиссажу", 120), Triple("polygon", "Кристалл", 150),
        Triple("confetti", "Конфетти", 130),
    )
    val SIZES = listOf("normal" to "Обычный", "wide" to "Широкий", "full" to "Во весь экран")

    fun height(kind: String): Int = KINDS.firstOrNull { it.first == kind }?.third ?: 0
    fun exists(kind: String): Boolean = KINDS.any { it.first == kind }

    /** Animations that fill their whole box fade out at its edges instead of ending in a hard line. */
    val SOFT_EDGES = setOf("waves", "aurora", "fireflies", "starfield", "matrix", "rain", "bubbles", "grid", "ocean",
        "confetti", "snow", "helix", "voice", "runner")
}

private fun rand(seed: Int): Float {
    val x = sin(seed * 9301.0 + 49297.0) * 233280.0
    return (x - floor(x)).toFloat()
}

private class Fly(val x: Float, val y: Float, val s: Float, val p: Float)
private class Star(val x: Float, val y: Float, val z: Float)
private class Drop(val x: Float, val v: Float, val o: Float)
private class Bubble(val x: Float, val r: Float, val v: Float, val o: Float)
private class Bit(val x: Float, val v: Float, val o: Float, val s: Float, val spin: Float)

private val FLIES = List(22) { Fly(rand(it), rand(it + 50), 0.4f + rand(it + 99), rand(it + 7) * 6.28f) }
private val STARS = List(90) { Star(rand(it) * 2 - 1, rand(it + 200) * 2 - 1, rand(it + 400)) }
private val DROPS = List(26) { Drop(rand(it + 600), 0.5f + rand(it + 700), rand(it + 800)) }
private val BUBBLES = List(24) { Bubble(rand(it + 900), 2 + rand(it + 950) * 7, 0.3f + rand(it + 990), rand(it + 1030)) }
private val CONFETTI = List(46) { Bit(rand(it + 1100), 0.4f + rand(it + 1150), rand(it + 1200), 3 + rand(it + 1250) * 4, rand(it + 1300) * 6) }
private const val GLYPHS = "ﾊﾐﾋｰｳｼﾅﾓﾆｻﾜﾂｵﾘｱﾎﾃﾏｹﾒｴｶｷﾑﾕﾗｾﾈｽﾀﾇﾍ0123456789"
private const val TAU = (PI * 2).toFloat()

/**
 * One idle animation at ~30 fps. [still] (system animations off, power saving, «Плавные переходы»
 * off) draws one frame at t = 0 and stops.
 */
@Composable
fun IdleAnimation(kind: String, color: Color, still: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val animate = remember { systemAnimationsEnabled(context) } && !still
    // Redraws on the display's frames, at most «Частота кадров» times a second (core/ui/Frames.kt).
    // The seconds ring keeps showing real seconds even with animations off.
    val ms = com.kirdev.pulsedeckpanel.core.ui.rememberFrameTime(animate || kind == "seconds")
    // Soft edges need an offscreen layer: skipped in light mode (weak GPUs).
    val soft = kind in Idle.SOFT_EDGES && !com.kirdev.pulsedeckpanel.core.ui.Device.lite
    val glyphPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER } }
    Canvas(modifier.then(if (soft) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)) {
        val d = density
        val w = size.width / d
        val h = size.height / d
        if (w <= 0f || h <= 0f) return@Canvas
        scale(d, d, Offset.Zero) {
            drawIdle(kind, w, h, ms.value, color, glyphPaint)
        }
        if (soft) softEdges()
    }
}

private fun DrawScope.softEdges() {
    val mask = Color.Black
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.18f to mask, 0.82f to mask, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn)
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.2f to mask, 0.8f to mask, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn)
}

/** A soft halo instead of the canvas shadowBlur of the web version. */
private fun DrawScope.glowDot(ink: Color, center: Offset, r: Float, alpha: Float = 1f, blur: Float = 8f) {
    drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = 0.35f * alpha), Color.Transparent), center, r + blur), r + blur, center)
    drawCircle(ink.copy(alpha = alpha), r, center)
}

private fun DrawScope.line(ink: Color, alpha: Float, width: Float, from: Offset, to: Offset) =
    drawLine(ink.copy(alpha = alpha.coerceIn(0f, 1f)), from, to, width, StrokeCap.Round)

private fun DrawScope.stroke(path: Path, ink: Color, width: Float, alpha: Float = 1f) =
    drawPath(path, ink.copy(alpha = alpha.coerceIn(0f, 1f)), style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.drawIdle(kind: String, w: Float, h: Float, t: Float, ink: Color, glyphs: Paint) {
    val mid = h / 2
    val cx = w / 2
    val r0 = min(w, h) / 2
    when (kind) {
        "wave" -> {                     // exactly the panel: 48 steps, sin(i·0.42 − t/135)·2.3, scaled to the width
            val k = w / 144
            val path = Path()
            for (i in 0..48) {
                val x = i / 48f * w
                val y = mid + sin(i * 0.42f - t / 135) * 2.3f * min(k, 2f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(path, ink, 1.15f * min(k, 1.6f))
        }
        "waves" -> for (layer in 0 until 3) {       // three layered sine waves drifting at different speeds
            val path = Path()
            var x = 0f
            while (x <= w) {
                val y = mid + sin(x / (38 + layer * 14) - t / (900 - layer * 220) + layer) * (h * (0.32f - layer * 0.08f)) *
                    sin(x / w * PI.toFloat())
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 3
            }
            stroke(path, ink, 2 - layer * 0.4f, 0.9f - layer * 0.28f)
        }
        "pulse" -> {                    // the logo's heartbeat running across, with a fading trail
            val period = 2400f
            val head = (t % period) / period * (w + 80) - 40
            val path = Path()
            var x = 0f
            while (x <= w) {
                val dx = x - head
                var y = mid
                if (dx > -26 && dx < 0) {
                    val u = -dx / 26
                    y = mid - when {
                        u < 0.2f -> sin(u / 0.2f * PI.toFloat()) * h * 0.08f
                        u < 0.55f -> sin((u - 0.2f) / 0.35f * PI.toFloat()) * h * 0.42f
                        else -> -sin((u - 0.55f) / 0.45f * PI.toFloat()) * h * 0.16f
                    }
                }
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 2
            }
            drawPath(path, Brush.horizontalGradient(listOf(ink.copy(alpha = 0f), ink), head - w * 0.8f, head + 4),
                style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (head in 0f..w) glowDot(ink, Offset(head, mid), 3f)
        }
        "eq" -> {                       // rounded bars with a soft reflection
            val bars = (w / 22).roundToInt().coerceIn(8, 32)
            val slot = w / bars
            val bw = min(12f, slot * 0.55f)
            val base = h * 0.72f
            for (i in 0 until bars) {
                val v = 0.18f + 0.82f * abs(sin(t / (480 + i * 47) + i * 1.3f) * sin(t / 1900 + i * 0.4f))
                val bh = v * base
                val x = i * slot + (slot - bw) / 2
                drawRoundRect(ink, Offset(x, base - bh), Size(bw, bh), CornerRadius(bw / 2))
                drawRoundRect(ink.copy(alpha = 0.18f), Offset(x, base + 3), Size(bw, bh * 0.35f), CornerRadius(bw / 2))
            }
        }
        "breathe" -> {                  // concentric rings, 4 s in / 4 s out
            val k = 0.5f + 0.5f * sin(t / 1273)
            val r = (min(h / 2, w / 2) - 4) * (0.55f + 0.45f * k)
            for (ring in 3 downTo 0) {
                drawCircle(ink.copy(alpha = ((0.12f + 0.5f * k) * (1 - ring * 0.22f)).coerceIn(0f, 1f)), r * (1 - ring * 0.18f),
                    Offset(cx, mid), style = Stroke(1.6f))
            }
            drawCircle(ink.copy(alpha = 0.18f * k), r * 0.42f, Offset(cx, mid))
        }
        "orbit" -> {                    // three planets on tilted ellipses around a glowing sun
            val u = min(w / 2, h * 1.45f) * 0.92f
            glowDot(ink, Offset(cx, mid), max(4f, u * 0.05f), blur = 14f)
            listOf(Triple(0.95f, 3200f, 3f), Triple(0.68f, 5200f, 2.4f), Triple(0.4f, 2100f, 1.8f)).forEachIndexed { i, (f, period, size) ->
                val rx = u * f
                val ry = min(rx * 0.34f, h / 2 - 4)
                val a = t / period * TAU + i * 2
                drawOval(ink.copy(alpha = 0.22f), Offset(cx - rx, mid - ry), Size(rx * 2, ry * 2), style = Stroke(1f))
                drawCircle(ink, size, Offset(cx + cos(a) * rx, mid + sin(a) * ry))
            }
        }
        "fireflies" -> for (f in FLIES) {     // soft dots drifting and glowing on and off
            val x = ((f.x + sin(t / 4000 * f.s + f.p) * 0.08f + t / 90000 * f.s) % 1f) * w
            val y = (f.y + cos(t / 3000 * f.s + f.p) * 0.12f) * h
            glowDot(ink, Offset(x, y), 1.6f + f.s, 0.25f + 0.75f * max(0f, sin(t / 900 * f.s + f.p)), 6f)
        }
        "ripples" -> {                  // rings spreading from the centre, like a drop in water
            val u0 = min(w * 0.48f, (h / 2 - 2) / 0.36f)
            for (i in 0 until 4) {
                val u = (t / 3200 + i / 4f) % 1f
                drawOval(ink.copy(alpha = (1 - u) * 0.8f), Offset(cx - u * u0, mid - u * u0 * 0.36f),
                    Size(u * u0 * 2, u * u0 * 0.72f), style = Stroke(1.5f))
            }
        }
        "aurora" -> for (band in 0 until 4) {     // northern lights: soft ribbons
            val path = Path().apply { moveTo(0f, h) }
            var x = 0f
            while (x <= w) {
                path.lineTo(x, mid + sin(x / (70 + band * 25) + t / (2600 + band * 700) + band) * h * 0.28f +
                    sin(x / 23 + t / 1300) * 3 - band * 6)
                x += 6
            }
            path.lineTo(w, h); path.close()
            drawPath(path, Brush.verticalGradient(0f to ink.copy(alpha = 0f), 0.5f to ink.copy(alpha = 0.22f - band * 0.04f),
                1f to ink.copy(alpha = 0f), startY = 0f, endY = h))
        }
        "starfield" -> for (star in STARS) {      // flying through stars
            val z = 1 - ((star.z + t / 9000) % 1f)
            val x = cx + star.x / z * w * 0.12f
            val y = mid + star.y / z * h * 0.25f
            if (x < 0 || x > w || y < 0 || y > h) continue
            drawCircle(ink.copy(alpha = min(1f, (1 - z) * 1.4f)), max(0.4f, (1 - z) * 2.2f), Offset(x, y))
        }
        "matrix" -> {                   // falling glyph columns
            val size = 13f
            glyphs.textSize = size
            DROPS.forEachIndexed { i, drop ->
                val x = drop.x * w
                val head = ((t / 1000 * drop.v * 40 + drop.o * h * 3) % (h + 90)) - 30
                for (k in 0 until 7) {
                    val y = head - k * size
                    if (y < 0 || y > h) continue
                    glyphs.color = ink.copy(alpha = if (k == 0) 1f else (1 - k / 7f) * 0.6f).toArgb()
                    val ch = GLYPHS[(i * 7 + k + (t / 180).toInt()).mod(GLYPHS.length)]
                    drawContext.canvas.nativeCanvas.drawText(ch.toString(), x, y, glyphs)
                }
            }
        }
        "spectrum" -> {                 // radial bars around a circle
            val rr = r0 * 0.45f
            val n = (rr * 1.6f).roundToInt().coerceIn(36, 90)
            val width = min(2.4f, max(1.2f, TAU * rr / n * 0.45f))
            for (i in 0 until n) {
                val a = i.toFloat() / n * TAU + t / 6000
                val v = 0.15f + 0.85f * abs(sin(t / 420 + i * 0.55f) * sin(t / 1700 + i * 0.13f))
                val r1 = rr + 2 + v * r0 * 0.45f
                line(ink, 0.45f + 0.55f * v, width, Offset(cx + cos(a) * rr, mid + sin(a) * rr), Offset(cx + cos(a) * r1, mid + sin(a) * r1))
            }
        }
        "helix" -> {                    // DNA: two strands with rungs, turning
            var x = 0f
            while (x <= w) {
                val a = x / 34 + t / 700
                val y1 = mid + sin(a) * h * 0.36f
                val y2 = mid - sin(a) * h * 0.36f
                val front = cos(a) > 0
                line(ink, 0.25f, 1f, Offset(x, y1), Offset(x, y2))
                drawCircle(ink.copy(alpha = if (front) 1f else 0.45f), if (front) 3f else 2f, Offset(x, y1))
                drawCircle(ink.copy(alpha = if (front) 0.45f else 1f), if (front) 2f else 3f, Offset(x, y2))
                x += 10
            }
        }
        "rain" -> DROPS.forEachIndexed { i, d ->     // thin slanted rain
            val y = ((t / 1000 * (160 + d.v * 140) + d.o * h * 4) % (h + 30)) - 15
            val x = (d.x * w + y * 0.18f + i * 7) % w
            line(ink, 0.35f + 0.5f * d.v, 1.2f, Offset(x, y), Offset(x - 3, y - 12))
        }
        "bubbles" -> for (b in BUBBLES) {           // bubbles rising with a wobble
            val u = (t / 1000 * b.v * 0.12f + b.o) % 1f
            val x = b.x * w + sin(t / 700 + b.o * 20) * 6
            val y = h + b.r - u * (h + b.r * 2)
            val alpha = 0.25f + 0.6f * sin(u * PI.toFloat())
            drawCircle(ink.copy(alpha = alpha), b.r, Offset(x, y), style = Stroke(1.2f))
            drawCircle(ink.copy(alpha = alpha), max(0.6f, b.r * 0.18f), Offset(x - b.r * 0.35f, y - b.r * 0.35f))
        }
        "voice" -> {                    // mirrored waveform, like a voice message
            val n = (w / 6).toInt()
            for (i in 0 until n) {
                val x = i * 6f + 2
                val env = sin(i.toFloat() / n * PI.toFloat())
                val v = (0.15f + 0.85f * abs(sin(i * 0.9f + t / 260) * sin(i * 0.23f - t / 900))) * env
                drawRect(ink.copy(alpha = 0.5f + 0.5f * env), Offset(x, mid - v * h * 0.45f), Size(3f, max(2f, v * h * 0.9f)))
            }
        }
        "galaxy" -> for (i in 0 until 260) {        // a rotating spiral of dots
            val arm = i % 2
            val k = i / 260f
            val a = k * PI.toFloat() * 5 + arm * PI.toFloat() + t / 3000
            val r = k * r0 * 0.95f
            drawCircle(ink.copy(alpha = 0.25f + 0.75f * (1 - k)), 0.6f + (1 - k) * 1.6f,
                Offset(cx + cos(a) * r * (if (w / h > 2) 1.8f else 1f), mid + sin(a) * r * 0.6f))
        }
        "grid" -> {                     // a grid of dots with a travelling ripple
            val step = 16f
            var x = step / 2
            while (x < w) {
                var y = step / 2
                while (y < h) {
                    val dist = hypot(x - cx, (y - mid) * 1.6f)
                    val v = 0.5f + 0.5f * sin(dist / 18 - t / 400)
                    drawCircle(ink.copy(alpha = 0.15f + 0.75f * v), 0.8f + v * 1.6f, Offset(x, y))
                    y += step
                }
                x += step
            }
        }
        "dots" -> for (i in 0 until 3) {           // three dots bouncing, the «typing» loader
            val v = max(0f, sin(t / 260 - i * 0.7f))
            val r = (h * 0.11f).coerceIn(4f, 10f)
            drawCircle(ink.copy(alpha = 0.4f + 0.6f * v), r, Offset(cx + (i - 1) * r * 3.6f, mid - v * h * 0.22f))
        }
        "infinity" -> {                 // a glowing tracer running along ∞
            val ax = min(w * 0.42f, h * 1.15f)
            val ay = min(ax * 0.42f, h * 0.4f)
            val path = Path()
            for (i in 0..120) {
                val s = i / 120f * TAU
                val x = cx + ax * sin(s)
                val y = mid + ay * sin(s) * cos(s) * 2
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(path, ink, 1f, 0.2f)
            for (k in 0 until 18) {
                val s = t / 900 - k * 0.06f
                glowDot(ink, Offset(cx + ax * sin(s), mid + ay * sin(s) * cos(s) * 2), 3.2f - k * 0.15f, 1 - k / 18f,
                    if (k == 0) 10f else 3f)
            }
        }
        "heart" -> {                    // a heart beating twice… then resting
            val beat = t % 1200
            val k = when {
                beat < 160 -> 1 + 0.12f * sin(beat / 160 * PI.toFloat())
                beat < 360 -> 1 + 0.07f * sin((beat - 160) / 200 * PI.toFloat())
                else -> 1f
            }
            val s = min(w * 0.7f / 32, h * 0.78f / 30) * k
            val path = Path()
            for (i in 0..100) {
                val a = i / 100f * TAU
                val x = 16 * sin(a) * sin(a) * sin(a)
                val y = -(13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a))
                if (i == 0) path.moveTo(cx + x * s, mid + h * 0.04f + y * s) else path.lineTo(cx + x * s, mid + h * 0.04f + y * s)
            }
            path.close()
            drawPath(path, ink.copy(alpha = (0.15f + (k - 1) * 3).coerceIn(0f, 1f)))
            stroke(path, ink, 2f)
        }
        "seconds" -> {                  // the real seconds of the phone's clock as a ring
            val now = LocalTime.now()
            val sec = now.second + now.nano / 1e9f
            val r = r0 * 0.8f
            drawCircle(ink.copy(alpha = 0.15f), r, Offset(cx, mid), style = Stroke(3f))
            drawArc(ink, -90f, sec / 60 * 360, false, Offset(cx - r, mid - r), Size(r * 2, r * 2), style = Stroke(3f, cap = StrokeCap.Round))
            for (i in 0 until 12) {
                val a = i / 12f * TAU
                drawCircle(ink.copy(alpha = 0.35f), 1.2f, Offset(cx + cos(a) * (r - 9), mid + sin(a) * (r - 9)))
            }
            glyphs.textSize = (r * 0.5f).roundToInt().toFloat()
            glyphs.color = ink.toArgb()
            val text = "%02d".format(sec.toInt())
            val bounds = android.graphics.Rect().also { glyphs.getTextBounds(text, 0, text.length, it) }
            drawContext.canvas.nativeCanvas.drawText(text, cx, mid + bounds.height() / 2f, glyphs)
        }
        "ocean" -> for (layer in 0 until 3) {      // filled waves, one behind another
            val path = Path().apply { moveTo(0f, h) }
            var x = 0f
            while (x <= w) {
                path.lineTo(x, h * (0.45f + layer * 0.15f) + sin(x / (60 - layer * 12) + t / (1400 - layer * 300) + layer * 2) * h * 0.08f)
                x += 4
            }
            path.lineTo(w, h); path.close()
            drawPath(path, ink.copy(alpha = 0.22f + layer * 0.22f))
        }
        "lissajous" -> {                // a slowly morphing Lissajous figure
            val delta = t / 2500
            val rx = min(w * 0.42f, h * 0.42f * 1.35f)
            val ry = rx / 1.35f
            val path = Path()
            for (i in 0..400) {
                val s = i / 400f * TAU
                val x = cx + sin(3 * s + delta) * rx
                val y = mid + sin(2 * s) * ry
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(path, ink, 1.4f, 0.85f)
        }
        "polygon" -> for (ring in 0 until 4) {     // a crystal: nested hexagons turning against each other
            val r = r0 * (0.85f - ring * 0.18f)
            val rot = (if (ring % 2 == 1) -1 else 1) * t / (3000 + ring * 900)
            val path = Path()
            for (i in 0..6) {
                val a = rot + i / 6f * TAU
                val x = cx + cos(a) * r
                val y = mid + sin(a) * r
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            stroke(path, ink, 1.3f, 0.9f - ring * 0.18f)
        }
        "pendulum" -> {                 // a pendulum wave: each ball a little slower than the next
            val n = (w / 26).roundToInt().coerceIn(9, 22)
            val slot = w / n
            val r = (slot * 0.22f).coerceIn(3f, 7f)
            for (i in 0 until n) {
                val x = i * slot + slot / 2
                val y = mid + sin(t / 1000 * (2 + i * 0.12f)) * (h / 2 - r - 3)
                line(ink, 0.25f, 1f, Offset(x, mid), Offset(x, y))
                drawCircle(ink, r, Offset(x, y))
            }
        }
        "snow" -> for (f in FLIES) {               // soft flakes drifting down
            val y = ((t / 1000 * (8 + f.s * 14)) + f.y * h * 3) % (h + 10) - 5
            val x = ((f.x * w + sin(t / 2200 * f.s + f.p) * 14) % w + w) % w
            drawCircle(ink.copy(alpha = 0.4f + 0.5f * f.s / 1.4f), 1 + f.s * 1.6f, Offset(x, y))
        }
        "ring" -> {                     // a neon arc spinning with a fading tail
            val r = r0 * 0.78f
            val a = t / 520
            val width = max(2f, r0 * 0.06f)
            val box = Rect(cx - r, mid - r, cx + r, mid + r)
            drawCircle(ink.copy(alpha = 0.12f), r, Offset(cx, mid), style = Stroke(width))
            for (k in 0 until 24) {
                val end = a - k * 0.09f
                drawArc(ink.copy(alpha = (1 - k / 24f) * 0.9f), Math.toDegrees((end - 0.09f).toDouble()).toFloat(),
                    Math.toDegrees(0.09).toFloat(), false, box.topLeft, box.size, style = Stroke(width, cap = StrokeCap.Round))
            }
        }
        "runner" -> {                   // a row of dots making a travelling sine wave
            val n = (w / 12).roundToInt().coerceIn(16, 48)
            val slot = w / n
            for (i in 0 until n) {
                val v = sin(i * 0.42f - t / 260)
                drawCircle(ink.copy(alpha = 0.35f + 0.65f * (v + 1) / 2), (slot * 0.22f).coerceIn(1.6f, 4f) * (1 + v * 0.25f),
                    Offset(i * slot + slot / 2, mid + v * h * 0.28f))
            }
        }
        "confetti" -> for (p in CONFETTI) {        // slow confetti in the accent colour
            val y = ((t / 1000 * (25 + p.v * 30)) + p.o * h * 3) % (h + 20) - 10
            val x = p.x * w + sin(t / 900 + p.o * 10) * 10
            withTransform({ translate(x, y); rotate(Math.toDegrees((t / 600 * p.spin).toDouble()).toFloat(), Offset.Zero) }) {
                drawRect(ink.copy(alpha = 0.45f + 0.55f * p.v), Offset(-p.s / 2, -p.s / 4), Size(p.s, p.s / 2))
            }
        }
    }
}
