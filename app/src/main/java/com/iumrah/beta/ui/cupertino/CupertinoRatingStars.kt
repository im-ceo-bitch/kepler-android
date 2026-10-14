package com.iumrah.beta.ui.cupertino

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

/** Shared star rating; never use platform-dependent ★ text glyphs as icons. */
@Composable
fun CupertinoRatingStars(
    count: Int,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFFFB000),
    size: Dp = 12.dp,
    spacing: Dp = 2.dp,
) {
    val safeCount = count.coerceIn(0, 5)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = "$safeCount / 5" },
        horizontalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(safeCount) { CupertinoIcon(CupertinoSymbol.StarFill, null, Modifier.size(size), tint) }
    }
}
