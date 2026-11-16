package com.iumrah.beta.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.smart.HomeActivity
import com.iumrah.beta.data.smart.HomeActivityStore
import com.iumrah.beta.data.smart.HomePublicContentCache
import com.iumrah.beta.domain.smart.SmartDraft
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.hotels.nuiteeText
import com.iumrah.beta.ui.hotels.nuiteeUSD
import java.time.LocalDate

@Composable private fun HomePersonalHeader(title: String) = Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
@Composable private fun HomeSmallActivityCard(symbol: CupertinoSymbol, label: String, action: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.width(220.dp).height(184.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = action).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)), contentAlignment = Alignment.Center) { CupertinoIcon(symbol, null, Modifier.size(20.dp), Color(0xFF007AFF)) }
            Spacer(Modifier.weight(1f)); CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        content()
    }
}
@Composable internal fun HomeRecentActivitySection(language: AppLanguage, activity: HomeActivity, booking: StoredBookingSession?, chrome: AppChromeStore,
                                                   resume: (SmartDraft) -> Unit, repeat: (TripDraft) -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    if (activity.smart == null && activity.flight == null && booking == null) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Продолжите с того места, где остановились", "Pick up where you left off", "To‘xtagan joyingizdan davom eting", "Тўхтаган жойингиздан давом этинг"))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            booking?.let { b -> item { HomeSmallActivityCard(CupertinoSymbol.SuitcaseFill, t("Ваша поездка", "Your journey", "Safaringiz", "Сафарингиз"), { chrome.openBookingDetail(b.id) }) {
                Text("${b.booking.input.originCode} → ${b.booking.input.arrivalAirportCode}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(L10n.status(b.effectiveStatus, language), fontSize = 13.sp, maxLines = 2)
                Text(b.displayBookingNumber, fontSize = 12.sp)
            } } }
            activity.smart?.let { draft -> item { HomeSmallActivityCard(if (draft.wasAssembled) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Sparkles,
                if (draft.wasAssembled) t("Недавно собранный пакет", "Recently assembled package", "Yaqinda tuzilgan paket", "Яқинда тузилган пакет") else t("Незавершённая Умра", "Unfinished Umrah", "Tugallanmagan Umra", "Тугалланмаган Умра"), { resume(draft) }) {
                Text("iumrah Smart", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("${draft.preferences.month} · ${draft.preferences.duration} " + t("дн.", "days", "kun", "кун"), fontSize = 12.sp)
                if (!draft.wasAssembled) LinearProgressIndicator(progress = { draft.step.coerceIn(0, 8) / 8f }, modifier = Modifier.fillMaxWidth())
                Text(if (draft.wasAssembled) t("Проверить пакет", "Recheck package", "Paketni tekshirish", "Пакетни текшириш") else t("Продолжить сборку", "Continue", "Davom etish", "Давом этиш"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } } }
            activity.flight?.trip()?.let { trip -> item { HomeSmallActivityCard(CupertinoSymbol.Airplane, t("Недавний поиск", "Recent search", "So‘nggi qidiruv", "Сўнгги қидирув"), { repeat(trip) }) {
                Text("${trip.originCode} → ${trip.outboundDestinationCode}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(trip.departureDate.toString(), fontSize = 12.sp)
                Text(t("Повторить поиск", "Search again", "Qayta qidirish", "Қайта қидириш"), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            } } }
        }
    }
}
@Composable internal fun HomeDestinationsSection(language: AppLanguage, onCity: (String) -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Ваше следующее путешествие", "Your next journey", "Keyingi safaringiz", "Кейинги сафарингиз"))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeDestinationCard(Modifier.weight(1f), R.drawable.iumrah_makkah_background, t("Мекка", "Makkah", "Makka", "Макка"), t("Совершите Умру", "Perform Umrah", "Umra ziyorati", "Умра зиёрати")) { onCity("makkah") }
            HomeDestinationCard(Modifier.weight(1f), R.drawable.ziyarat_quba_1, t("Медина", "Madinah", "Madina", "Мадина"), t("Город Пророка ﷺ", "City of the Prophet ﷺ", "Payg‘ambarimiz ﷺ shahri", "Пайғамбаримиз ﷺ шаҳри")) { onCity("madinah") }
        }
    }
}
@Composable private fun HomeDestinationCard(modifier: Modifier, image: Int, title: String, subtitle: String, action: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = action).padding(9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(image), null, Modifier.fillMaxWidth().height(138.dp).clip(RoundedCornerShape(15.dp)), contentScale = ContentScale.Crop)
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
@Composable internal fun HomeAvailablePackagesSection(language: AppLanguage, origin: String, chrome: AppChromeStore, startSmart: () -> Unit) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    var values by remember(origin) { mutableStateOf<List<StorefrontPackageSnapshot>>(emptyList()) }
    LaunchedEffect(origin) { values = HomePublicContentCache.packages(origin) }
    val points = values.filter { it.startDate?.take(10)?.let { day -> day >= LocalDate.now().toString() } == true }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Актуальные пакеты Умры", "Umrah packages", "Umra paketlari", "Умра пакетлари"))
        if (points.isEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = startSmart).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            CupertinoIcon(CupertinoSymbol.SuitcaseFill, null, Modifier.size(36.dp)); Column(Modifier.weight(1f)) {
                Text(t("Подобрать пакет по вашим датам", "Find a package for your dates", "Sanalaringizga mos paket", "Саналарингизга мос пакет"), fontWeight = FontWeight.Bold)
                Text(t("Рейсы, отели и услуги — единая стоимость", "Flights, hotels and services — one total", "Reys, mehmonxona va xizmatlar — bitta narx", "Рейс, меҳмонхона ва хизматлар — битта нарх"), fontSize = 12.sp)
            }; CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(15.dp))
        } else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(points, key = { it.id }) { point ->
            HomeSmallActivityCard(CupertinoSymbol.Calendar, point.startDate.orEmpty().take(10), { chrome.openFlightPackage(point.id) }) {
                Text(point.hotelName.orEmpty(), fontWeight = FontWeight.Bold, maxLines = 2)
                Text(point.pricePerPerson?.let(::nuiteeUSD) ?: "—", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(t("На одного паломника · тариф требует проверки", "Per traveler · subject to recheck", "Bir ziyoratchiga · tarif tekshiriladi", "Бир зиёратчига · тариф текширилади"), fontSize = 11.sp, maxLines = 2)
            }
        } }
    }
}
@Composable internal fun HomeWhyAndPreferencesSection(language: AppLanguage, history: HomeActivityStore, owner: String?, revision: Int) {
    fun t(ru: String, en: String, uz: String, cy: String) = nuiteeText(language, ru, en, uz, cy)
    var expanded by remember { mutableStateOf(false) }
    val enabled = remember(revision) { history.enabled }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HomePersonalHeader(t("Почему iumrah?", "Why iumrah?", "Nega iumrah?", "Нега iumrah?"))
        val features = listOf(
            Triple(CupertinoSymbol.PersonsFill,t("С семьёй, без группы", "With family, not a group", "Oila bilan, guruhsiz", "Оила билан, гуруҳсиз"), t("Путешествуйте в удобном темпе", "Travel at your own pace", "O‘zingizga qulay sur’atda", "Ўзингизга қулай суръатда")),
            Triple(CupertinoSymbol.PackageBox,t("Единый пакет", "One package", "Bitta paket", "Битта пакет"),t("Рейсы, отели и услуги вместе", "Flights, hotels and services together", "Reys, mehmonxona va xizmatlar birga", "Рейс, меҳмонхона ва хизматлар бирга")),
            Triple(CupertinoSymbol.HeartFill,t("Персональная поддержка", "Personal support", "Shaxsiy yordam", "Шахсий ёрдам"), t("Сопровождение вашей поездки", "Support throughout your journey", "Safaringiz davomida yordam", "Сафарингиз давомида ёрдам")),
            Triple(CupertinoSymbol.Sliders,t("Свобода выбора", "Freedom to choose", "Tanlash erkinligi", "Танлаш эркинлиги"), t("Уровень комфорта и маршрут на ваш выбор", "Your comfort level and route", "Qulaylik va yo‘nalishni tanlang", "Қулайлик ва йўналишни танланг")))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(features) { (symbol, title, desc) ->
            Column(Modifier.width(190.dp).height(168.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CupertinoIcon(symbol, null, Modifier.size(32.dp)); Text(title, fontWeight = FontWeight.Bold); Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(t("Управление рекомендациями", "Manage recommendations", "Tavsiyalarni boshqarish", "Тавсияларни бошқариш"), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                CupertinoIcon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(16.dp))
            }
            if (expanded) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text(t("Персональные рекомендации", "Personal recommendations", "Shaxsiy tavsiyalar", "Шахсий тавсиялар"), Modifier.weight(1f)); Switch(checked = enabled, onCheckedChange = history::setEnabled) }
                Text(t("Недавние действия хранятся на этом устройстве. Отключение очищает историю рекомендаций, но не удаляет бронирования.", "Recent activity stays on this device. Turning it off clears recommendation history, not bookings.", "Tarix shu qurilmada saqlanadi. O‘chirish bronlarni emas, tavsiyalar tarixini tozalaydi.", "Тарих шу қурилмада сақланади. Ўчириш бронларни эмас, тавсиялар тарихини тозалайди."), fontSize = 12.sp)
                TextButton(onClick = { history.clear(owner) }) { Text(t("Очистить недавние действия", "Clear recent activity", "So‘nggi harakatlarni tozalash", "Сўнгги ҳаракатларни тозалаш")) }
            }
        }
    }
}
