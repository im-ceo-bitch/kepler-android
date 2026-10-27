package com.iumrah.beta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.design.IumrahDesign

/**
 * Standard CONTENT card corresponding to iOS DesignSystem.swift::IumrahCardModifier.
 * This is a solid surface, not Liquid Glass. Parameter values are taken from SwiftUI.
 * Shadow rendering is native per platform and requires screenshot verification.
 */
@Composable
fun Modifier.iumrahCard(
    background: Color = MaterialTheme.colorScheme.surface,
): Modifier {
    val shape = RoundedCornerShape(IumrahDesign.CardRadius)
    return this
        .fillMaxWidth()
        .shadow(
            elevation = 18.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.045f),
            spotColor = Color.Black.copy(alpha = 0.045f),
        )
        .clip(shape)
        .background(background)
        .border(width = 0.75.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.095f), shape = shape)
        .padding(18.dp)
}

@Composable
fun IumrahCardSurface(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) = Box(modifier = modifier.iumrahCard(background)) { content() }

/**
 * SwiftUI DesignSystem.swift::IumrahMarketingCardModifier. Kept independent of
 * Android Material card elevation so its geometry and palette stay deterministic.
 */
@Composable
fun IumrahMarketingCardSurface(
    dark: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(IumrahDesign.HeroRadius)
    val border = if (dark) Color.White.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.075f)
    val fill = if (dark) {
        Brush.linearGradient(listOf(IumrahColors.CareDark, IumrahColors.Graphite))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
    }
    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.14f else 0.045f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.14f else 0.045f),
            )
            .clip(shape)
            .background(fill)
            .border(width = 0.7.dp, color = border, shape = shape)
            .padding(22.dp),
    ) { content() }
}
