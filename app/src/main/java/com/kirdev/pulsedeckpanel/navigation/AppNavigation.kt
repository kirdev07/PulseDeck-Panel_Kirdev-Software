package com.kirdev.pulsedeckpanel.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kirdev.pulsedeckpanel.PanelApp
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.feature.connection.ConnectionScreen
import com.kirdev.pulsedeckpanel.feature.connection.ConnectionViewModel
import com.kirdev.pulsedeckpanel.feature.dashboard.DashboardScreen
import com.kirdev.pulsedeckpanel.feature.dashboard.DashboardViewModel
import com.kirdev.pulsedeckpanel.feature.settings.SettingsScreen

enum class Route { Dashboard, Settings, Connection }

/** Three screens do not need a navigation library: a saved route and the back button. */
@Composable
fun AppNavigation(app: PanelApp) {
    val state by app.connection.state.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(if (app.pairing.load() == null) Route.Connection else Route.Dashboard) }

    // Revoked on the PC or never paired: pairing is the only way forward.
    LaunchedEffect(state) {
        if (state is ConnectionState.NotPaired || state is ConnectionState.Revoked) route = Route.Connection
    }
    val paired = app.pairing.load() != null

    when (route) {
        Route.Dashboard -> {
            val vm: DashboardViewModel = viewModel(factory = viewModelFactory { initializer { DashboardViewModel(app.connection, app.settings) } })
            DashboardScreen(vm, onSettings = { route = Route.Settings }, onConnection = { route = Route.Connection })
        }
        Route.Settings -> {
            BackHandler { route = Route.Dashboard }
            SettingsScreen(app, onBack = { route = Route.Dashboard }, onConnection = { route = Route.Connection })
        }
        Route.Connection -> {
            if (paired) BackHandler { route = Route.Dashboard }
            val vm: ConnectionViewModel = viewModel(key = "connection", factory = viewModelFactory {
                initializer { ConnectionViewModel(app.pairing, app.pairingClient, app.connection) }
            })
            LaunchedEffect(Unit) { vm.search() }     // each visit starts fresh, not at «paired»
            ConnectionScreen(vm, onBack = if (paired) ({ route = Route.Dashboard }) else null,
                onPaired = { route = Route.Dashboard })
        }
    }
}
