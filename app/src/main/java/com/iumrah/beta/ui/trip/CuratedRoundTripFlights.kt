package com.iumrah.beta.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.flight.CuratedFlightRecommendation
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Uses the geometry of the (pre-existing) iOS TripBuilderView.curatedRoundTripCard. */
@Composable
internal fun CuratedRoundTripRow(
    language: AppLanguage,
    title: String,
    subtitle: String,
    values: List<CuratedFlightRecommendation>,
    selectedID: String?,
    onSelect: (CuratedFlightRecommendation) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
            Text(values.size.toString(), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(values, key = { "complete-${it.id}" }) { flight ->
                CuratedRoundTripCard(language, flight, selectedID == flight.id) { onSelect(flight) }
            }
        }
    }
}

@Composable
internal fun CuratedFlightTypeSelector(
    language: AppLanguage,
    showRoundTrip: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(generatorRaisedColor()).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(false, true).forEach { roundTrip ->
            val active = roundTrip == showRoundTrip
            IumrahPressable(
                onClick = { onChange(roundTrip) },
                modifier = Modifier.weight(1f).height(34.dp),
                cornerRadius = 9.dp,
                background = if (active) generatorPageColor() else Color.Transparent,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (roundTrip) curatedTripText(language, "Туда-обратно", "Round trip", "Borib-kelish", "Бориб-келиш")
                        else curatedTripText(language, "Отдельные рейсы", "One way", "Alohida reyslar", "Алоҳида рейслар"),
                        fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CuratedRoundTripCard(
    language: AppLanguage,
    flight: CuratedFlightRecommendation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val orange = Color(0xFFFF9500)
    val shape = RoundedCornerShape(22.dp)
    val inbound = flight.inbound ?: return
    val inboundDate = flight.inboundDate ?: return
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.width(292.dp).height(204.dp),
        cornerRadius = 22.dp,
        background = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().clip(shape)
                .background(if (selected) orange.copy(alpha = .075f) else generatorRaisedColor())
                .border(1.dp, if (selected) orange.copy(alpha = .35f)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), shape)
                .padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundTripAirlineLogo(flight.primaryAirlineCode)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(flight.primaryAirlineName, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(flight.flightNumbers.joinToString(" · "), fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.height(25.dp).clip(CircleShape).background(orange.copy(alpha = .1f))
                    .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text(curatedTripText(language, "ТУДА-ОБРАТНО", "ROUND TRIP", "BORIB-KELISH", "БОРИБ-КЕЛИШ"),
                        color = orange, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                CuratedRoundTripLeg(language, flight.outbound.origin, flight.outbound.destination,
                    flight.outboundDate, CupertinoSymbol.AirplaneTakeoff)
                CuratedRoundTripLeg(language, inbound.origin, inbound.destination,
                    inboundDate, CupertinoSymbol.AirplaneLand)
            }
            Box(Modifier.fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(curatedTripText(language, "Выбрать эти даты", "Choose these dates", "Shu sanalarni tanlash", "Шу саналарни танлаш"),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (selected) orange else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f))
                Icon(if (selected) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.ArrowRight,
                    null, Modifier.size(20.dp),
                    tint = if (selected) orange else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CuratedRoundTripLeg(
    language: AppLanguage,
    origin: String,
    destination: String,
    date: String,
    icon: CupertinoSymbol,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = Color(0xFFFF9500))
        Text(origin, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
        Text(destination, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        val parsed = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()
        val shortDate = parsed?.format(DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag(language.localeTag))) ?: date
        Text(shortDate, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
    }
}

@Composable
private fun RoundTripAirlineLogo(codeRaw: String?) {
    val code = codeRaw?.trim()?.uppercase()?.takeIf { it.matches(Regex("^[A-Z0-9]{2}$")) }
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.size(44.dp).clip(shape).background(Color.White)
        .border(.7.dp, Color.Black.copy(alpha = .06f), shape), contentAlignment = Alignment.Center) {
        when {
            code == "C6" -> Image(painterResource(R.drawable.centrum_air_logo), null,
                Modifier.size(38.dp), contentScale = ContentScale.Fit)
            code != null -> AsyncImage(
                model = "https://www.gstatic.com/flights/airline_logos/70px/$code.png",
                contentDescription = null, modifier = Modifier.size(31.dp), contentScale = ContentScale.Fit,
            )
            else -> Icon(CupertinoSymbol.Airplane, null, Modifier.size(15.dp),
                tint = Color.Black.copy(alpha = .55f))
        }
    }
}

private fun curatedTripText(l: AppLanguage, ru: String, en: String, uz: String, uzCy: String): String = when (l) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> uzCy
}

internal fun curatedRoundTripRowTitle(language: AppLanguage): String = curatedTripText(language,
    "Билеты туда-обратно", "Round-trip tickets", "Borib-kelish chiptalari", "Бориб-келиш чипталари")
