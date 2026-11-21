package com.kirdev.pulsedeckpanel.feature.monitoring

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.ThinProgress
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.SystemVolume

/** A tap on a tile: a bigger graph of the last 3 minutes and everything the PC sends about it. */
@Composable
fun TileDetail(tile: Tile, metrics: Metrics?, volume: SystemVolume?, history: History, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.Surface)
                .border(1.dp, Palette.Border, RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(tile.title, fontSize = 17.sp, color = Palette.Text)
            Spacer(Modifier.height(4.dp))
            val m = metrics
            if (m == null && tile != Tile.VOLUME) {
                Text("Нет данных: ПК не на связи.", fontSize = 13.sp, color = Palette.Text3)
            } else {
                SERIES[tile]?.let { keys ->
                    Sparkline(history, keys, tile != Tile.NET, Modifier.fillMaxWidth().height(110.dp))
                    Text("последние 3 минуты", fontSize = 11.sp, color = Palette.Text3)
                    Spacer(Modifier.height(6.dp))
                }
                when (tile) {
                    Tile.CPU, Tile.CORES -> {
                        DetailRow("Нагрузка", Format.percent(m!!.cpu.load))
                        DetailRow("Процессор", m.cpuName ?: Format.UNKNOWN)
                        DetailRow("Потоков", m.cores?.toString() ?: Format.UNKNOWN)
                        if (m.perCore.isNotEmpty()) Cores(m.perCore)
                    }
                    Tile.GPU, Tile.VRAM -> {
                        val total = m!!.gpuMemoryTotal
                        val used = m.gpuMemoryUsed
                        DetailRow("Нагрузка", Format.percent(m.gpu.load))
                        DetailRow("Видеокарта", m.gpuName ?: Format.UNKNOWN)
                        DetailRow("Видеопамять", when {
                            used != null && total != null -> "${Format.bytes(used)} из ${Format.bytes(total)}"
                            total != null -> "всего ${Format.bytes(total)}"
                            else -> Format.UNKNOWN
                        })
                    }
                    Tile.RAM -> {
                        val ram = m!!.ram
                        DetailRow("Занято", if (ram.used != null) "${Format.bytes(ram.used)} (${Format.percent(ram.percent)})" else Format.UNKNOWN)
                        DetailRow("Свободно", if (ram.used != null && ram.total != null) Format.bytes(ram.total - ram.used) else Format.UNKNOWN)
                        DetailRow("Всего", Format.bytes(ram.total))
                    }
                    Tile.NET -> {
                        DetailRow("Приём ↓", Format.speed(m!!.netDown))
                        DetailRow("Отдача ↑", Format.speed(m.netUp))
                        Text("Яркая линия — приём, бледная — отдача.", fontSize = 11.sp, color = Palette.Text3)
                    }
                    Tile.DISK -> {
                        if (m!!.disks.isEmpty()) Text("ПК не сообщил о дисках.", fontSize = 13.sp, color = Palette.Text3)
                        for (disk in m.disks) {
                            DetailRow(if (disk.label.isNotBlank()) "${disk.name} ${disk.label}" else disk.name,
                                "${Format.bytes(disk.free)} свободно из ${Format.bytes(disk.total)}")
                            ThinProgress(disk.usedFraction, Modifier.fillMaxWidth().padding(bottom = 6.dp))
                        }
                    }
                    Tile.VOLUME -> {
                        DetailRow("Громкость ПК", volume?.let { if (it.muted) "без звука" else "${it.level}%" } ?: Format.UNKNOWN)
                        Text("Менять громкость — ползунком в карточке плеера.", fontSize = 11.sp, color = Palette.Text3)
                    }
                }
                if (m != null && (tile == Tile.CPU || tile == Tile.RAM)) {
                    m.uptime?.let { DetailRow("ПК работает", Format.uptime(it)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Закрыть", color = Palette.Text) }
            }
        }
    }
}

/** Every logical processor as a column with its number. */
@Composable
private fun Cores(cores: List<Double?>) {
    val accent = Palette.Accent
    val track = Palette.Track
    val label = Palette.Text3
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val gap = 4.dp.toPx()
            val w = ((size.width - gap * (cores.size - 1)) / cores.size).coerceAtLeast(2f)
            cores.forEachIndexed { i, v ->
                val x = i * (w + gap)
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(3.dp.toPx()))
                val h = size.height * ((v ?: 0.0).coerceIn(2.0, 100.0) / 100.0).toFloat()
                drawRoundRect(accent, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            cores.indices.forEach { i ->
                Text("${i + 1}", fontSize = 9.sp, color = label, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}
