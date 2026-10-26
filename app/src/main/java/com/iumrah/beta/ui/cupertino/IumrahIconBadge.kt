package com.iumrah.beta.ui.cupertino

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Android implementation of iOS Sources/Core/DesignSystem.swift::IumrahIconBadge.
 * The native glyph is always a real Cupertino font codepoint; the badge is a
 * solid semantic fill, with a contrasting foreground and iOS-defined geometry.
 *
 * This is a common UI primitive, not a replacement for custom illustrations.
 */
@Composable
fun IumrahIconBadge(
    symbol: CupertinoSymbol,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    symbolSize: Dp = 16.dp,
    cornerRadius: Dp = 14.dp,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(cornerRadius)
    // iOS uses dark foreground only on its yellow waiting/warning/rating roles.
    val foreground = if (tint == Color(0xFFFFCC00)) Color.Black.copy(alpha = 0.78f) else Color.White
    Box(
        modifier = modifier.size(size).clip(shape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        CupertinoIcon(symbol, null, Modifier.size(symbolSize), foreground)
    }
}
