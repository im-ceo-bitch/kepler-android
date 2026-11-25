package com.iumrah.beta.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IOSContinuousShape
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

internal data class IOSTabItem(val tab: AppTab, val icon: CupertinoSymbol, val label: String)
internal fun iosTabItems(language: AppLanguage) = listOf(
    IOSTabItem(AppTab.HOME, CupertinoSymbol.Home, L10n.text("tab_home", language)),
    IOSTabItem(AppTab.HOTELS, CupertinoSymbol.Building, L10n.text("tab_hotels", language)),
    IOSTabItem(AppTab.BOOKING, CupertinoSymbol.Suitcase, L10n.text("tab_booking", language)),
    IOSTabItem(AppTab.CARE, CupertinoSymbol.HeartFill, L10n.text("tab_care", language)),
    IOSTabItem(AppTab.ACCOUNT, CupertinoSymbol.PersonCircle, when (language) {
        AppLanguage.RUSSIAN, AppLanguage.UZBEK_CYRILLIC -> "Профиль"
        AppLanguage.UZBEK, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Profil"
        AppLanguage.ARABIC -> "الملف الشخصي"
        AppLanguage.ENGLISH -> "Profile"
    }),
)

/** iOS IumrahAdaptiveAppShell: 268pt ideal width, five routes and services.
 * Native NavigationSplitView rendering is approximated in Compose. */
@Composable
internal fun IOSWideSidebar(language: AppLanguage, selected: AppTab, unread: Int,
    onSelect: (AppTab) -> Unit, onESIM: () -> Unit, onNotifications: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < .5f
    Column(Modifier.width(268.dp).fillMaxHeight().background(colors.surface)
        .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp)) {
        Image(painterResource(if (dark) R.drawable.iumrah_header_wordmark_dark else R.drawable.iumrah_header_wordmark_light),
            "iumrah", Modifier.padding(top = 16.dp, bottom = 10.dp).width(142.dp).height(40.dp),
            contentScale = ContentScale.Fit, alignment = Alignment.CenterStart)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Column(Modifier.selectableGroup()) {
                iosTabItems(language).forEach { item ->
                    val active = item.tab == selected
                    Row(Modifier.fillMaxWidth().clip(IOSContinuousShape(10.dp))
                        .background(if (active) colors.onSurface.copy(alpha = .085f) else Color.Transparent)
                        .selectable(selected = active, role = Role.Tab, onClick = { if (!active) onSelect(item.tab) })
                        .heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                            CupertinoIcon(item.icon, null, Modifier.size(15.dp), colors.onSurface)
                        }
                        Text(item.label, fontSize = 17.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.onSurface, modifier = Modifier.weight(1f))
                    }
                }
            }
            val services = when(language) {
                AppLanguage.RUSSIAN -> "Сервисы"
                AppLanguage.UZBEK -> "Xizmatlar"
                AppLanguage.UZBEK_CYRILLIC -> "Хизматлар"
                else -> "Services"
            }
            Text(services, Modifier.padding(top = 18.dp, start = 10.dp, bottom = 5.dp), fontSize = 13.sp,
                color = colors.onSurface.copy(alpha = .55f))
            IOSSidebarService("iumrah eSIM", CupertinoSymbol.SimCard, null, onESIM)
            IOSSidebarService(when(language) {
                AppLanguage.RUSSIAN -> "Уведомления"
                AppLanguage.UZBEK -> "Bildirishnomalar"
                AppLanguage.UZBEK_CYRILLIC -> "Билдиришномалар"
                else -> "Notifications"
            }, CupertinoSymbol.Bell, unread.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() }, onNotifications)
        }
        // Do not present a permanent 'online' badge: the iOS footer is decorative;
        // Android should not claim current connectivity when it is not observed.
        Text("iumrah", Modifier.padding(vertical = 14.dp, horizontal = 2.dp),
            color = colors.onSurface.copy(alpha = .55f), fontSize = 12.sp)
    }
}

@Composable
private fun IOSSidebarService(title: String, icon: CupertinoSymbol, badge: String?, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(IOSContinuousShape(10.dp)).clickable(role = Role.Button, onClick = action)
        .heightIn(min = 44.dp).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
            CupertinoIcon(icon, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface)
        }
        Text(title, Modifier.weight(1f), fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (badge != null) Text(badge, Modifier.clip(androidx.compose.foundation.shape.CircleShape)
            .background(IumrahColors.SystemRed).padding(horizontal = 8.dp, vertical = 3.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
