package com.kirdev.pulsedeckpanel.feature.monitoring

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Numbers
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.ThinProgress
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.model.Metrics
import kotlinx.coroutines.delay

/** One cell of the bottom bar. [fraction] null = no bar (unknown value or nothing to fill). */
@Immutable
data class MetricCell(val icon: ImageVector, val name: String, val value: String, val caption: String, val fraction: Float?)

@Immutable
data class BarState(val cells: List<MetricCell>, val disks: List<MetricCell>)

/** Real metrics -> bar cells. Disks are the PC's actual volumes; they take turns in the last cell. */
fun barState(metrics: Metrics?): BarState {
    val unknown = Format.UNKNOWN
    if (metrics == null) {
        return BarState(listOf(
            MetricCell(PanelIcons.Cpu, "CPU", unknown, "", null),
            MetricCell(PanelIcons.Gpu, "GPU", unknown, "", null),
            MetricCell(PanelIcons.Ram, "RAM", unknown, "", null),
            MetricCell(PanelIcons.Network, "Сеть", unknown, "", null),
        ), emptyList())
    }
    val fraction = { value: Double? -> value?.let(Format::fraction) }
    val vram = when {
        metrics.gpuMemoryTotal == null -> null
        metrics.gpuMemoryUsed != null && metrics.gpuMemoryUsed > 0 ->
            "${Format.bytes(metrics.gpuMemoryUsed)} / ${Format.bytes(metrics.gpuMemoryTotal)}"
        else -> Format.bytes(metrics.gpuMemoryTotal)
    }
    val ram = metrics.ram
    return BarState(
        cells = listOf(
            MetricCell(PanelIcons.Cpu, "CPU", Format.percent(metrics.cpu.load), Names.cpu(metrics.cpuName),
                fraction(metrics.cpu.load)),
            MetricCell(PanelIcons.Gpu, "GPU", Format.percent(metrics.gpu.load),
                listOfNotNull(Names.gpu(metrics.gpuName).ifBlank { null }, vram).joinToString(" · "),
                fraction(metrics.gpu.load)),
            MetricCell(PanelIcons.Ram, "RAM", Format.percent(ram.percent),
                if (ram.used != null && ram.total != null) "${Format.bytes(ram.used)} / ${Format.bytes(ram.total)}" else "",
                fraction(ram.percent)),
            MetricCell(PanelIcons.Network, "Сеть", "↓${Format.speed(metrics.netDown)}", "↑ ${Format.speed(metrics.netUp)}", null),
        ),
        disks = metrics.disks.map { disk ->
            MetricCell(PanelIcons.Disk, if (disk.label.isNotBlank()) "${disk.name} ${disk.label}" else disk.name,
                Format.bytes(disk.free), "свободно из ${Format.bytes(disk.total)}", disk.usedFraction)
        },
    )
}

/** Short hardware names that fit a cell: «13th Gen Intel(R) Core(TM) i5-13420H» -> «Intel Core i5-13420H». */
object Names {
    fun cpu(name: String?): String = name.orEmpty()
        .replace(Regex("""\(R\)|\(TM\)|®|™"""), "")
        .replace(Regex("""\b\d+(st|nd|rd|th) Gen\b"""), "")
        .replace(Regex("""\b(CPU|Processor)\b"""), "")
        .replace(Regex("""@.*$"""), "")
        .replace(Regex("""\s+"""), " ").trim()

    fun gpu(name: String?): String = name.orEmpty()
        .replace(Regex("""\(R\)|\(TM\)"""), "")
        .replace(Regex("""\bNVIDIA GeForce\b|\bAMD Radeon\b|\bIntel\b|\bGPU\b|\bLaptop\b"""), "")
        .replace(Regex("""\b\d+\s?GB\b"""), "")
        .replace(Regex("""\s+"""), " ").trim()
}

@Composable
fun MetricsBar(state: BarState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        state.cells.forEachIndexed { index, cell ->
            if (index > 0) Divider()
            Cell(cell, Modifier.weight(1f))
        }
        if (state.disks.isNotEmpty()) {
            Divider()
            DiskCell(state.disks, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(30.dp).background(Palette.Border))

/** Several volumes take turns every 5 s; a tap shows the next one. Dots tell which one it is. */
@Composable
private fun DiskCell(disks: List<MetricCell>, modifier: Modifier) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(disks.size) {
        while (disks.size > 1) {
            delay(5_000)
            index = (index + 1) % disks.size
        }
    }
    val shown = disks[index.coerceIn(0, disks.lastIndex)]
    Column(modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        index = (index + 1) % disks.size
    }) {
        AnimatedContent(shown, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "disk") {
            Cell(it, Modifier)
        }
        if (disks.size > 1) {
            Row(Modifier.padding(start = 28.dp, top = 4.dp)) {
                disks.indices.forEach { i ->
                    Canvas(Modifier.padding(end = 4.dp).size(4.dp)) {
                        drawCircle(if (i == index) Palette.Text else Palette.Disabled)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(cell: MetricCell, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(cell.icon, contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The label always fits whole; a long value (network speed) yields instead.
                Text(cell.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Palette.Text2, maxLines = 1)
                Spacer(Modifier.width(6.dp))
                Text(cell.value, style = Numbers, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
            }
            Text(cell.caption.ifBlank { " " }, fontSize = 9.sp, color = Palette.Text3, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            if (cell.fraction != null) ThinProgress(cell.fraction, Modifier.fillMaxWidth()) else Spacer(Modifier.height(3.dp))
        }
    }
}
