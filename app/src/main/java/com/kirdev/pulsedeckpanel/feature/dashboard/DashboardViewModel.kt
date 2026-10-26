package com.kirdev.pulsedeckpanel.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.Cell
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.feature.monitoring.BarState
import com.kirdev.pulsedeckpanel.feature.monitoring.barState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class DashboardViewModel(private val connection: PanelConnection, settings: AppSettings) : ViewModel() {
    val state: StateFlow<ConnectionState> = connection.state
    val media = connection.media
    val cover = connection.cover
    val volume = connection.volume
    val display: StateFlow<DisplayPrefs> = settings.display

    /** Rebuilt only when metrics (every 1–10 s) or the chosen cells change. */
    val bar: StateFlow<BarState> = combine(connection.metrics, settings.display) { metrics, prefs ->
        visible(barState(metrics), prefs.cells)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), visible(barState(null), settings.display.value.cells))

    fun command(action: String, value: Number? = null) {
        connection.send(action, value)
    }
}

/** Keeps only the cells the user chose in Settings (order stays CPU, GPU, RAM, network, disks). */
fun visible(bar: BarState, cells: Set<Cell>): BarState {
    val byName = mapOf("CPU" to Cell.CPU, "GPU" to Cell.GPU, "RAM" to Cell.RAM, "Сеть" to Cell.NET)
    return BarState(bar.cells.filter { byName[it.name] in cells }, if (Cell.DISK in cells) bar.disks else emptyList())
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
