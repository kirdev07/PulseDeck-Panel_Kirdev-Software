package com.kirdev.pulsedeckpanel.feature.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirdev.pulsedeckpanel.core.theme.Palette
import com.kirdev.pulsedeckpanel.core.ui.IconButtonSmall
import com.kirdev.pulsedeckpanel.core.ui.PanelIcons
import com.kirdev.pulsedeckpanel.data.model.ConnectionState
import com.kirdev.pulsedeckpanel.feature.clock.ClockBlock
import com.kirdev.pulsedeckpanel.feature.clock.rememberMinuteClock
import com.kirdev.pulsedeckpanel.feature.monitoring.MetricsBar
import com.kirdev.pulsedeckpanel.feature.music.CompactPlayer
import com.kirdev.pulsedeckpanel.feature.pulse.KiroXMotion

@Composable
fun DashboardScreen(viewModel: DashboardViewModel, onSettings: () -> Unit, onConnection: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val media by viewModel.media.collectAsStateWithLifecycle()
    val cover by viewModel.cover.collectAsStateWithLifecycle()
    val bar by viewModel.bar.collectAsStateWithLifecycle()
    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val prefs by viewModel.display.collectAsStateWithLifecycle()
    val night = prefs.isNight(rememberMinuteClock().hour)
    val shift = rememberBurnInShift()
    NightBrightness(night)

    // This screen is the «display»: keep it lit only while it is shown (released when leaving).
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Box(
        Modifier.fillMaxSize().background(Palette.Black)
            .offset(shift.x, shift.y)                               // AMOLED burn-in protection
            .graphicsLayer { alpha = if (night) 0.45f else 1f }     // night mode: dimmer than the lowest brightness
            .padding(horizontal = 28.dp, vertical = 14.dp),
    ) {
        ConnectionBadge(state, onConnection, Modifier.align(Alignment.TopStart).padding(top = 10.dp))
        IconButtonSmall(PanelIcons.Settings, "Настройки", onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd))

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            ClockBlock(size = 128.sp, use24h = prefs.use24h)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val session = media
                AnimatedContent(
                    targetState = session?.title to (session != null),
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "pulse-or-music",
                ) { (_, playing) ->
                    if (playing && session != null) {
                        CompactPlayer(session, cover?.takeIf { it.first == session.coverKey }?.second, volume,
                            onCommand = viewModel::command, modifier = Modifier.widthIn(max = 560.dp))
                    } else {
                        KiroXMotion()
                    }
                }
            }
            MetricsBar(bar, Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun ConnectionBadge(state: ConnectionState, onConnection: () -> Unit, modifier: Modifier = Modifier) {
    val online = state is ConnectionState.Connected
    // Pairing has to be redone: the badge leads there.
    val needsPairing = state is ConnectionState.CertificateChanged || state is ConnectionState.Revoked
    Row(
        modifier.widthIn(max = 230.dp).clickable(enabled = needsPairing, onClick = onConnection),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(7.dp)) {
            if (online) drawCircle(Palette.Text) else drawCircle(Palette.Text3, style = Stroke(1.5.dp.toPx()))
        }
        Spacer(Modifier.width(8.dp))
        Text(statusText(state), fontSize = 12.sp, color = if (online || needsPairing) Palette.Text2 else Palette.Text3,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
    }
}
