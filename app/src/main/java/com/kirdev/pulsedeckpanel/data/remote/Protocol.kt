package com.kirdev.pulsedeckpanel.data.remote

import com.kirdev.pulsedeckpanel.data.model.Disk
import com.kirdev.pulsedeckpanel.data.model.Load
import com.kirdev.pulsedeckpanel.data.model.MediaSession
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import com.kirdev.pulsedeckpanel.data.model.Ram
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import org.json.JSONException
import org.json.JSONObject

/** Wire format of /api/v1/display (docs/PANEL_PROTOCOL.md in the PulseDeck repository). */
object Protocol {
    const val VERSION = 1
    const val CLOSE_REVOKED = 4001
    val COMMANDS = setOf("toggle", "next", "prev", "seek", "volume")

    sealed interface Message {
        data class Hello(val protocol: Int, val pcName: String) : Message
        data class MetricsUpdate(val metrics: Metrics) : Message
        data class MediaUpdate(val session: MediaSession?, val volume: SystemVolume? = null) : Message
        data class Error(val code: String, val text: String) : Message
        data object Pong : Message
        /** Unknown types are ignored, so the PC can add messages without breaking old panels. */
        data object Unknown : Message
    }

    fun parse(text: String, nowElapsed: Long): Message = try {
        val json = JSONObject(text)
        when (json.optString("type")) {
            "hello" -> Message.Hello(json.optInt("protocol", 0), json.optString("name"))
            "metrics" -> Message.MetricsUpdate(metrics(json))
            "media" -> Message.MediaUpdate(json.optJSONObject("session")?.let { media(it, nowElapsed) },
                json.optJSONObject("system_volume")?.let { SystemVolume(it.optInt("level"), it.optBoolean("muted")) })
            "error" -> Message.Error(json.optString("code"), json.optString("message"))
            "pong" -> Message.Pong
            else -> Message.Unknown
        }
    } catch (_: JSONException) {
        Message.Unknown
    }

    /** Background: ask the PC for media only, no metrics (saves its CPU and the phone's battery). */
    fun subscribe(metrics: Boolean, interval: Double? = null): String = JSONObject().put("type", "subscribe")
        .put("metrics", metrics).apply { if (interval != null) put("interval", interval) }.toString()

    fun command(action: String, value: Number? = null): String {
        require(action in COMMANDS) { "Unsupported command: $action" }
        return JSONObject().put("type", "command").put("action", action)
            .apply { if (value != null) put("value", value) }.toString()
    }

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.stringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun load(json: JSONObject?) = Load(json?.doubleOrNull("load"), json?.doubleOrNull("temp"))

    private fun metrics(json: JSONObject): Metrics {
        val ram = json.optJSONObject("ram")
        val disks = json.optJSONArray("disks")
        val cpu = json.optJSONObject("cpu")
        val gpu = json.optJSONObject("gpu")
        val net = json.optJSONObject("net")
        return Metrics(
            cpu = load(cpu),
            cpuName = cpu?.stringOrNull("name"),
            gpu = load(gpu),
            gpuName = gpu?.stringOrNull("name"),
            gpuMemoryTotal = gpu?.longOrNull("memory_total")?.takeIf { it > 0 },
            gpuMemoryUsed = gpu?.longOrNull("memory_used"),
            ram = Ram(ram?.doubleOrNull("percent"), ram?.longOrNull("used"), ram?.longOrNull("total")),
            netDown = net?.doubleOrNull("down"),
            netUp = net?.doubleOrNull("up"),
            disks = (0 until (disks?.length() ?: 0)).mapNotNull { index ->
                disks?.optJSONObject(index)?.let {
                    Disk(it.optString("name"), it.optString("label"), it.optLong("total"), it.optLong("free"))
                }
            }.filter { it.total > 0 },
        )
    }

    private fun media(json: JSONObject, nowElapsed: Long): MediaSession? {
        val status = when (json.optString("status")) {
            "playing" -> PlayStatus.PLAYING
            "paused" -> PlayStatus.PAUSED
            else -> return null
        }
        return MediaSession(
            title = json.optString("title"),
            artist = json.optString("artist"),
            album = json.optString("album"),
            status = status,
            position = json.optDouble("position", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            duration = json.optDouble("duration", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            source = json.optString("source"),
            coverKey = json.optString("cover"),
            canPrev = json.optBoolean("can_prev", true),
            canNext = json.optBoolean("can_next", true),
            receivedAt = nowElapsed,
        )
    }
}
