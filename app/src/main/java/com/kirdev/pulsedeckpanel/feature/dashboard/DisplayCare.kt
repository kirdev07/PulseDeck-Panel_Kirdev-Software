package com.kirdev.pulsedeckpanel.feature.dashboard

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * AMOLED burn-in protection: the whole display slowly walks through 9 positions within
 * ±[range] (one step every [periodMs], 2 s glide), so static white pixels never stay put.
 */
@Composable
fun rememberBurnInShift(range: Int = 5, periodMs: Long = 120_000): DpOffset {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMs)
            step = (step + 1) % ORBIT.size
        }
    }
    val (dx, dy) = ORBIT[step]
    val x by animateDpAsState((dx * range).dp, tween(2_000), label = "shift-x")
    val y by animateDpAsState((dy * range).dp, tween(2_000), label = "shift-y")
    return DpOffset(x, y)
}

private val ORBIT = listOf(0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

/** Night mode: the screen goes to the lowest brightness; restored when it ends or the screen closes. */
@Composable
fun NightBrightness(night: Boolean) {
    val view = LocalView.current
    DisposableEffect(night, view) {
        val window = (view.context as? Activity)?.window
        window?.attributes = window?.attributes?.apply {
            screenBrightness = if (night) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        onDispose {
            window?.attributes = window?.attributes?.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}
