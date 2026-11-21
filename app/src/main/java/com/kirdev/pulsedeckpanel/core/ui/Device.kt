package com.kirdev.pulsedeckpanel.core.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * How strong this phone is, and «Облегчённый режим» (Настройки → Экран): on a weak phone
 * (Android's low-RAM flag, under 3 GB of memory or 4 cores and fewer) the display keeps its
 * look but drops what costs the most — animations at 30 frames, no blurred cover, no soft
 * edges, no pulsing cover or dancing bars, no fades between screens.
 */
object Device {
    /** Effective light mode for the screens (Compose state: a change applies at once). */
    var lite by mutableStateOf(false)
        private set

    @Volatile var weak = false
        private set
    var memoryGb = 0.0
        private set
    var cores = 0
        private set

    fun probe(context: Context) {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }
        memoryGb = info.totalMem / 1_073_741_824.0
        cores = Runtime.getRuntime().availableProcessors()
        weak = manager.isLowRamDevice || memoryGb < 3.0 || cores <= 4
    }

    /** «auto» = on for a weak phone, «on», «off». */
    fun apply(choice: String) {
        lite = when (choice) { "on" -> true; "off" -> false; else -> weak }
    }

    /** What the settings say about this phone. */
    fun summary(): String = "%.1f ГБ памяти, ядер: %d — %s".format(memoryGb, cores, if (weak) "слабый телефон" else "мощности хватает")
}

/** The display of this window on every Android version (Activity.display exists from Android 11). */
@Suppress("DEPRECATION")
fun Activity.currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
