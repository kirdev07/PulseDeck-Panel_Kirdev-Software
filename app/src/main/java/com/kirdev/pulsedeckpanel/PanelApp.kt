package com.kirdev.pulsedeckpanel

import android.app.Application
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.PairingStore
import com.kirdev.pulsedeckpanel.data.remote.PairingClient
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import com.kirdev.pulsedeckpanel.data.update.Updater
import com.kirdev.pulsedeckpanel.feature.monitoring.AlertCenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the few app-wide objects; screens get them through ViewModel factories. */
class PanelApp : Application() {
    /** The launch animation already played in this process (not again after a rotation or a return). */
    var introDone = false
    lateinit var pairing: PairingStore
        private set
    lateinit var connection: PanelConnection
        private set
    lateinit var settings: AppSettings
        private set
    val pairingClient = PairingClient()
    lateinit var updater: Updater
        private set
    lateinit var alerts: AlertCenter
        private set

    override fun onCreate() {
        super.onCreate()
        com.kirdev.pulsedeckpanel.core.ui.Device.probe(this)
        pairing = PairingStore(this)
        settings = AppSettings(this)
        connection = PanelConnection(pairing, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        updater = Updater(this, CoroutineScope(SupervisorJob() + Dispatchers.Main))
        // One thread: the warning state is touched by the metrics and the connection collectors.
        alerts = AlertCenter(this, connection, settings, CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)))
    }

    /** Warnings with notifications keep watching the PC while the app is in the background (every 5 s). */
    fun syncBackgroundMetrics() {
        val prefs = settings.display.value
        connection.setBackgroundMetrics(prefs.alerts && prefs.notify && settings.background &&
            com.kirdev.pulsedeckpanel.service.AlertNotices.allowed(this))
    }
}
