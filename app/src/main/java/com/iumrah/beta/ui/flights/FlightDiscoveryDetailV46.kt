@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.flights

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.data.flight.AviasalesSearchLinkBuilder
import com.iumrah.beta.data.flight.FlightDiscoveryItineraryValidator
import com.iumrah.beta.data.flight.FlightDiscoveryVerifiedSegment
import com.iumrah.beta.data.flight.FlightDataParity
import java.util.Locale
import com.iumrah.beta.ui.components.IumrahPressable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun t(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

/** Keep a source-compatible matcher for the existing ticket detail callsite. */
internal fun exactFlightMatch(expected: FlightDiscoveryOffer, candidate: FlightDiscoveryOffer): Boolean =
    FlightDataParity.isSameFare(expected, candidate)

private fun fmtDate(raw: String) = runCatching {
    LocalDate.parse(raw.take(10)).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
}.getOrDefault(raw.take(10))
private fun fmtTime(raw: String) = runCatching {
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(raw.substringAfter('T', "").take(5))
private fun price(value: Double, currency: String) = if (currency.equals("usd",true)) "$${value.toInt()}" else "${currency.uppercase()} ${value.toInt()}"

@Composable
internal fun FlightDiscoveryDetailSheetV46(
    language: AppLanguage,
    initialOffer: FlightDiscoveryOffer,
    service: AviasalesFlightDiscoveryService,
    currency: String,
    adults: Int,
    children: Int,
    infants: Int,
    favorite: Boolean,
    onDismiss: () -> Unit,
    onFavorite: (FlightDiscoveryOffer) -> Unit,
    onBuildUmrah: (FlightDiscoveryOffer) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var offer by remember(initialOffer.monitorKey) { mutableStateOf(initialOffer) }
    var checking by remember(initialOffer.monitorKey) { mutableStateOf(false) }
    var refreshed by remember(initialOffer.monitorKey) { mutableStateOf(false) }
    var message by remember(initialOffer.monitorKey) { mutableStateOf<String?>(null) }
    var changed by remember(initialOffer.monitorKey) { mutableStateOf(false) }
    var isFavorite by remember(initialOffer.monitorKey) { mutableStateOf(favorite) }

    suspend fun checkPrice() {
        if (checking) return
        checking = true
        try {
            val current = service.offers(
                initialOffer.origin, initialOffer.destination, initialOffer.departureAt.take(10),
                initialOffer.returnAt?.take(10), direct = initialOffer.isDirect, limit = 100, currency = currency, forceRefresh = true,
            ).offers.let { FlightDataParity.matchingFare(initialOffer, it) }
            if (current == null) {
                message = t(language,
                    "Именно этот билет не найден в последних данных. Проверьте цену у продавца.",
                    "This exact itinerary was not found in recent data. Check with the seller.",
                    "Aynan shu chipta yangi ma’lumotlarda topilmadi. Sotuvchidan tekshiring.",
                    "Айнан шу чипта янги маълумотларда топилмади. Сотувчидан текширинг.")
            } else {
                changed = kotlin.math.abs(current.price - initialOffer.price) >= .5
                offer = current
                refreshed = true
                message = t(language,
                    "Цена сверена с последними данными. Наличие уточняйте у продавца.",
                    "Fare matched recently available data. Confirm availability with the seller.",
                    "Narx so‘nggi ma’lumot bilan solishtirildi. Mavjudligini sotuvchidan tekshiring.",
                    "Нарх сўнгги маълумот билан солиштирилди. Мавжудлигини сотувчидан текширинг.")
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            message = t(language,
                "Не удалось обновить цену. Проверьте на Aviasales.",
                "Could not refresh fare. Check on Aviasales.",
                "Narx yangilanmadi. Aviasales’da tekshiring.",
                "Нарх янгиланмади. Aviasales’да текширинг.")
        } finally {
            checking = false
        }
    }

    LaunchedEffect(initialOffer.monitorKey) { checkPrice() }

    // iOS opens this as a dedicated navigation destination, not a compact bottom sheet.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 19.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.ChevronLeft, null, Modifier.size(24.dp).clickable(onClick = onDismiss).padding(2.dp),
                    MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(t(language, "Авиабилет", "Flight", "Aviachipta", "Авиачипта"),
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Icon(if (isFavorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, null,
                    Modifier.size(38.dp).clickable { isFavorite = !isFavorite; onFavorite(offer) }.padding(7.dp),
                    if (isFavorite) Color(0xFFFF375F) else MaterialTheme.colorScheme.onSurface)
                Icon(CupertinoSymbol.Share, null,
                    Modifier.size(38.dp).clickable { share(context, offer, currency, adults, children, infants) }.padding(7.dp),
                    MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (offer.isRoundTrip) t(language,"Туда‑обратно","Round trip","Borib‑kelish","Бориб‑келиш")
                    else t(language,"В одну сторону","One way","Bir tomonga","Бир томонга"),
                    color=Color(0xFF007AFF),fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text(price(offer.price, currency),fontSize=43.sp,fontWeight=FontWeight.Bold)
                if (changed) Text(t(language,"Цена изменилась с ${price(initialOffer.price,currency)}",
                    "Changed from ${price(initialOffer.price,currency)}","Oldingi narx ${price(initialOffer.price,currency)}",
                    "Олдинги нарх ${price(initialOffer.price,currency)}"),fontSize=12.sp,color=Color(0xFF34C759))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (checking) t(language,"Проверяем цену…","Checking fare…","Narx tekshirilmoqda…","Нарх текширилмоқда…")
                        else if (refreshed) t(language,"Недавно обновлено","Recently checked","Hozir tekshirildi","Ҳозир текширилди")
                        else t(language,"Предварительная цена","Indicative fare","Taxminiy narx","Тахминий нарх"),
                        fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.65f))
                    if (checking) CircularProgressIndicator(Modifier.size(15.dp),strokeWidth=2.dp)
                }
                if (message != null) Text(message!!,fontSize=12.sp,lineHeight=17.sp,
                    color=MaterialTheme.colorScheme.onSurface.copy(alpha=.7f))
                IumrahPressable(onClick={scope.launch { checkPrice() }}, modifier=Modifier.fillMaxWidth().height(46.dp),
                    cornerRadius=16.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp) {
                    Box(Modifier.fillMaxWidth().height(46.dp),contentAlignment=Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(CupertinoSymbol.Refresh, null, Modifier.size(17.dp))
                            Text(t(language,"Обновить цену","Refresh fare","Narxni yangilash","Нархни янгилаш"),fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            FlightDetailLegV46(language, CupertinoSymbol.AirplaneTakeoff, t(language,"Рейс туда","Outbound flight","Borish reysi","Бориш рейси"),
                offer.origin, offer.destination, offer.departureAt, offer.airlineCode, offer.flightNumber, offer.transfers,
                offer.durationMinutes, offer.outboundSegments)
            if (offer.isRoundTrip) {
                FlightDetailLegV46(language, CupertinoSymbol.AirplaneLand, t(language,"Рейс обратно","Return flight","Qaytish reysi","Қайтиш рейси"),
                    offer.destination, offer.origin, offer.returnAt.orEmpty(), offer.returnAirlineCode.orEmpty(), offer.returnFlightNumber.orEmpty(), offer.returnTransfers,
                    offer.returnDurationMinutes ?: 0, offer.inboundSegments)
                if (offer.returnAirlineCode.isNullOrBlank() || offer.returnFlightNumber.isNullOrBlank()) {
                    Text(t(language,"Перевозчик и номер обратного рейса пока не подтверждены. Уточните перед покупкой.",
                        "Return carrier and flight number are not yet confirmed. Verify before paying.",
                        "Qaytish reysi va aviakompaniya tasdiqlanmagan. Sotuvchidan tekshiring.",
                        "Қайтиш рейси ва авиакомпания тасдиқланмаган. Сотувчидан текширинг."),
                        fontSize=12.sp,color=Color(0xFFEE9500))
                }
            }
            if (offer.transfers > 0 || (offer.returnTransfers ?: 0) > 0) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(t(language,"Пересадки","Connections","Transferlar","Трансферлар"),fontWeight=FontWeight.Bold)
                    Text(t(language,"Аэропорты пересадок, время ожидания и следующие рейсы уточняйте при поиске у продавца.",
                        "Connection airports, layovers and additional flights are available during the seller’s search.",
                        "Almashish aeroportlari, kutish va keyingi reyslarni sotuvchida tekshiring.",
                        "Алмашиш аэропортлари, кутиш ва кейинги рейсларни сотувчида текширинг."),
                        fontSize=12.sp,lineHeight=18.sp)
                }
            }
            Text(t(language,"Наличие и окончательная цена подтверждаются при поиске у продавца.",
                "Availability and the final fare are confirmed during the seller’s search.",
                "Mavjudlik va yakuniy narx sotuvchida tasdiqlanadi.",
                "Мавжудлик ва якуний нарх сотувчида тасдиқланади."),fontSize=12.sp,
                lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            IumrahPressable(onClick={
                val url = AviasalesSearchLinkBuilder.searchUrl(
                    offer.origin, offer.destination, offer.departureAt, offer.returnAt,
                    adults, children, infants,
                )
                if (url == null) {
                    message = t(language, "Не удалось сформировать поиск для этих дат.",
                        "Could not open a search for these dates.",
                        "Ushbu sanalar uchun qidiruv ochilmadi.",
                        "Ушбу саналар учун қидирув очилмади.")
                } else {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
            },modifier=Modifier.fillMaxWidth().height(57.dp),
                cornerRadius=20.dp,background=Color.Black,shadowElevation=0.dp) {
                Box(Modifier.fillMaxWidth().height(57.dp),contentAlignment=Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t(language,"Проверить и купить","Check fare & buy","Narxni tekshirish va sotib olish","Нархни текшириш ва сотиб олиш"),
                            color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold)
                        Icon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), Color.White)
                    }
                }
            }
            if (offer.origin.uppercase() in setOf("JED","MED") || offer.destination.uppercase() in setOf("JED","MED")) {
                IumrahPressable(onClick={onBuildUmrah(offer)},modifier=Modifier.fillMaxWidth().height(57.dp),
                    cornerRadius=20.dp,background=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=0.dp) {
                    Box(Modifier.fillMaxWidth().height(57.dp),contentAlignment=Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(CupertinoSymbol.Plus, null, Modifier.size(17.dp))
                            Text(t(language,"Добавить рейс в сборку Umrah","Add to Umrah package",
                                "Umra paketiga qo‘shish","Умра пакетига қўшиш"),fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun FlightDetailLegV46(language: AppLanguage, icon: CupertinoSymbol, title: String, from: String,
    to: String, at: String, code: String, number: String, stops: Int?, durationMinutes: Int,
    segments: List<FlightDiscoveryVerifiedSegment>?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement=Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
            Text(title, fontSize=15.sp, fontWeight=FontWeight.Bold)
        }
        Text("${from.uppercase()} → ${to.uppercase()}",fontSize=24.sp,fontWeight=FontWeight.Bold)
        val locale = when (language) {
            AppLanguage.RUSSIAN -> Locale("ru")
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn-UZ")
            AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl-UZ")
        }
        Text("${FlightDataParity.localDate(at, from, locale)} · ${FlightDataParity.localTime(at, from)}",fontSize=13.sp)
        if (durationMinutes > 0) {
            Text(t(language,"Прибытие (расчётное)","Arrival (estimated)","Yetib kelish (taxminiy)","Етиб келиш (тахминий)") + ": " +
                FlightDataParity.arrivalDate(at, durationMinutes, to, locale) + " · " +
                FlightDataParity.arrivalTime(at, durationMinutes, to),
                fontSize=12.sp, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.66f))
        }
        Text(if(code.isBlank() || number.isBlank()) t(language,"Перевозчик / номер не подтверждены","Carrier / flight unconfirmed",
            "Reys ma’lumoti tasdiqlanmagan","Рейс маълумоти тасдиқланмаган")
            else "${FlightReferenceCatalog.airlineName(code,code)} · $code $number",fontSize=13.sp,
            color=MaterialTheme.colorScheme.onSurface.copy(alpha=.74f))
        val verified = FlightDiscoveryItineraryValidator.verified(segments, from, to, stops ?: -1)
        if (verified != null) {
            verified.forEachIndexed { index, leg ->
                if (index > 0) {
                    val connection = FlightDiscoveryItineraryValidator.connectionMinutes(verified[index - 1], leg)
                    Text(t(language, "Пересадка", "Connection", "Transfer", "Трансфер") +
                        " · ${leg.originAirport} · ${connection ?: "?"} " +
                        t(language, "мин", "min", "daq", "дақ"), fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${leg.originAirport} → ${leg.destinationAirport} · ${leg.airlineCode} ${leg.flightNumber}",
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("${leg.departureAt.take(16).replace('T', ' ')} → ${leg.arrivalAt.take(16).replace('T', ' ')}",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(when(stops) {
            null -> t(language,"Число пересадок не подтверждено","Stop count unconfirmed","Transferlar noma’lum","Трансферлар номаълум")
            0 -> t(language,"Прямой рейс","Non-stop","To‘g‘ri reys","Тўғри рейс")
            else -> "$stops  ${t(language,"пересадок","stops","transfer","трансфер")}" },
            color=if(stops==0) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurface.copy(alpha=.6f),fontSize=12.sp)
    }
}

private fun share(context: Context, offer: FlightDiscoveryOffer, currency: String, adults: Int, children: Int, infants: Int) {
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="text/plain"
        putExtra(Intent.EXTRA_TEXT, "${offer.routeTitle}\n${fmtDate(offer.departureAt)}\n${price(offer.price,currency)}\n${AviasalesSearchLinkBuilder.searchUrl(offer.origin, offer.destination, offer.departureAt, offer.returnAt, adults, children, infants).orEmpty()}")
    },null)) }
}
