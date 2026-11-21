package com.kirdev.pulsedeckpanel.feature.monitoring

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Numbers
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.ThinProgress
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import kotlinx.coroutines.delay

fun iconOf(tile: Tile): ImageVector = when (tile) {
    Tile.CPU, Tile.CORES -> PanelIcons.Cpu
    Tile.GPU, Tile.VRAM -> PanelIcons.Gpu
    Tile.RAM -> PanelIcons.Ram
    Tile.NET -> PanelIcons.Network
    Tile.DISK -> PanelIcons.Disk
    Tile.VOLUME -> PanelIcons.Volume
}

/**
 * The tiles chosen in «Настройки → Плитки». [cards] = the «Мониторинг» layout: big cards that
 * fill the space; otherwise the panel's bottom row (a second row or a swipe when they do not fit).
 */
@Composable
fun Tiles(
    prefs: DisplayPrefs,
    metrics: Metrics?,
    volume: SystemVolume?,
    history: History,
    glowing: Set<Tile>,
    onOpen: (Tile) -> Unit,
    modifier: Modifier = Modifier,
    cards: Boolean = false,
) {
    val tiles = prefs.shownTiles
    if (tiles.isEmpty()) return
    val disks = metrics?.disks.orEmpty()
    val rotate = prefs.disk.isBlank() && disks.size > 1
    @Composable
    fun one(tile: Tile, cellModifier: Modifier) {
        if (tile == Tile.DISK && rotate) {
            RotatingDisk(disks.size, cellModifier) { index ->
                TileCell(tileView(tile, metrics, volume, disks[index]), history, prefs.sparks, tile in glowing, cards,
                    dots = disks.size to index, onOpen = onOpen, modifier = Modifier)
            }
        } else {
            TileCell(tileView(tile, metrics, volume, chosenDisk(metrics, prefs.disk)), history, prefs.sparks,
                tile in glowing, cards, onOpen = onOpen, modifier = cellModifier)
        }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = ((maxWidth.value + 24) / (if (cards) 170f else 150f)).toInt().coerceAtLeast(1)
        when {
            cards -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in tiles.chunked(perRow)) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { one(it, Modifier.weight(1f).fillMaxHeight()) }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            prefs.tileRows == "scroll" && tiles.size > perRow ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    tiles.forEachIndexed { index, tile ->
                        if (index > 0) Divider()
                        one(tile, Modifier.width(150.dp))
                    }
                }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val rows = if (tiles.size > perRow) tiles.chunked((tiles.size + 1) / 2) else listOf(tiles)
                for (row in rows) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        row.forEachIndexed { index, tile ->
                            if (index > 0) Divider()
                            one(tile, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(30.dp).background(Palette.Border))

/** Several volumes take turns every 5 s; a tap on the dots shows the next one. */
@Composable
private fun RotatingDisk(count: Int, modifier: Modifier, content: @Composable (Int) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(count) {
        while (count > 1) {
            delay(5_000)
            index = (index + 1) % count
        }
    }
    Box(modifier) {
        AnimatedContent(index.coerceIn(0, count - 1), transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "disk") { content(it) }
    }
}

@Composable
private fun TileCell(
    view: TileView,
    history: History,
    sparks: Boolean,
    alert: Boolean,
    card: Boolean,
    onOpen: (Tile) -> Unit,
    modifier: Modifier,
    dots: Pair<Int, Int>? = null,
) {
    val glow = if (alert) {
        val pulse by rememberInfiniteTransition(label = "warn").animateFloat(0.12f, 0.28f,
            infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "warn-pulse")
        pulse
    } else 0f
    val shape = RoundedCornerShape(if (card) 16.dp else 10.dp)
    val base = modifier.clip(shape)
        .then(if (card) Modifier.background(Palette.Surface).border(1.dp, Palette.Border, shape) else Modifier)
        .then(if (alert) Modifier.background(Palette.Warn.copy(alpha = glow)) else Modifier)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen(view.tile) }
        .padding(horizontal = if (card) 14.dp else 4.dp, vertical = if (card) 12.dp else 3.dp)
    val ink = if (alert) Palette.Warn else Palette.Text
    val series = SERIES[view.tile]
    Row(base, verticalAlignment = if (card) Alignment.Top else Alignment.CenterVertically) {
        Icon(iconOf(view.tile), contentDescription = null, tint = if (alert) Palette.Warn else Palette.Text2,
            modifier = Modifier.size(if (card) 24.dp else 20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).then(if (card) Modifier.fillMaxHeight() else Modifier)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The label always fits whole; a long value (network speed) yields instead.
                Text(view.name, fontSize = if (card) 15.sp else 11.sp, fontWeight = FontWeight.Medium, color = Palette.Text2,
                    maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(view.value, style = Numbers.copy(color = ink, fontSize = if (card) 15.sp else 11.sp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text(view.caption.ifBlank { " " }, fontSize = if (card) 11.sp else 9.sp, color = Palette.Text3, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(if (card) 6.dp else 3.dp))
            if (view.bar) ThinProgress(view.fraction ?: 0f, Modifier.fillMaxWidth(), if (alert) Palette.Warn else Palette.Accent)
            if (view.cores.isNotEmpty()) CoreStrip(view.cores, Modifier.fillMaxWidth().height(if (card) 40.dp else 14.dp))
            // Graphs only in the big cards of «Мониторинг»; the bottom row of the panel stays clean.
            if (card && sparks && series != null) {
                Spacer(Modifier.height(4.dp))
                Sparkline(history, series, view.tile != Tile.NET, Modifier.fillMaxWidth().weight(1f), fill = true)
            }
            dots?.let { (count, index) ->
                Row(Modifier.padding(top = 4.dp)) {
                    repeat(count) { i ->
                        Canvas(Modifier.padding(end = 4.dp).size(4.dp)) { drawCircle(if (i == index) Palette.Text else Palette.Disabled) }
                    }
                }
            }
        }
    }
}

/** One thin column per logical processor, as tall as its load. */
@Composable
fun CoreStrip(cores: List<Double?>, modifier: Modifier) {
    val accent = Palette.Accent
    Canvas(modifier) {
        if (cores.isEmpty()) return@Canvas
        val gap = 1.dp.toPx()
        val w = ((size.width - gap * (cores.size - 1)) / cores.size).coerceAtLeast(1f)
        cores.forEachIndexed { i, v ->
            val h = size.height * ((v ?: 0.0).coerceIn(8.0, 100.0) / 100.0).toFloat()
            drawRoundRect(accent.copy(alpha = 0.85f), Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(1.dp.toPx()))
        }
    }
}

/**
 * A line graph of one or two series (the web panel's drawGraph): percents on 0..100, speeds on
 * their own maximum; the newest sample is at the right edge.
 */
@Composable
fun Sparkline(history: History, keys: List<String>, percent: Boolean, modifier: Modifier, fill: Boolean = true) {
    val ink = Palette.Accent
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val top = if (percent) 100.0 else (keys.flatMap { history[it].filterNotNull() }.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        keys.forEachIndexed { index, key ->
            val data = history[key]
            if (data.size < 2) return@forEachIndexed
            val step = w / (History.POINTS - 1)
            val start = w - (data.size - 1) * step
            val path = Path()
            var first: Float? = null
            var lastX = start
            data.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val x = start + i * step
                val y = h - 1 - (v.coerceAtMost(top) / top).toFloat() * (h - 3)
                if (first == null) { path.moveTo(x, y); first = x } else path.lineTo(x, y)
                lastX = x
            }
            val begin = first ?: return@forEachIndexed
            drawPath(path, ink.copy(alpha = if (index == 0) 1f else 0.5f),
                style = Stroke(width = (if (index == 0) 1.4f else 1f) * density))
            if (fill && index == 0) {
                path.lineTo(lastX, h)
                path.lineTo(begin, h)
                path.close()
                drawPath(path, ink.copy(alpha = 0.12f))
            }
        }
    }
}

/** A row of the detail window: label on the left, value on the right. */
@Composable
fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Palette.Text2, modifier = Modifier.weight(1f))
        Text(value, style = Numbers.copy(fontSize = 13.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
