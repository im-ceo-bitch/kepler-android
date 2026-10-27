package com.iumrah.beta.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.IumrahIconBadge

enum class BookingPrimaryPageAndroid { BOOKING, STATUS, SCHEDULE }

internal const val BOOKING_PAGE_PADDING = 18

@Composable
internal fun bookingIosDark(): Boolean {
    val c = MaterialTheme.colorScheme.background
    return (.2126f * c.red + .7152f * c.green + .0722f * c.blue) < .45f
}

@Composable internal fun bookingIosPage(): Color = if (bookingIosDark()) Color.Black else Color(0xFFF2F2F7)
@Composable internal fun bookingIosCard(): Color = if (bookingIosDark()) Color(0xFF1C1C1E) else Color.White
@Composable internal fun bookingIosRaised(): Color = if (bookingIosDark()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
@Composable internal fun bookingIosPrimary(): Color = if (bookingIosDark()) Color(0xFFF5F5F7) else Color.Black
@Composable internal fun bookingIosPrimaryText(): Color = if (bookingIosDark()) Color(0xFF121316) else Color.White
internal val BookingCareDark = Color(0xFF0E2422)
internal val BookingCareLight = Color(0xFF74A187)

@Composable
internal fun <T> BookingSegmentedControl(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val foreground = MaterialTheme.colorScheme.onBackground
    val activeSurface = if (bookingIosDark()) Color(0xFF3A3A3C) else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(foreground.copy(alpha = if (bookingIosDark()) .12f else .06f))
            .border(.55.dp, foreground.copy(alpha = .045f), RoundedCornerShape(11.dp))
            .padding(2.dp),
    ) {
        items.forEach { (value, label) ->
            val active = value == selected
            IumrahPressable(
                onClick = {
                    if (!active) IumrahHaptics.selection(view)
                    onSelect(value)
                },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                cornerRadius = 9.dp,
                background = if (active) activeSurface else Color.Transparent,
                shadowElevation = if (active && !bookingIosDark()) 1.dp else 0.dp,
                pressedScale = .988f,
                haptic = false,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = foreground.copy(alpha = if (active) .96f else .70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
internal fun BookingPageSwitcher(
    language: AppLanguage,
    selected: BookingPrimaryPageAndroid,
    onSelected: (BookingPrimaryPageAndroid) -> Unit,
    modifier: Modifier = Modifier,
) {
    BookingSegmentedControl(
        items = listOf(
            BookingPrimaryPageAndroid.BOOKING to bookingText(language, "Бронирование", "Booking", "Bron", "Брон"),
            BookingPrimaryPageAndroid.STATUS to bookingText(language, "Статус", "Status", "Holat", "Ҳолат"),
            BookingPrimaryPageAndroid.SCHEDULE to bookingText(language, "Расписание", "Schedule", "Jadval", "Жадвал"),
        ),
        selected = selected,
        onSelect = onSelected,
        modifier = modifier,
    )
}

@Composable
internal fun BookingNavigationBar(
    title: String,
    subtitle: String? = null,
    chrome: AppChromeStore,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (subtitle.isNullOrBlank()) 52.dp else 58.dp),
    ) {
        IumrahPressable(
            onClick = chrome::back,
            modifier = Modifier.align(Alignment.CenterStart).size(40.dp),
            cornerRadius = 20.dp,
            background = bookingIosRaised().copy(alpha = if (bookingIosDark()) .92f else .86f),
            pressedScale = .94f,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CupertinoIcon(
                    CupertinoSymbol.ChevronLeft,
                    contentDescription = "Back",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                title,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BookingCard(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    radius: Dp = 28.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onBackground
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(18.dp, RoundedCornerShape(radius), clip = false, ambientColor = Color.Black.copy(alpha = .025f), spotColor = Color.Black.copy(alpha = .045f))
            .clip(RoundedCornerShape(radius))
            .background(bookingIosCard())
            .border(.7.dp, fg.copy(alpha = .075f), RoundedCornerShape(radius))
            .padding(padding),
        content = content,
    )
}

@Composable
internal fun BookingRaisedCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    radius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(bookingIosRaised())
            .padding(padding),
        content = content,
    )
}

@Composable
internal fun BookingSectionHeader(
    title: String,
    eyebrow: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                eyebrow.uppercase(),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                letterSpacing = .7.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            )
        }
        Text(
            title,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = (-.7).sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            )
        }
    }
}

@Composable
internal fun BookingIconBadge(
    symbol: CupertinoSymbol,
    tint: Color,
    size: Dp = 44.dp,
    symbolSize: Dp = 17.dp,
    radius: Dp = 15.dp,
    circle: Boolean = false,
) {
    IumrahIconBadge(
        symbol = symbol,
        tint = tint,
        size = size,
        symbolSize = symbolSize,
        cornerRadius = radius,
        circle = circle,
    )
}

@Composable
internal fun BookingPrimaryAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: CupertinoSymbol? = null,
    trailing: CupertinoSymbol? = null,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        cornerRadius = 19.dp,
        background = bookingIosPrimary(),
        pressedScale = .985f,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            leading?.let { CupertinoIcon(it, null, Modifier.size(18.dp), bookingIosPrimaryText()) }
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = bookingIosPrimaryText(),
                textAlign = if (leading == null && trailing == null) TextAlign.Center else TextAlign.Start,
            )
            trailing?.let { CupertinoIcon(it, null, Modifier.size(15.dp), bookingIosPrimaryText()) }
        }
    }
}

@Composable
internal fun BookingSecondaryAction(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: CupertinoSymbol? = null,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(56.dp),
        cornerRadius = 19.dp,
        background = bookingIosRaised(),
        pressedScale = .985f,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = if (trailing == null) TextAlign.Center else TextAlign.Start,
            )
            trailing?.let { CupertinoIcon(it, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .68f)) }
        }
    }
}

@Composable
internal fun BookingCareCard(
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 30.dp,
        background = Color.Transparent,
        pressedScale = .985f,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(BookingCareDark.copy(alpha = .96f), BookingCareLight.copy(alpha = .92f)),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(30.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.HeartFill, null, Modifier.size(27.dp), BookingCareDark)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text(body, color = Color.White.copy(alpha = .76f), fontSize = 14.sp, lineHeight = 19.sp)
                Text(action, color = Color.White.copy(alpha = .92f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            }
            CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(19.dp), Color.White.copy(alpha = .88f))
        }
    }
}

internal fun bookingText(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
