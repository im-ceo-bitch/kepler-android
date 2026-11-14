@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.flights

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.FlightDiscoveryOffer
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.data.flight.AviasalesSearchLinkBuilder
import com.iumrah.beta.data.flight.FlightDiscoveryItineraryValidator
import com.iumrah.beta.data.flight.FlightDiscoveryVerifiedSegment
import com.iumrah.beta.data.flight.FlightDataParity
import com.iumrah.beta.data.flight.FlightOperationalPolicy
import com.iumrah.beta.data.flight.IumrahFlightDetailService
import com.iumrah.beta.data.flight.IumrahFlightOperationalDetail

import java.util.Locale
import com.iumrah.beta.ui.components.IumrahPressable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun t(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
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
    var help by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val page = if(MaterialTheme.colorScheme.background.red < .5f) Color(0xFF0B0B0B) else Color(0xFFF2F2F2)
        Column(Modifier.fillMaxSize().background(page)) {
            Row(Modifier.fillMaxWidth().background(FlightHeroColor).statusBarsPadding().padding(horizontal=16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.ChevronLeft, null, Modifier.size(24.dp).clickable(onClick = onDismiss).padding(2.dp),
                    Color.White)
                Spacer(Modifier.width(8.dp))
                Text(FlightReferenceCatalog.airlineName(offer.airlineCode, t(language,"Авиабилет","Flight","Aviachipta","Авиачипта")),
                    Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 17.sp, color=Color.White, maxLines=1)
                Icon(CupertinoSymbol.Share, null,
                    Modifier.size(38.dp).clickable { share(context, offer, currency, adults, children, infants) }.padding(7.dp),
                    Color.White)
                Icon(CupertinoSymbol.QuestionCircle, t(language,"Помощь","Help","Yordam","Ёрдам"), Modifier.size(38.dp).clickable { help=true }.padding(7.dp), Color.White)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start=20.dp,end=20.dp,bottom=20.dp), verticalArrangement=Arrangement.spacedBy(18.dp)) {
            FlightDetailLegV46(language, CupertinoSymbol.AirplaneTakeoff, t(language,"Рейс туда","Outbound flight","Borish reysi","Бориш рейси"),
                offer.originAirport.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: offer.origin, offer.destinationAirport.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: offer.destination, offer.departureAt, offer.airlineCode, offer.flightNumber, offer.transfers,
                offer.durationMinutes, offer.outboundSegments, offer.arrivalAt, pageHeroInset=20.dp)
            if (offer.isRoundTrip) {
                FlightDetailLegV46(language, CupertinoSymbol.AirplaneLand, t(language,"Рейс обратно","Return flight","Qaytish reysi","Қайтиш рейси"),
                    offer.destinationAirport.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: offer.destination, offer.originAirport.takeIf { it.matches(Regex("[A-Z]{3}")) } ?: offer.origin, offer.returnAt.orEmpty(), offer.returnAirlineCode.orEmpty(), offer.returnFlightNumber.orEmpty(), offer.returnTransfers,
                    offer.returnDurationMinutes ?: 0, offer.inboundSegments)
                if (offer.returnAirlineCode.isNullOrBlank() || offer.returnFlightNumber.isNullOrBlank()) {
                    Text(t(language,"Перевозчик и номер обратного рейса пока не подтверждены. Уточните перед покупкой.",
                        "Return carrier and flight number are not yet confirmed. Verify before paying.",
                        "Qaytish reysi va aviakompaniya tasdiqlanmagan. Sotuvchidan tekshiring.",
                        "Қайтиш рейси ва авиакомпания тасдиқланмаган. Сотувчидан текширинг."),
                        fontSize=12.sp,color=Color(0xFFEE9500))
                }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (offer.isRoundTrip) t(language,"Туда‑обратно","Round trip","Borib‑kelish","Бориб‑келиш")
                    else t(language,"В одну сторону","One way","Bir tomonga","Бир томонга"),
                    color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=12.sp,fontWeight=FontWeight.Bold)
                Text(price(offer.price, currency),fontSize=43.sp,fontWeight=FontWeight.Bold)
                TextButton(onClick={isFavorite=!isFavorite; onFavorite(offer)}) {
                    Icon(if(isFavorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart,null,Modifier.size(17.dp),if(isFavorite) Color(0xFFFF375F) else MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(6.dp)); Text(if(isFavorite) t(language,"В избранном","Saved","Saqlangan","Сақланган") else t(language,"Сохранить","Save","Saqlash","Сақлаш"))
                }
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
    if(help) AlertDialog(onDismissRequest={help=false},title={Text(t(language,"Данные рейса","Flight details","Reys ma’lumotlari","Рейс маълумотлари"))},text={Text(t(language,"Время показано по местному времени аэропорта. Цена и наличие подтверждаются у продавца. Оперативные данные обновляются отдельно.","Times are local to each airport. Confirm price and availability with the seller. Operational data is refreshed separately.","Vaqt aeroportning mahalliy vaqti bo‘yicha. Narx va mavjudlikni sotuvchidan tekshiring.","Вақт аэропортнинг маҳаллий вақти бўйича. Нарх ва мавжудликни сотувчидан текширинг."))},confirmButton={TextButton(onClick={help=false}){Text("OK")}})
}

@Composable
private fun FlightDetailLegV46(language: AppLanguage, icon: CupertinoSymbol, title: String, from: String,
    to: String, at: String, code: String, number: String, stops: Int?, durationMinutes: Int,
    segments: List<FlightDiscoveryVerifiedSegment>?, arrival: String? = null, pageHeroInset: Dp=0.dp) {
    val verified = FlightDiscoveryItineraryValidator.verified(segments, from, to, stops ?: -1)
    Column(Modifier.fillMaxWidth(), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if (verified != null) {
            verified.forEachIndexed { index, leg ->
                if (index > 0) {
                    val connection = FlightDiscoveryItineraryValidator.connectionMinutes(verified[index - 1], leg)
                    if (connection != null) Text(t(language,"Пересадка","Connection","Transfer","Трансфер") + " · ${leg.originAirport} · ${connection / 60}h ${connection % 60}m", fontSize=14.sp)
                }
                val duration = java.time.Duration.between(FlightDataParity.instant(leg.departureAt)!!, FlightDataParity.instant(leg.arrivalAt)!!).toMinutes().toInt()
                IumrahPremiumFlightDetail(PremiumFlightLeg(leg.originAirport,leg.destinationAirport,leg.departureAt,leg.arrivalAt,leg.airlineCode,leg.flightNumber,FlightReferenceCatalog.airlineName(leg.airlineCode,leg.airlineCode),0,duration),language,pageHeroInset=if(index==0) pageHeroInset else 0.dp)
            }
        } else {
            IumrahPremiumFlightDetail(PremiumFlightLeg(from,to,at,arrival,code,number,FlightReferenceCatalog.airlineName(code,title),stops,durationMinutes),language,pageHeroInset=pageHeroInset)
        }
    }
}

private fun share(context: Context, offer: FlightDiscoveryOffer, currency: String, adults: Int, children: Int, infants: Int) {
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type="text/plain"
        putExtra(Intent.EXTRA_TEXT, "${offer.routeTitle}\n${fmtDate(offer.departureAt)}\n${price(offer.price,currency)}\n${AviasalesSearchLinkBuilder.searchUrl(offer.origin, offer.destination, offer.departureAt, offer.returnAt, adults, children, infants).orEmpty()}")
    },null)) }
}
