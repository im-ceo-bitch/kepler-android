package com.iumrah.beta.ui.packageflow

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.pricing.PackageGenerator
import com.iumrah.beta.domain.trip.HaramainFareClass
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.GeneratorGeometry
import com.iumrah.beta.ui.generator.GeneratorHeader
import com.iumrah.beta.ui.generator.GeneratorPrimaryButton
import com.iumrah.beta.ui.generator.GeneratorStage
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class TransferPhase { SEARCHING, MATCHED }

/** Pixel-oriented Android counterpart of iOS TransferSelectionView. */
@Composable
fun TransferSelectionScreen(
    language: AppLanguage,
    journey: JourneyStore,
    generator: PackageGenerator,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    var phase by remember { mutableStateOf(if (state.transferSelectionConfirmed) TransferPhase.MATCHED else TransferPhase.SEARCHING) }
    var second by remember { mutableIntStateOf(0) }
    val duration = remember { Random.nextInt(20, 41) }

    LaunchedEffect(Unit) {
        journey.ensureHaramainTicketDefaults()
        journey.setHaramainFareClass(HaramainFareClass.ECONOMY)
        if (journey.state.value.selectedTransferVehicle == null) {
            journey.chooseTransferVehicle(TransferVehicleKind.CARNIVAL)
        }

        // iOS refreshes the server-owned package price while transfer discovery runs.
        runCatching { generator.generate(journey.state.value) }
            .onSuccess(journey::setQuote)

        if (journey.state.value.transferSelectionConfirmed) {
            phase = TransferPhase.MATCHED
            return@LaunchedEffect
        }
        while (second < duration) {
            delay(1000)
            second += 1
        }
        journey.chooseTransferVehicle(TransferVehicleKind.CARNIVAL)
        phase = TransferPhase.MATCHED
    }

    AnimatedContent(phase, label = "transfer-phase") { value ->
        if (value == TransferPhase.SEARCHING) {
            TransferSearchScreen(
                language = language,
                chrome = chrome,
                second = second,
                duration = duration,
                travelers = state.trip.travelerCount,
                includesMadinah = state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH,
                currentPrice = state.quote?.let(::quoteTitle),
            )
        } else {
            TransferMatchedScreen(language, journey, generator, chrome)
        }
    }
}

@Composable
private fun TransferSearchScreen(
    language: AppLanguage,
    chrome: AppChromeStore,
    second: Int,
    duration: Int,
    travelers: Int,
    includesMadinah: Boolean,
    currentPrice: String?,
) {
    val page = generatorPageColor()
    val fg = if (page == Color.White) Color.Black else Color.White

    Box(Modifier.fillMaxSize().background(page)) {
        TransferSearchLiveMap(second, duration, language)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(page, page.copy(alpha = .96f), page.copy(alpha = .30f), Color.Transparent, page.copy(alpha = .18f)),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = GeneratorGeometry.pagePadding, vertical = 10.dp)) {
            GeneratorHeader(GeneratorStage.TRANSFER, language, chrome, currentPriceText = currentPrice)
            Spacer(Modifier.weight(1f))
            TransferSearchBottomSheet(language, second, duration, travelers, includesMadinah, fg)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TransferSearchBottomSheet(
    language: AppLanguage,
    second: Int,
    duration: Int,
    travelers: Int,
    includesMadinah: Boolean,
    fg: Color,
) {
    val card = generatorCardColor()
    val raised = generatorRaisedColor()
    val nearby = minOf(11, 2 + second / 3)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(card.copy(alpha = .96f))
            .border(.7.dp, fg.copy(alpha = .07f), RoundedCornerShape(30.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Car, null, Modifier.size(20.dp), tint = Color(0xFF007AFF))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(transferTr(language, "Ищем свободный трансфер", "Finding an available transfer", "Bo‘sh transfer qidirilmoqda", "Бўш трансфер қидирилмоқда"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
                Text(searchStatus(language, second), fontSize = 13.sp, color = fg.copy(alpha = .55f))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${second}s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg)
                Text(transferTr(language, "идёт поиск", "searching", "qidiruv", "қидирув"), fontSize = 10.sp, color = fg.copy(alpha = .48f))
            }
        }

        TransferSearchActivityBar(second, fg)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchChip(CupertinoSymbol.Location, if (includesMadinah) "Makkah ↔ Madinah" else "Makkah", raised, fg)
            SearchChip(CupertinoSymbol.Persons, travelers.toString(), raised, fg)
            SearchChip(CupertinoSymbol.Car, transferTr(language, "$nearby рядом", "$nearby nearby", "$nearby yaqin", "$nearby яқин"), raised, fg)
        }

        Text(
            transferTr(
                language,
                "Проверяем автомобили в Мекке, маршрут поездки, количество гостей и багаж. После проверки iumrah предложит подходящий свободный автомобиль.",
                "Checking vehicles around Makkah, your trip route, party size and luggage. iumrah will then match a suitable available vehicle.",
                "Makkadagi avtomobillar, safar yo‘nalishi, mehmonlar va bagaj tekshirilmoqda. So‘ng iumrah mos bo‘sh avtomobilni tanlaydi.",
                "Маккадаги автомобиллар, сафар йўналиши, меҳмонлар ва багаж текширилмоқда. Сўнг iumrah мос бўш автомобилни танлайди.",
            ),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = fg.copy(alpha = .54f),
        )

        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(fg.copy(alpha = .08f))) {
            Box(Modifier.fillMaxWidth((second.toFloat() / duration.coerceAtLeast(1)).coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(fg.copy(alpha = .78f)))
        }
    }
}

@Composable
private fun SearchChip(icon: CupertinoSymbol, text: String, bg: Color, fg: Color) {
    Row(
        Modifier.height(32.dp).clip(CircleShape).background(bg.copy(alpha = .92f)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(12.dp), tint = fg.copy(alpha = .7f))
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@Composable
private fun TransferSearchActivityBar(second: Int, fg: Color) {
    val active = (second / 5).coerceIn(0, 5)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(6) { index ->
            Box(Modifier.weight(1f).height(if (index == active) 5.dp else 3.dp).clip(CircleShape).background(fg.copy(alpha = if (index <= active) .78f else .10f)))
        }
    }
}

private fun searchStatus(language: AppLanguage, second: Int): String = when (second) {
    in 0..4 -> transferTr(language, "Сканируем дороги рядом с Харамом", "Scanning roads around the Haram", "Haram atrofidagi yo‘llar tekshirilmoqda", "Ҳарам атрофидаги йўллар текширилмоқда")
    in 5..9 -> transferTr(language, "Проверяем доступность водителей", "Checking driver availability", "Haydovchilar mavjudligi tekshirilmoqda", "Ҳайдовчилар мавжудлиги текширилмоқда")
    in 10..14 -> transferTr(language, "Сопоставляем ваши даты", "Matching your travel dates", "Safar sanalari moslashtirilmoqda", "Сафар саналари мослаштирилмоқда")
    in 15..19 -> transferTr(language, "Учитываем гостей и багаж", "Matching guests and luggage", "Mehmonlar va bagaj hisoblanmoqda", "Меҳмонлар ва бағаж ҳисобланмоқда")
    in 20..24 -> transferTr(language, "Проверяем маршрут Мекка — Медина", "Checking the Makkah–Madinah route", "Makka–Madina yo‘nalishi tekshirilmoqda", "Макка–Мадина йўналиши текширилмоқда")
    else -> transferTr(language, "Закрепляем лучший доступный вариант", "Securing the best available match", "Eng mos variant biriktirilmoqda", "Энг мос вариант бириктирилмоқда")
}


@Composable
private fun TransferMatchedScreen(language: AppLanguage, journey: JourneyStore, generator: PackageGenerator, chrome: AppChromeStore) {
    val state by journey.state.collectAsState()
    val vehicles = TransferVehicleKind.entries
    val selected = state.resolvedTransferVehicle
    val initial = vehicles.indexOf(selected).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initial, pageCount = { vehicles.size })
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pager.currentPage) {
        val vehicle = vehicles[pager.currentPage]
        if (journey.state.value.selectedTransferVehicle != vehicle) journey.chooseTransferVehicle(vehicle)
    }

    LaunchedEffect(selected, state.haramainTrainSelected, state.haramainAdultTickets, state.haramainChildTickets) {
        delay(180)
        refreshing = true
        runCatching { generator.generate(journey.state.value) }
            .onSuccess { quote -> journey.setQuote(quote); error = null }
            .onFailure { cause -> error = cause.message }
        refreshing = false
    }

    MaterialTheme(colorScheme = if (selected == TransferVehicleKind.YUKON) darkColorScheme() else lightColorScheme()) {
        val fg = currentFg(selected)
        Column(
            Modifier.fillMaxSize()
                .background(generatorPageColor())
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GeneratorGeometry.pagePadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GeneratorHeader(GeneratorStage.TRANSFER, language, chrome, currentPriceText = state.quote?.let(::quoteTitle))
            TransferMatchedHeader(
                language = language,
                selected = selected,
                selectedIndex = pager.currentPage,
                total = vehicles.size,
                routeTitle = transferRouteTitle(state.trip.scope, state.trip.arrivalAirport),
                dateRangeTitle = transferDateRangeTitle(language, state.trip.departureDate, state.trip.returnDate),
                fg = fg,
            )

            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 0.dp),
                pageSpacing = 0.dp,
                modifier = Modifier.fillMaxWidth().height(340.dp),
            ) { page ->
                VehicleStage(
                    vehicle = vehicles[page],
                    index = page,
                    active = page == pager.currentPage,
                )
            }

            VehicleInformationCard(
                language = language,
                selected = selected,
                scope = state.trip.scope,
                travelers = state.trip.travelerCount,
                includesMadinah = state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH,
                usesTrain = state.haramainTrainSelected,
                currentPrice = state.quote?.let(::quoteTitle) ?: "—",
            )

            TransferRefundPolicyCard(language) { chrome.openBookingPolicy("refund") }

            if (state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH) {
                HaramainCard(
                    language = language,
                    enabled = state.haramainTrainSelected,
                    adults = state.haramainAdultTickets,
                    children = state.haramainChildTickets,
                    maxAdults = state.trip.adults,
                    maxChildren = state.trip.children,
                    onToggle = {
                        journey.ensureHaramainTicketDefaults()
                        journey.setHaramainFareClass(HaramainFareClass.ECONOMY)
                        journey.setHaramainTrainSelected(!state.haramainTrainSelected)
                    },
                    onAdults = journey::setHaramainAdultTickets,
                    onChildren = journey::setHaramainChildTickets,
                )
            }

            if (refreshing) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = fg)
                    Text(transferTr(language, "Обновляем итоговую цену…", "Updating package total…", "Paket narxi yangilanmoqda…", "Пакет нархи янгиланмоқда…"), fontSize = 12.sp, color = fg.copy(alpha = .54f))
                }
            }
            error?.let { Text(it, color = Color(0xFFFF453A), fontSize = 13.sp) }

            TransferConfirmationButton(
                language = language,
                confirming = confirming,
                currentPrice = state.quote?.let(::quoteTitle) ?: "—",
                enabled = !confirming,
            ) {
                if (!confirming) {
                    confirming = true
                    error = null
                    journey.confirmTransferSelection()
                    scope.launch {
                        runCatching { generator.generate(journey.state.value) }
                            .onSuccess { quote ->
                                journey.setQuote(quote)
                                if (quote.quoteId?.startsWith("server-") == true && !quote.quoteProof.isNullOrBlank() && quote.totalPackagePrice.signum() > 0 && quote.pricePerPerson.signum() > 0) {
                                    chrome.openFinalPackage()
                                } else {
                                    error = transferTr(language, "Не удалось подтвердить итоговую конфигурацию поездки. Попробуйте ещё раз.", "The trip configuration could not be confirmed. Please try again.", "Safar konfiguratsiyasini tasdiqlab bo‘lmadi. Qayta urinib ko‘ring.", "Сафар конфигурациясини тасдиқлаб бўлмади. Қайта уриниб кўринг.")
                                }
                                confirming = false
                            }
                            .onFailure { cause ->
                                confirming = false
                                error = cause.message ?: transferTr(language, "Не удалось получить актуальную цену.", "Could not fetch the current package price.", "Joriy paket narxini olib bo‘lmadi.", "Жорий пакет нархини олиб бўлмади.")
                            }
                    }
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun TransferMatchedHeader(
    language: AppLanguage,
    selected: TransferVehicleKind,
    selectedIndex: Int,
    total: Int,
    routeTitle: String,
    dateRangeTitle: String,
    fg: Color,
) {
    val vip = selected == TransferVehicleKind.YUKON
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(17.dp), tint = if (vip) Color.White else Color(0xFF34C759))
            Text(
                if (vip) "VIP TRANSFER" else transferTr(language, "ТРАНСФЕР НАЙДЕН", "TRANSFER MATCHED", "TRANSFER TOPILDI", "ТРАНСФЕР ТОПИЛДИ"),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = fg.copy(alpha = .55f),
            )
            Spacer(Modifier.weight(1f))
            Text("${selectedIndex + 1} / $total", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = .55f))
        }
        Text(
            if (vip) transferTr(language, "VIP трансфер", "VIP transfer", "VIP transfer", "VIP трансфер")
            else transferTr(language, "Мы нашли свободный автомобиль", "We found an available vehicle", "Bo‘sh avtomobil topildi", "Бўш автомобиль топилди"),
            fontSize = if (vip) 38.sp else 32.sp,
            lineHeight = if (vip) 40.sp else 35.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.9).sp,
            color = fg,
        )
        Text("$routeTitle · $dateRangeTitle", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = .55f))
    }
}

@Composable
private fun VehicleStage(vehicle: TransferVehicleKind, index: Int, active: Boolean) {
    val vip = vehicle == TransferVehicleKind.YUKON
    val fg = if (vip) Color.White else Color.Black
    val shape = RoundedCornerShape(36.dp)
    Box(
        Modifier.fillMaxSize()
            .clip(shape)
            .background(if (vip) Color.Black else Color.White)
            .border(.8.dp, if (vip) Color.White.copy(alpha = .12f) else Color.Black.copy(alpha = .055f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (vip) {
            Box(
                Modifier.size(300.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = .45f), Color.White.copy(alpha = .12f), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
        }
        Text(
            String.format("%02d", index + 1),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            fontSize = 156.sp,
            lineHeight = 156.sp,
            fontWeight = FontWeight.Black,
            color = fg.copy(alpha = if (vip) .055f else .035f),
        )
        Text(
            vehicleClassRaw(vehicle).uppercase(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 18.dp, vertical = 28.dp),
            fontSize = if (vip) 104.sp else 72.sp,
            lineHeight = if (vip) 104.sp else 72.sp,
            fontWeight = FontWeight.Black,
            color = fg.copy(alpha = if (vip) .12f else .06f),
            maxLines = 1,
        )
        if (active) {
            Box(
                Modifier.align(Alignment.Center).padding(top = 130.dp)
                    .width(if (vehicle == TransferVehicleKind.MALIBU) 260.dp else 300.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = if (vip) .18f else .12f)),
            )
        }
        Image(
            painterResource(vehicleDrawable(vehicle)),
            vehicle.modelName,
            Modifier.fillMaxWidth().padding(horizontal = if (vehicle == TransferVehicleKind.MALIBU) 20.dp else 8.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun VehicleInformationCard(
    language: AppLanguage,
    selected: TransferVehicleKind,
    scope: JourneyScope,
    travelers: Int,
    includesMadinah: Boolean,
    usesTrain: Boolean,
    currentPrice: String,
) {
    val vip = selected == TransferVehicleKind.YUKON
    val fg = if (vip) Color.White else Color.Black
    val shape = RoundedCornerShape(28.dp)
    val requiredVehicles = maxOf(1, ceil(maxOf(1, travelers).toDouble() / selected.passengerCapacity.toDouble()).toInt())
    val addOn = selected.publicUpgradeUsd(scope)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(generatorCardColor())
            .border(.7.dp, fg.copy(alpha = .06f), shape).padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vehicleClass(language, selected), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .85.sp, color = fg.copy(alpha = .55f))
                Text(selected.modelName, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.45).sp, color = fg)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(14.dp), tint = if (vip) Color.White else Color(0xFF34C759))
                    Text(transferTr(language, "Свободен на ваши даты", "Available for your dates", "Sanalaringizda bo‘sh", "Саналарингизда бўш"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (vip) Color.White else Color(0xFF34C759))
                }
                if (selected == TransferVehicleKind.CARNIVAL) {
                    Box(Modifier.height(25.dp).clip(CircleShape).background(Color(0xFF74A187).copy(alpha = .14f)).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
                        Text(transferTr(language, "Рекомендуем", "Recommended", "Tavsiya", "Тавсия"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
                    }
                }
            }
        }

        Text(vehicleRecommendationBody(language, selected), fontSize = 15.sp, lineHeight = 20.sp, color = fg.copy(alpha = .58f))

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            VehicleMetricChip(CupertinoSymbol.Persons, "${minOf(travelers, selected.passengerCapacity)}/${selected.passengerCapacity}", fg)
            VehicleMetricChip(CupertinoSymbol.Suitcase, "≤ ${selected.luggageCapacity}", fg)
            if (requiredVehicles > 1) VehicleMetricChip(CupertinoSymbol.Car, "$requiredVehicles ×", fg)
        }

        TransferIncludedCard(language, selected, includesMadinah, usesTrain, embedded = true)

        Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = .10f)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(transferTr(language, "Изменение пакета", "Package change", "Paket o‘zgarishi", "Пакет ўзгариши"), fontSize = 12.sp, color = fg.copy(alpha = .55f))
                Text(if (addOn > 0) "+$$addOn" else "+$0", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(transferTr(language, "Итог сейчас", "Current total", "Joriy jami", "Жорий жами"), fontSize = 12.sp, color = fg.copy(alpha = .55f))
                Text(currentPrice, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = fg)
            }
        }
    }
}

@Composable
private fun VehicleMetricChip(icon: CupertinoSymbol, text: String, fg: Color) {
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(generatorRaisedColor()).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = fg)
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun TransferIncludedCard(
    language: AppLanguage,
    selected: TransferVehicleKind,
    includesMadinah: Boolean,
    usesTrain: Boolean,
    embedded: Boolean = false,
) {
    val fg = currentFg(selected)
    val modifier = if (embedded) {
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(generatorRaisedColor()).padding(14.dp)
    } else {
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(generatorCardColor()).border(.7.dp, fg.copy(alpha = .06f), RoundedCornerShape(28.dp)).padding(19.dp)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(transferTr(language, "Что входит в трансфер", "Included in your transfer", "Transferga kiradi", "Трансферга киради"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = fg.copy(alpha = .55f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Center) {
            CoverageStop(CupertinoSymbol.AirplaneLand, transferTr(language, "Аэропорт", "Airport", "Aeroport", "Аэропорт"), fg)
            CoverageLine(fg)
            CoverageStop(CupertinoSymbol.Hotel, "Makkah", fg)
            if (includesMadinah) {
                CoverageLine(fg)
                CoverageStop(if (usesTrain) CupertinoSymbol.Route else CupertinoSymbol.Car, transferTr(language, "Межгород", "Intercity", "Shaharlararo", "Шаҳарлараро"), fg)
                CoverageLine(fg)
                CoverageStop(CupertinoSymbol.Hotel, "Madinah", fg)
            }
            CoverageLine(fg)
            CoverageStop(CupertinoSymbol.AirplaneTakeoff, transferTr(language, "Вылет", "Departure", "Jo‘nab ketish", "Жўнаб кетиш"), fg)
        }
    }
}

@Composable
private fun TransferRefundPolicyCard(language: AppLanguage, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(24.dp)
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, background = generatorCardColor()) {
        Row(
            Modifier.fillMaxWidth().clip(shape).border(.7.dp, fg.copy(alpha = .075f), shape).padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF00C7BE).copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.Car, null, Modifier.size(19.dp), tint = Color(0xFF00A99D))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(transferTr(language, "Трансфер", "Transfer", "Transfer", "Трансфер"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = fg)
                Text(transferTr(language, "Бесплатно до 5 дней", "Free until 5 days before", "5 kun oldin bepul", "5 кун олдин бепул"), fontSize = 12.sp, color = fg.copy(alpha = .55f))
            }
            Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), tint = fg.copy(alpha = .28f))
        }
    }
}

@Composable
private fun TransferConfirmationButton(
    language: AppLanguage,
    confirming: Boolean,
    currentPrice: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.onBackground
    val text = MaterialTheme.colorScheme.background
    IumrahPressable(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(GeneratorGeometry.controlHeight), cornerRadius = GeneratorGeometry.compactRadius, background = bg) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (confirming) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = text)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (confirming) transferTr(language, "Подтверждаем трансфер…", "Confirming transfer…", "Transfer tasdiqlanmoqda…", "Трансфер тасдиқланмоқда…")
                    else transferTr(language, "Подтвердить трансфер", "Confirm transfer", "Transferni tasdiqlash", "Трансферни тасдиқлаш"),
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = text,
                )
                if (!confirming) Text(currentPrice, fontSize = 12.sp, color = text.copy(alpha = .74f))
            }
            Spacer(Modifier.weight(1f))
            if (!confirming) Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp), tint = text)
        }
    }
}

private fun vehicleRecommendationBody(language: AppLanguage, vehicle: TransferVehicleKind): String = when (vehicle) {
    TransferVehicleKind.MALIBU -> transferTr(language,
        "Компактный частный трансфер. Доступен без доплаты к текущему пакету.",
        "A compact private transfer, available with no package surcharge.",
        "Ixcham shaxsiy transfer. Joriy paketga qo‘shimcha to‘lovsiz.",
        "Ихчам шахсий трансфер. Жорий пакетга қўшимча тўловсиз.")
    TransferVehicleKind.CARNIVAL -> transferTr(language,
        "Рекомендованный iumrah Family Transfer: больше пространства для семьи и багажа, без доплаты.",
        "The recommended iumrah Family Transfer: more room for your party and luggage with no surcharge.",
        "Tavsiya etilgan iumrah Family Transfer: oila va bagaj uchun ko‘proq joy, qo‘shimcha to‘lovsiz.",
        "Тавсия этилган iumrah Family Transfer: оила ва багаж учун кўпроқ жой, қўшимча тўловсиз.")
    TransferVehicleKind.YUKON -> transferTr(language,
        "VIP-класс для поездки Мекка — Медина: больше пространства, приватности и отдельная премиальная подача.",
        "VIP class for the Makkah–Madinah journey with more space, privacy and a dedicated premium experience.",
        "Makka–Madina safari uchun VIP klass: ko‘proq joy, maxfiylik va premium tajriba.",
        "Макка–Мадина сафари учун VIP класс: кўпроқ жой, махфийлик ва премиум тажриба.")
}

private fun transferRouteTitle(scope: JourneyScope, arrival: com.iumrah.beta.domain.trip.SaudiArrivalAirport): String {
    if (scope != JourneyScope.MAKKAH_AND_MADINAH) return "JED → Makkah"
    return if (arrival == com.iumrah.beta.domain.trip.SaudiArrivalAirport.MADINAH) "MED → Madinah → Makkah → JED" else "JED → Makkah → Madinah → MED"
}

private fun transferDateRangeTitle(language: AppLanguage, departure: LocalDate, returning: LocalDate): String =
    "${shortTransferDate(language, departure)} – ${shortTransferDate(language, returning)}"

private fun shortTransferDate(language: AppLanguage, date: LocalDate): String {
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru", "RU")
        AppLanguage.ENGLISH -> Locale.US
        AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn-UZ")
        AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl-UZ")
    }
    return date.format(DateTimeFormatter.ofPattern("d MMM", locale)).replace(".", "")
}

private fun vehicleClassRaw(vehicle: TransferVehicleKind): String = when (vehicle) {
    TransferVehicleKind.MALIBU -> "Sedan"
    TransferVehicleKind.CARNIVAL -> "Family"
    TransferVehicleKind.YUKON -> "VIP"
}

@Composable
private fun CoverageStop(icon: CupertinoSymbol, title: String, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(fg.copy(alpha = .08f)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(14.dp), tint = fg) }
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1)
    }
}

@Composable
private fun CoverageLine(fg: Color) {
    Box(Modifier.padding(top = 16.dp).width(15.dp).height(2.dp).clip(CircleShape).background(fg.copy(alpha = .18f)))
}

@Composable
private fun HaramainCard(
    language: AppLanguage,
    enabled: Boolean,
    adults: Int,
    children: Int,
    maxAdults: Int,
    maxChildren: Int,
    onToggle: () -> Unit,
    onAdults: (Int) -> Unit,
    onChildren: (Int) -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onSurface
    val standardPrice = HaramainFareClass.ECONOMY.publicSeatPriceUsd
    val preview = (adults + children) * standardPrice
    val shape = RoundedCornerShape(30.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(generatorCardColor())
            .border(.8.dp, if (enabled) Color(0xFF34C759).copy(alpha = .35f) else fg.copy(alpha = .06f), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.haramain_mark), "Haramain", Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Haramain High Speed Railway", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = fg)
                Text("Makkah ↔ Madinah", fontSize = 11.sp, color = fg.copy(alpha = .52f))
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(CupertinoSymbol.CheckCircle, null, Modifier.size(14.dp), tint = Color(0xFF34C759))
                    Text(transferTr(language, "Добавлено", "Added", "Qo‘shildi", "Қўшилди"), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                }
            }
        }

        HaramainPhotoGallery()

        Text(
            transferTr(language, "Замените только междугородний участок Мекка ↔ Медина поездом. Автомобиль до станции и после прибытия остаётся частью вашего iumrah Transfer.", "Replace only the Makkah ↔ Madinah intercity segment by train. Your car transfer to the station and after arrival remains part of iumrah Transfer.", "Faqat Makka ↔ Madina shaharlararo qismini poyezdga almashtiring. Vokzalgacha va keyingi avtomobil transferi iumrah Transfer tarkibida qoladi.", "Фақат Макка ↔ Мадина шаҳарлараро қисмини поездга алмаштиринг. Вокзалгача ва кейинги автомобиль трансфери iumrah Transfer таркибида қолади."),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = fg.copy(alpha = .56f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TrainFact("300 km/h", Modifier.weight(1f))
            TrainFact("≈ 2h 20m", Modifier.weight(1f))
            TrainFact("Wi‑Fi", Modifier.weight(1f))
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorRaisedColor()).padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(transferTr(language, "Билеты для вашей группы", "Tickets for your party", "Guruhingiz uchun chiptalar", "Гуруҳингиз учун чипталар"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
                    Text("Standard · $$standardPrice / ${transferTr(language, "паломник", "pilgrim", "ziyoratchi", "зиёратчи")}", fontSize = 10.sp, color = fg.copy(alpha = .52f))
                }
                Text("+$$preview", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = fg)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = .09f)))
            TicketStepper(transferTr(language, "Взрослые", "Adults", "Kattalar", "Катталар"), adults, 1, maxOf(1, maxAdults), onAdults)
            if (maxChildren > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = .09f)))
                TicketStepper(transferTr(language, "Дети", "Children", "Bolalar", "Болалар"), children, 0, maxChildren, onChildren)
            }
        }

        IumrahPressable(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(62.dp), cornerRadius = 20.dp, background = if (enabled) Color(0xFF34C759) else Color.Black) {
            Row(Modifier.fillMaxSize().padding(horizontal = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (enabled) CupertinoSymbol.CheckCircle else CupertinoSymbol.Plus, null, Modifier.size(20.dp), tint = Color.White)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (enabled) transferTr(language, "Поезд подключён", "Train added", "Poyezd qo‘shildi", "Поезд қўшилди") else transferTr(language, "Подключить поезд к поездке", "Add train to this trip", "Poyezdni safarga qo‘shish", "Поездни сафарга қўшиш"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (enabled) transferTr(language, "Нажмите, чтобы отключить", "Tap to remove", "Olib tashlash uchun bosing", "Олиб ташлаш учун босинг") else "+$$preview · Standard", fontSize = 10.sp, color = Color.White.copy(alpha = .72f))
                }
                Icon(if (enabled) CupertinoSymbol.Checkmark else CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), tint = Color.White)
            }
        }
    }
}

@Composable
private fun HaramainPhotoGallery() {
    Row(Modifier.fillMaxWidth().height(104.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        listOf(R.drawable.haramain_gallery_station, R.drawable.haramain_gallery_interior, R.drawable.haramain_gallery_train).forEach { drawable ->
            Image(painterResource(drawable), null, Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun TrainFact(title: String, modifier: Modifier) {
    val fg = MaterialTheme.colorScheme.onSurface
    Box(modifier.height(36.dp).clip(CircleShape).background(generatorRaisedColor()), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun TicketStepper(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        Spacer(Modifier.weight(1f))
        MiniStep(CupertinoSymbol.Minus, value > min) { onChange(value - 1) }
        Text(value.toString(), Modifier.width(36.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = fg)
        MiniStep(CupertinoSymbol.Plus, value < max) { onChange(value + 1) }
    }
}

@Composable
private fun MiniStep(icon: CupertinoSymbol, enabled: Boolean, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    IumrahPressable(onClick = onClick, enabled = enabled, modifier = Modifier.size(30.dp), cornerRadius = 99.dp, background = fg.copy(alpha = .08f)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(13.dp), tint = fg.copy(alpha = if (enabled) 1f else .3f)) }
    }
}

private fun quoteTitle(quote: com.iumrah.beta.domain.pricing.PackageQuote): String =
    "${quote.totalPackagePrice.setScale(0, RoundingMode.HALF_UP)} ${quote.currency}"

private fun vehicleDrawable(vehicle: TransferVehicleKind) = when (vehicle) {
    TransferVehicleKind.MALIBU -> R.drawable.transfer_malibu
    TransferVehicleKind.CARNIVAL -> R.drawable.transfer_carnival
    TransferVehicleKind.YUKON -> R.drawable.transfer_yukon
}

private fun vehicleClass(language: AppLanguage, vehicle: TransferVehicleKind) = when (vehicle) {
    TransferVehicleKind.MALIBU -> transferTr(language, "Sedan", "Sedan", "Sedan", "Sedan")
    TransferVehicleKind.CARNIVAL -> transferTr(language, "Family", "Family", "Family", "Family")
    TransferVehicleKind.YUKON -> "VIP"
}

@Composable
private fun currentFg(selected: TransferVehicleKind) = if (selected == TransferVehicleKind.YUKON) Color.White else MaterialTheme.colorScheme.onBackground

private fun transferTr(l: AppLanguage, ru: String, en: String, uz: String, uzCy: String) = when (l) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> uzCy
}
