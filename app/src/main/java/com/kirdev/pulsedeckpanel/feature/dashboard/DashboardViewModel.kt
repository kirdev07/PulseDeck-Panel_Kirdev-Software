package com.kirdev.pulsedeckpanel.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.feature.monitoring.AlertCenter
import com.kirdev.pulsedeckpanel.feature.monitoring.History
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val connection: PanelConnection,
    settings: AppSettings,
    alertCenter: AlertCenter,
) : ViewModel() {
    val state: StateFlow<ConnectionState> = connection.state
    val media = connection.media
    val cover = connection.cover
    val volume = connection.volume
    val metrics = connection.metrics
    val players = connection.players
    val source = connection.source
    val activity = connection.activity
    val display: StateFlow<DisplayPrefs> = settings.display
    val alerts: StateFlow<Set<Limit>> = alertCenter.alerts

    private val _history = MutableStateFlow(History())
    /** The last ~3 minutes for the graphs (kept through a short drop, cleared for another PC). */
    val history: StateFlow<History> = _history.asStateFlow()

    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Short notices at the bottom of the screen (warnings, gestures). */
    val toasts: SharedFlow<String> = _toasts.asSharedFlow()

    private var pcName = ""

    init {
        viewModelScope.launch {
            connection.metrics.collect { m -> if (m != null) _history.value = _history.value.plus(m) }
        }
        viewModelScope.launch {
            connection.state.collect { s ->
                if (s is ConnectionState.Connected) {
                    if (pcName.isNotEmpty() && pcName != s.pcName) _history.value = History()
                    pcName = s.pcName
                }
            }
        }
        viewModelScope.launch { alertCenter.notices.collect { _toasts.tryEmit(it) } }
    }

    fun command(action: String, value: Number? = null) {
        connection.send(action, value)
    }

    fun chooseSource(id: String) = connection.chooseSource(id)

    fun toast(text: String) {
        _toasts.tryEmit(text)
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
