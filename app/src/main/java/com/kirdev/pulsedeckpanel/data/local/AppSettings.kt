package com.kirdev.pulsedeckpanel.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bottom-bar cells the user can hide. */
enum class Cell(val title: String) { CPU("CPU"), GPU("GPU"), RAM("RAM"), NET("Сеть"), DISK("Диски") }

data class DisplayPrefs(
    val use24h: Boolean = true,
    val cells: Set<Cell> = Cell.entries.toSet(),
    val intervalSeconds: Double = 1.5,
    val nightMode: Boolean = false,
    val nightStart: Int = 23,
    val nightEnd: Int = 7,
    /** «DARK» or «LIGHT» (PanelColors). */
    val theme: String = "DARK",
) {
    /** True between [nightStart] and [nightEnd] (the range may cross midnight). */
    fun isNight(hour: Int): Boolean = nightMode && if (nightStart <= nightEnd) hour in nightStart until nightEnd
        else hour >= nightStart || hour < nightEnd
}

/** Everything on the Settings screen; display prefs are observable so the dashboard updates at once. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Keep the link to the PC (and the media notification) while the app is not on screen. */
    var background: Boolean
        get() = prefs.getBoolean(BACKGROUND, true)
        set(value) = prefs.edit().putBoolean(BACKGROUND, value).apply()

    /** After the phone starts: run in the background and open the display once the phone is unlocked. */
    var openOnUnlock: Boolean
        get() = prefs.getBoolean(OPEN_ON_UNLOCK, false)
        set(value) = prefs.edit().putBoolean(OPEN_ON_UNLOCK, value).apply()

    private val _display = MutableStateFlow(readDisplay())
    val display: StateFlow<DisplayPrefs> = _display.asStateFlow()

    fun updateDisplay(change: (DisplayPrefs) -> DisplayPrefs) {
        val value = change(_display.value).let { it.copy(intervalSeconds = it.intervalSeconds.coerceIn(1.0, 10.0),
            nightStart = it.nightStart.mod(24), nightEnd = it.nightEnd.mod(24)) }
        prefs.edit()
            .putBoolean(USE_24H, value.use24h)
            .putStringSet(CELLS, value.cells.map { it.name }.toSet())
            .putFloat(INTERVAL, value.intervalSeconds.toFloat())
            .putBoolean(NIGHT, value.nightMode)
            .putInt(NIGHT_START, value.nightStart)
            .putInt(NIGHT_END, value.nightEnd)
            .putString(THEME, value.theme)
            .apply()
        _display.value = value
    }

    private fun readDisplay() = DisplayPrefs(
        use24h = prefs.getBoolean(USE_24H, true),
        cells = prefs.getStringSet(CELLS, null)?.mapNotNull { name -> Cell.entries.firstOrNull { it.name == name } }?.toSet()
            ?: Cell.entries.toSet(),
        intervalSeconds = prefs.getFloat(INTERVAL, 1.5f).toDouble(),
        nightMode = prefs.getBoolean(NIGHT, false),
        nightStart = prefs.getInt(NIGHT_START, 23),
        nightEnd = prefs.getInt(NIGHT_END, 7),
        theme = prefs.getString(THEME, "DARK") ?: "DARK",
    )

    private companion object {
        const val BACKGROUND = "background"
        const val OPEN_ON_UNLOCK = "open_on_unlock"
        const val USE_24H = "use_24h"
        const val CELLS = "cells"
        const val INTERVAL = "interval"
        const val THEME = "theme"
        const val NIGHT = "night"
        const val NIGHT_START = "night_start"
        const val NIGHT_END = "night_end"
    }
}
