package com.kirdev.pulsedeckpanel.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirdev.pulsedeckpanel.core.theme.Palette

/** 3 dp bar; drawn in one Canvas instead of nested layouts. */
@Composable
fun ThinProgress(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.height(3.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(Palette.Track, cornerRadius = radius)
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) drawRoundRect(Palette.Text, size = Size(width, size.height), cornerRadius = radius)
    }
}

@Composable
fun IconButtonSmall(icon: ImageVector, label: String, modifier: Modifier = Modifier, size: Dp = 40.dp, onClick: () -> Unit) {
    Box(
        modifier.size(size).clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Text2, modifier = Modifier.size(22.dp))
    }
}

/** Title row of secondary screens: back arrow + title. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButtonSmall(PanelIcons.Back, "Назад", onClick = onBack)
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 18.sp, color = Palette.Text)
    }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
