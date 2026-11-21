package com.kirdev.pulsedeckpanel.feature.dashboard

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.feature.monitoring.AlertMonitor
import com.kirdev.pulsedeckpanel.feature.monitoring.History
import com.kirdev.pulsedeckpanel.service.AlertNotices
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val app: Application,
    private val connection: PanelConnection,
    private val settings: AppSettings,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = connection.state
    val media = connection.media
    val cover = connection.cover
    val volume = connection.volume
    val metrics = connection.metrics
    val display: StateFlow<DisplayPrefs> = settings.display

    private val _history = MutableStateFlow(History())
    /** The last ~3 minutes for the sparklines (kept through a short drop, cleared for another PC). */
    val history: StateFlow<History> = _history.asStateFlow()

    private val monitor = AlertMonitor()
    private val _alerts = MutableStateFlow<Set<Limit>>(emptySet())
    val alerts: StateFlow<Set<Limit>> = _alerts.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short notices at the bottom of the screen (warnings, gestures). */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private var pcName = ""
    private var offlineJob: Job? = null

    init {
        viewModelScope.launch {
            connection.metrics.collect { m ->
                if (m == null) return@collect
                _history.value = _history.value.plus(m)
                val prefs = settings.display.value
                for ((key, text) in monitor.check(m, prefs, prefs.intervalSeconds)) notice(text, key.name)
                _alerts.value = monitor.activeLimits
            }
        }
        viewModelScope.launch {
            var online = false
            connection.state.collect { s ->
                val now = s is ConnectionState.Connected
                if (s is ConnectionState.Connected) {
                    if (pcName.isNotEmpty() && pcName != s.pcName) _history.value = History()
                    pcName = s.pcName
                    offlineJob?.cancel()
                } else if (online) {
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
                online = now
            }
        }
    }

    fun command(action: String, value: Number? = null) {
        connection.send(action, value)
    }

    fun toast(text: String) {
        _toasts.tryEmit(text)
    }

    private fun notice(text: String, key: String) {
        _toasts.tryEmit(text)
        if (settings.display.value.notify) AlertNotices.show(app, "PulseDeck · ${pcName.ifBlank { "ПК" }}", text, key)
    }
}

/** Short status line for the top-left corner. */
fun statusText(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> state.pcName + if (state.usb) " · USB" else ""
    is ConnectionState.Connecting -> "Подключение к ${state.pc.name}…"
    is ConnectionState.Reconnecting -> "${state.reason}. Повтор через ${state.inSeconds} с"
    is ConnectionState.CertificateChanged -> "Сертификат ПК изменился. Нажмите, чтобы подключить заново"
    ConnectionState.Revoked -> "Доступ отозван на ПК. Нажмите, чтобы подключить заново"
    ConnectionState.NotPaired -> "ПК не подключён"
}
