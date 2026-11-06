package io.github.romanvht.byedpi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Green from 80 %, amber from 40 %, red below. */
fun scoreColor(percent: Int): Color = when {
    percent >= 80 -> Palette.Success
    percent >= 40 -> Palette.Warning
    else -> Palette.Error
}

fun percentOf(part: Int, whole: Int): Int = if (whole <= 0) 0 else part * 100 / whole

/** A short coloured label. [large] is for facts people act on (sites opened, speed, gains and losses). */
@Composable
fun MetricPill(text: String, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text,
        color = color,
        fontSize = if (large) 13.sp else 10.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = if (large) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(if (large) 10.dp else 8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = if (large) 10.dp else 7.dp, vertical = if (large) 5.dp else 3.dp)
    )
}
