package com.kirdev.pulsedeckpanel.data.model

/** A value the PC could not measure is null and is shown as «—», never as a made-up number. */
data class Load(val load: Double?, val temp: Double?)

data class Ram(val percent: Double?, val used: Long?, val total: Long?)

data class Disk(val name: String, val label: String, val total: Long, val free: Long) {
    val usedFraction: Float get() = if (total > 0) ((total - free).toFloat() / total).coerceIn(0f, 1f) else 0f
}

data class Metrics(
    val cpu: Load,
    val cpuName: String?,
    val gpu: Load,
    val gpuName: String?,
    val gpuMemoryTotal: Long?,
    val gpuMemoryUsed: Long?,
    val ram: Ram,
    val netDown: Double?,
    val netUp: Double?,
    val disks: List<Disk>,
)

enum class PlayStatus { PLAYING, PAUSED }

data class MediaSession(
    val title: String,
    val artist: String,
    val album: String,
    val status: PlayStatus,
    val position: Double,
    val duration: Double,
    val source: String,
    val coverKey: String,
    val canPrev: Boolean,
    val canNext: Boolean,
    /** SystemClock.elapsedRealtime() when [position] was received; the UI moves it forward. */
    val receivedAt: Long,
) {
    /** Playback position now, without asking the PC every second. */
    fun positionAt(nowElapsed: Long): Double {
        if (status != PlayStatus.PLAYING) return position
        val moved = position + (nowElapsed - receivedAt) / 1000.0
        return if (duration > 0) moved.coerceAtMost(duration) else moved
    }
}

/** A PC found on the local network by the UDP discovery. */
data class DiscoveredPc(val name: String, val host: String, val tlsPort: Int?) {
    /** Pre-1.0 PulseDeck builds answer without a TLS port: the panel cannot use them. */
    val supported: Boolean get() = tlsPort != null
}

/** A PC this phone is paired with (identified by its certificate). The token never leaves PairingStore. */
data class PairedPc(val id: String, val name: String, val host: String, val port: Int, val fingerprint: String) {
    companion object {
        fun idOf(fingerprint: String) = fingerprint.replace(":", "").take(16).lowercase()
        fun of(name: String, host: String, port: Int, fingerprint: String) = PairedPc(idOf(fingerprint), name, host, port, fingerprint)
    }
}

/** PC speaker volume as reported with every media update; null without pycaw on the PC. */
data class SystemVolume(val level: Int, val muted: Boolean)

sealed interface ConnectionState {
    data object NotPaired : ConnectionState
    data class Connecting(val pc: PairedPc) : ConnectionState
    /** [usb]: connected through the cable (adb reverse to 127.0.0.1), not over Wi-Fi. */
    data class Connected(val pc: PairedPc, val pcName: String, val usb: Boolean = false) : ConnectionState
    data class Reconnecting(val pc: PairedPc, val inSeconds: Int, val reason: String) : ConnectionState
    /** The PC revoked this phone (or forgot it): a new pairing is required. */
    data object Revoked : ConnectionState
    /** The PC presented a different certificate than the one pinned at pairing. */
    data class CertificateChanged(val pc: PairedPc) : ConnectionState
}
