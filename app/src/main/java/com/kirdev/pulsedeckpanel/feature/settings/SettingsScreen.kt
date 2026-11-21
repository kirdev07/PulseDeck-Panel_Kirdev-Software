package com.kirdev.pulsedeckpanel.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.PanelApp
import com.kirdev.pulsedeckpanel.core.theme.Choices
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.theme.Swatch
import com.kirdev.pulsedeckpanel.core.ui.IconButtonSmall
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.currentDisplay
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Limit
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.data.update.UpdateState
import com.kirdev.pulsedeckpanel.feature.dashboard.LAYOUTS
import com.kirdev.pulsedeckpanel.feature.monitoring.iconOf
import com.kirdev.pulsedeckpanel.feature.pulse.Idle
import com.kirdev.pulsedeckpanel.service.AlertNotices
import com.kirdev.pulsedeckpanel.service.PanelService

/** A section of Settings: a page of its own with «Назад», like the settings of a phone (and the web panel). */
private enum class Page(val title: String, val icon: ImageVector) {
    LOOK("Оформление", PanelIcons.Palette), CLOCK("Часы", PanelIcons.Clock), PLAYER("Плеер", PanelIcons.Music),
    TILES("Плитки", PanelIcons.Cpu), ALERTS("Предупреждения", PanelIcons.Bell), MOTION("Анимации", PanelIcons.Pulse),
    SCREEN("Экран", PanelIcons.Monitor), WORK("Работа в фоне", PanelIcons.Network), PCS("Компьютеры", PanelIcons.Monitor),
    UPDATES("Обновления", PanelIcons.Download),
}

@Composable
fun SettingsScreen(app: PanelApp, onBack: () -> Unit, onConnection: () -> Unit) {
    var page by rememberSaveable { mutableStateOf<Page?>(null) }
    BackHandler(enabled = page != null) { page = null }
    val display by app.settings.display.collectAsStateWithLifecycle()
    val set: ((DisplayPrefs) -> DisplayPrefs) -> Unit = { app.settings.updateDisplay(it) }

    Column(Modifier.fillMaxSize().background(Palette.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButtonSmall(PanelIcons.Back, "Назад", onClick = { if (page != null) page = null else onBack() })
            Spacer(Modifier.width(8.dp))
            Text(page?.title ?: "Настройки", fontSize = 18.sp, color = Palette.Text)
        }
        key(page) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (page) {
                        null -> Main(app, display, set) { page = it }
                        Page.LOOK -> Look(display, set)
                        Page.CLOCK -> ClockPage(display, set)
                        Page.PLAYER -> PlayerPage(app, display, set)
                        Page.TILES -> TilesPage(app, display, set)
                        Page.ALERTS -> AlertsPage(display, set)
                        Page.MOTION -> MotionPage(display, set)
                        Page.SCREEN -> ScreenPage(display, set)
                        Page.WORK -> WorkPage(app)
                        Page.PCS -> PcsPage(app, onConnection)
                        Page.UPDATES -> Updates(app)
                    }
                    Spacer(Modifier.size(12.dp))
                }
            }
        }
    }
}

@Composable
private fun Main(app: PanelApp, p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit, open: (Page) -> Unit) {
    val update by app.updater.state.collectAsStateWithLifecycle()
    Panel("Раскладка", "") {
        KeyChips(LAYOUTS, p.layout) { v -> set { it.copy(layout = v) } }
    }
    Panel("Тема", "") {
        KeyChips(listOf("DARK" to "Тёмная", "LIGHT" to "Белая"), p.theme) { v -> set { it.copy(theme = v, themeByTime = false) } }
    }
    val idleName = Idle.KINDS.firstOrNull { it.first == p.idle }?.second ?: "Без анимации"
    val subs = mapOf(
        Page.LOOK to "${if (Palette.theme.dark) "Тёмная" else "Белая"} тема, цвета, шрифт",
        Page.CLOCK to listOfNotNull(if (p.use24h) "24 ч" else "12 ч", "секунды".takeIf { p.seconds }, "дата".takeIf { p.showDate })
            .joinToString(", "),
        Page.PLAYER to "Вид, волна, жесты",
        Page.TILES to "Показано: ${p.tiles.size}",
        Page.ALERTS to if (p.alerts) "Пороги и уведомления" else "Выключены",
        Page.MOTION to idleName,
        Page.SCREEN to "Выгорание, ночь, без связи, частота",
        Page.WORK to if (app.settings.background) "Связь с ПК в фоне" else "Только пока открыто",
        Page.PCS to (app.pairing.load()?.name ?: "Нет подключённых ПК"),
        Page.UPDATES to if (update is UpdateState.Available) "Есть новая версия" else "Версия ${app.updater.current}",
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp)),
    ) {
        Page.entries.forEachIndexed { index, item ->
            if (index > 0) Box(Modifier.fillMaxWidth().padding(start = 52.dp).size(1.dp).background(Palette.Border))
            Row(
                Modifier.fillMaxWidth().clickable { open(item) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, fontSize = 15.sp, color = Palette.Text)
                    Text(subs[item].orEmpty(), fontSize = 12.sp, color = Palette.Text3)
                }
                if (item == Page.UPDATES && update is UpdateState.Available) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Palette.Accent))
                    Spacer(Modifier.width(8.dp))
                }
                Icon(PanelIcons.Chevron, contentDescription = null, tint = Palette.Text3, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Look(p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    Panel("Тема", "Тёмная — для AMOLED и ночи, белая — для светлой комнаты.") {
        KeyChips(listOf("DARK" to "Тёмная", "LIGHT" to "Белая"), p.theme) { v -> set { it.copy(theme = v, themeByTime = false) } }
    }
    Toggle("Белая днём, тёмная вечером", "Тема меняется сама в заданное время.", p.themeByTime) { on -> set { it.copy(themeByTime = on) } }
    if (p.themeByTime) {
        Panel("Белая тема", "С %s до %s".format(hm(p.dayFrom), hm(p.dayTo))) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper("С", hm(p.dayFrom)) { d -> set { it.copy(dayFrom = it.dayFrom + d * 30) } }
                Stepper("До", hm(p.dayTo)) { d -> set { it.copy(dayTo = it.dayTo + d * 30) } }
            }
        }
    }
    Panel("Цвет акцента", "Полоски, ползунки, кнопка «Играть», мини-графики.") {
        Swatches(Choices.ACCENTS.map { if (it.key == "white") it.copy(color = Palette.Text) else it }, p.accent) { v ->
            set { it.copy(accent = v) }
        }
    }
    Toggle("Цвет акцента из обложки трека", "Пока играет музыка, акцент и мягкое свечение фона — в цвет обложки.", p.coverColors) { on ->
        set { it.copy(coverColors = on) }
    }
    Toggle("Размытая обложка на фоне", "Только Android 12 и новее.", p.coverBackdrop) { on -> set { it.copy(coverBackdrop = on) } }
    if (Palette.theme.dark) {
        Panel("Фон (тёмная тема)", "") { Swatches(Choices.BACKGROUNDS_DARK, p.bgDark) { v -> set { it.copy(bgDark = v) } } }
    } else {
        Panel("Фон (белая тема)", "") { Swatches(Choices.BACKGROUNDS_LIGHT, p.bgLight) { v -> set { it.copy(bgLight = v) } } }
    }
    Panel("Шрифт интерфейса", "Шрифты самого телефона.") {
        KeyChips(Choices.UI_FONTS.map { it.first to it.second.first }, p.uiFont) { v -> set { it.copy(uiFont = v) } }
    }
}

@Composable
private fun ClockPage(p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    Panel("Цвет", "") {
        Swatches(Choices.CLOCK_COLORS.map {
            when (it.key) {
                "ink" -> it.copy(color = Palette.Text)
                "accent" -> it.copy(color = Palette.accentOf(p.accent))
                else -> it
            }
        }, p.clockColor) { v -> set { it.copy(clockColor = v) } }
    }
    Panel("Шрифт", "Шрифты самого телефона: ничего не скачивается.") {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((key, pair) in Choices.CLOCK_FONTS) {
                val on = p.clockFont == key
                Column(
                    Modifier.width(104.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Palette.Text else Palette.Surface2)
                        .clickable { set { it.copy(clockFont = key) } }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("12:34", style = TextStyle(fontFamily = pair.second, fontSize = 22.sp, fontWeight = FontWeight.Light,
                        color = if (on) Palette.Black else Palette.Text))
                    Text(pair.first, fontSize = 11.sp, color = if (on) Palette.Black else Palette.Text3)
                }
            }
        }
    }
    Panel("Толщина", "") {
        KeyChips(listOf("thin" to "Тонкие", "regular" to "Обычные", "bold" to "Жирные"), p.clockWeight) { v -> set { it.copy(clockWeight = v) } }
    }
    Panel("Размер", "") { KeyChips(listOf("s" to "S", "m" to "M", "l" to "L"), p.clockSize) { v -> set { it.copy(clockSize = v) } } }
    Toggle("24 часа", "Иначе — 12-часовой: 9:41 PM вместо 21:41.", p.use24h) { on -> set { it.copy(use24h = on) } }
    Toggle("Секунды", "", p.seconds) { on -> set { it.copy(seconds = on) } }
    Toggle("Дата", "", p.showDate) { on -> set { it.copy(showDate = on) } }
    Toggle("Что сейчас открыто на ПК", "Под датой: приложение на переднем плане и его окно (PulseDeck 1.0.4 и новее).",
        p.showActivity) { on -> set { it.copy(showActivity = on) } }
}

@Composable
private fun PlayerPage(app: PanelApp, p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    val players by app.connection.players.collectAsStateWithLifecycle()
    val source by app.connection.source.collectAsStateWithLifecycle()
    Panel("Источник музыки", if (players.isEmpty())
        "Список приложений с музыкой приходит с ПК (PulseDeck 1.0.4 и новее), когда ПК на связи."
        else "Чей трек показывать. «Автоматически» — что сейчас играет на ПК. То же — по тапу на название приложения в плеере.") {
        if (players.isNotEmpty()) {
            KeyChips(listOf("" to "Автоматически") + players.map {
                it.id to (if (it.title.isNotBlank()) "${it.source} — ${it.title}".take(48) else it.source)
            }, source) { app.connection.chooseSource(it) }
        }
    }
    Toggle("Показывать, даже когда ничего не играет", "", p.playerAlways) { on -> set { it.copy(playerAlways = on) } }
    Toggle("Обложка", "", p.showCover) { on -> set { it.copy(showCover = on) } }
    Toggle("Полоса времени", "", p.showProgress) { on -> set { it.copy(showProgress = on) } }
    Toggle("Ползунок волной, пока играет", "Как в плеере Android и One UI.", p.waveSeek) { on -> set { it.copy(waveSeek = on) } }
    Toggle("Полоски «играет» у названия", "", p.nowPlaying) { on -> set { it.copy(nowPlaying = on) } }
    Toggle("Громкость ПК", "", p.showVolume) { on -> set { it.copy(showVolume = on) } }
    Section("Жесты")
    Toggle("Жесты на плеере и часах",
        "По плееру: свайп влево — следующий трек, вправо — предыдущий, вверх/вниз — громкость ПК, двойной тап — пауза. " +
            "Тап по часам — следующая раскладка.", p.gestures) { on -> set { it.copy(gestures = on) } }
}

@Composable
private fun TilesPage(app: PanelApp, p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    val metrics by app.connection.metrics.collectAsStateWithLifecycle()
    Panel("Плитки внизу", "Какие показывать и в каком порядке.") {
        p.tileOrder.forEachIndexed { index, tile ->
            val on = tile in p.tiles
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Palette.Surface2)
                    .clickable(enabled = !on || p.tiles.size > 1) { set { it.copy(tiles = if (on) it.tiles - tile else it.tiles + tile) } }
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(if (on) Palette.Accent else Palette.Surface)
                    .border(1.5.dp, if (on) Palette.Accent else Palette.Text3, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                    if (on) Icon(PanelIcons.Check, contentDescription = null, tint = Palette.Black, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(12.dp))
                Icon(iconOf(tile), contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(tile.title, fontSize = 14.sp, color = Palette.Text, modifier = Modifier.weight(1f))
                MoveButton(PanelIcons.Up, "Выше", index > 0) { set { it.copy(tileOrder = it.tileOrder.swap(index, index - 1)) } }
                MoveButton(PanelIcons.Down, "Ниже", index < p.tileOrder.lastIndex) { set { it.copy(tileOrder = it.tileOrder.swap(index, index + 1)) } }
            }
        }
    }
    val disks = metrics?.disks.orEmpty()
    if (Tile.DISK in p.tiles && disks.size > 1) {
        Panel("Какой диск", "«По очереди» — диски сменяют друг друга каждые 5 секунд.") {
            KeyChips(listOf("" to "По очереди") + disks.map { it.name to (if (it.label.isNotBlank()) "${it.name} ${it.label}" else it.name) },
                p.disk) { v -> set { it.copy(disk = v) } }
        }
    }
    Panel("Если плитки не помещаются", "") {
        KeyChips(listOf("wrap" to "Второй ряд", "scroll" to "Листать пальцем"), p.tileRows) { v -> set { it.copy(tileRows = v) } }
    }
    Toggle("Графики в раскладке «Мониторинг»", "Последние 3 минуты в больших карточках. В нижнем ряду графиков нет.", p.sparks) { on -> set { it.copy(sparks = on) } }
    Note("Нажмите на плитку — откроются подробности: график, ядра, все диски.")
}

@Composable
private fun AlertsPage(p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(AlertNotices.allowed(context)) }
    val app = LocalContext.current.applicationContext as PanelApp
    LifecycleResumeEffect(Unit) {
        allowed = AlertNotices.allowed(context)
        app.syncBackgroundMetrics()
        onPauseOrDispose { }
    }
    val askNotifications = rememberNotificationAsk()
    Toggle("Подсвечивать плитку и сообщать", "Сообщение — если значение держится за порогом ~10 секунд. Диск и батарея — когда осталось меньше порога.",
        p.alerts) { on -> set { it.copy(alerts = on) } }
    if (p.alerts) {
        Panel("Пороги", "") {
            for (limit in Limit.entries) LimitRow(limit, p.limit(limit)) { v -> set { it.copy(limits = it.limits + (limit to v)) } }
        }
    }
    Toggle("Сообщать, если пропала связь с ПК", "Через 30 секунд без связи.", p.alertOffline) { on -> set { it.copy(alertOffline = on) } }
    Section("Уведомления")
    Toggle("Системные уведомления",
        if (allowed) "Предупреждения приходят и в шторку телефона. С «Работать в фоне» — и когда приложение свёрнуто: " +
            "тогда показатели запрашиваются раз в 5 секунд."
        else "Android не разрешает уведомления этому приложению. Включите их в настройках приложения.",
        p.notify && allowed) { on ->
        if (on && !allowed) askNotifications()          // the switch shows the real state once Android answers
        set { it.copy(notify = on) }
    }
}

@Composable
private fun MotionPage(p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    Panel("Когда ничего не играет", "Анимации — для красоты, данных они не показывают.") {
        KeyChips(Idle.KINDS.map { it.first to it.second } + ("none" to "Нет"), p.idle) { v -> set { it.copy(idle = v) } }
    }
    if (p.idle != "none") {
        Panel("Размер анимации", "") { KeyChips(Idle.SIZES, p.idleSize) { v -> set { it.copy(idleSize = v) } } }
    }
    Toggle("Плавные переходы и анимации", "Выключите, чтобы экран стоял неподвижно.", p.motion) { on -> set { it.copy(motion = on) } }
    Toggle("Обложка пульсирует, пока играет", "", p.pulse) { on -> set { it.copy(pulse = on) } }
}

@Composable
private fun ScreenPage(p: DisplayPrefs, set: ((DisplayPrefs) -> DisplayPrefs) -> Unit) {
    Toggle("Защита от выгорания (AMOLED)", "Экран медленно сдвигается на несколько точек каждые 2 минуты.", p.burnIn) { on ->
        set { it.copy(burnIn = on) }
    }
    Toggle("Без связи — только часы", "Пока ПК недоступен, на экране остаются одни часы.", p.offlineClock) { on ->
        set { it.copy(offlineClock = on) }
    }
    val activity = androidx.activity.compose.LocalActivity.current
    val top = com.kirdev.pulsedeckpanel.core.ui.Frames.maxRate(activity?.let { it.currentDisplay() })
    Panel("Частота кадров", "Как часто перерисовываются анимации. Экран телефона переключается на эту частоту: ниже — меньше расход батареи. Ночью всегда экономно.") {
        KeyChips(listOf("eco" to "Экономно · 30", "smooth" to "Плавно · 60") + (if (top > 60) listOf("max" to "Максимум · $top Гц") else emptyList()),
            p.frameRate) { v -> set { it.copy(frameRate = v) } }
    }
    Panel("Облегчённый режим", "Для слабых телефонов: анимации 30 кадров, без размытой обложки, мягких краёв, пульсации и переходов. " +
        "Этот телефон: " + com.kirdev.pulsedeckpanel.core.ui.Device.summary() + ".") {
        KeyChips(listOf("auto" to "Авто", "on" to "Включён", "off" to "Выключен"), p.lite) { v -> set { it.copy(lite = v) } }
    }
    Panel("Обновление показателей", "Реже — меньше нагрузка на ПК и телефон.") {
        val options = listOf(1.0 to "1 с", 1.5 to "1,5 с", 3.0 to "3 с", 5.0 to "5 с", 10.0 to "10 с")
        KeyChips(options.map { it.first.toString() to it.second }, p.intervalSeconds.toString()) { v ->
            set { it.copy(intervalSeconds = v.toDouble()) }
        }
    }
    Section("Ночной режим")
    Toggle("Приглушать экран ночью", "Минимальная яркость и затемнённый интерфейс в заданные часы.", p.nightMode) { on ->
        set { it.copy(nightMode = on) }
    }
    if (p.nightMode) {
        Panel("Часы", "С %02d:00 до %02d:00".format(p.nightStart, p.nightEnd)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper("Начало", "%02d:00".format(p.nightStart)) { d -> set { it.copy(nightStart = it.nightStart + d) } }
                Stepper("Конец", "%02d:00".format(p.nightEnd)) { d -> set { it.copy(nightEnd = it.nightEnd + d) } }
            }
        }
    }
}

@Composable
private fun WorkPage(app: PanelApp) {
    val context = LocalContext.current
    var background by remember { mutableStateOf(app.settings.background) }
    var openOnUnlock by remember { mutableStateOf(app.settings.openOnUnlock) }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    LifecycleResumeEffect(Unit) {                                    // back from the system permission screen
        overlayAllowed = Settings.canDrawOverlays(context)
        onPauseOrDispose { }
    }
    val active = remember { app.pairing.load() }
    val askNotifications = rememberNotificationAsk()
    Toggle("Работать в фоне",
        "Связь с ПК и управление музыкой из уведомления, когда приложение свёрнуто. " +
            "Показатели в фоне не запрашиваются.", background) { on ->
        background = on
        app.settings.background = on
        app.syncBackgroundMetrics()
        if (on) {
            if (!AlertNotices.allowed(context)) askNotifications()      // the music controls live in a notification
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
}

@Composable
private fun PcsPage(app: PanelApp, onConnection: () -> Unit) {
    var pcsVersion by remember { mutableIntStateOf(0) }             // re-read the PC list after a change
    val pcs = remember(pcsVersion) { app.pairing.all() }
    val active = remember(pcsVersion) { app.pairing.load() }
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
}

/**
 * Asks for the notification permission from a switch that needs it; when Android no longer shows
 * the question (denied twice), opens the app's notification settings instead.
 */
@Composable
private fun rememberNotificationAsk(): () -> Unit {
    val context = LocalContext.current
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    return remember(context) {
        {
            val activity = context as? android.app.Activity
            if (android.os.Build.VERSION.SDK_INT >= 33 && activity != null &&
                (!(context.applicationContext as PanelApp).settings.askedNotifications ||
                    activity.shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))) {
                (context.applicationContext as PanelApp).settings.askedNotifications = true
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

private fun <T> List<T>.swap(a: Int, b: Int): List<T> = toMutableList().also { val x = it[a]; it[a] = it[b]; it[b] = x }

private fun hm(minutes: Int): String = "%02d:%02d".format(minutes.mod(1440) / 60, minutes.mod(1440) % 60)

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
        if (text.isNotBlank()) Text(text, fontSize = 12.sp, color = Palette.Text3)
        content()
    }
}

/** Chips with a key and a label; the chosen one is filled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyChips(options: List<Pair<String, String>>, selected: String, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((key, label) in options) {
            val on = key == selected
            Text(label, fontSize = 13.sp, color = if (on) Palette.Black else Palette.Text2,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Palette.Text else Palette.Surface2)
                    .clickable { onClick(key) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

/** Round colour swatches; the chosen one has a ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(options: List<Swatch>, selected: String, onClick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (option in options) {
            val on = option.key == selected
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .border(2.dp, if (on) Palette.Text else Color.Transparent, CircleShape)
                    .padding(4.dp).clip(CircleShape).background(option.color)
                    .border(1.dp, Palette.Border, CircleShape)
                    .clickable { onClick(option.key) },
            )
        }
    }
    Text(options.firstOrNull { it.key == selected }?.title.orEmpty(), fontSize = 12.sp, color = Palette.Text2)
}

@Composable
private fun LimitRow(limit: Limit, value: Int, onChange: (Int) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(limit.title, fontSize = 13.sp, color = Palette.Text2, modifier = Modifier.width(150.dp))
        Slider(
            value = current, onValueChange = { current = it }, onValueChangeFinished = { onChange(current.toInt()) },
            valueRange = limit.range.first.toFloat()..limit.range.last.toFloat(), modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Palette.Accent, activeTrackColor = Palette.Accent,
                inactiveTrackColor = Palette.Track),
        )
        Spacer(Modifier.width(10.dp))
        Text("${if (limit.over) "≥" else "≤"} ${current.toInt()}%", fontSize = 13.sp, color = Palette.Text,
            modifier = Modifier.width(56.dp))
    }
}

@Composable
private fun MoveButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = label, tint = if (enabled) Palette.Text2 else Palette.Disabled, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Stepper(label: String, value: String, onStep: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = Palette.Text2)
        TextButton(onClick = { onStep(-1) }) { Text("−", color = Palette.Text, fontSize = 18.sp) }
        Text(value, fontSize = 15.sp, color = Palette.Text)
        TextButton(onClick = { onStep(1) }) { Text("+", color = Palette.Text, fontSize = 18.sp) }
    }
}

@Composable
private fun Note(text: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 12.sp, color = Palette.Text2, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onClick) { Text(action, color = Palette.Text) }
    }
}

@Composable
private fun Toggle(title: String, text: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = if (enabled) Palette.Text else Palette.Text3)
            if (text.isNotBlank()) Text(text, fontSize = 12.sp, color = Palette.Text3)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Black, checkedTrackColor = Palette.Accent,
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
    val notes = (state as? UpdateState.Available)?.release?.notes.orEmpty().replace("**", "").trim()
    Panel("PulseDeck Panel $current", text) {
        if (notes.isNotEmpty()) Text(notes.take(600), fontSize = 12.sp, color = Palette.Text2)
        if (action != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = action.second) { Text(action.first, color = Palette.Text) }
            }
        }
    }
}
