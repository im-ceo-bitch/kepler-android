package com.iumrah.beta.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.floor

/** IumrahAnimatedSearchBorder in IumrahSignatureChrome.swift: 5s revolution, 3pt stroke. */
@Composable
internal fun Modifier.iumrahAnimatedSearchBorder(shape: Shape, motionAllowed: Boolean, guidance: Boolean = false): Modifier {
    val palette = remember(guidance) {
        if (guidance) listOf(Color(0xFFF5A814), Color(0xFFFFD454), Color.White, Color(0xFFFFEBA6), Color(0xFFF5A814))
        else listOf(Color.Black, Color.Black, Color.White, Color.Gray, Color.Black, Color.White, Color.Black)
    }
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(motionAllowed) {
        phase = 0f
        if (motionAllowed) while (true) {
            phase = (SystemClock.elapsedRealtime() % 5_000L) / 5_000f
            delay(42) // 24 fps decorative timeline, as on iOS
        }
    }
    val stops = remember(palette, phase) {
        // Include the interpolated wrap color at both ends; rotate the gradient,
        // not the rounded rectangle or its hit region.
        val segments = palette.lastIndex
        val wrapped = (1f - phase) % 1f * segments
        val index = floor(wrapped).toInt().coerceIn(0, segments - 1)
        val seam = lerp(palette[index], palette[index + 1], (wrapped - index).coerceIn(0f, 1f))
        val shifted = (0 until segments).map { i -> ((i.toFloat() / segments + phase) % 1f) to palette[i] }
            .filter { it.first > 0f && it.first < 1f }.sortedBy { it.first }
        (listOf(0f to seam) + shifted + (1f to seam)).toTypedArray()
    }
    return this.clip(shape).background(MaterialTheme.colorScheme.surface)
        .border(3.dp, Brush.sweepGradient(*stops), shape)
}
