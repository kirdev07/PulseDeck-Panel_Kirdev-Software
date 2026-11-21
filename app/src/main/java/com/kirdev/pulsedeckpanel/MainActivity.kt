package com.kirdev.pulsedeckpanel

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.currentDisplay
import com.kirdev.pulsedeckpanel.core.ui.AppOverlays
import com.kirdev.pulsedeckpanel.core.theme.PanelTheme
import com.kirdev.pulsedeckpanel.navigation.AppNavigation
import com.kirdev.pulsedeckpanel.service.PanelService

class MainActivity : ComponentActivity() {
    private val app get() = application as PanelApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())        // full-screen display; swipe shows the bars
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        // Notifications are needed for the music controls of «Работать в фоне» and for warnings: asked
        // once here when one of them is on; later only from the switch that needs them (Настройки).
        val display = app.settings.display.value
        if (Build.VERSION.SDK_INT >= 33 && !app.settings.askedNotifications && (app.settings.background || display.notify) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            app.settings.askedNotifications = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        lifecycleScope.launch {                       // «Обновление показателей» applies to the live link
            app.settings.display.collect {
                app.connection.setInterval(it.intervalSeconds)
                app.syncBackgroundMetrics()
                applyFrames()
                Palette.apply(it, minuteOfDay())
            }
        }
        lifecycleScope.launch {                       // «Белая днём, тёмная вечером»: checked every minute
            while (true) {
                delay((60 - LocalTime.now().second) * 1000L)
                Palette.apply(app.settings.display.value, minuteOfDay())
                applyFrames()                         // night mode starts / ends on the minute
            }
        }
        setContent {
            PanelTheme {
                Box(Modifier.fillMaxSize()) {
                    AppNavigation(app)
                    AppOverlays(app)
                    var intro by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(!app.introDone) }
                    if (intro) {
                        com.kirdev.pulsedeckpanel.core.ui.LaunchIntro(
                            enabled = app.settings.display.value.motion && !com.kirdev.pulsedeckpanel.core.ui.Device.lite,
                            background = com.kirdev.pulsedeckpanel.core.theme.Palette.Black,
                        ) {
                            app.introDone = true
                            intro = false
                        }
                    }          // local network permission (Android 17), update prompt
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.connection.setUiVisible(true)
        app.connection.hold(UI)
        app.updater.checkAutomatically()       // a new version downloads at once and is offered
        if (app.settings.background && app.pairing.load() != null) PanelService.start(this)
    }

    override fun onStop() {
        // With background work on, the service keeps the link (media only); otherwise it closes.
        app.connection.setUiVisible(false)
        app.connection.release(UI)
        super.onStop()
    }

    /** «Частота кадров» and night mode → the animation limit and the display mode of this window. */
    private fun applyFrames() {
        val prefs = app.settings.display.value
        com.kirdev.pulsedeckpanel.core.ui.Device.apply(prefs.lite)
        val lite = com.kirdev.pulsedeckpanel.core.ui.Device.lite
        com.kirdev.pulsedeckpanel.core.ui.Frames.apply(window, currentDisplay(), if (lite) "eco" else prefs.frameRate,
            prefs.isNight(LocalTime.now().hour))
    }

    private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    private companion object {
        const val UI = "ui"
    }
}
