package com.kirdev.pulsedeckpanel

import android.app.Application
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.PairingStore
import com.kirdev.pulsedeckpanel.data.remote.PairingClient
import com.kirdev.pulsedeckpanel.data.remote.PanelConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the few app-wide objects; screens get them through ViewModel factories. */
class PanelApp : Application() {
    lateinit var pairing: PairingStore
        private set
    lateinit var connection: PanelConnection
        private set
    lateinit var settings: AppSettings
        private set
    val pairingClient = PairingClient()

    override fun onCreate() {
        super.onCreate()
        pairing = PairingStore(this)
        settings = AppSettings(this)
        connection = PanelConnection(pairing, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
