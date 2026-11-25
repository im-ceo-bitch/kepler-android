package com.iumrah.beta.ui.shell
import com.iumrah.beta.ui.cupertino.Icon

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.CompositionLocalProvider
import com.iumrah.beta.core.design.LocalIOSParity
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.RectangleShape
import com.iumrah.beta.R
import com.iumrah.beta.core.design.iosSpring
import com.iumrah.beta.core.localization.L10n
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahPressable

@Composable
fun SidebarDrawerHost(
    open: Boolean,
    language: AppLanguage,
    chrome: AppChromeStore,
    preserveHome: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = if (preserveHome) true else rememberIumrahMotionAllowed()
    val contentScale = animateFloatAsState(if (open) .985f else 1f, if (motion) iosSpring(.34f, .88f) else snap(), label = "sidebar-content-scale").value
    val drawerX = animateFloatAsState(if (open) 0f else -1f, if (motion) iosSpring(.34f, .88f) else snap(), label = "sidebar-x").value
    val scrim = animateFloatAsState(if (open) .28f else 0f, if (motion) iosSpring(.34f, .88f) else snap(), label = "sidebar-scrim").value

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = (maxWidth * .74f).coerceAtMost(360.dp)
        val hiddenOffset = (maxWidth * .78f).coerceAtMost(380.dp)
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
            },
        ) { content() }

        if (open || scrim > .001f) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = chrome::closeSidebar,
                    ),
            )
        }

        if (open || drawerX > -.999f) Box(
            Modifier
                .width(width)
                .fillMaxHeight()
                .graphicsLayer { translationX = drawerX * hiddenOffset.toPx() }
                .shadow(if (open) 30.dp else 0.dp, RectangleShape),
        ) {
            CompositionLocalProvider(LocalIOSParity provides !preserveHome) { SidebarDrawer(language, chrome) }
        }
    }
}

@Composable
private fun SidebarDrawer(language: AppLanguage, chrome: AppChromeStore) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .98f))
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    Image(painterResource(R.drawable.iumrah_header_wordmark_dark), contentDescription = "iumrah",
                        modifier = Modifier.width(160.dp).height(42.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface), contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart)
                }
                IumrahPressable(onClick = chrome::closeSidebar, modifier = Modifier.size(44.dp), cornerRadius = 99.dp,
                    background = MaterialTheme.colorScheme.surfaceVariant, pressedScale = .90f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(CupertinoSymbol.Close, contentDescription = L10n.text("cancel", language), modifier = Modifier.size(17.dp))
                    }
                }
            }
            Text(sidebarCopy(language).subtitle, modifier = Modifier.padding(top = 6.dp), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DrawerRow(CupertinoSymbol.HomeFill, sidebarCopy(language).home) { chrome.navigate(AppTab.HOME) }
                DrawerRow(CupertinoSymbol.SuitcaseFill, sidebarCopy(language).trips) { chrome.navigate(AppTab.BOOKING) }
                DrawerRow(CupertinoSymbol.HeartFill, L10n.text("tab_care", language)) { chrome.navigate(AppTab.CARE) }
                DrawerRow(CupertinoSymbol.PersonCircleFill, sidebarCopy(language).account) { chrome.navigate(AppTab.ACCOUNT) }
            }
            SidebarESIMCard(language, chrome)
            Spacer(Modifier.height(18.dp))
        }
        Text("Independent Umrah · iumrah", modifier = Modifier.padding(bottom = 14.dp), fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
    }
}

@Composable
private fun DrawerRow(icon: CupertinoSymbol, title: String, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, background = MaterialTheme.colorScheme.surfaceVariant, pressedScale = .975f) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp)) }
            Text(title, modifier = Modifier.padding(start = 14.dp), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(CupertinoSymbol.ChevronRight, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
        }
    }
}

@Composable
private fun SidebarESIMCard(language: AppLanguage, chrome: AppChromeStore) {
    val body = when (language) {
        AppLanguage.RUSSIAN -> "Активация и остаток интернета прямо в приложении."
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Activation and data balance directly in the app."
        AppLanguage.UZBEK -> "Faollashtirish va internet qoldig‘i ilovaning o‘zida."
        AppLanguage.UZBEK_CYRILLIC -> "Фаоллаштириш ва интернет қолдиғи илованинг ўзида."
    }
    val badge = when (language) {
        AppLanguage.RUSSIAN -> "В СОСТАВЕ ПАКЕТА"
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "INCLUDED WITH PACKAGE"
        AppLanguage.UZBEK -> "PAKET TARKIBIDA"
        AppLanguage.UZBEK_CYRILLIC -> "ПАКЕТ ТАРКИБИДА"
    }
    IumrahPressable(
        onClick = chrome::openESIM,
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        cornerRadius = 28.dp,
        background = Color.Transparent,
        pressedScale = .975f,
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(IumrahColors.CareDark, IumrahColors.Graphite)),
                RoundedCornerShape(28.dp),
            ).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.SimCard, null, modifier = Modifier.size(22.dp), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Icon(CupertinoSymbol.ArrowUpRight, null, modifier = Modifier.size(14.dp), tint = Color.White.copy(alpha = .70f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("iumrah eSIM", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .72f))
            }
            Box(Modifier.height(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f)).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}

private data class SidebarCopy(val subtitle: String, val home: String, val trips: String, val account: String)
private fun sidebarCopy(language: AppLanguage): SidebarCopy = when (language) {
    AppLanguage.RUSSIAN -> SidebarCopy("Ваша умра — в одном месте", "Главная", "Поездки", "Аккаунт")
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> SidebarCopy("Your Umrah in one place", "Home", "Trips", "Account")
    AppLanguage.UZBEK -> SidebarCopy("Umrangiz — bir joyda", "Bosh sahifa", "Safarlar", "Akkaunt")
    AppLanguage.UZBEK_CYRILLIC -> SidebarCopy("Умрангиз — бир жойда", "Бош саҳифа", "Сафарлар", "Аккаунт")
}
