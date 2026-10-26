package com.kirdev.pulsedeckpanel.feature.music

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Numbers
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.core.ui.ThinProgress
import com.kirdev.pulsedeckpanel.core.utils.Format
import com.kirdev.pulsedeckpanel.data.model.MediaSession
import com.kirdev.pulsedeckpanel.data.model.PlayStatus
import com.kirdev.pulsedeckpanel.data.model.SystemVolume
import kotlinx.coroutines.delay

/** Compact «now playing» card (inspired by the iOS mini player): tap or drag the bar to seek. */
@Composable
fun CompactPlayer(
    session: MediaSession,
    cover: Bitmap?,
    volume: SystemVolume?,
    onCommand: (String, Number?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Position moves locally between PC updates; ticks only while playing (2×/s is enough for a bar).
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.status, session.receivedAt) {
        while (session.status == PlayStatus.PLAYING) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
        now = SystemClock.elapsedRealtime()
    }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val position = dragging?.let { it * session.duration } ?: session.positionAt(now)
    Row(
        modifier
            .clip(RoundedCornerShape(24.dp))                  // FusionRadius.xl
            .background(Palette.Surface)
            .border(1.dp, Palette.Border, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(cover, 76.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(session.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Artist, then which app is playing (Spotify, Яндекс Музыка, a browser…) in the same line.
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (session.artist.isNotBlank()) {
                    Text(session.artist, fontSize = 13.sp, color = Palette.Text2, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                if (session.source.isNotBlank()) {
                    Text((if (session.artist.isNotBlank()) "  ·  " else "") + session.source.uppercase(),
                        fontSize = 10.sp, letterSpacing = 1.sp, color = Palette.Text3, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (session.duration > 0) {
                SeekBar(
                    fraction = (position / session.duration).toFloat(),
                    onDrag = { dragging = it },
                    onSeek = { fraction ->
                        dragging = null
                        onCommand("seek", (fraction * session.duration).coerceIn(0.0, session.duration))
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Format.time(position), style = Numbers.copy(color = Palette.Text3))
                    Text(Format.time(session.duration), style = Numbers.copy(color = Palette.Text3))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlButton(PanelIcons.Prev, "Предыдущий трек", session.canPrev) { onCommand("prev", null) }
                Spacer(Modifier.width(8.dp))
                val playing = session.status == PlayStatus.PLAYING
                ControlButton(if (playing) PanelIcons.Pause else PanelIcons.Play, if (playing) "Пауза" else "Воспроизвести",
                    enabled = true, size = 50.dp, ring = true) { onCommand("toggle", null) }
                Spacer(Modifier.width(8.dp))
                ControlButton(PanelIcons.Next, "Следующий трек", session.canNext) { onCommand("next", null) }
            }
            if (volume != null) VolumeRow(volume) { onCommand("volume", it) }
        }
    }
}

/** Thin bar with a 24 dp touch area: tap to jump, drag to scrub (sent when the finger lifts). */
@Composable
private fun SeekBar(fraction: Float, onDrag: (Float) -> Unit, onSeek: (Float) -> Unit) {
    var width by remember { mutableFloatStateOf(1f) }
    var current by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxWidth().height(24.dp)
            .pointerInput(Unit) {
                width = size.width.toFloat()
                detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { current = (it.x / size.width).coerceIn(0f, 1f); onDrag(current) },
                    onHorizontalDrag = { change, _ -> current = (change.position.x / size.width).coerceIn(0f, 1f); onDrag(current) },
                    onDragEnd = { onSeek(current) },
                    onDragCancel = { onSeek(current) },
                )
            }
            .semantics { contentDescription = "Позиция трека" },
        contentAlignment = Alignment.Center,
    ) {
        ThinProgress(fraction, Modifier.fillMaxWidth())
    }
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
            SeekBar(value, onDrag = { dragging = it }, onSeek = { dragging = null; onSet((it * 100).toInt()) })
        }
        Spacer(Modifier.width(8.dp))
        Text("${(value * 100).toInt()}", style = Numbers.copy(color = Palette.Text3), modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun Cover(cover: Bitmap?, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(Palette.Surface2),
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
            .then(if (ring) Modifier.border(1.4.dp, Palette.Text2, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Palette.Text else Palette.Disabled,
            modifier = Modifier.size(if (ring) 22.dp else 24.dp))
    }
}
