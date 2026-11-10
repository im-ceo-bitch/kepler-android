package com.iumrah.beta.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.analytics.IumrahIntelligence
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/** Guest-accessible parity of iOS Account → Settings → Usage analytics. */
@Composable
fun AccountAnalyticsScreen(language: AppLanguage, chrome: AppChromeStore) {
    val russian = language == AppLanguage.RUSSIAN
    val uzbek = language == AppLanguage.UZBEK || language == AppLanguage.UZBEK_CYRILLIC
    var enabled by remember { mutableStateOf(IumrahIntelligence.isEnabled()) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(45.dp).clip(RoundedCornerShape(23.dp)).background(MaterialTheme.colorScheme.surface).clickable { chrome.back() }, contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(21.dp))
            }
            Spacer(Modifier.size(12.dp))
            Text(if (russian) "Аналитика использования" else if (uzbek) "Foydalanish tahlili" else "Usage analytics", fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (russian) "Помогать улучшать iumrah" else if (uzbek) "iumrah’ni yaxshilashga yordam" else "Help improve iumrah", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (russian) "Отправлять обезличенные события использования" else if (uzbek) "Ilovadan foydalanish voqealarini yuborish" else "Share pseudonymous product usage events", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f))
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it; IumrahIntelligence.setEnabled(it) })
        }
        Text(if (russian) "Мы считаем просмотры экранов, переходы, поиски, ошибки и время на экранах, чтобы улучшать приложение. Мы не отправляем вводимый текст, переписки, паспорта, контакты, платёжные данные или точную геолокацию."
             else if (uzbek) "Ilovani yaxshilash uchun ekranlar, qidiruvlar va xatoliklar haqidagi ma’lumotlar yuboriladi. Matnlar, chatlar, pasportlar va to‘lov ma’lumotlari yuborilmaydi."
             else "We measure screen visits, navigation, searches, failures and screen durations to improve the product. We don't send typed text, chats, passports, contact details, payments or precise location.",
             fontSize = 14.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
        Text(if (russian) "Если отключить аналитику, очередь событий и случайный идентификатор установки будут удалены. По умолчанию аналитика включена."
             else if (uzbek) "O‘chirilganda navbatdagi ma’lumotlar va tasodifiy identifikator o‘chiriladi."
             else "Turning this off removes queued events and resets the random installation identifier. Analytics is enabled by default.", fontSize = 13.sp, lineHeight = 19.sp)
    }
}
