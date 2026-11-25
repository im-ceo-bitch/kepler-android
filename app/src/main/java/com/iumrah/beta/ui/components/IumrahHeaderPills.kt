package com.iumrah.beta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.LocalIOSParity
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

internal data class HeaderPill(val label: String, val symbol: CupertinoSymbol, val enabled: Boolean = true)

/** SwiftUI IumrahHeaderPills: horizontally scrollable independent 44pt capsules. */
@Composable
internal fun IumrahHeaderPills(pills: List<HeaderPill>, selected: Int? = null, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    val view = LocalView.current
    val parity = LocalIOSParity.current
    val motion = if (parity) rememberIumrahMotionAllowed() else false
    LaunchedEffect(selected, pills.size, motion) {
        val index = selected ?: return@LaunchedEffect
        if (index in pills.indices) {
            if (motion && listState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                // Center the selected item using measured widths where available.
                val viewport = listState.layoutInfo.viewportSize.width
                val itemWidth = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size
                val offset = itemWidth?.let { -((viewport - it) / 2).coerceAtLeast(0) } ?: 0
                listState.animateScrollToItem(index, scrollOffset = offset)
            } else listState.scrollToItem(index)
        }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(IumrahDesign.HeaderPillViewport).selectableGroup(),
        state = listState,
        contentPadding = PaddingValues(horizontal = IumrahDesign.PagePadding, vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(IumrahDesign.HeaderPillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(pills, key = { index, _ -> index }) { index, pill ->
            val active = selected == index
            val tint = if (active) Color.Black else Color.White.copy(alpha = if (pill.enabled) 1f else .5f)
            Row(
                Modifier.height(IumrahDesign.HeaderPillHeight).clip(CircleShape)
                    .background(if (active) Color.White else Color.White.copy(alpha = .07f))
                    .border(1.dp, Color.White.copy(alpha = if (active) 1f else .65f), CircleShape)
                    .selectable(selected = active, enabled = pill.enabled, role = Role.Button) {
                        IumrahHaptics.selection(view)
                        onSelect(index)
                    }.padding(horizontal = IumrahDesign.HeaderPillHorizontal),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CupertinoIcon(pill.symbol, null, Modifier.size(IumrahDesign.HeaderPillIcon), tint)
                Text(pill.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1)
            }
        }
    }
}

internal fun headerCopy(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
    AppLanguage.ENGLISH -> en
    AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
}

/** The My package destination owns an independent manual component basket. */
@Composable
internal fun IumrahDiscoveryPills(language: AppLanguage, selection: Int, includeGuides: Boolean = false, onSelect: (Int) -> Unit) {
    IumrahHeaderPills(listOf(
        HeaderPill(headerCopy(language, "Собрать умру", "Build Umrah", "Umra tuzish", "Умра тузиш"), CupertinoSymbol.Sparkles),
        HeaderPill(headerCopy(language, "Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар"), CupertinoSymbol.Bed),
        HeaderPill(headerCopy(language, "Авиабилеты", "Flights", "Aviachiptalar", "Авиачипталар"), CupertinoSymbol.Airplane),
        HeaderPill(headerCopy(language, "Трансфер", "Transfer", "Transfer", "Трансфер"), CupertinoSymbol.Car),
        HeaderPill(headerCopy(language, "Мой пакет", "My package", "Mening paketim", "Менинг пакетим"), CupertinoSymbol.Suitcase),
    ).let { pills -> if (includeGuides) pills.take(4) + HeaderPill(headerCopy(language, "Гиды и команда", "Guides & team", "Gidlar va jamoa", "Гидлар ва жамоа"), CupertinoSymbol.PersonCircle) + pills.drop(4) else pills }, selected = selection, onSelect = onSelect)
}

/** Measure both siblings in one viewport: the overlapped search remains fully tappable. */
@Composable
internal fun IumrahOverlappingMasthead(header: @Composable () -> Unit, search: @Composable () -> Unit) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = { Box { header() }; Box { search() } },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minHeight = 0)
        val masthead = measurables[0].measure(childConstraints)
        val card = measurables[1].measure(childConstraints)
        val overlap = IumrahDesign.SearchOverlap.roundToPx().coerceAtMost(minOf(masthead.height, card.height))
        val height = (masthead.height + card.height - overlap).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, height) {
            masthead.placeRelative(0, 0)
            card.placeRelative(0, masthead.height - overlap, zIndex = 1f)
        }
    }
}
