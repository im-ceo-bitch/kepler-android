package com.iumrah.beta.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import com.iumrah.beta.core.navigation.AppRoute
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.localization.L10n
import android.provider.Settings
import com.iumrah.beta.core.network.IumrahConnectivityStatus
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.delay

/**
 * Compose mirror of iOS IumrahRootPageTitle.
 * iOS remains the source of truth for sizes, spacing and visual hierarchy.
 */
@Composable
fun IumrahRootPageHeader(
    title: String,
    chrome: AppChromeStore,
    modifier: Modifier = Modifier,
    usesBrandLogo: Boolean = false,
    brandScale: Float = 1f,
    showsConnectivityStatus: Boolean = false,
    unreadCount: Int = 0,
    showsMakkahTime: Boolean = false,
    lightStyle: Boolean = false,
    drawsSignatureBackground: Boolean = true,
    roundedBottom: Boolean = false,
    language: AppLanguage = AppLanguage.ENGLISH,
    subtitle: String? = null,
    discovery: Boolean = false,
    onHelp: (() -> Unit)? = null,
    flexibleFooter: Boolean = false,
    animateMap: Boolean = true,
    footer: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val statusTopPx = WindowInsets.statusBars.getTop(density)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            // Report header visibility as the user scrolls. Only root-page
            // headers participate; nested/detail headers are not the status surface.
            .onGloballyPositioned { coordinates ->
                val state = chrome.state.value
                if (state.route == AppRoute.Root && !lightStyle && drawsSignatureBackground) {
                    chrome.reportMastheadVisibility(state.currentTab,
                        coordinates.boundsInWindow().bottom > statusTopPx)
                }
            }
            .then(if (lightStyle) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (!lightStyle) {
            // Exact iOS IumrahPrimaryHeaderBrandRow hierarchy:
            // menu (48) | flexible space | centered wordmark | flexible space | bell (48).
            // No app-specific connectivity caption and no second wordmark.
            // The bleed adds the two page insets back before the brand row is measured.
            val headerWidth = maxWidth + if (drawsSignatureBackground) IumrahDesign.PagePadding * 2 else 0.dp
            // iOS: 18pt edge insets, two 48pt slots, two minimum 4pt gaps.
            val slotWidth = if (onHelp != null) 88.dp else 48.dp
            val logoWidth = minOf(IumrahDesign.SignatureLogoWidth,
                (headerWidth - IumrahDesign.PagePadding * 2 - slotWidth * 2 - 8.dp).coerceAtLeast(0.dp))
            val bottomRadius = if (roundedBottom || discovery) IumrahDesign.SignatureBottomRadius else 0.dp
            val container = if (drawsSignatureBackground) Modifier.iumrahSignatureBleed()
                else Modifier.fillMaxWidth()
            val headerHeight = IumrahDesign.SignatureHeaderHeight + when {
                discovery -> IumrahDesign.DiscoveryHeaderExtra
                footer != null -> IumrahDesign.ActionHeaderExtra
                else -> 0.dp
            }
            Box(container.clip(RoundedCornerShape(bottomStart = bottomRadius,
                    bottomEnd = bottomRadius))) {
                if (drawsSignatureBackground) {
                    IumrahSignatureWorldBackground(Modifier.matchParentSize(), mapOpacity = .33f, isAnimating = animateMap && !discovery)
                } else {
                    Box(Modifier.matchParentSize().background(IumrahColors.SignatureBlack))
                }
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().statusBarsPadding()
                        .then(if (flexibleFooter) Modifier else Modifier.height(headerHeight))
                        .padding(top = IumrahDesign.SignatureHeaderTop, bottom = if (flexibleFooter) 18.dp else 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = IumrahDesign.PagePadding).height(IumrahDesign.SignatureLogoHeight),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Box(Modifier.width(slotWidth), contentAlignment = Alignment.CenterStart) {
                            SignatureHeaderButton(CupertinoSymbol.Menu, 44.dp, chrome::openSidebar, headerCopy(language, "Меню", "Menu", "Menyu", "Меню"))
                        }
                        Spacer(Modifier.weight(1f))
                        Image(
                            painterResource(R.drawable.iumrah_header_wordmark_dark),
                            contentDescription = "iumrah",
                            modifier = Modifier.width(logoWidth).height(IumrahDesign.SignatureLogoHeight),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.weight(1f))
                        if (onHelp == null) SignatureHeaderButton(
                            if (unreadCount > 0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell,
                            48.dp, chrome::openNotifications, headerCopy(language, "Уведомления", "Notifications", "Bildirishnomalar", "Билдиришномалар"),
                        ) else Row(Modifier.width(88.dp).height(44.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = .105f)).border(.8.dp, Color.White.copy(alpha = .25f), CircleShape),
                            verticalAlignment = Alignment.CenterVertically) {
                            SignatureGroupAction(if (unreadCount > 0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell,
                                headerCopy(language, "Уведомления", "Notifications", "Bildirishnomalar", "Билдиришномалар"), chrome::openNotifications)
                            SignatureGroupAction(CupertinoSymbol.QuestionMark, headerCopy(language, "Что делать здесь?", "What can I do here?", "Bu yerda nima qilish kerak?", "Бу ерда нима қилиш керак?"), onHelp)
                        }
                    }
                    if (!usesBrandLogo) {
                        Text(
                            title,
                            modifier = Modifier.padding(horizontal = IumrahDesign.PagePadding).padding(top = 7.dp),
                            fontSize = 28.sp, lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold, color = Color.White,
                            maxLines = 1,
                        )
                    }
                    subtitle?.let {
                        Text(it, Modifier.padding(horizontal = IumrahDesign.PagePadding).padding(top = 3.dp),
                            fontSize = 14.sp, color = Color.White.copy(alpha = .76f), maxLines = 1)
                    }
                    if (footer != null) {
                        Spacer(Modifier.height(if (discovery) 33.dp else 12.dp))
                        footer()
                    }
                }
            }
        } else if (usesBrandLogo && showsConnectivityStatus) {
            val compact = maxWidth < 370.dp
            val veryCompact = maxWidth < 340.dp
            val brandWidth = when {
                veryCompact -> 96.dp
                compact -> 110.dp
                else -> 132.dp
            }
            val controlSize = when {
                veryCompact -> 38.dp
                compact -> 40.dp
                else -> 44.dp
            }
            val spacing = when {
                veryCompact -> 6.dp
                compact -> 7.dp
                else -> 8.dp
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                BrandWordmark(brandWidth, brandScale, lightStyle)
                Spacer(Modifier.weight(1f))
                AnimatedConnectivityIndicator(
                    lightStyle = lightStyle,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.width(spacing))
                HeaderControls(
                    chrome = chrome,
                    unreadCount = unreadCount,
                    controlSize = controlSize,
                    spacing = spacing,
                    lightStyle = lightStyle,
                    showsMakkahTime = showsMakkahTime,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (usesBrandLogo) {
                    BrandWordmark(180.dp * brandScale, brandScale, lightStyle)
                } else {
                    Text(
                        title,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.0).sp,
                        color = if (lightStyle) Color.White else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                HeaderControls(
                    chrome = chrome,
                    unreadCount = unreadCount,
                    controlSize = 46.dp,
                    spacing = 10.dp,
                    lightStyle = lightStyle,
                    showsMakkahTime = showsMakkahTime,
                )
            }
        }
    }
}

@Composable
private fun RowScope.SignatureGroupAction(symbol: CupertinoSymbol, label: String, onClick: () -> Unit) {
    Box(Modifier.weight(1f).height(44.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        CupertinoIcon(symbol, label, Modifier.size(19.dp), Color.White)
    }
}

@Composable
private fun SignatureHeaderButton(
    symbol: CupertinoSymbol,
    size: Dp,
    onClick: () -> Unit,
    label: String,
) {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.CenterStart) {
        Surface(
            modifier = Modifier.size(size),
            onClick = onClick,
            shape = CircleShape,
            color = Color.White.copy(alpha = .105f),
            border = BorderStroke(.8.dp, Color.White.copy(alpha = .25f)),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                CupertinoIcon(symbol, label, Modifier.size(17.dp), Color.White)
            }
        }
        // Current iOS notificationAction uses bell.badge, without a second numeric overlay.
    }
}

@Composable
private fun BrandWordmark(width: Dp, scale: Float, lightStyle: Boolean, fixedHeight: Dp? = null) {
    Image(
        painter = painterResource(
            if (lightStyle || isSystemInDarkTheme()) R.drawable.iumrah_header_wordmark_dark
            else R.drawable.iumrah_header_wordmark_light,
        ),
        contentDescription = "iumrah",
        modifier = Modifier.width(width).height(fixedHeight ?: (46.dp * scale)),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
    )
}

@Composable
private fun HeaderControls(
    chrome: AppChromeStore,
    unreadCount: Int,
    controlSize: Dp,
    spacing: Dp,
    lightStyle: Boolean,
    showsMakkahTime: Boolean,
) {
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)) {
            Box {
                GlassCircleButton(
                    size = controlSize,
                    lightStyle = lightStyle,
                    onClick = chrome::openNotifications,
                ) {
                    CupertinoIcon(
                        if (unreadCount > 0) CupertinoSymbol.BellSignal else CupertinoSymbol.Bell,
                        contentDescription = "Notifications",
                        modifier = Modifier.size(17.dp),
                        tint = if (lightStyle) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .graphicsLayer { translationX = -3.dp.toPx(); translationY = 4.dp.toPx() }
                            .height(16.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(Color(0xFFFF3B30))
                            .border(1.dp, Color.White.copy(alpha = .95f), RoundedCornerShape(99.dp))
                            .padding(horizontal = if (unreadCount > 9) 5.dp else 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (unreadCount > 9) "9+" else unreadCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            GlassCircleButton(
                size = controlSize,
                lightStyle = lightStyle,
                onClick = chrome::openSidebar,
            ) {
                CupertinoIcon(
                    CupertinoSymbol.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier.size(17.dp),
                    tint = if (lightStyle) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (showsMakkahTime) {
            Spacer(Modifier.height(8.dp))
            MakkahClock(lightStyle)
        }
    }
}

@Composable
private fun GlassCircleButton(
    size: Dp,
    lightStyle: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val fill = if (lightStyle) Color.Black.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .92f)
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(.7.dp, if (lightStyle) Color.White.copy(alpha = .12f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun MakkahClock(lightStyle: Boolean) {
    val now by produceState(initialValue = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Riyadh"))) {
        while (true) {
            value = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Riyadh"))
            delay(30_000)
        }
    }
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.End) {
        Text("Makkah", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (lightStyle) Color.White.copy(alpha = .96f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        Text(
            String.format("%02d:%02d", now.hour, now.minute),
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (lightStyle) Color.White.copy(alpha = .96f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .62f),
        )
    }
}

@Composable
private fun AnimatedConnectivityIndicator(
    lightStyle: Boolean,
    modifier: Modifier = Modifier,
    language: AppLanguage = AppLanguage.ENGLISH,
) {
    val app = LocalContext.current.applicationContext as IumrahApplication
    val status by app.container.connectivityMonitor.status.collectAsState()
    val motionEnabled = remember(LocalContext.current) {
        runCatching { Settings.Global.getFloat(
            app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f }.getOrDefault(true)
    }
    val localizedStatus = when (status) {
        IumrahConnectivityStatus.CHECKING -> when(language) {
            AppLanguage.RUSSIAN -> "Проверка"
            AppLanguage.UZBEK -> "Tekshirilmoqda"
            AppLanguage.UZBEK_CYRILLIC -> "Текширилмоқда"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Checking"
        }
        IumrahConnectivityStatus.ONLINE -> when(language) {
            AppLanguage.RUSSIAN -> "В сети"
            AppLanguage.UZBEK -> "Onlayn"
            AppLanguage.UZBEK_CYRILLIC -> "Онлайн"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Online"
        }
        IumrahConnectivityStatus.OFFLINE -> when(language) {
            AppLanguage.RUSSIAN -> "Не в сети"
            AppLanguage.UZBEK -> "Oflayn"
            AppLanguage.UZBEK_CYRILLIC -> "Офлайн"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Offline"
        }
    }
    var expanded by remember(status) { mutableStateOf(false) }
    LaunchedEffect(status) {
        if (!motionEnabled) {
            expanded = status != IumrahConnectivityStatus.CHECKING
            return@LaunchedEffect
        }
        if (status == IumrahConnectivityStatus.CHECKING) {
            expanded = false
            return@LaunchedEffect
        }
        while (true) {
            delay(1_650)
            expanded = true
            delay(3_650)
            expanded = false
            delay(620)
        }
    }
    val infinite = rememberInfiniteTransition(label = "connectivity-rail")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1550, easing = LinearEasing), RepeatMode.Restart),
        label = "connectivity-rotation",
    )
    val width by animateDpAsState(
        targetValue = if (expanded) 88.dp else 34.dp,
        animationSpec = if (motionEnabled) spring(dampingRatio = .82f, stiffness = 220f) else snap(),
        label = "connectivity-pill-morph",
    )
    // iOS ConnectivityStatusPill.swift uses a WHITE/GRAY angular rail in
    // both states. The old cyan/pink/rainbow Android rail broke the brand.
    val gradientStops = listOf(
        0f to Color.White.copy(alpha = .55f),
        .20f to Color.White.copy(alpha = .98f),
        .47f to Color(0xFF666666),
        .65f to Color(0xFF2E2E2E),
        .86f to Color.White.copy(alpha = .86f),
        1f to Color.White.copy(alpha = .55f),
    )
    // SwiftUI AngularGradient uses nonuniform stops and rotates the RAIL,
    // not the button content. Resample the same stops at 64 positions to
    // rotate without changing the capsule/text/globe geometry.
    val angularPhase = (if (motionEnabled) rotation else 18f) / 360f
    val rotatedStops = (0..64).map { sample ->
        val x = sample / 64f
        val u = ((x - angularPhase) % 1f + 1f) % 1f
        val right = gradientStops.indexOfFirst { it.first >= u }.coerceAtLeast(1)
        val leftStop = gradientStops[right - 1]
        val rightStop = gradientStops[right]
        val weight = ((u - leftStop.first) / (rightStop.first - leftStop.first)).coerceIn(0f, 1f)
        x to androidx.compose.ui.graphics.lerp(leftStop.second, rightStop.second, weight)
    }
    Box(modifier.width(88.dp).height(34.dp), contentAlignment = Alignment.CenterEnd) {
    Box(
        Modifier
            .width(width)
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color.Black.copy(alpha = .985f))
            .border(
                2.15.dp,
                Brush.sweepGradient(*rotatedStops.toTypedArray()),
                RoundedCornerShape(17.dp),
            ),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            Modifier.padding(start = if (expanded) 10.dp else 7.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) {
                Text(
                    localizedStatus,
                    modifier = Modifier.width(40.dp),
                    color = Color.White,
                    // SwiftUI's 40pt serif label allows a .86 scale floor.
                    // Use the equivalent reduction for longer Lato translations.
                    fontSize = if (localizedStatus.length > 7) 9.9.sp else 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
            }
            CupertinoIcon(CupertinoSymbol.Globe, null, Modifier.size(19.dp), Color.White)
        }
    }
    }
}

