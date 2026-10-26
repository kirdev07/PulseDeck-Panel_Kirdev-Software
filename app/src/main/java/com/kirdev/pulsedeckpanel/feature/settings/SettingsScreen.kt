package com.kirdev.pulsedeckpanel.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.PanelApp
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.theme.PanelColors
import com.kirdev.pulsedeckpanel.core.ui.ScreenHeader
import com.kirdev.pulsedeckpanel.data.local.Cell
import com.kirdev.pulsedeckpanel.data.update.UpdateState
import com.kirdev.pulsedeckpanel.service.PanelService

@Composable
fun SettingsScreen(app: PanelApp, onBack: () -> Unit, onConnection: () -> Unit) {
    val context = LocalContext.current
    var background by remember { mutableStateOf(app.settings.background) }
    var openOnUnlock by remember { mutableStateOf(app.settings.openOnUnlock) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val display by app.settings.display.collectAsStateWithLifecycle()
    var pcsVersion by remember { mutableIntStateOf(0) }             // re-read the PC list after a change
    LifecycleResumeEffect(Unit) {                                    // back from the system permission screen
        overlayAllowed = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    val pcs = remember(pcsVersion) { app.pairing.all() }
    val active = remember(pcsVersion) { app.pairing.load() }

    Column(Modifier.fillMaxSize().background(Palette.Black)) {
        ScreenHeader("Настройки", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Section("Работа")
                Toggle("Работать в фоне",
                    "Связь с ПК и управление музыкой из уведомления, когда приложение свёрнуто. " +
                        "Показатели в фоне не запрашиваются.", background) { on ->
                    background = on
                    app.settings.background = on
                    if (on) {
                        if (active != null) PanelService.start(context)
                    } else {
                        openOnUnlock = false
                        app.settings.openOnUnlock = false
                        PanelService.stop(context)
                    }
                }
                Toggle("Открывать после включения и разблокировки",
                    "Телефон как дисплей: после перезагрузки и каждой разблокировки сразу показывается PulseDeck Panel.",
                    openOnUnlock, enabled = background) { on ->
                    openOnUnlock = on
                    app.settings.openOnUnlock = on
                    PanelService.stop(context)                 // restart to (un)register the unlock listener
                    if (active != null) PanelService.start(context)
                }
                if (openOnUnlock && !overlayAllowed) {
                    Note("Android открывает приложение сам только с разрешением «Поверх других окон».", "Разрешить") {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }

                Section("Отображение")
                Panel("Тема", "Тёмная — для AMOLED и ночи, белая — для светлой комнаты.") {
                    Chips(PanelColors.entries.map { it.title }, listOf(Palette.theme.title)) { title ->
                        val chosen = PanelColors.entries.first { it.title == title }
                        app.settings.updateDisplay { it.copy(theme = chosen.name) }
                    }
                }
                Toggle("24-часовой формат", "Иначе — 12-часовой: 9:41 вместо 21:41.", display.use24h) { on ->
                    app.settings.updateDisplay { it.copy(use24h = on) }
                }
                Panel("Нижняя панель", "Какие показатели показывать.") {
                    Chips(Cell.entries.map { it.title }, Cell.entries.filter { it in display.cells }.map { it.title }) { title ->
                        val cell = Cell.entries.first { it.title == title }
                        app.settings.updateDisplay {
                            val cells = if (cell in it.cells) it.cells - cell else it.cells + cell
                            it.copy(cells = cells.ifEmpty { it.cells })        // never hide everything
                        }
                    }
                }
                Panel("Обновление показателей", "Реже — меньше нагрузка на ПК и телефон.") {
                    val options = listOf(1.0 to "1 с", 1.5 to "1,5 с", 3.0 to "3 с", 5.0 to "5 с", 10.0 to "10 с")
                    Chips(options.map { it.second }, options.filter { it.first == display.intervalSeconds }.map { it.second }) { label ->
                        app.settings.updateDisplay { it.copy(intervalSeconds = options.first { o -> o.second == label }.first) }
                    }
                }

                Section("Ночной режим")
                Toggle("Приглушать экран ночью", "Минимальная яркость и затемнённый интерфейс в заданные часы.",
                    display.nightMode) { on -> app.settings.updateDisplay { it.copy(nightMode = on) } }
                if (display.nightMode) {
                    Panel("Часы", "С %02d:00 до %02d:00".format(display.nightStart, display.nightEnd)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Stepper("Начало", display.nightStart) { v -> app.settings.updateDisplay { it.copy(nightStart = v) } }
                            Stepper("Конец", display.nightEnd) { v -> app.settings.updateDisplay { it.copy(nightEnd = v) } }
                        }
                    }
                }

                Section("Компьютеры")
                if (pcs.isEmpty()) Text("Нет подключённых ПК.", fontSize = 13.sp, color = Palette.Text3)
                for (pc in pcs) {
                    val isActive = pc.id == active?.id
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
                            .border(1.dp, if (isActive) Palette.Disabled else Palette.Border, RoundedCornerShape(16.dp))
                            .clickable(enabled = !isActive) {
                                app.pairing.activate(pc.id)
                                app.connection.restart()
                                pcsVersion++
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pc.name, fontSize = 15.sp, color = Palette.Text)
                            Text((if (isActive) "Активный · " else "Нажмите, чтобы переключиться · ") + pc.host,
                                fontSize = 12.sp, color = Palette.Text3)
                        }
                        TextButton(onClick = {
                            app.pairing.forget(pc.id)
                            app.connection.restart()
                            pcsVersion++
                        }) { Text("Забыть", color = Palette.Text2) }
                    }
                }
                Note("«Забыть» удаляет ключ с телефона. Чтобы отключить телефон и на ПК, отзовите его на экране «Телефон» в PulseDeck.",
                    "Добавить ПК", onConnection)

                Section("Обновления")
                Updates(app)
            }
        }
    }
}

@Composable
private fun Section(title: String) =
    Text(title.uppercase(), fontSize = 11.sp, color = Palette.Text3, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp))

@Composable
private fun Panel(title: String, text: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 15.sp, color = Palette.Text)
        Text(text, fontSize = 12.sp, color = Palette.Text3)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: List<String>, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in options) {
            val on = option in selected
            Text(option, fontSize = 13.sp, color = if (on) Palette.Black else Palette.Text2,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Text else Palette.Surface2)
                    .clickable { onClick(option) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Palette.Text2)
        TextButton(onClick = { onChange((value + 23) % 24) }) { Text("−", color = Palette.Text, fontSize = 18.sp) }
        Text("%02d:00".format(value), fontSize = 15.sp, color = Palette.Text)
        TextButton(onClick = { onChange((value + 1) % 24) }) { Text("+", color = Palette.Text, fontSize = 18.sp) }
    }
}

@Composable
private fun Note(text: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.sp, color = Palette.Text2, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action, color = Palette.Text) }
    }
}

@Composable
private fun Toggle(title: String, text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (enabled) Palette.Text else Palette.Text3)
            Text(text, fontSize = 12.sp, color = Palette.Text3)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Black, checkedTrackColor = Palette.Text,
                uncheckedThumbColor = Palette.Text2, uncheckedTrackColor = Palette.Surface2,
                uncheckedBorderColor = Palette.Border,
            ),
        )
    }
}

/** GitHub Releases: check, show what is new, download; Android's installer does the rest. */
@Composable
private fun Updates(app: PanelApp) {
    val state by app.updater.state.collectAsStateWithLifecycle()
    val current = app.updater.current
    val check = "Проверить" to { app.updater.check() }
    val (text, action) = when (val s = state) {
        UpdateState.Idle -> "Установлена версия $current." to check
        UpdateState.Checking -> "Проверяю обновления…" to null
        is UpdateState.UpToDate -> "Установлена последняя версия ${s.version}." to check
        is UpdateState.Available -> "Доступна версия ${s.release.version} (у вас $current)." to
            ("Скачать и установить" to { app.updater.download(s.release) })
        is UpdateState.Downloading -> "Скачиваю ${s.release.version}: ${(s.fraction * 100).toInt()}%" to null
        is UpdateState.Ready -> ("Версия ${s.release.version} скачана. Подтвердите установку в окне Android. " +
            "Если окна нет — разрешите PulseDeck Panel устанавливать приложения и нажмите «Установить».") to
            ("Установить" to { app.updater.install(s.file) })
        is UpdateState.Failed -> s.message to check
    }
    val notes = (state as? UpdateState.Available)?.release?.notes.orEmpty().trim()
    Panel("PulseDeck Panel $current", text) {
        if (notes.isNotEmpty()) Text(notes.take(600), fontSize = 12.sp, color = Palette.Text2)
        if (action != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = action.second) { Text(action.first, color = Palette.Text) }
            }
        }
    }
}
