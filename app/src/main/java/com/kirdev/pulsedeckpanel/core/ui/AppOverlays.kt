package com.kirdev.pulsedeckpanel.core.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.PanelApp
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.data.update.UpdateState

/** Things that belong to the whole app, drawn above every screen. */
@Composable
fun AppOverlays(app: PanelApp) {
    LocalNetworkGate(app)
    UpdatePrompt(app)
}

/**
 * Android 17 blocks every connection to the local network (search, Wi-Fi link to the PC)
 * for apps targeting SDK 37 until the user allows «Nearby devices». Asked once on start;
 * while it is missing a banner explains why nothing connects. USB (127.0.0.1) still works.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun required() = Build.VERSION.SDK_INT >= 37

    fun granted(context: Context) =
        !required() || context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun LocalNetworkGate(app: PanelApp) {
    if (!LocalNetwork.required()) return
    val context = LocalContext.current
    var granted by remember { mutableStateOf(LocalNetwork.granted(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) app.connection.restart()
    }
    // Back from system settings: the user may have allowed it there.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = LocalNetwork.granted(context)
        if (now && !granted) app.connection.restart()
        granted = now
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(LocalNetwork.PERMISSION)
        }
    }
    if (granted) return
    Column(Modifier.statusBarsPadding().padding(12.dp)) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Palette.Surface).padding(16.dp)) {
            Text("Нет доступа к локальной сети", fontSize = 15.sp, color = Palette.Text)
            Text("Android 17 не даёт приложениям связываться с устройствами в вашей сети без разрешения " +
                "«Устройства поблизости». Без него PulseDeck Panel не найдёт ПК и не подключится по Wi-Fi (по USB-кабелю — работает).",
                fontSize = 12.sp, color = Palette.Text2, lineHeight = 17.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Настройки", color = Palette.Text2) }
                TextButton(onClick = { launcher.launch(LocalNetwork.PERMISSION) }) { Text("Разрешить", color = Palette.Text) }
            }
        }
    }
}

/** A new version is downloaded automatically (see Updater.checkAutomatically); this shows it. */
@Composable
private fun UpdatePrompt(app: PanelApp) {
    val state by app.updater.state.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf("") }
    val (version, progress, ready) = when (val s = state) {
        is UpdateState.Downloading -> Triple(s.release.version, s.fraction, null)
        is UpdateState.Ready -> Triple(s.release.version, 1f, s)
        else -> return
    }
    if (dismissed == version) return
    AlertDialog(
        onDismissRequest = { dismissed = version },
        containerColor = Palette.Surface,
        title = { Text("Обновление PulseDeck Panel $version", color = Palette.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ready == null) {
                    Text("Скачиваю новую версию: ${(progress * 100).toInt()}%", color = Palette.Text2, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(),
                        color = Palette.Text, trackColor = Palette.Surface2)
                } else {
                    Text("Версия скачана. Нажмите «Установить» и подтвердите в окне Android — пара с ПК и настройки сохранятся.",
                        color = Palette.Text2, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        },
        confirmButton = {
            if (ready != null) TextButton(onClick = { app.updater.install(ready.file) }) { Text("Установить", color = Palette.Text) }
        },
        dismissButton = { TextButton(onClick = { dismissed = version }) { Text("Позже", color = Palette.Text3) } },
    )
}
