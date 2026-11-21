package com.kirdev.pulsedeckpanel.feature.monitoring

import androidx.compose.runtime.Immutable
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.data.model.Disk
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import kotlin.math.roundToInt

/**
 * One tile as shown. [fraction] null = an empty bar (unknown value), [bar] false = no bar at all
 * (network, cores). Values the PC did not send are «—», never invented.
 */
@Immutable
data class TileView(
    val tile: Tile,
    val name: String,
    val value: String,
    val caption: String,
    val fraction: Float?,
    val bar: Boolean = true,
    val cores: List<Double?> = emptyList(),
)

/** The history series a tile draws as its sparkline (the second one is paler: upload). */
val SERIES: Map<Tile, List<String>> = mapOf(
    Tile.CPU to listOf("cpu"), Tile.GPU to listOf("gpu"), Tile.RAM to listOf("ram"),
    Tile.VRAM to listOf("vram"), Tile.NET to listOf("down", "up"),
)

fun tileView(tile: Tile, m: Metrics?, volume: SystemVolume?, disk: Disk?): TileView {
    val unknown = Format.UNKNOWN
    val fraction = { value: Double? -> value?.let(Format::fraction) }
    if (m == null && tile != Tile.VOLUME) return TileView(tile, tile.short, unknown, "", null, bar = tile !in NO_BAR)
    return when (tile) {
        Tile.CPU -> TileView(tile, "CPU", Format.percent(m!!.cpu.load),
            Names.cpu(m.cpuName).ifBlank { m.cores?.let { "$it потоков" }.orEmpty() }, fraction(m.cpu.load))
        Tile.GPU -> {
            val vram = when {
                m!!.gpuMemoryTotal == null -> null
                m.gpuMemoryUsed != null && m.gpuMemoryUsed > 0 -> "${Format.bytes(m.gpuMemoryUsed)} / ${Format.bytes(m.gpuMemoryTotal)}"
                else -> Format.bytes(m.gpuMemoryTotal)
            }
            TileView(tile, "GPU", Format.percent(m.gpu.load),
                listOfNotNull(Names.gpu(m.gpuName).ifBlank { null }, vram).joinToString(" · "), fraction(m.gpu.load))
        }
        Tile.RAM -> TileView(tile, "RAM", Format.percent(m!!.ram.percent),
            if (m.ram.used != null && m.ram.total != null) "${Format.bytes(m.ram.used)} / ${Format.bytes(m.ram.total)}" else "",
            fraction(m.ram.percent))
        Tile.NET -> TileView(tile, "Сеть", "↓${Format.speed(m!!.netDown)}", "↑ ${Format.speed(m.netUp)}", null, bar = false)
        Tile.DISK -> if (disk == null) TileView(tile, "Диск", unknown, "", null)
            else TileView(tile, if (disk.label.isNotBlank()) "${disk.name} ${disk.label}" else disk.name,
                Format.bytes(disk.free), "свободно из ${Format.bytes(disk.total)}", disk.usedFraction)
        Tile.VRAM -> {
            val total = m!!.gpuMemoryTotal
            val used = m.gpuMemoryUsed
            if (total != null && used != null) TileView(tile, "VRAM", Format.percent(m.vramPercent),
                "${Format.bytes(used)} / ${Format.bytes(total)}", fraction(m.vramPercent))
            else TileView(tile, "VRAM", unknown, "нет данных", null)
        }
        Tile.CORES -> {
            val list = m!!.perCore
            TileView(tile, "Ядра", if (list.isEmpty()) unknown else "${list.size}",
                if (list.isEmpty()) "нет данных" else "макс. ${Format.percent(list.filterNotNull().maxOrNull())}",
                null, bar = false, cores = list)
        }
        Tile.VOLUME -> TileView(tile, "Звук",
            volume?.let { if (it.muted) "без звука" else "${it.level}%" } ?: unknown, "громкость ПК",
            volume?.let { it.level / 100f })
    }
}

private val NO_BAR = setOf(Tile.NET, Tile.CORES)

/** The disk a tile or a warning is about: the chosen one, or the first (none chosen = all in turn). */
fun chosenDisk(metrics: Metrics?, name: String): Disk? =
    metrics?.disks?.let { disks -> disks.firstOrNull { it.name == name } ?: disks.firstOrNull() }

/** Short hardware names that fit a tile: «13th Gen Intel(R) Core(TM) i5-13420H» -> «Intel Core i5-13420H». */
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

/** The last ~3 minutes of each series (90 samples), for the sparklines and the detail graphs. */
@Immutable
data class History(val series: Map<String, List<Double?>> = emptyMap()) {
    operator fun get(key: String): List<Double?> = series[key].orEmpty()

    fun plus(m: Metrics): History {
        val values = mapOf("cpu" to m.cpu.load, "gpu" to m.gpu.load, "ram" to m.ram.percent, "vram" to m.vramPercent,
            "down" to m.netDown, "up" to m.netUp)
        return History(values.mapValues { (key, value) -> (this[key] + value).takeLast(POINTS) })
    }

    companion object {
        const val POINTS = 90
    }
}

/**
 * Warnings («Предупреждения»): a value that stays past its limit for ~10 s raises one notice and
 * makes its tile glow; it is armed again once the value is 5 points back on the safe side.
 */
class AlertMonitor {
    private val count = mutableMapOf<Limit, Int>()
    private val active = mutableSetOf<Limit>()

    val activeLimits: Set<Limit> get() = active.toSet()

    /** Returns the texts of new warnings (empty most of the time). */
    fun check(m: Metrics, prefs: DisplayPrefs, intervalSeconds: Double): List<Pair<Limit, String>> {
        val disk = if (prefs.disk.isBlank()) m.disks.minByOrNull { if (it.total > 0) it.free.toDouble() / it.total else 1.0 }
            else chosenDisk(m, prefs.disk)
        val values = mapOf(
            Limit.CPU to m.cpu.load, Limit.GPU to m.gpu.load, Limit.RAM to m.ram.percent, Limit.VRAM to m.vramPercent,
            Limit.DISK to disk?.takeIf { it.total > 0 }?.let { 100.0 * it.free / it.total },
            Limit.BATTERY to m.battery?.takeIf { !it.plugged }?.percent,
        )
        val needed = kotlin.math.ceil(ALERT_SECONDS / intervalSeconds.coerceAtLeast(1.0)).toInt().coerceAtLeast(2)
        val fresh = mutableListOf<Pair<Limit, String>>()
        for (key in Limit.entries) {
            val limit = prefs.limit(key)
            val value = values[key]
            val bad = prefs.alerts && value != null && (if (key.over) value >= limit else value <= limit)
            val fine = value == null || (if (key.over) value < limit - 5 else value > limit + 5)
            count[key] = if (bad) (count[key] ?: 0) + 1 else 0
            if (key !in active && (count[key] ?: 0) >= needed) {
                active += key
                val v = value!!.roundToInt()
                fresh += key to when (key) {
                    Limit.DISK -> "Мало места: свободно $v% на ${disk?.name ?: "диске"}"
                    Limit.BATTERY -> "Батарея ПК: $v%, подключите зарядку"
                    else -> "${key.title}: $v% (порог $limit%)"
                }
            } else if (key in active && (fine || !prefs.alerts)) {
                active -= key
            }
        }
        return fresh
    }

    fun reset() {
        count.clear()
        active.clear()
    }

    companion object {
        const val ALERT_SECONDS = 10.0

        /** Which tiles glow for a warning. */
        fun tilesOf(limits: Set<Limit>): Set<Tile> = limits.flatMap {
            when (it) {
                Limit.CPU -> listOf(Tile.CPU, Tile.CORES)
                Limit.GPU -> listOf(Tile.GPU)
                Limit.RAM -> listOf(Tile.RAM)
                Limit.VRAM -> listOf(Tile.VRAM)
                Limit.DISK -> listOf(Tile.DISK)
                Limit.BATTERY -> emptyList()
            }
        }.toSet()
    }
}
