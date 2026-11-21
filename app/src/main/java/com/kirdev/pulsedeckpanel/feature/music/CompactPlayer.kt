package com.kirdev.pulsedeckpanel.feature.music

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Numbers
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.ThinProgress
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.local.DisplayPrefs
import com.kirdev.pulsedeckpanel.data.model.MediaSession
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import com.kirdev.pulsedeckpanel.data.model.Player
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Card sizes of the layouts: «Panel» and «Часы» = normal, «Музыка» = big, «Мониторинг» = small. */
enum class PlayerSize(val cover: Dp, val title: TextUnit, val meta: TextUnit, val padH: Dp, val padV: Dp, val gap: Dp) {
    NORMAL(76.dp, 17.sp, 13.sp, 14.dp, 12.dp, 16.dp),
    BIG(150.dp, 24.sp, 15.sp, 20.dp, 18.dp, 22.dp),
    SMALL(48.dp, 15.sp, 12.sp, 12.dp, 8.dp, 12.dp),
}

/**
 * «Now playing» card (inspired by the iOS mini player): tap or drag the bar to seek. With
 * [session] null it is the «Ничего не играет» card («Показывать, даже когда ничего не играет»).
 * Gestures (when on): swipe left / right — next / previous, up / down — PC volume, double tap — pause.
 */
@Composable
fun CompactPlayer(
    session: MediaSession?,
    cover: Bitmap?,
    volume: SystemVolume?,
    prefs: DisplayPrefs,
    onCommand: (String, Number?) -> Unit,
    modifier: Modifier = Modifier,
    size: PlayerSize = PlayerSize.NORMAL,
    onToast: (String) -> Unit = {},
    coverSize: Dp = size.cover,
    players: List<Player> = emptyList(),
    source: String = "",
    onSource: (String) -> Unit = {},
) {
    // Position moves locally between PC updates; ticks only while playing (2×/s is enough for a bar).
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session?.status, session?.receivedAt, prefs.waveSeek) {
        while (session?.status == PlayStatus.PLAYING) {
            now = SystemClock.elapsedRealtime()
            delay(if (prefs.waveSeek) 250 else 500)
        }
        now = SystemClock.elapsedRealtime()
    }
    val playing = session?.status == PlayStatus.PLAYING
    var dragging by remember { mutableStateOf<Float?>(null) }
    var gestureVolume by remember { mutableStateOf<Int?>(null) }
    val position = session?.let { s -> dragging?.let { it * s.duration } ?: s.positionAt(now) } ?: 0.0
    val density = LocalDensity.current
    val current by rememberUpdatedState(Triple(session, volume, playing))
    val gestures = if (prefs.gestures && session != null) Modifier
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = {
                onCommand("toggle", null)
                onToast(if (current.third) "Пауза" else "Играть")
            })
        }
        .pointerInput(Unit) {
            var total = Offset.Zero
            var startedAt = 0L
            var startVolume = 0
            detectDragGestures(
                onDragStart = {
                    total = Offset.Zero
                    startedAt = SystemClock.elapsedRealtime()
                    startVolume = current.second?.level ?: 0
                },
                onDrag = { change, delta ->
                    change.consume()
                    total += delta
                    val dy = with(density) { -total.y.toDp().value }
                    val dx = with(density) { total.x.toDp().value }
                    if (current.second != null && abs(dy) > 24 && abs(dy) > abs(dx) * 1.4f) {
                        gestureVolume = (startVolume + dy / 3).roundToInt().coerceIn(0, 100)
                    }
                },
                onDragEnd = {
                    val dy = with(density) { -total.y.toDp().value }
                    val dx = with(density) { total.x.toDp().value }
                    val level = gestureVolume
                    gestureVolume = null
                    if (level != null) {
                        onCommand("volume", level)
                        onToast("Громкость ПК: $level%")
                    } else if (SystemClock.elapsedRealtime() - startedAt < 600 && abs(dx) > 60 && abs(dy) < 50) {
                        onCommand(if (dx < 0) "next" else "prev", null)
                        onToast(if (dx < 0) "Следующий трек" else "Предыдущий трек")
                    }
                },
                onDragCancel = { gestureVolume = null },
            )
        } else Modifier

    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))                  // FusionRadius.xl
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(24.dp))
            .then(gestures)
            .padding(horizontal = size.padH, vertical = size.padV),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefs.showCover) {
            Cover(cover, coverSize, pulse = prefs.pulse && prefs.motion && playing && !com.kirdev.pulsedeckpanel.core.ui.Device.lite)
            Spacer(Modifier.width(size.gap))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (prefs.nowPlaying && playing && prefs.motion && !com.kirdev.pulsedeckpanel.core.ui.Device.lite) {
                    NowPlayingBars(true, Modifier.size(if (size == PlayerSize.BIG) 22.dp else 16.dp, if (size == PlayerSize.BIG) 20.dp else 14.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(session?.title ?: "Ничего не играет", fontSize = size.title, fontWeight = FontWeight.SemiBold,
                    color = if (session != null) Palette.Text else Palette.Text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // Artist, then which app is playing (Spotify, Яндекс Музыка, a browser…) in the same line.
            if (session != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.artist.isNotBlank()) {
                    Text(session.artist, fontSize = size.meta, color = Palette.Text2, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (session.source.isNotBlank() || players.size > 1) {
                    SourceLabel((if (session.artist.isNotBlank()) "  ·  " else "") + session.source.uppercase(),
                        players, source, onSource)
                }
            }
            if (session == null && players.isNotEmpty()) {
                SourceLabel("ВЫБРАТЬ ИСТОЧНИК", players, source, onSource)
            }
            if (session != null && prefs.showProgress && session.duration > 0) {
                Spacer(Modifier.height(if (size == PlayerSize.SMALL) 2.dp else 8.dp))
                SeekBar(
                    fraction = (position / session.duration).toFloat(),
                    wave = prefs.waveSeek, playing = playing, motion = prefs.motion,
                    onDrag = { dragging = it },
                    onSeek = { fraction ->
                        dragging = null
                        onCommand("seek", (fraction * session.duration).coerceIn(0.0, session.duration))
                    },
                )
                if (size != PlayerSize.SMALL) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Format.time(position), style = Numbers.copy(color = Palette.Text3))
                    Text(Format.time(session.duration), style = Numbers.copy(color = Palette.Text3))
                }
            }
        }
        Spacer(Modifier.width(size.gap))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val small = size == PlayerSize.SMALL
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlButton(PanelIcons.Prev, "Предыдущий трек", session?.canPrev == true, if (small) 36.dp else 42.dp) { onCommand("prev", null) }
                Spacer(Modifier.width(8.dp))
                ControlButton(if (playing) PanelIcons.Pause else PanelIcons.Play, if (playing) "Пауза" else "Воспроизвести",
                    enabled = session != null, size = if (small) 42.dp else if (size == PlayerSize.BIG) 58.dp else 50.dp,
                    ring = true) { onCommand("toggle", null) }
                Spacer(Modifier.width(8.dp))
                ControlButton(PanelIcons.Next, "Следующий трек", session?.canNext == true, if (small) 36.dp else 42.dp) { onCommand("next", null) }
            }
            if (volume != null && prefs.showVolume && !small) {
                VolumeRow(gestureVolume?.let { SystemVolume(it, false) } ?: volume) { onCommand("volume", it) }
            }
        }
    }
}

/**
 * Which app is playing; with several players on the PC a tap opens the list
 * («Автоматически» = the PC decides, as before).
 */
@Composable
private fun SourceLabel(text: String, players: List<Player>, source: String, onSource: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choosable = players.isNotEmpty()
    Box {
        Row(verticalAlignment = Alignment.CenterVertically,
            modifier = if (choosable) Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = true } else Modifier) {
            Text(text, fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Text3, maxLines = 1)
            if (choosable) Icon(PanelIcons.Down, contentDescription = "Выбрать источник музыки", tint = Palette.Text3,
                modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.Surface2) {
            val items = listOf(Player("", "Автоматически", "", "")) + players
            for (player in items) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(player.source, fontSize = 14.sp, color = Palette.Text)
                            if (player.title.isNotBlank()) Text(player.title, fontSize = 12.sp, color = Palette.Text3, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    },
                    leadingIcon = {
                        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            if (player.id == source) Icon(PanelIcons.Check, contentDescription = null, tint = Palette.Accent,
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { open = false; onSource(player.id) },
                )
            }
        }
    }
}

/** Four bars dancing next to the title while a track plays (the web panel's «.eq»). */
@Composable
private fun NowPlayingBars(motion: Boolean, modifier: Modifier) {
    val accent = Palette.Accent
    val transition = rememberInfiniteTransition(label = "eq")
    val durations = listOf(1000, 800, 1150, 900)
    val offsets = listOf(0, 300, 600, 150)
    val heights = durations.mapIndexed { i, d ->
        transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(d / 2), RepeatMode.Reverse,
            androidx.compose.animation.core.StartOffset(offsets[i])), label = "eq$i")
    }
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val w = (size.width - gap * 3) / 4
        heights.forEachIndexed { i, h ->
            val k = if (motion) h.value else 0.6f
            val bh = size.height * k
            drawRoundRect(accent, Offset(i * (w + gap), size.height - bh), Size(w, bh), CornerRadius(w / 2))
        }
    }
}

/** Track position with a 24 dp touch area: tap to jump, drag to scrub (sent when the finger lifts). */
/** Track position and PC volume: the shared [PulseSlider], slim enough for the panel's compact player. */
@Composable
private fun SeekBar(
    fraction: Float,
    onDrag: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    wave: Boolean = false,
    playing: Boolean = false,
    motion: Boolean = true,
    label: String = "Позиция трека",
    bubble: ((Float) -> String)? = null,
) {
    com.kirdev.pulsedeckpanel.core.ui.PulseSlider(fraction, onDrag, onSeek, label = label, track = 8.dp, wave = wave,
        playing = playing && motion, bubble = bubble, minHeight = 28.dp, motion = motion)
}

/** PC speaker volume (not the player's): the same thin bar as the track, sent when the finger lifts. */
@Composable
private fun VolumeRow(volume: SystemVolume, onSet: (Int) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val value = dragging ?: (volume.level / 100f)
    Row(Modifier.width(170.dp).padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(PanelIcons.Volume, contentDescription = null, tint = Palette.Text3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f).semantics { contentDescription = "Громкость ПК" }) {
            SeekBar(value, onDrag = { dragging = it }, onSeek = { dragging = null; onSet((it * 100).toInt()) }, label = "Громкость ПК")
        }
        Spacer(Modifier.width(8.dp))
        Text("${(value * 100).toInt()}", style = Numbers.copy(color = Palette.Text3), modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun Cover(cover: Bitmap?, size: Dp, pulse: Boolean) {
    val scale = if (pulse) {
        val k by rememberInfiniteTransition(label = "beat").animateFloat(1f, 1.035f,
            infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "beat-scale")
        k
    } else 1f
    Box(
        Modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(if (size > 100.dp) 16.dp else 12.dp)).background(Palette.Surface2),
        contentAlignment = Alignment.Center,
    ) {
        if (cover != null) {
            Image(cover.asImageBitmap(), contentDescription = "Обложка", contentScale = ContentScale.Crop,
                modifier = Modifier.size(size))
        } else {
            Icon(PanelIcons.Music, contentDescription = null, tint = Palette.Text3, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector, label: String, enabled: Boolean, size: Dp = 42.dp, ring: Boolean = false, onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .then(if (ring) Modifier.border(1.4.dp, Palette.AccentRing, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Palette.Text else Palette.Disabled,
            modifier = Modifier.size(if (ring) (size.value * 0.44f).dp else 24.dp))
    }
}
