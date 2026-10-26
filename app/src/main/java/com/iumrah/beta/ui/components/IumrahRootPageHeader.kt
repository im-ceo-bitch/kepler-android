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
    language: AppLanguage = AppLanguage.ENGLISH,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        if (!lightStyle) {
            // Mirror SwiftUI ViewThatFits: choose the first ROW that fits,
            // not a hardcoded device breakpoint. maxWidth here is the inner
            // content width (screen width minus 36dp horizontal gutters).
            // SwiftUI HStack includes explicit spacing on EITHER side of
            // Spacer(minLength:0), so count 4 gaps when status is present.
            // Wide:   132+88+42+42+4*9=340; medium: 105+88+38+38+4*7=297.
            // No status: 132+42+42+3*9=243; medium: 105+38+38+3*7=202.
            val wideNeeded = if (showsConnectivityStatus) 340.dp else 243.dp
            val mediumNeeded = if (showsConnectivityStatus) 297.dp else 202.dp
            val rowMode = when {
                maxWidth >= wideNeeded -> 0
                maxWidth >= mediumNeeded -> 1
                else -> 2
            }
            val brandWidth = when (rowMode) {
                0 -> 132.dp
                1 -> 105.dp
                else -> 84.dp
            }
            val controlSize = when (rowMode) {
                0 -> 42.dp
                1 -> 38.dp
                else -> 36.dp
            }
            val spacing = when (rowMode) {
                0 -> 9.dp
                1 -> 7.dp
                else -> 5.dp
            }
            // iOS uses .padding(.horizontal, -18) around the signature header.
            // Expand the header BOTH ways, instead of spilling only to the right.
            // Hotels already owns the expanded shared surface; do not expand twice.
            Box(
                if (drawsSignatureBackground) Modifier.iumrahSignatureBleed()
                else Modifier.fillMaxWidth()
            ) {
                if (drawsSignatureBackground) {
                    IumrahSignatureWorldBackground(Modifier.matchParentSize())
                }
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 14.dp, bottom = if (showsConnectivityStatus) 22.dp else 25.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        BrandWordmark(brandWidth, 1f, lightStyle = true, fixedHeight = 42.dp)
                        Spacer(Modifier.weight(1f))
                        if (showsConnectivityStatus) {
                            AnimatedConnectivityIndicator(
                                lightStyle = true,
                                language = language,
                            )
                        }
                        SignatureHeaderButton(if (unreadCount > 0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell, controlSize, unreadCount, chrome::openNotifications)
                        SignatureHeaderButton(CupertinoSymbol.Menu, controlSize, 0, chrome::openSidebar)
                    }
                    if (!showsConnectivityStatus) {
                        Text(
                            title,
                            fontSize = 34.sp,
                            lineHeight = 39.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-.8).sp,
                            color = Color.White,
                            maxLines = 2,
                        )
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
private fun SignatureHeaderButton(
    symbol: CupertinoSymbol,
    size: Dp,
    unreadCount: Int,
    onClick: () -> Unit,
) {
    Box {
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
                CupertinoIcon(symbol, null, Modifier.size(17.dp), Color.White)
            }
        }
        if (unreadCount > 0) {
            // iOS unreadDot: a 16pt red capsule, white 1pt outline,
            // numeral (or 9+) and topTrailing offset (+3, -4).
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-4).dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFF3B30))
                    .border(1.dp, Color.White.copy(alpha = .95f), RoundedCornerShape(50))
                    .width(if (unreadCount > 9) 26.dp else 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unreadCount > 9) "9+" else unreadCount.toString(), color = Color.White,
                    fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
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
            AppLanguage.ENGLISH -> "Checking"
        }
        IumrahConnectivityStatus.ONLINE -> when(language) {
            AppLanguage.RUSSIAN -> "В сети"
            AppLanguage.UZBEK -> "Onlayn"
            AppLanguage.UZBEK_CYRILLIC -> "Онлайн"
            AppLanguage.ENGLISH -> "Online"
        }
        IumrahConnectivityStatus.OFFLINE -> when(language) {
            AppLanguage.RUSSIAN -> "Не в сети"
            AppLanguage.UZBEK -> "Oflayn"
            AppLanguage.UZBEK_CYRILLIC -> "Офлайн"
            AppLanguage.ENGLISH -> "Offline"
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

