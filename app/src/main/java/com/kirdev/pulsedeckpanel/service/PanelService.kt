package com.kirdev.pulsedeckpanel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.kirdev.pulsedeckpanel.MainActivity
import com.kirdev.pulsedeckpanel.PanelApp
import com.kirdev.pulsedeckpanel.R
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.model.MediaSession
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Background work: keeps the link to the PC while the screen is not shown, with an ongoing
 * notification (PC status, current track, ⏮ ⏯ ⏭). Metrics are not requested in the background,
 * only media. Optionally opens the display when the phone is unlocked.
 */
class PanelService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val app get() = application as PanelApp
    private var unlockReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(app.connection.state.value, null),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        app.connection.hold(HOLDER)
        scope.launch {
            app.connection.state.combine(app.connection.media) { state, media -> state to media }
                .distinctUntilChanged { a, b -> a.first == b.first && sameTrack(a.second, b.second) }
                .collect { (state, media) -> notify(build(state, media)) }
        }
        if (app.settings.openOnUnlock) watchUnlock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> app.connection.send("toggle")
            ACTION_NEXT -> app.connection.send("next")
            ACTION_PREV -> app.connection.send("prev")
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unlockReceiver?.let { unregisterReceiver(it) }
        app.connection.release(HOLDER)
        scope.cancel()
        super.onDestroy()
    }

    /** USER_PRESENT cannot be declared in the manifest since Android 8, so the running service listens. */
    private fun watchUnlock() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (app.settings.openOnUnlock) openDisplay(context)
            }
        }
        ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        unlockReceiver = receiver
    }

    private fun sameTrack(a: MediaSession?, b: MediaSession?) =
        a?.title == b?.title && a?.artist == b?.artist && a?.status == b?.status

    private fun notify(notification: Notification) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Связь с ПК", NotificationManager.IMPORTANCE_LOW).apply {
            description = "PulseDeck Panel работает в фоне: состояние ПК и управление музыкой."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun action(action: String, request: Int) = PendingIntent.getService(
        this, request, Intent(this, PanelService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun build(state: ConnectionState, media: MediaSession?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val title = when (state) {
            is ConnectionState.Connected -> state.pcName + if (state.usb) " · USB" else ""
            is ConnectionState.Reconnecting -> "Нет связи с ПК"
            is ConnectionState.Connecting -> "Подключение…"
            ConnectionState.NotPaired, ConnectionState.Revoked, is ConnectionState.CertificateChanged -> "ПК не подключён"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(media?.let { listOf(it.title, it.artist).filter(String::isNotBlank).joinToString(" — ") }
                ?: "Ничего не играет")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (media != null && state is ConnectionState.Connected) {
            builder.addAction(0, "⏮", action(ACTION_PREV, 1))
            builder.addAction(0, if (media.status == PlayStatus.PLAYING) "Пауза" else "Играть", action(ACTION_TOGGLE, 2))
            builder.addAction(0, "⏭", action(ACTION_NEXT, 3))
        }
        builder.addAction(0, "Остановить", action(ACTION_STOP, 4))
        return builder.build()
    }

    companion object {
        private const val CHANNEL = "panel"
        private const val NOTIFICATION_ID = 1
        private const val HOLDER = "service"
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_NEXT = "next"
        private const val ACTION_PREV = "prev"
        private const val ACTION_STOP = "stop"

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, PanelService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, PanelService::class.java))

        /**
         * Opens the display. Android lets a background app start an activity only with the
         * «display over other apps» permission; without it a tappable notification is used.
         */
        fun openDisplay(context: Context) {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (Settings.canDrawOverlays(context)) context.startActivity(intent)
        }
    }
}
