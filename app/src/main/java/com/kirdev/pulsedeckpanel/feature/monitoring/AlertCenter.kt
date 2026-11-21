package com.kirdev.pulsedeckpanel.feature.monitoring

import android.content.Context
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.service.AlertNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * «Предупреждения» for the whole app: watches the metrics wherever they come from — the open
 * display, or the background link when system notifications are on — so a warning is raised
 * once, whether the screen is on or not.
 */
class AlertCenter(
    private val context: Context,
    private val connection: PanelConnection,
    private val settings: AppSettings,
    scope: CoroutineScope,
) {
    private val monitor = AlertMonitor()
    private val _alerts = MutableStateFlow<Set<Limit>>(emptySet())
    val alerts: StateFlow<Set<Limit>> = _alerts.asStateFlow()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Texts for the on-screen notice. */
    val notices: SharedFlow<String> = _notices.asSharedFlow()

    private var pcName = ""
    private var offlineJob: Job? = null

    init {
        scope.launch {
            connection.metrics.collect { m ->
                if (m == null) return@collect
                val prefs = settings.display.value
                for ((key, text) in monitor.check(m, prefs, android.os.SystemClock.elapsedRealtime())) notice(text, key.name)
                _alerts.value = monitor.activeLimits
            }
        }
        scope.launch {
            var online = false
            connection.state.collect { s ->
                when {
                    s is ConnectionState.Connected -> {
                        pcName = s.pcName
                        online = true
                        offlineJob?.cancel()
                    }
                    // Only a lost network link (the countdown updates every second: started once),
                    // not «Забыть», a revoked key or a new certificate.
                    s is ConnectionState.Reconnecting || s is ConnectionState.Connecting -> if (online) {
                        online = false
                        monitor.reset()
                        _alerts.value = emptySet()
                        offlineJob?.cancel()
                        // «Нет связи» only after 30 s: a short Wi-Fi hiccup is not worth a notice.
                        offlineJob = launch {
                            delay(30_000)
                            if (settings.display.value.alertOffline) {
                                notice("Нет связи с ПК${if (pcName.isNotBlank()) " $pcName" else ""}. Он выключен или вне сети?", "offline")
                            }
                        }
                    }
                    else -> {
                        online = false
                        monitor.reset()
                        _alerts.value = emptySet()
                        offlineJob?.cancel()
                    }
                }
            }
        }
    }

    private fun notice(text: String, key: String) {
        _notices.tryEmit(text)
        if (settings.display.value.notify) AlertNotices.show(context, "PulseDeck · ${pcName.ifBlank { "ПК" }}", text, key)
    }
}
