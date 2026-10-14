package com.iumrah.beta.ui.flights

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyState
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class FlightSearchMode { GLOBAL, IUMRAH }

private fun flightText(language: AppLanguage, ru: String, en: String, uz: String, cy: String) = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

@Composable
internal fun FlightSearchModeBar(language: AppLanguage, mode: FlightSearchMode, onChange: (FlightSearchMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(
            FlightSearchMode.GLOBAL to flightText(language, "Глобальный поиск", "Global search", "Global qidiruv", "Глобал қидирув"),
            FlightSearchMode.IUMRAH to flightText(language, "Рекомендует iumrah", "iumrah recommends", "iumrah tavsiya qiladi", "iumrah тавсия қилади"),
        ).forEach { (value, title) ->
            val active = value == mode
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(horizontal = 5.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (active) 1f else .68f),
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun FlightConfigRouteLine(title: String, place: String, code: String, symbol: CupertinoSymbol, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(43.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .09f)), contentAlignment = Alignment.Center) {
            Icon(symbol, null, Modifier.size(20.dp), Color(0xFF007AFF))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .45.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .50f))
            Text(place, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(code.uppercase(), fontSize = 12.sp, letterSpacing = .5.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f))
        }
    }
}

@Composable
internal fun FlightUnifiedConfigurationCard(
    language: AppLanguage,
    originCode: String,
    originName: String,
    destinationCode: String,
    destinationName: String,
    routeScope: JourneyScope,
    dateSummary: String,
    travelerSummary: String,
    onOrigin: () -> Unit,
    onDestination: () -> Unit,
    onSwap: () -> Unit,
    onScope: (JourneyScope) -> Unit,
    onDates: () -> Unit,
    onTravelers: () -> Unit,
    onMap: () -> Unit,
) {
    val border = MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(.8.dp, border, RoundedCornerShape(28.dp)),
    ) {
        Box {
            Column {
                FlightConfigRouteLine(flightText(language, "Откуда", "From", "Qayerdan", "Қаердан"), originName, originCode, CupertinoSymbol.AirplaneTakeoff, onOrigin)
                Box(Modifier.fillMaxWidth().padding(start = 62.dp, end = 14.dp).height(.8.dp).background(border))
                FlightConfigRouteLine(flightText(language, "Куда", "To", "Qayerga", "Қаерга"), destinationName, destinationCode, CupertinoSymbol.AirplaneLand, onDestination)
            }
            Box(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).size(45.dp)
                .clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onSwap), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.ArrowLeftRight, null, Modifier.size(21.dp))
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(.8.dp).background(border))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(flightText(language, "Маршрут Umrah", "Umrah route", "Umra yo‘nalishi", "Умра йўналиши"),
                fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .60f))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf(
                    JourneyScope.MAKKAH_ONLY to flightText(language, "Мекка", "Makkah", "Makka", "Макка"),
                    JourneyScope.MAKKAH_AND_MADINAH to flightText(language, "Мекка + Медина", "Makkah + Madinah", "Makka + Madina", "Макка + Мадина"),
                ).forEach { (value, title) ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (routeScope == value) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onScope(value) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp).height(.8.dp).background(border))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            FlightConfigValue(Modifier.weight(1f), CupertinoSymbol.Calendar, flightText(language, "Даты", "Dates", "Sanalar", "Саналар"), dateSummary, onDates)
            Box(Modifier.width(.8.dp).height(48.dp).background(border))
            FlightConfigValue(Modifier.weight(1f), CupertinoSymbol.Persons, flightText(language, "Паломники", "Pilgrims", "Ziyoratchilar", "Зиёратчилар"), travelerSummary, onTravelers)
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onMap), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Map, null, Modifier.size(21.dp), Color(0xFF007AFF))
            }
        }
    }
}

@Composable
private fun FlightConfigValue(modifier: Modifier, symbol: CupertinoSymbol, title: String, value: String, onClick: () -> Unit) {
    Row(modifier.clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(symbol, null, Modifier.size(20.dp), Color(0xFF007AFF))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun FlightDiscoveryActions(language: AppLanguage, directOnly: Boolean, airlinesSelected: Boolean,
    onChart: () -> Unit, onDirect: () -> Unit, onAirlines: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(CupertinoSymbol.Calendar, flightText(language, "График цен", "Price chart", "Narx grafigi", "Нарх графиги"), Color(0xFF007AFF)),
            Triple(CupertinoSymbol.Airplane, flightText(language, "Прямые", "Non-stop", "To‘g‘ri", "Тўғри"), Color(0xFF34C759)),
            Triple(CupertinoSymbol.Sliders, flightText(language, "Авиакомпании", "Airlines", "Aviakompaniya", "Авиакомпания"), Color(0xFFAF52DE)),
        ).forEachIndexed { index, (icon, title, tint) ->
            val active = if (index == 1) directOnly else index == 2 && airlinesSelected
            Row(Modifier.width(174.dp).height(68.dp).clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), RoundedCornerShape(22.dp))
                .clickable { when (index) { 0 -> onChart(); 1 -> onDirect(); else -> onAirlines() } }
                .padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape)
                    .background(if (active) tint else tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(19.dp), if (active) Color.White else tint)
                }
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp, maxLines = 2)
            }
        }
    }
}

@Composable
internal fun FlightPublishedCarousel(language: AppLanguage, title: String, subtitle: String,
    flights: List<CuratedFlightRecommendation>, loading: Boolean, selectedIDs: Set<String>, onSelect: (CuratedFlightRecommendation) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        if (flights.isEmpty()) {
            Text(if (loading) flightText(language,"Загружаем опубликованные рейсы…","Loading published flights…","Reyslar yuklanmoqda…","Рейслар юкланмоқда…")
                else flightText(language,"Нет опубликованных рейсов по выбранному маршруту.","No published flights for this route.","Tanlangan yo‘nalishda reys yo‘q.","Танланган йўналишда рейс йўқ."),
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                flights.take(30).forEach { offer ->
                    val selected = offer.id in selectedIDs
                    Column(Modifier.width(302.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface)
                        .border(if (selected) 1.5.dp else .75.dp,
                            if (selected) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), RoundedCornerShape(24.dp))
                        .clickable { onSelect(offer) }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(offer.primaryAirlineName, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Icon(if (selected) CupertinoSymbol.Checkmark else CupertinoSymbol.Airplane, null,
                                Modifier.size(19.dp), if (selected) Color(0xFF34C759) else Color(0xFF007AFF))
                        }
                        Text("${offer.outbound.origin.uppercase()} → ${offer.outbound.destination.uppercase()}",
                            fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = -.4.sp)
                        val date = runCatching { LocalDate.parse(offer.outbound.departureAt.take(10)) }
                            .getOrNull()?.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)) ?: offer.outboundDate
                        Text("$date   ·   ${offer.outbound.airlineCode} ${offer.outbound.flightNumber}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(flightText(language, "Прямой рейс", "Non-stop flight", "To‘g‘ri reys", "Тўғри рейс"),
                                Modifier.weight(1f), fontSize = 11.sp, color = Color(0xFF34C759), fontWeight = FontWeight.SemiBold)
                            if (selected) Text(flightText(language,"Выбран","Selected","Tanlandi","Танланди"),
                                color = Color(0xFF34C759), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            else Icon(CupertinoSymbol.Plus, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FlightAssemblyCard(
    language: AppLanguage,
    state: JourneyState,
    onContinue: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    expandedState: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
) {
    val staged = state.packageFlightPath == com.iumrah.beta.domain.trip.PackageFlightPath.AVIASALES_SELECTED
    val published = state.packageFlightPath == com.iumrah.beta.domain.trip.PackageFlightPath.PUBLISHED_DIRECT
    val selectionCount = if (staged) listOfNotNull(state.stagedAviasalesRoundTrip,
        state.stagedAviasalesOutbound, state.stagedAviasalesReturn).size
        else if (published) listOfNotNull(state.selectedPublishedCompleteID,
            state.selectedPublishedOutboundID, state.selectedPublishedReturnID).size else 0
    // iOS displays the assembly tray even before the first flight is selected.
    val complete = selectionCount > 0 &&
        (if (staged) state.hasCompleteStagedFlightSelection else state.hasCompletePublishedFlightSelection)
    var locallyExpanded by remember { mutableStateOf(false) }
    val expanded = expandedState ?: locallyExpanded
    val rows = if (staged) listOfNotNull(state.stagedAviasalesRoundTrip,
        state.stagedAviasalesOutbound, state.stagedAviasalesReturn) else emptyList()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(.8.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), RoundedCornerShape(30.dp))) {
        Row(Modifier.fillMaxWidth().clickable {
            if (expandedState == null) locallyExpanded = !locallyExpanded
            else onExpandedChange?.invoke(!expanded)
        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(Modifier.size(38.dp).clip(CircleShape)
                .background(Color(0xFF34C759).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.SuitcaseFill, null, Modifier.size(18.dp), Color(0xFF34C759))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(flightText(language, "Сборка Umrah пакета", "Umrah package assembly", "Umra paketini yig‘ish", "Умра пакетини йиғиш"),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (complete) flightText(language,"Рейсы туда и обратно выбраны","Outbound and return selected","Borish va qaytish tanlandi","Бориш ва қайтиш танланди")
                    else if (selectionCount == 0) flightText(language,"Ещё ничего не выбрано","Nothing selected yet","Hali hech narsa tanlanmagan","Ҳали ҳеч нарса танланмаган")
                    else flightText(language,"Выберите второе направление","Choose the other flight","Ikkinchi reysni tanlang","Иккинчи рейсни танланг"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1)
            }
            Icon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null,
                Modifier.size(19.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
        if (expanded) {
            Column(Modifier.padding(start = 17.dp, end = 17.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectionCount == 0) Text(
                    flightText(language, "Вы ещё ничего не выбрали.", "You haven’t selected anything yet.", "Hali hech narsa tanlamadingiz.", "Ҳали ҳеч нарса танламадингиз."),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                )
                rows.forEach { offer ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(CupertinoSymbol.Airplane, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface)
                            Text("${offer.origin.uppercase()} → ${offer.destination.uppercase()}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Text("${offer.airlineCode} ${offer.flightNumber}  ·  ${offer.departureAt.take(10)}",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        if (offer.isRoundTrip) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Icon(CupertinoSymbol.AirplaneLand, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                            Text(offer.returnAt?.take(10).orEmpty(), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                        }
                    }
                }
                if (published && selectionCount > 0) Text(flightText(language,"Опубликованные билеты iumrah","Published iumrah flights",
                    "iumrah e’lon qilgan reyslar","iumrah эълон қилган рейслар"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectionCount > 0) Text(flightText(language,"Сбросить","Clear","Tozalash","Тозалаш"),
                        Modifier.clickable(onClick = onClear).padding(10.dp),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .63f))
                    Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (complete) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
                        .clickable(enabled = complete, onClick = onContinue), contentAlignment = Alignment.Center) {
                        Text(if (complete) flightText(language,"Продолжить сборку Umrah","Continue Umrah package","Umra paketini davom ettirish","Умра пакетини давом эттириш")
                            else flightText(language,"Выберите второй рейс","Choose the other flight","Ikkinchi reysni tanlang","Иккинчи рейсни танланг"),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = if (complete) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun FlightPartnerGatewayCard(language: AppLanguage, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp)).background(MaterialTheme.colorScheme.surface)
        .padding(14.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.iumrah_flights_partner), null,
            modifier=Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(21.dp)),contentScale=ContentScale.Crop)
        Text(flightText(language,"Не нашли нужный маршрут?","Need a different route?","Kerakli yo‘nalishni topmadingizmi?","Керакли йўналишни топмадингизми?"),fontSize=22.sp,fontWeight=FontWeight.Bold)
        Text(flightText(language,"Для других маршрутов перейдите к нашему партнёру Aviasales. iumrah рекомендует прямые рейсы.",
            "For broader coverage, continue with Aviasales. iumrah recommends non-stop flights.",
            "Boshqa yo‘nalishlarni hamkorimiz Aviasales orqali qidiring. iumrah to‘g‘ri reyslarni tavsiya qiladi.",
            "Бошқа йўналишларни ҳамкоримиз Aviasales орқали қидиринг. iumrah тўғри рейсларни тавсия қилади."),
            fontSize=13.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.63f))
        IumrahPressable(onClick=onOpen,modifier=Modifier.fillMaxWidth().height(48.dp),cornerRadius=16.dp,
            background=Color(0xFF007AFF),shadowElevation=0.dp) {
            Box(Modifier.fillMaxWidth().height(48.dp),contentAlignment=Alignment.Center){
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Aviasales", color=Color.White, fontWeight=FontWeight.Bold)
                    Icon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(15.dp), Color.White)
                }
            }
        }
    }
}
