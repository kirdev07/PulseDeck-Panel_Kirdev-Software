package com.kirdev.pulsedeckpanel.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import com.kirdev.pulsedeckpanel.data.local.PairingStore
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.model.MediaSession
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.PairedPc
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.net.ssl.SSLHandshakeException

/**
 * The live link to the paired PC (/api/v1/display). Runs only between [start] and [stop]
 * (the activity's onStart/onStop): no background service, nothing while the screen is off.
 */
class PanelConnection(private val store: PairingStore, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ConnectionState>(
        store.load()?.let { ConnectionState.Connecting(it) } ?: ConnectionState.NotPaired)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow<Metrics?>(null)
    val metrics: StateFlow<Metrics?> = _metrics.asStateFlow()

    private val _media = MutableStateFlow<MediaSession?>(null)
    val media: StateFlow<MediaSession?> = _media.asStateFlow()

    /** Cover of the current track: (key, image); null while unknown or when the source has none. */
    private val _cover = MutableStateFlow<Pair<String, Bitmap>?>(null)
    val cover: StateFlow<Pair<String, Bitmap>?> = _cover.asStateFlow()

    private val _volume = MutableStateFlow<SystemVolume?>(null)
    val volume: StateFlow<SystemVolume?> = _volume.asStateFlow()

    private var job: Job? = null
    @Volatile private var interval = 1.5
    @Volatile private var socket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var coverJob: Job? = null
    /** The current session got as far as «hello»; a later loss restarts the backoff. */
    @Volatile private var sessionConnected = false

    private enum class End { LOST, REVOKED, CERTIFICATE, STOPPED, USB }

    /** Who needs the link: the visible screen («ui») and/or the background service («service»). */
    private val holders = mutableSetOf<String>()
    /** The dashboard is on screen: ask for metrics. In the background only media is needed. */
    @Volatile private var uiVisible = false

    @Synchronized
    fun hold(holder: String) {
        holders += holder
        start()
    }

    @Synchronized
    fun release(holder: String) {
        holders -= holder
        if (holders.isEmpty()) stop()
    }

    /** Seconds between metric updates (Settings); applied to the live session at once. */
    fun setInterval(seconds: Double) {
        interval = seconds
        socket?.send(Protocol.subscribe(metrics = uiVisible, interval = seconds))
    }

    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        socket?.send(Protocol.subscribe(metrics = visible, interval = interval))
        if (!visible) _metrics.value = null      // not refreshed in the background: never show stale numbers
    }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        socket?.close(1000, "panel closed")
        socket = null
        coverJob?.cancel()
        client?.let { it.dispatcher.executorService.shutdown(); it.connectionPool.evictAll() }
        client = null
    }

    /** Playback command; returns false when there is no live connection. */
    fun send(action: String, value: Number? = null): Boolean = socket?.send(Protocol.command(action, value)) ?: false

    /** After a new pairing or when the user leaves the connection screen. */
    fun restart() {
        stop()
        _state.value = store.load()?.let { ConnectionState.Connecting(it) } ?: ConnectionState.NotPaired
        start()
    }

    private suspend fun run() {
        var failures = 0
        while (scope.isActive && job?.isActive == true) {
            val pc = store.load()
            val token = store.token()
            if (pc == null || token == null) {
                _state.value = ConnectionState.NotPaired
                return
            }
            if (failures == 0) _state.value = ConnectionState.Connecting(pc)
            sessionConnected = false
            // USB first: with the cable and adb, PulseDeck maps the phone's 127.0.0.1 to the PC.
            val usb = usbReachable(pc.port)
            when (connectOnce(pc, token, if (usb) LOOPBACK else pc.host, usb)) {
                End.REVOKED -> {
                    store.clear()
                    clearData()
                    _state.value = ConnectionState.Revoked
                    return
                }
                End.CERTIFICATE -> {
                    _state.value = ConnectionState.CertificateChanged(pc)
                    return
                }
                End.STOPPED -> return
                End.USB -> failures = 0                // cable plugged in: reconnect through it right away
                End.LOST -> {
                    clearData()         // old numbers on screen would pass for live ones
                    failures = if (sessionConnected) 1 else failures + 1
                    if (failures % 3 == 0) relocate(pc)
                    val wait = Backoff.seconds(failures)
                    for (left in wait downTo 1) {
                        _state.value = ConnectionState.Reconnecting(pc, left, "Нет связи с ПК")
                        delay(1000)
                    }
                }
            }
        }
    }

    /** One WebSocket session; suspends until it ends and says why. */
    private suspend fun usbReachable(port: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching { java.net.Socket().use { it.connect(java.net.InetSocketAddress(LOOPBACK, port), 300) } }.isSuccess
    }

    private suspend fun connectOnce(pc: PairedPc, token: String, host: String, usb: Boolean): End {
        val trust = PinnedTls.PinTrustManager(pin = pc.fingerprint)
        val http = PinnedTls.client(trust, pingSeconds = 20).also { client = it }
        val ended = CompletableDeferred<End>()
        val request = Request.Builder()
            .url("wss://${PairingClient.hostPart(host)}:${pc.port}/api/v1/display")
            .header("Authorization", "Bearer $token")
            .build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (val message = Protocol.parse(text, SystemClock.elapsedRealtime())) {
                    is Protocol.Message.Hello -> {
                        sessionConnected = true
                        webSocket.send(Protocol.subscribe(metrics = uiVisible, interval = interval))
                        _state.value = ConnectionState.Connected(pc, message.pcName.ifBlank { pc.name }, usb)
                    }
                    is Protocol.Message.MetricsUpdate -> _metrics.value = message.metrics
                    is Protocol.Message.MediaUpdate -> {
                        _volume.value = message.volume
                        onMedia(message.session, host, pc.port, token, http)
                    }
                    is Protocol.Message.Error -> Log.i(TAG, "PC: ${message.code}")      // never log tokens
                    else -> Unit
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                ended.complete(if (code == Protocol.CLOSE_REVOKED) End.REVOKED else End.LOST)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                ended.complete(if (code == Protocol.CLOSE_REVOKED) End.REVOKED else End.LOST)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val result = when {
                    response?.code == 401 -> End.REVOKED
                    t is SSLHandshakeException && causedByMismatch(t) -> End.CERTIFICATE
                    else -> End.LOST
                }
                Log.i(TAG, "Connection ended: ${t.javaClass.simpleName}")
                ended.complete(result)
            }
        })
        // On Wi-Fi, keep checking for the cable: USB is steadier, so switch as soon as it appears.
        val usbWatch = if (usb) null else scope.launch {
            while (isActive && !ended.isCompleted) {
                delay(USB_CHECK_MS)
                if (sessionConnected && usbReachable(pc.port)) ended.complete(End.USB)
            }
        }
        return try {
            ended.await()
        } finally {
            usbWatch?.cancel()
            socket?.cancel()
            socket = null
            http.dispatcher.executorService.shutdown()
        }
    }

    private fun causedByMismatch(t: Throwable): Boolean {
        var cause: Throwable? = t
        while (cause != null) {
            if (cause is PinnedTls.CertificateMismatch) return true
            cause = cause.cause
        }
        return false
    }

    private fun onMedia(session: MediaSession?, host: String, port: Int, token: String, http: OkHttpClient) {
        _media.value = session
        val key = session?.coverKey.orEmpty()
        if (key.isEmpty()) {
            _cover.value = null
            return
        }
        if (_cover.value?.first == key || coverJob?.isActive == true) return
        coverJob = scope.launch(Dispatchers.IO) {
            val request = Request.Builder()
                .url("https://${PairingClient.hostPart(host)}:$port/api/v1/player/cover?key=" +
                     java.net.URLEncoder.encode(key, "UTF-8"))
                .header("Authorization", "Bearer $token")
                .build()
            val image = runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null else response.body?.bytes()?.let(::decodeCover)
                }
            }.getOrNull()
            if (_media.value?.coverKey == key) _cover.value = image?.let { key to it }
        }
    }

    /** The PC may have a new IP after a router restart: find it again by name. */
    private suspend fun relocate(pc: PairedPc) {
        val match = runCatching { Discovery.find(1500) }.getOrDefault(emptyList())
            .firstOrNull { it.name == pc.name && it.tlsPort != null && it.host != pc.host }
        if (match != null) withContext(Dispatchers.IO) { store.updateHost(match.host, match.tlsPort!!) }
    }

    private fun clearData() {
        _volume.value = null
        _metrics.value = null
        _media.value = null
        _cover.value = null
    }

    private companion object {
        const val TAG = "PanelConnection"
        const val LOOPBACK = "127.0.0.1"
        const val USB_CHECK_MS = 5_000L

        /** Covers are shown at ~80 dp; decode small to save memory on a 3 GB phone. */
        fun decodeCover(bytes: ByteArray): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 256 && bounds.outHeight / (sample * 2) >= 256) sample *= 2
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}

/** Reconnection delays: quick at first, then every 30 s at most (no busy loop). */
object Backoff {
    private val steps = intArrayOf(1, 2, 5, 10, 15, 30)
    fun seconds(failures: Int): Int = steps[(failures - 1).coerceIn(0, steps.lastIndex)]
}
