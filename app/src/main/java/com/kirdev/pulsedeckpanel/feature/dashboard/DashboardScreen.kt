package com.kirdev.pulsedeckpanel.feature.dashboard

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.IconButtonSmall
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.local.Tile
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.data.model.Metrics
import com.kirdev.pulsedeckpanel.data.model.PcActivity
import com.kirdev.pulsedeckpanel.data.model.Player
import com.kirdev.pulsedeckpanel.feature.clock.ClockBlock
import com.kirdev.pulsedeckpanel.feature.clock.clockSize
import com.kirdev.pulsedeckpanel.feature.clock.rememberMinuteClock
import com.kirdev.pulsedeckpanel.feature.monitoring.AlertMonitor
import com.kirdev.pulsedeckpanel.feature.monitoring.TileDetail
import com.kirdev.pulsedeckpanel.feature.monitoring.Tiles
import com.kirdev.pulsedeckpanel.feature.music.CompactPlayer
import com.kirdev.pulsedeckpanel.feature.music.PlayerSize
import com.kirdev.pulsedeckpanel.feature.music.coverColor
import com.kirdev.pulsedeckpanel.feature.pulse.Idle
import com.kirdev.pulsedeckpanel.feature.pulse.IdleAnimation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** «Panel», «Часы», «Музыка», «Мониторинг» — the order a tap on the clock walks through. */
val LAYOUTS = listOf("panel" to "Panel", "clock" to "Часы", "music" to "Музыка", "monitoring" to "Мониторинг")

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onSettings: () -> Unit,
    onConnection: () -> Unit,
    onLayout: (String) -> Unit,
    updateAvailable: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val metrics by viewModel.metrics.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val prefs by viewModel.display.collectAsStateWithLifecycle()
    val players by viewModel.players.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val activity by viewModel.activity.collectAsStateWithLifecycle()
    val playerExtras = PlayerExtras(players, source, viewModel::chooseSource)
    val night = prefs.isNight(rememberMinuteClock().hour)
    val shift = rememberBurnInShift()
    NightBrightness(night)
    var detail by remember { mutableStateOf<Tile?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.toasts.collect { text ->
            toast = text
            delay(2_600)
            if (toast == text) toast = null
        }
    }

    // This screen is the «display»: keep it lit only while it is shown (released when leaving).
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // «Цвет акцента из обложки»: the accent follows the playing track.
    val coverBitmap = cover?.takeIf { it.first == media?.coverKey }?.second
    val dark = Palette.theme.dark
    LaunchedEffect(coverBitmap, prefs.coverColors, dark, media != null) {
        Palette.cover = if (prefs.coverColors && media != null && coverBitmap != null)
            withContext(Dispatchers.Default) { coverColor(coverBitmap, dark) } else null
    }
    DisposableEffect(Unit) { onDispose { Palette.cover = null } }

    val connected = state is ConnectionState.Connected
    val paired = state !is ConnectionState.NotPaired
    val saver = !connected && paired && prefs.offlineClock          // without a connection: only the clock
    val nextLayout = {
        val next = LAYOUTS[(LAYOUTS.indexOfFirst { it.first == prefs.layout } + 1) % LAYOUTS.size]
        onLayout(next.first)
        viewModel.toast("Раскладка: ${next.second}")
    }

    Box(Modifier.fillMaxSize().background(Palette.Black)) {
        // Backgrounds of the playing track: a soft glow of its colour and, if chosen, the blurred cover.
        val glow = Palette.cover
        if (glow != null) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow.copy(alpha = 0.22f), Palette.Black))))
        }
        val lite = com.kirdev.pulsedeckpanel.core.ui.Device.lite
        if (prefs.coverBackdrop && !lite && media != null && coverBitmap != null && Build.VERSION.SDK_INT >= 31) {
            Image(coverBitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp).graphicsLayer { alpha = if (dark) 0.35f else 0.45f })
        }
        Box(
            Modifier.fillMaxSize()
                .then(if (prefs.burnIn) Modifier.offset(shift.x, shift.y) else Modifier)   // AMOLED burn-in protection
                .graphicsLayer { alpha = if (night) 0.45f else 1f }     // night mode: dimmer than the lowest brightness
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
          Column(Modifier.fillMaxSize()) {
            // The top line has a strip of its own (TOP_STRIP): the clock and the cards start below it.
            Row(Modifier.fillMaxWidth().height(TOP_STRIP), verticalAlignment = Alignment.CenterVertically) {
                StatusLine(state, metrics, saver, onConnection, Modifier.weight(1f))
                Box {
                    IconButtonSmall(PanelIcons.Settings, if (updateAvailable) "Настройки, есть обновление" else "Настройки", onClick = onSettings)
                    if (updateAvailable) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(CircleShape).background(Palette.Text))
                }
            }
            val clockTap = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                enabled = prefs.gestures) { nextLayout() }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val height = maxHeight
                val width = maxWidth
                val hasPlayer = media != null || prefs.playerAlways
                val hasActivity = prefs.showActivity && activity != null
                if (saver) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ClockBlock(prefs, fitClock(prefs, "saver", width, height, false, false))
                    }
                } else when (prefs.layout) {
                    "music" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ClockBlock(prefs, 34.sp, clockTap, showDate = false, weight = FontWeight.Light)
                        Center(viewModel, prefs, media, coverBitmap, volume, PlayerSize.BIG, 720.dp, min(150.dp, height * 0.34f),
                            Modifier.weight(1f).fillMaxWidth(), playerExtras)
                        Tiles(prefs, metrics, volume, history, AlertMonitor.tilesOf(alerts), { detail = it }, Modifier.padding(bottom = 2.dp))
                    }
                    "monitoring" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ClockBlock(prefs, fitClock(prefs, "monitoring", width, height, hasPlayer, hasActivity), clockTap, dateSize = 13.sp)
                        ActivityLine(prefs, activity)
                        if (media != null || prefs.playerAlways) {
                            Spacer(Modifier.height(6.dp))
                            CompactPlayer(media, coverBitmap, volume, prefs, viewModel::command, Modifier.widthIn(max = 460.dp),
                                PlayerSize.SMALL, viewModel::toast, players = players, source = source,
                                onSource = viewModel::chooseSource)
                        }
                        Spacer(Modifier.height(10.dp))
                        Tiles(prefs, metrics, volume, history, AlertMonitor.tilesOf(alerts), { detail = it },
                            Modifier.weight(1f).padding(bottom = 2.dp), cards = true)
                    }
                    else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        ClockBlock(prefs, fitClock(prefs, prefs.layout, width, height, hasPlayer, hasActivity), clockTap)
                        ActivityLine(prefs, activity)
                        Center(viewModel, prefs, media, coverBitmap, volume, PlayerSize.NORMAL,
                            if (prefs.layout == "clock") 460.dp else 560.dp, PlayerSize.NORMAL.cover, Modifier.weight(1f).fillMaxWidth(),
                            playerExtras)
                        Tiles(prefs, metrics, volume, history, AlertMonitor.tilesOf(alerts), { detail = it }, Modifier.padding(bottom = 2.dp))
                    }
                }
            }
          }
        }
        AnimatedVisibility(toast != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp), enter = fadeIn(), exit = fadeOut()) {
            Text(toast.orEmpty(), fontSize = 13.sp, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(12.dp)).background(Palette.Surface2)
                    .padding(horizontal = 14.dp, vertical = 9.dp))
        }
    }
    detail?.let { tile -> TileDetail(tile, metrics, volume, history) { detail = null } }
}

/** The clock of each layout: the chosen size; bigger in «Часы», smaller in «Мониторинг». */
/** Height of the top line (PC, uptime, battery, settings); the layouts start below it. */
private val TOP_STRIP = 40.dp

/**
 * The clock of each layout: the chosen size (bigger in «Часы», smaller in «Мониторинг»), but
 * never taller than the room the rest of the layout leaves — the date, «Сейчас на ПК», the
 * player at its full height and the tiles — so nothing runs into anything.
 */
private fun fitClock(prefs: DisplayPrefs, layout: String, width: Dp, height: Dp, player: Boolean, activity: Boolean): androidx.compose.ui.unit.TextUnit {
    val wanted = when (layout) {
        "clock", "saver" -> clockSize(prefs).value * 1.6f
        "monitoring" -> 64f
        else -> clockSize(prefs).value
    }
    if (layout == "saver") return minOf(wanted, height.value * 0.7f).sp
    val perRow = ((width.value + 24) / (if (layout == "monitoring") 170f else 150f)).toInt().coerceAtLeast(1)
    val tiles = prefs.shownTiles.size
    val rows = if (prefs.tileRows == "scroll" && layout != "monitoring") 1 else (tiles + perRow - 1) / perRow
    val used = (if (prefs.showDate) 24f else 0f) + (if (activity) 22f else 0f) +
        when (layout) {
            "monitoring" -> (if (player) 82f else 0f) + rows * 78f + 20f      // small player, tile cards
            else -> (if (player) 112f else 40f) + rows * 54f + 8f            // the full player card, tile rows
        }
    // A clock line is about 1.2 × its font size tall (seconds sit beside, not above).
    val room = ((height.value - used) / 1.2f).coerceAtLeast(28f)
    val byWidth = width.value / (if (prefs.use24h) 3.1f else 4.6f) / (if (prefs.seconds) 1.25f else 1f)   // «00:00» fits across
    return minOf(wanted, room, byWidth).sp
}

/** Between the clock and the tiles: the player, or the idle animation when nothing plays. */
@Composable
private fun Center(
    viewModel: DashboardViewModel,
    prefs: DisplayPrefs,
    media: com.kirdev.pulsedeckpanel.data.model.MediaSession?,
    cover: android.graphics.Bitmap?,
    volume: com.kirdev.pulsedeckpanel.data.model.SystemVolume?,
    size: PlayerSize,
    maxWidth: Dp,
    coverSize: Dp,
    modifier: Modifier,
    extras: PlayerExtras,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val room = this
        val showPlayer = media != null || prefs.playerAlways
        AnimatedContent(
            targetState = showPlayer to (media?.title),
            transitionSpec = { if (prefs.motion && !com.kirdev.pulsedeckpanel.core.ui.Device.lite) fadeIn(tween(400)) togetherWith fadeOut(tween(400)) else fadeIn(tween(0)) togetherWith fadeOut(tween(0)) },
            label = "player-or-idle",
        ) { (player, _) ->
            if (player) {
                // Not enough height for the chosen card (many tiles, a big clock): the compact one.
                val fits = if (room.maxHeight < 118.dp) PlayerSize.SMALL else size
                CompactPlayer(media, cover, volume, prefs, viewModel::command, Modifier.widthIn(max = maxWidth), fits,
                    viewModel::toast, if (fits == size) coverSize else fits.cover, extras.players, extras.source, extras.onSource)
            } else if (prefs.idle != "none" && Idle.exists(prefs.idle)) {
                val (w, h) = idleBox(prefs, room.maxWidth, room.maxHeight)
                IdleAnimation(prefs.idle, Palette.Accent, still = !prefs.motion, modifier = Modifier.width(w).height(h))
            } else {
                Spacer(Modifier.size(1.dp))
            }
        }
    }
}

/** The PC's players and the chosen one, for the source menu of the player card. */
private class PlayerExtras(val players: List<Player>, val source: String, val onSource: (String) -> Unit)

/** «Сейчас на ПК» under the date: the app in front and its window. */
@Composable
private fun ActivityLine(prefs: DisplayPrefs, activity: PcActivity?) {
    if (!prefs.showActivity || activity == null) return
    val title = activity.title.takeIf { it.isNotBlank() && it != activity.app }?.let { " — $it" }.orEmpty()
    Text(
        buildAnnotatedString {
            append(if (activity.fullscreen) "На весь экран: " else "Сейчас на ПК: ")
            withStyle(SpanStyle(color = Palette.Text2, fontWeight = FontWeight.Medium)) { append(activity.app) }
            append(title)
        },
        fontSize = 13.sp, color = Palette.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 560.dp).padding(top = 4.dp),
    )
}

/** «Размер анимации»: about the player's width, almost the whole width, or all the free space. */
private fun idleBox(prefs: DisplayPrefs, width: Dp, height: Dp): Pair<Dp, Dp> {
    if (prefs.idleSize == "full") return width to (height - 8.dp).coerceAtLeast(40.dp)
    val wide = prefs.idleSize == "wide"
    val w = minOf((if (wide) 1100f else 480f), width.value * (if (wide) 0.92f else 0.8f)).dp
    val h = minOf(Idle.height(prefs.idle) * (if (wide) 1.35f else 1f), (height.value - 8f).coerceAtLeast(20f)).dp
    return w to h
}

/** Top left: the PC, how long it has been on and its battery; a tap leads to pairing when it is needed. */
@Composable
private fun StatusLine(state: ConnectionState, metrics: Metrics?, saver: Boolean, onConnection: () -> Unit, modifier: Modifier = Modifier) {
    val online = state is ConnectionState.Connected
    // Pairing has to be redone: the badge leads there.
    val needsPairing = state is ConnectionState.CertificateChanged || state is ConnectionState.Revoked
    Row(
        modifier.widthIn(max = 520.dp).clickable(enabled = needsPairing, onClick = onConnection),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) {
            if (online) drawCircle(Palette.Accent) else drawCircle(Palette.Text3, style = Stroke(1.5.dp.toPx()))
        }
        Spacer(Modifier.width(8.dp))
        val extra = buildList {
            if (online && metrics?.uptime != null) add("работает ${Format.uptime(metrics.uptime)}")
            if (saver) add("нет связи")
        }
        Text(statusText(state) + extra.joinToString("") { " · $it" }, fontSize = 12.sp,
            color = if (online || needsPairing) Palette.Text2 else Palette.Text3,
            maxLines = if (needsPairing) 2 else 1, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
            modifier = Modifier.weight(1f, fill = false))
        val battery = metrics?.battery
        if (online && battery?.percent != null) {
            Spacer(Modifier.width(10.dp))
            BatteryIcon(battery.percent, battery.plugged)
        }
    }
}

/** The PC's battery (laptops): level, a bolt while charging, red when low and unplugged. */
@Composable
private fun BatteryIcon(percent: Double, plugged: Boolean) {
    val low = percent <= 20 && !plugged
    val ink = if (low) Palette.Warn else Palette.Text2
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(20.dp, 11.dp)) {
            val s = density
            drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width - 2.5f * s, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * s), style = Stroke(1.2f * s))
            drawRect(ink, topLeft = androidx.compose.ui.geometry.Offset(size.width - 2f * s, size.height * 0.3f),
                size = androidx.compose.ui.geometry.Size(2f * s, size.height * 0.4f))
            val inner = (size.width - 6.5f * s) * (percent / 100.0).toFloat().coerceIn(0.04f, 1f)
            drawRoundRect(ink, topLeft = androidx.compose.ui.geometry.Offset(2f * s, 2f * s),
                size = androidx.compose.ui.geometry.Size(inner, size.height - 4f * s),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(1f * s))
        }
        Spacer(Modifier.width(4.dp))
        Text((if (plugged) "⚡" else "") + "${percent.toInt()}%", fontSize = 11.sp, color = ink)
    }
}
