package com.iumrah.beta.ui.components
import com.iumrah.beta.ui.cupertino.Icon

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.design.IumrahGalaxyMetrics
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.IumrahMotion

@Composable
fun IumrahPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = IumrahMotion.CardPressedScale,
    cornerRadius: Dp = IumrahGalaxyMetrics.RadiusCard,
    background: Color = MaterialTheme.colorScheme.surface,
    pressedBackgroundAlpha: Float = 0.88f,
    shadowElevation: Dp = 0.dp,
    haptic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = IumrahMotion.press,
        label = "iumrah-press-scale",
    )
    val animatedBackground by animateColorAsState(
        targetValue = if (pressed && enabled) {
            background.copy(alpha = background.alpha * pressedBackgroundAlpha)
        } else {
            background
        },
        animationSpec = IumrahMotion.fastColor,
        label = "iumrah-press-background",
    )
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(if (shadowElevation > 0.dp) Modifier.shadow(shadowElevation, shape, clip = false) else Modifier)
            .clip(shape)
            .background(animatedBackground)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                onClick = {
                    if (haptic) IumrahHaptics.soft(view)
                    onClick()
                },
            ),
    ) {
        content()
    }
}

@Composable
fun IumrahPrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(IumrahGalaxyMetrics.PrimaryButtonHeight),
        enabled = enabled,
        pressedScale = 0.985f, // Matches iOS IumrahPrimaryButtonStyle
        cornerRadius = IumrahGalaxyMetrics.RadiusButton,
        background = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        pressedBackgroundAlpha = .84f,
        shadowElevation = 0.dp, // iOS primary button has no elevation
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = title,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
fun IumrahSecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(IumrahGalaxyMetrics.ControlHeight),
        pressedScale = 0.985f, // Matches iOS IumrahSecondaryButtonStyle
        cornerRadius = IumrahGalaxyMetrics.RadiusButton,
        background = MaterialTheme.colorScheme.surfaceVariant,
        pressedBackgroundAlpha = .90f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun IumrahSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(5.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun IumrahBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = modifier.offset(y = 6.dp).size(IumrahGalaxyMetrics.TouchTarget),
        cornerRadius = 18.dp,
        background = MaterialTheme.colorScheme.surfaceVariant,
        pressedScale = .93f,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = CupertinoSymbol.ChevronLeft,
                contentDescription = "Back",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun IumrahPill(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    foreground: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = foreground, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun IumrahDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val width by animateFloatAsState(
        targetValue = if (selected) 20f else 6f,
        animationSpec = IumrahMotion.selection,
        label = "indicator-width",
    )
    Box(
        modifier = modifier
            .size(width = width.dp, height = 6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.onBackground
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.16f),
            ),
    )
}
