package com.kirdev.pulsedeckpanel.core.utils

import java.util.Locale
import kotlin.math.roundToInt

object Format {
    const val UNKNOWN = "—"

    fun percent(value: Double?): String = value?.let { "${it.roundToInt()}%" } ?: UNKNOWN

    /** 312 GB, 1.4 TB, 820 MB — compact, for the bottom bar. */
    fun bytes(value: Long?): String {
        if (value == null || value < 0) return UNKNOWN
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1000 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return if (size >= 100 || unit == 0) "${size.roundToInt()} ${units[unit]}"
        else String.format(Locale.US, "%.1f %s", size, units[unit])
    }

    /** Network rate per second: 980 B/s, 1.2 MB/s. */
    fun speed(bytesPerSecond: Double?): String = bytesPerSecond?.let { bytes(it.toLong()) + "/s" } ?: UNKNOWN

    /** 3:28, 1:02:05. */
    fun time(seconds: Double): String {
        val total = seconds.coerceAtLeast(0.0).toInt()
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** Fraction 0..1 for a progress bar, 0 when unknown. */
    fun fraction(percent: Double?): Float = ((percent ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f)
}
