@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.hotels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException

private data class IncludedRow(val icon: CupertinoSymbol, val title: String, val body: String)

@Composable
internal fun IumrahFriendsCalculatorCard(
    language: AppLanguage,
    service: HotelCatalogService,
    flightPackages: List<StorefrontPackageSnapshot>,
    makkahHotels: List<HotelSummary>,
    madinahHotels: List<HotelSummary>,
) {
    var stars by remember { mutableStateOf(3) }
    var adults by remember { mutableStateOf(2) }
    var children by remember { mutableStateOf(0) }
    var quote by remember { mutableStateOf<PackageQuote?>(null) }
    var usedPreview by remember { mutableStateOf<StorefrontPackageSnapshot?>(null) }
    var calculating by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var showIncluded by remember { mutableStateOf(false) }
    val total = adults + children
    val rooms = minOf(4, maxOf(1, ceil(maxOf(1, total) / 4.0).toInt()))

    LaunchedEffect(stars, adults, children, flightPackages.size, makkahHotels.size, madinahHotels.size) {
        delay(420)
        calculating = true
        quote = null
        errorText = null
        val preview = flightPackages
            .asSequence()
            .filter { it.outbound?.stops == 0 && it.inbound?.stops == 0 && (it.madinahNights ?: 0) > 0 }
            .sortedWith(compareBy<StorefrontPackageSnapshot> { abs((it.totalDays ?: 10) - 10) }.thenBy { it.outbound?.departureAt.orEmpty() })
            .firstOrNull()
        if (preview == null) {
            errorText = friendsTr(language,
                "Сейчас нет опубликованной пары прямых рейсов. Попробуйте после обновления ленты рейсов.",
                "No published direct flight pair is available right now. Try again after the flight feed refreshes.",
                "Hozir e’lon qilingan to‘g‘ridan-to‘g‘ri reys juftligi yo‘q. Reyslar yangilangach qayta urinib ko‘ring.",
                "Ҳозир эълон қилинган тўғридан-тўғри рейс жуфтлиги йўқ. Рейслар янгилангандан кейин қайта уриниб кўринг.",
            )
            calculating = false
            return@LaunchedEffect
        }
        val makkah = makkahHotels.firstOrNull { (it.stars ?: 0) == stars }
        val madinah = madinahHotels.firstOrNull { (it.stars ?: 0) == stars }
        if (makkah == null || madinah == null) {
            errorText = friendsTr(language,
                "Для этой звёздности сейчас не опубликован Primary Hotel.",
                "No current Primary Hotel is published for this star level.",
                "Bu yulduz darajasi uchun hozir Primary Hotel e’lon qilinmagan.",
                "Бу юлдуз даражаси учун ҳозир Primary Hotel эълон қилинмаган.",
            )
            calculating = false
            return@LaunchedEffect
        }
        val out = preview.outbound
        val inbound = preview.inbound
        if (out == null || inbound == null) {
            errorText = friendsUnavailable(language)
            calculating = false
            return@LaunchedEffect
        }
        val outID = preview.outboundOfferId ?: preview.providerItineraryId ?: "${preview.id}:outbound"
        val inID = preview.inboundOfferId ?: preview.providerItineraryId ?: "${preview.id}:inbound"
        runCatching {
            service.storefrontPackageQuote(
                snapshot = preview,
                outbound = out,
                inbound = inbound,
                outboundOfferId = outID,
                inboundOfferId = inID,
                adults = adults,
                children = children,
                infants = 0,
                rooms = rooms,
                makkahLunch = false,
                makkahDinner = false,
                madinahDinner = false,
                transferVehicle = "carnival",
                haramainEnabled = false,
                haramainFareClass = "economy",
                haramainTicketCount = 0,
                makkahHotelIdOverride = makkah.id,
                madinahHotelIdOverride = madinah.id,
                includeMadinahOverride = true,
                makkahRoomIdOverride = null,
                madinahRoomIdOverride = null,
            )
        }.onSuccess {
            quote = it
            usedPreview = preview
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            errorText = friendsUnavailable(language)
        }
        calculating = false
    }

    // iOS embeds the live Package Engine calculator in the Hotels list.
    // Keep the single existing pricing state/operation above; no mock prices.
    val cardRadius = RoundedCornerShape(34.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(7.dp, cardRadius, clip = false, ambientColor = Color.Black.copy(alpha = .05f), spotColor = Color.Black.copy(alpha = .05f))
            .clip(cardRadius)
            .background(MaterialTheme.colorScheme.surface)
            .border(.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), cardRadius)
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.iumrah_friends_hotels_cover),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(20.dp)).background(Color.White),
            contentScale = ContentScale.Crop,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("iumrah Finance", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IumrahColors.SystemGreen)
            Text(
                friendsTr(language, "Калькулятор Umrah-пакета", "Umrah package calculator", "Umrah paketi kalkulyatori", "Umrah пакети калькулятори"),
                fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-.3).sp, fontWeight = FontWeight.Bold,
            )
        }
            Box(Modifier.fillMaxWidth()) {
                FriendsCard(24.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(friendsTr(language, "Класс отеля", "Hotel class", "Mehmonxona klassi", "Меҳмонхона класси"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..5).forEach { value ->
                                val selected = stars == value
                                Box(
                                    Modifier.weight(1f).height(39.dp).clip(RoundedCornerShape(11.dp))
                                        .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { stars = value },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    val selectedColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                                    Text("$value★", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = selectedColor)
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                FriendsCard(24.dp) {
                    Column {
                        CounterRow(language, friendsTr(language, "Взрослые", "Adults", "Kattalar", "Катталар"), friendsTr(language, "До 16 человек всего", "1–16 people total", "Jami 16 kishigacha", "Жами 16 кишигача"), adults, adults > 1, total < 16, { adults = maxOf(1, adults - 1) }, { if (total < 16) adults++ })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                        CounterRow(language, friendsTr(language, "Дети", "Children", "Bolalar", "Болалар"), friendsTr(language, "Входят в общий состав группы", "Included in the same group", "Umumiy guruh tarkibida", "Умумий гуруҳ таркибида"), children, children > 0, total < 16, { children = maxOf(0, children - 1) }, { if (total < 16) children++ })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                            CupertinoIcon(CupertinoSymbol.Bed, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.size(8.dp))
                            Text(friendsTr(language, "Комнаты · до 4 гостей в каждой", "Rooms · up to 4 guests each", "Xonalar · har birida 4 kishigacha", "Хоналар · ҳар бирида 4 кишигача"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            Text(rooms.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                FriendsCard(24.dp) {
                    when {
                        calculating -> Row(Modifier.fillMaxWidth().height(88.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(friendsTr(language, "Секундочку, считаем", "One second, calculating", "Bir soniya, hisoblayapmiz", "Бир сония, ҳисоблаяпмиз"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(friendsTr(language, "Проверяем рейсы, отели и все сервисы пакета.", "Checking published flights, hotels and package services.", "Reyslar, mehmonxonalar va paket servislarini tekshiryapmiz.", "Рейслар, меҳмонхоналар ва пакет сервисларини текширяпмиз."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        quote != null -> {
                            val q = quote!!
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(friendsTr(language, "ИТОГО ДЛЯ ВАШЕЙ ГРУППЫ", "TOTAL FOR YOUR GROUP", "GURUH UCHUN JAMI", "ГУРУҲ УЧУН ЖАМИ"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(money(q.totalPackagePrice.toPlainString()), fontSize = 33.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
                                }
                                Spacer(Modifier.weight(1f))
                                Text(perPerson(language, q.pricePerPerson.toPlainString()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            usedPreview?.let { p ->
                                val route = "${p.outbound?.origin.orEmpty()} → ${p.outbound?.destination.orEmpty()}"
                                val date = p.outbound?.departureAt?.take(10).orEmpty()
                                val days = friendsTr(language, "${p.totalDays ?: 10} дней", "${p.totalDays ?: 10} days", "${p.totalDays ?: 10} kun", "${p.totalDays ?: 10} кун")
                                Text("$route · $date · $days", fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        else -> Text(errorText ?: friendsUnavailable(language), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().height(72.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth()) {
                IumrahPressable(onClick = { showIncluded = true }, modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, background = IumrahColors.SystemGreen.copy(alpha = .09f), shadowElevation = 0.dp) {
                    Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(IumrahColors.SystemGreen), contentAlignment = Alignment.Center) {
                            CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(19.dp), Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(friendsTr(language, "Всё включено", "Everything included", "Hammasi kiritilgan", "Ҳаммаси киритилган"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(friendsTr(language, "Посмотреть, что входит в пакет", "See the services inside the package", "Paket ichidagilarni ko‘rish", "Пакет ичидагиларни кўриш"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
    }

    if (showIncluded) {
        ModalBottomSheet(
            onDismissRequest = { showIncluded = false },
            containerColor = MaterialTheme.colorScheme.background,
        ) { IncludedContent(language = language, onBack = { showIncluded = false }) }
    }
}

@Composable
private fun FriendsCard(radius: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surface)
            .border(.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f), RoundedCornerShape(radius)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun CounterRow(language: AppLanguage, title: String, subtitle: String, value: Int, canMinus: Boolean, canPlus: Boolean, minus: () -> Unit, plus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CircleCounterButton(CupertinoSymbol.Minus, canMinus, minus)
        Text(value.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        CircleCounterButton(CupertinoSymbol.Plus, canPlus, plus)
    }
}

@Composable
private fun CircleCounterButton(symbol: CupertinoSymbol, enabled: Boolean, action: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .07f else .03f)).clickable(enabled = enabled, onClick = action),
        contentAlignment = Alignment.Center,
    ) { CupertinoIcon(symbol, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .25f)) }
}

@Composable
private fun IncludedContent(language: AppLanguage, onBack: () -> Unit) {
    val rows = listOf(
        IncludedRow(CupertinoSymbol.Airplane, friendsTr(language, "Прямые авиабилеты", "Direct flights", "To‘g‘ridan-to‘g‘ri aviachiptalar", "Тўғридан-тўғри авиачипталар"), friendsTr(language, "В расчёт входит опубликованная пара рейсов туда и обратно.", "A published outbound and return pair is included in the package calculation.", "Hisobga e’lon qilingan borish va qaytish reyslari kiradi.", "Ҳисобга эълон қилинган бориш ва қайтиш рейслари киради.")),
        IncludedRow(CupertinoSymbol.Building, "Primary Hotels", friendsTr(language, "Отели Мекки и Медины берутся из актуального каталога iumrah для выбранной звёздности.", "Makkah and Madinah hotels are selected from the current iumrah hotel catalog for your star level.", "Makka va Madina mehmonxonalari tanlangan yulduz darajasi bo‘yicha amaldagi iumrah katalogidan olinadi.", "Макка ва Мадина меҳмонхоналари танланган юлдуз даражаси бўйича амалдаги iumrah каталогидан олинади.")),
        IncludedRow(CupertinoSymbol.Car, friendsTr(language, "Полный трансфер", "Full transfer", "To‘liq transfer", "Тўлиқ трансфер"), friendsTr(language, "Включены встреча в аэропорту и переезды между ключевыми точками поездки.", "Airport pickup and transfers between the key points of the journey are included.", "Aeroportda kutib olish va asosiy nuqtalar orasidagi transferlar kiritilgan.", "Аэропортда кутиб олиш ва асосий нуқталар орасидаги трансферлар киритилган.")),
        IncludedRow(CupertinoSymbol.ShieldCheck, "iumrah Guide", friendsTr(language, "Команда в Саудовской Аравии сопровождает группу от прилёта до Умры и обратного вылета.", "Your Saudi team supports the group from arrival through Umrah and return departure.", "Saudiya jamoasi guruhni kelishdan Umra va qaytishgacha kuzatadi.", "Саудия жамоаси гуруҳни келишдан Умра ва қайтишгача кузатади.")),
        IncludedRow(CupertinoSymbol.Map, friendsTr(language, "Маршруты зияратов", "Ziyarat routes", "Ziyorat yo‘nalishlari", "Зиёрат йўналишлари"), friendsTr(language, "Ключевые места Мекки и Медины входят в связанный маршрут поездки.", "Key Makkah and Madinah sites are part of the connected journey plan.", "Makka va Madinadagi asosiy joylar safar yo‘nalishiga kiritilgan.", "Макка ва Мадинадаги асосий жойлар сафар йўналишига киритилган.")),
        IncludedRow(CupertinoSymbol.HeartFill, "iumrah Care", friendsTr(language, "Поддержка остаётся привязана к бронированию до и во время поездки.", "Support stays linked to the booking before and during the journey.", "Yordam bron bilan safardan oldin va safar davomida bog‘langan bo‘ladi.", "Ёрдам брон билан сафардан олдин ва сафар давомида боғланган бўлади.")),
        IncludedRow(CupertinoSymbol.SimCard, "iumrah eSIM", friendsTr(language, "Связь в Саудовской Аравии подготавливается внутри пакета поездки.", "Connectivity can be prepared as part of the travel package.", "Saudiya Arabistonidagi aloqa safar paketi ichida tayyorlanadi.", "Саудия Арабистонидаги алоқа сафар пакети ичида тайёрланади.")),
    )
    LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IumrahPressable(onClick = onBack, modifier = Modifier.size(38.dp), cornerRadius = 19.dp, background = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 0.dp) {
                    Box(Modifier.fillMaxWidth().height(38.dp), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface) }
                }
                Spacer(Modifier.weight(1f))
                Text(friendsTr(language, "Что включено", "Included", "Nimalar kiritilgan", "Нималар киритилган"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f)); Spacer(Modifier.size(38.dp))
            }
        }
        items(rows) { item ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(17.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(IumrahColors.SystemGreen.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
                    CupertinoIcon(item.icon, null, Modifier.size(19.dp), IumrahColors.SystemGreen)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(item.body, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun money(raw: String): String = runCatching {
    "$" + java.math.BigDecimal(raw).setScale(0, RoundingMode.HALF_UP).toPlainString()
}.getOrDefault("$$raw")

private fun perPerson(language: AppLanguage, raw: String): String {
    val value = money(raw)
    return when (language) {
        AppLanguage.RUSSIAN -> "$value / чел."
        AppLanguage.ENGLISH -> "$value / person"
        AppLanguage.UZBEK -> "$value / kishi"
        AppLanguage.UZBEK_CYRILLIC -> "$value / киши"
    }
}

private fun friendsUnavailable(language: AppLanguage): String = friendsTr(language,
    "Сейчас не удалось получить актуальную цену пакета. Приблизительную себестоимость мы не показываем.",
    "The live package price could not be calculated right now. No estimated supplier price is shown.",
    "Hozir paketning amaldagi narxini hisoblab bo‘lmadi. Taxminiy tannarx ko‘rsatilmaydi.",
    "Ҳозир пакетнинг амалдаги нархини ҳисоблаб бўлмади. Тахминий таннарх кўрсатилмайди.",
)

private fun friendsTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
