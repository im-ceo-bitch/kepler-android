package com.iumrah.beta.ui.shell
import com.iumrah.beta.ui.cupertino.Icon

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
    content: @Composable () -> Unit,
) {
    val width = (LocalConfiguration.current.screenWidthDp.dp * .74f).coerceAtMost(360.dp)
    val contentScale = animateFloatAsState(if (open) .985f else 1f, IumrahMotion.sidebar, label = "sidebar-content-scale").value
    val drawerX = animateFloatAsState(if (open) 0f else -1f, IumrahMotion.sidebar, label = "sidebar-x").value
    val scrim = animateFloatAsState(if (open) .28f else 0f, IumrahMotion.fastFade, label = "sidebar-scrim").value

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, .5f)
            },
        ) { content() }

        if (scrim > .001f) {
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

        Box(
            Modifier
                .width(width)
                .fillMaxHeight()
                .graphicsLayer { translationX = drawerX * width.toPx() }
                .shadow(if (open) 30.dp else 0.dp, RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp)),
        ) {
            SidebarDrawer(language, chrome)
        }
    }
}

@Composable
private fun SidebarDrawer(language: AppLanguage, chrome: AppChromeStore) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("iumrah", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            IumrahPressable(
                onClick = chrome::closeSidebar,
                modifier = Modifier.size(38.dp),
                cornerRadius = 99.dp,
                background = MaterialTheme.colorScheme.surfaceVariant,
                pressedScale = .90f,
            ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Close, contentDescription = "Close") } }
        }
        Text(sidebarCopy(language).subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Spacer(Modifier.size(8.dp))
        DrawerRow(CupertinoSymbol.Home, sidebarCopy(language).home) { chrome.navigate(AppTab.HOME) }
        DrawerRow(CupertinoSymbol.Suitcase, sidebarCopy(language).trips) { chrome.navigate(AppTab.BOOKING) }
        DrawerRow(CupertinoSymbol.Heart, "iumrah Care") { chrome.navigate(AppTab.CARE) }
        DrawerRow(CupertinoSymbol.PersonCircle, sidebarCopy(language).account) { chrome.navigate(AppTab.ACCOUNT) }
        DrawerRow(CupertinoSymbol.MoonStarsFill, when (language) {
            AppLanguage.RUSSIAN -> "Времена молитв"
            AppLanguage.ENGLISH -> "Prayer Times"
            AppLanguage.UZBEK -> "Namoz vaqtlari"
            AppLanguage.UZBEK_CYRILLIC -> "Намоз вақтлари"
        }) { chrome.openPrayerTimes() }
        SidebarESIMCard(language, chrome)
        Spacer(Modifier.weight(1f))
        Text("Independent Umrah · iumrah", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
    }
}

@Composable
private fun DrawerRow(icon: CupertinoSymbol, title: String, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp, background = MaterialTheme.colorScheme.surfaceVariant, pressedScale = .975f) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(title, modifier = Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Icon(CupertinoSymbol.ArrowRight, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .35f))
        }
    }
}

@Composable
private fun SidebarESIMCard(language: AppLanguage, chrome: AppChromeStore) {
    val body = when (language) {
        AppLanguage.RUSSIAN -> "Интернет для поездки и данные активации — прямо внутри Вашего пакета."
        AppLanguage.ENGLISH -> "Trip connectivity and activation data live directly inside your package."
        AppLanguage.UZBEK -> "Safar interneti va faollashtirish ma’lumotlari to‘g‘ridan-to‘g‘ri paketingiz ichida."
        AppLanguage.UZBEK_CYRILLIC -> "Сафар интернети ва фаоллаштириш маълумотлари тўғридан-тўғри пакетингиз ичида."
    }
    val badge = when (language) {
        AppLanguage.RUSSIAN -> "ВНУТРИ ПАКЕТА"
        AppLanguage.ENGLISH -> "INSIDE PACKAGE"
        AppLanguage.UZBEK -> "PAKET ICHIDA"
        AppLanguage.UZBEK_CYRILLIC -> "ПАКЕТ ИЧИДА"
    }
    IumrahPressable(
        onClick = chrome::openESIM,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        cornerRadius = 28.dp,
        background = Color.Transparent,
        pressedScale = .975f,
    ) {
        Column(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(IumrahColors.CareDark, IumrahColors.Graphite)),
                RoundedCornerShape(28.dp),
            ).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .11f)), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.SignalWave, null, modifier = Modifier.size(22.dp), tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Icon(CupertinoSymbol.ArrowUpRight, null, modifier = Modifier.size(15.dp), tint = Color.White.copy(alpha = .70f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("iumrah eSIM", style = MaterialTheme.typography.headlineSmall, color = Color.White)
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
    AppLanguage.ENGLISH -> SidebarCopy("Your Umrah in one place", "Home", "Trips", "Account")
    AppLanguage.UZBEK -> SidebarCopy("Umrangiz — bir joyda", "Bosh sahifa", "Safarlar", "Akkaunt")
    AppLanguage.UZBEK_CYRILLIC -> SidebarCopy("Умрангиз — бир жойда", "Бош саҳифа", "Сафарлар", "Аккаунт")
}
