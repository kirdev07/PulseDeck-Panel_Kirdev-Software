package com.kirdev.pulsedeckpanel.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kirdev.pulsedeckpanel.data.local.AppSettings
import com.kirdev.pulsedeckpanel.data.local.PairingStore

/**
 * After the phone starts (BOOT_COMPLETED comes once the user has unlocked it for the first
 * time): start the background link and open the display, if the user enabled it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val settings = AppSettings(context)
        if (!settings.openOnUnlock || PairingStore(context).load() == null) return
        PanelService.start(context)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) PanelService.openDisplay(context)
    }
}
