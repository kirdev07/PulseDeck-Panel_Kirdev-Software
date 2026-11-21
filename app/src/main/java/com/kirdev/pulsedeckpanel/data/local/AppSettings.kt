package com.kirdev.pulsedeckpanel.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Tiles of the bottom row, in their default order (the same set as the web panel). */
enum class Tile(val title: String, val short: String) {
    CPU("Процессор", "CPU"), GPU("Видеокарта", "GPU"), RAM("Память", "RAM"), NET("Сеть", "Сеть"),
    DISK("Диск", "Диск"), VRAM("Видеопамять", "VRAM"), CORES("Ядра процессора", "Ядра"), VOLUME("Громкость ПК", "Звук");

    companion object {
        fun of(name: String): Tile? = entries.firstOrNull { it.name == name }
    }
}

/** Warning thresholds: «over» values warn at or above the limit, the others at or below it. */
enum class Limit(val title: String, val over: Boolean, val default: Int) {
    CPU("Процессор", true, 90), GPU("Видеокарта", true, 90), RAM("Память", true, 90), VRAM("Видеопамять", true, 95),
    DISK("Свободно на диске", false, 10), BATTERY("Батарея ПК", false, 20);

    val range: IntRange get() = if (over) 50..100 else 5..50
}

/**
 * How the display looks and behaves. Everything lives on this phone only. The choices and their
 * names follow the web panel (PulseDeck: web_panel/app.js, DEFAULTS), so both look the same.
 */
data class DisplayPrefs(
    // Theme
    val theme: String = "DARK",                 // DARK / LIGHT (PanelColors)
    val themeByTime: Boolean = false,           // white by day, dark in the evening
    val dayFrom: Int = 7 * 60,                  // minutes after midnight
    val dayTo: Int = 20 * 60,
    val accent: String = "white",
    val bgDark: String = "default",
    val bgLight: String = "default",
    val uiFont: String = "system",
    val coverColors: Boolean = true,
    val coverBackdrop: Boolean = false,
    // Clock
    val use24h: Boolean = true,
    val seconds: Boolean = false,
    val showDate: Boolean = true,
    val showActivity: Boolean = true,
    val clockColor: String = "ink",
    val clockFont: String = "system",
    val clockWeight: String = "thin",
    val clockSize: String = "m",
    // Layout and tiles
    val layout: String = "panel",               // panel / clock / music / monitoring
    val tiles: List<Tile> = listOf(Tile.CPU, Tile.GPU, Tile.RAM, Tile.NET, Tile.DISK),
    val tileOrder: List<Tile> = Tile.entries,
    val disk: String = "",                      // "" = all disks in turn
    val tileRows: String = "wrap",              // wrap / scroll
    val sparks: Boolean = true,
    // Player
    val playerAlways: Boolean = false,
    val showCover: Boolean = true,
    val showProgress: Boolean = true,
    val waveSeek: Boolean = true,
    val nowPlaying: Boolean = true,
    val showVolume: Boolean = true,
    val gestures: Boolean = true,
    // Animations
    val idle: String = "wave",
    val idleSize: String = "normal",            // normal / wide / full
    val motion: Boolean = true,
    val pulse: Boolean = false,
    // Warnings
    val alerts: Boolean = true,
    val notify: Boolean = false,
    val alertOffline: Boolean = true,
    val limits: Map<Limit, Int> = Limit.entries.associateWith { it.default },
    // Screen
    val burnIn: Boolean = true,
    val offlineClock: Boolean = true,
    val intervalSeconds: Double = 1.5,
    val nightMode: Boolean = false,
    val nightStart: Int = 23,
    val nightEnd: Int = 7,
) {
    /** True between [nightStart] and [nightEnd] (the range may cross midnight). */
    fun isNight(hour: Int): Boolean = nightMode && if (nightStart <= nightEnd) hour in nightStart until nightEnd
        else hour >= nightStart || hour < nightEnd

    /** DARK or LIGHT right now: «by time» switches at [dayFrom] / [dayTo]. */
    fun themeAt(minuteOfDay: Int): String {
        if (!themeByTime) return if (theme == "LIGHT") "LIGHT" else "DARK"
        val day = if (dayFrom <= dayTo) minuteOfDay in dayFrom until dayTo else minuteOfDay >= dayFrom || minuteOfDay < dayTo
        return if (day) "LIGHT" else "DARK"
    }

    /** The chosen tiles in the chosen order. */
    val shownTiles: List<Tile> get() = tileOrder.filter { it in tiles }

    fun limit(key: Limit): Int = limits[key] ?: key.default

    fun toJson(): JSONObject = JSONObject().apply {
        put("theme", theme); put("themeByTime", themeByTime); put("dayFrom", dayFrom); put("dayTo", dayTo)
        put("accent", accent); put("bgDark", bgDark); put("bgLight", bgLight); put("uiFont", uiFont)
        put("coverColors", coverColors); put("coverBackdrop", coverBackdrop)
        put("use24h", use24h); put("seconds", seconds); put("showDate", showDate); put("showActivity", showActivity); put("clockColor", clockColor)
        put("clockFont", clockFont); put("clockWeight", clockWeight); put("clockSize", clockSize)
        put("layout", layout); put("tiles", JSONArray(tiles.map { it.name })); put("tileOrder", JSONArray(tileOrder.map { it.name }))
        put("disk", disk); put("tileRows", tileRows); put("sparks", sparks)
        put("playerAlways", playerAlways); put("showCover", showCover); put("showProgress", showProgress)
        put("waveSeek", waveSeek); put("nowPlaying", nowPlaying); put("showVolume", showVolume); put("gestures", gestures)
        put("idle", idle); put("idleSize", idleSize); put("motion", motion); put("pulse", pulse)
        put("alerts", alerts); put("notify", notify); put("alertOffline", alertOffline)
        put("limits", JSONObject().apply { limits.forEach { (k, v) -> put(k.name, v) } })
        put("burnIn", burnIn); put("offlineClock", offlineClock); put("interval", intervalSeconds)
        put("night", nightMode); put("nightStart", nightStart); put("nightEnd", nightEnd)
    }

    /** Clamps values to what the screens can show; unknown names fall back to the defaults. */
    fun cleaned(): DisplayPrefs {
        val order = (tileOrder + Tile.entries).distinct()
        return copy(
            intervalSeconds = intervalSeconds.coerceIn(1.0, 10.0),
            nightStart = nightStart.mod(24), nightEnd = nightEnd.mod(24),
            dayFrom = dayFrom.mod(24 * 60), dayTo = dayTo.mod(24 * 60),
            tileOrder = order, tiles = tiles.distinct().ifEmpty { listOf(Tile.CPU) },
            limits = Limit.entries.associateWith { (limits[it] ?: it.default).coerceIn(it.range) },
        )
    }

    companion object {
        fun fromJson(json: JSONObject): DisplayPrefs {
            val d = DisplayPrefs()
            fun s(key: String, def: String) = json.optString(key, def).ifBlank { def }
            fun b(key: String, def: Boolean) = json.optBoolean(key, def)
            fun i(key: String, def: Int) = json.optInt(key, def)
            fun tiles(key: String, def: List<Tile>): List<Tile> = json.optJSONArray(key)?.let { array ->
                (0 until array.length()).mapNotNull { Tile.of(array.optString(it)) }
            } ?: def
            val limitJson = json.optJSONObject("limits")
            return DisplayPrefs(
                theme = s("theme", d.theme), themeByTime = b("themeByTime", d.themeByTime),
                dayFrom = i("dayFrom", d.dayFrom), dayTo = i("dayTo", d.dayTo),
                accent = s("accent", d.accent), bgDark = s("bgDark", d.bgDark), bgLight = s("bgLight", d.bgLight),
                uiFont = s("uiFont", d.uiFont), coverColors = b("coverColors", d.coverColors),
                coverBackdrop = b("coverBackdrop", d.coverBackdrop),
                use24h = b("use24h", d.use24h), seconds = b("seconds", d.seconds), showDate = b("showDate", d.showDate),
                showActivity = b("showActivity", d.showActivity),
                clockColor = s("clockColor", d.clockColor), clockFont = s("clockFont", d.clockFont),
                clockWeight = s("clockWeight", d.clockWeight), clockSize = s("clockSize", d.clockSize),
                layout = s("layout", d.layout), tiles = tiles("tiles", d.tiles), tileOrder = tiles("tileOrder", d.tileOrder),
                disk = json.optString("disk", ""), tileRows = s("tileRows", d.tileRows), sparks = b("sparks", d.sparks),
                playerAlways = b("playerAlways", d.playerAlways), showCover = b("showCover", d.showCover),
                showProgress = b("showProgress", d.showProgress), waveSeek = b("waveSeek", d.waveSeek),
                nowPlaying = b("nowPlaying", d.nowPlaying), showVolume = b("showVolume", d.showVolume),
                gestures = b("gestures", d.gestures),
                idle = s("idle", d.idle), idleSize = s("idleSize", d.idleSize), motion = b("motion", d.motion),
                pulse = b("pulse", d.pulse),
                alerts = b("alerts", d.alerts), notify = b("notify", d.notify), alertOffline = b("alertOffline", d.alertOffline),
                limits = Limit.entries.associateWith { limitJson?.optInt(it.name, it.default) ?: it.default },
                burnIn = b("burnIn", d.burnIn), offlineClock = b("offlineClock", d.offlineClock),
                intervalSeconds = json.optDouble("interval", d.intervalSeconds).takeIf { !it.isNaN() } ?: d.intervalSeconds,
                nightMode = b("night", d.nightMode), nightStart = i("nightStart", d.nightStart), nightEnd = i("nightEnd", d.nightEnd),
            ).cleaned()
        }
    }
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
        val value = change(_display.value).cleaned()
        prefs.edit().putString(DISPLAY, value.toJson().toString()).apply()
        _display.value = value
    }

    private fun readDisplay(): DisplayPrefs {
        prefs.getString(DISPLAY, null)?.let { saved ->
            runCatching { return DisplayPrefs.fromJson(JSONObject(saved)) }
        }
        // 1.0.x kept a few separate keys: carry them over once.
        val cells = prefs.getStringSet(OLD_CELLS, null)
        return DisplayPrefs(
            use24h = prefs.getBoolean(OLD_24H, true),
            intervalSeconds = prefs.getFloat(OLD_INTERVAL, 1.5f).toDouble(),
            nightMode = prefs.getBoolean(OLD_NIGHT, false),
            nightStart = prefs.getInt(OLD_NIGHT_START, 23),
            nightEnd = prefs.getInt(OLD_NIGHT_END, 7),
            theme = prefs.getString(OLD_THEME, "DARK") ?: "DARK",
            tiles = cells?.mapNotNull { Tile.of(it) }?.sortedBy { it.ordinal } ?: DisplayPrefs().tiles,
        ).cleaned()
    }

    private companion object {
        const val BACKGROUND = "background"
        const val OPEN_ON_UNLOCK = "open_on_unlock"
        const val DISPLAY = "display_v2"
        const val OLD_24H = "use_24h"
        const val OLD_CELLS = "cells"
        const val OLD_INTERVAL = "interval"
        const val OLD_THEME = "theme"
        const val OLD_NIGHT = "night"
        const val OLD_NIGHT_START = "night_start"
        const val OLD_NIGHT_END = "night_end"
    }
}
