package com.iumrah.beta.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/** Mirror of IumrahPageHelpCard("home.default") in the supplied iOS snapshot.
 * Collapsed by default: the detailed explanatory copy should not consume
 * space unless the user asks for it. No fake actions or network data.
 */
@Composable
fun IumrahHomeHelpCard(language: AppLanguage) {
    var expanded by remember { mutableStateOf(false) }
    val copy = when (language) {
        AppLanguage.RUSSIAN -> Triple("Что делать сейчас?", "Начните с маршрута", "Раскройте поиск: укажите аэропорт вылета, маршрут, даты и пассажиров. Нажмите «Найти авиабилеты», чтобы перейти к выбору перелётов.")
        AppLanguage.UZBEK -> Triple("Hozir nima qilish kerak?", "Safar yo‘nalishidan boshlang", "Qidiruvni ochib, jo‘nash aeroporti, yo‘nalish, sanalar va yo‘lovchilarni belgilang. Parvozlarni tanlash uchun «Reyslarni topish» tugmasini bosing.")
        AppLanguage.UZBEK_CYRILLIC -> Triple("Ҳозир нима қилиш керак?", "Сафар йўналишидан бошланг", "Қидирувни очиб, жўнаш аэропорти, йўналиш, саналар ва йўловчиларни белгиланг. Парвозларни танлаш учун «Рейсларни топиш» тугмасини босинг.")
        else -> Triple("What should I do now?", "Start with your route", "Expand search and choose your departure airport, route, dates and travelers. Tap “Find flights” to choose your flights.")
    }
    val ink = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(IumrahDesign.HeroRadius)
    Column(
        modifier = Modifier.fillMaxWidth().animateContentSize()
            .clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(3.dp, Brush.sweepGradient(listOf(
                Color.Black.copy(alpha = .80f), Color.DarkGray, Color.LightGray,
                Color.White, Color.DarkGray, Color.Black.copy(alpha = .80f),
            ))), shape)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(copy.first, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = ink, modifier = Modifier.weight(1f))
            CupertinoIcon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown,
                null, Modifier.size(15.dp), ink)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(250)) + expandVertically(tween(250)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(copy.second, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ink)
                Text(copy.third, fontSize = 17.sp, lineHeight = 23.sp, color = ink)
            }
        }
    }
}
