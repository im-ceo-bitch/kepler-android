package com.iumrah.beta.ui.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageFlightPath
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.models.hotel.HotelSummary
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
import kotlinx.coroutines.launch

/** SwiftUI PrimaryHotelView parity. */
@Composable
fun HotelSelectionScreen(
    language: AppLanguage,
    journey: JourneyStore,
    catalog: HotelCatalogService,
    packageEngine: RemotePackageEngineClient,
    curatedFlights: CuratedFlightRecommendationService,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    val requiresMadinah = state.trip.scope == JourneyScope.MAKKAH_AND_MADINAH
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var preparingDirectQuote by remember { mutableStateOf(false) }
    var directQuoteError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.trip.packageTier, state.trip.hotelStars, state.trip.scope) {
        loading = true
        error = null
        runCatching {
            val makkah = catalog.listHotels(listOf("Makkah", "Mecca", "Makka"))
            if (state.makkahHotel == null) {
                val primary = runCatching { packageEngine.primaryHotel(state.trip.packageTier, state.trip.hotelStars, "Makkah") }.getOrNull()
                val chosen = primary?.let { row -> makkah.firstOrNull { it.id == row.hotelId } }
                    ?: primaryCandidate(makkah, state.trip.packageTier)
                chosen?.let(journey::selectHotel)
            }
            if (requiresMadinah) {
                val madinah = catalog.listHotels(madinahAliases)
                if (state.madinahHotel == null) {
                    var chosen: HotelSummary? = null
                    for (alias in madinahAliases) {
                        val primary = runCatching { packageEngine.primaryHotel(state.trip.packageTier, state.trip.hotelStars, alias) }.getOrNull()
                        chosen = primary?.let { row -> madinah.firstOrNull { it.id == row.hotelId } }
                        if (chosen != null) break
                    }
                    (chosen ?: primaryCandidate(madinah, state.trip.packageTier))?.let(journey::selectHotel)
                }
            }
        }.onFailure {
            error = it.message ?: hotelTr(language, "Не удалось загрузить Primary Hotels.", "Could not load Primary Hotels.", "Primary Hotels yuklanmadi.", "Primary Hotels юкланмади.")
        }
        loading = false
    }

    // iOS PrimaryHotelView deliberately requires the hotel only; a stale/missing
    // room cache must never block this step. The quote pipeline re-reads hotel detail.
    val canContinue = state.makkahHotel != null && (!requiresMadinah || state.madinahHotel != null)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = GeneratorGeometry.pagePadding, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { GeneratorHeader(GeneratorStage.HOTEL, language, chrome) }
        item { HotelHeading(language) }
        if (state.packageFlightPath == PackageFlightPath.AVIASALES_SELECTED) {
            item {
                FlightFirstHotelTierCard(language, state.trip.packageTier, onTier = {
                    directQuoteError = null
                    journey.setFlightFirstHotelTier(it)
                })
            }
            item {
                val outbound = state.stagedAviasalesRoundTrip ?: state.stagedAviasalesOutbound
                val inbound = state.stagedAviasalesReturn
                if (outbound != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(15.dp),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                        Text("${outbound.origin.uppercase()} → ${outbound.destination.uppercase()}  ·  ${outbound.departureAt.take(10)}",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                        val returnDate = inbound?.departureAt?.take(10) ?: outbound.returnAt?.take(10)
                        if (!returnDate.isNullOrBlank()) {
                            Icon(CupertinoSymbol.AirplaneLand, null, Modifier.size(15.dp),
                                MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                            Text(returnDate, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
                        }
                    }
                }
            }
        }
        if (loading && state.makkahHotel == null) {
            item { LoadingHotelCard(language) }
        } else {
            state.makkahHotel?.let { hotel ->
                item {
                    PrimaryStayCard(
                        language = language,
                        hotel = hotel,
                        roomName = state.makkahRoom?.name ?: state.makkahRoomCategory?.displayName,
                        cityTitle = localizedMakkah(language),
                        meals = state.trip.effectiveMealSelection,
                        tier = state.trip.packageTier,
                        isMadinah = false,
                        showMeals = state.hasSelectableHotelMeals,
                        onMealChange = journey::setMealSelection,
                        onViewHotel = { chrome.openConfiguratorHotelDetail(hotel.id, "makkah") },
                        onChangeHotel = { chrome.openConfiguratorHotelSelection("makkah") },
                    )
                }
            } ?: item { MissingHotelCard(language, false) { chrome.openConfiguratorHotelSelection("makkah") } }

            if (requiresMadinah) {
                state.madinahHotel?.let { hotel ->
                    item {
                        PrimaryStayCard(
                            language = language,
                            hotel = hotel,
                            roomName = state.madinahRoom?.name ?: state.madinahRoomCategory?.displayName,
                            cityTitle = localizedMadinah(language),
                            meals = state.trip.effectiveMealSelection,
                            tier = state.trip.packageTier,
                            isMadinah = true,
                            showMeals = state.hasSelectableHotelMeals,
                            onMealChange = journey::setMealSelection,
                            onViewHotel = { chrome.openConfiguratorHotelDetail(hotel.id, "madinah") },
                            onChangeHotel = { chrome.openConfiguratorHotelSelection("madinah") },
                        )
                    }
                } ?: item { MissingHotelCard(language, true) { chrome.openConfiguratorHotelSelection("madinah") } }
            }
        }

        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } }
        directQuoteError?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) } }
        item {
            val published = state.packageFlightPath == PackageFlightPath.PUBLISHED_DIRECT
            val aviasales = state.packageFlightPath == PackageFlightPath.AVIASALES_SELECTED
            GeneratorPrimaryButton(
                title = if (published || aviasales) hotelTr(language, "Продолжить к трансферу", "Continue to transfer", "Transferga davom etish", "Трансферга давом этиш")
                else hotelTr(language, "Продолжить к перелёту", "Continue to flights", "Parvozga davom etish", "Парвозга давом этиш"),
                enabled = canContinue && !loading && !preparingDirectQuote && (!published || state.hasCompletePublishedFlightSelection) && (!aviasales || state.hasCompleteStagedFlightSelection),
                onClick = {
                    if (!published && !aviasales) chrome.openFlights()
                    else scope.launch {
                        preparingDirectQuote = true
                        directQuoteError = null
                        val result = if (aviasales) journey.prepareAviasalesSelectedPackage(packageEngine)
                            else journey.preparePublishedDirectPackage(curatedFlights, packageEngine)
                        result.onSuccess { chrome.openTransferSelection() }
                            .onFailure { failure ->
                                directQuoteError = when (failure.message) {
                                    "AVIASALES_UNVERIFIED_DURATION" -> hotelTr(language,
                                        "Время этого рейса не подтверждено Data API. Проверьте маршрут у продавца или выберите другой рейс.",
                                        "Data API has not confirmed this flight duration. Verify with the seller or choose another flight.",
                                        "Data API reys davomiyligini tasdiqlamadi. Sotuvchidan tekshiring yoki boshqa reysni tanlang.",
                                        "Data API рейс давомийлигини тасдиқламади. Сотувчидан текширинг ёки бошқа рейсни танланг.")
                                    "AVIASALES_UNVERIFIED_RETURN_STOPS" -> hotelTr(language,
                                        "Число пересадок обратного рейса не подтверждено. Проверьте билет на Aviasales.",
                                        "The return flight's stops are unconfirmed. Verify the itinerary on Aviasales.",
                                        "Qaytish reysining transferlari tasdiqlanmagan. Aviasales orqali tekshiring.",
                                        "Қайтиш рейсининг трансферлари тасдиқланмаган. Aviasales орқали текширинг.")
                                    "AVIASALES_INVALID_FARE" -> hotelTr(language,
                                        "Данные тарифа устарели. Выберите билет заново.",
                                        "The observed fare is invalid. Select a flight again.",
                                        "Chipta narxi eskirgan. Qayta tanlang.",
                                        "Чипта нархи эскирган. Қайта танланг.")
                                    else -> failure.message ?: publishedFallback(language)
                                }
                            }
                        preparingDirectQuote = false
                    }
                },
            )
        }
        item { Spacer(Modifier.height(34.dp)) }
    }
}

/** The in-place Flight First tier selector, unlike the generic hotel-first screen.
 * One tap invalidates stale primary hotel choices without changing selected flights. */
@Composable
private fun FlightFirstHotelTierCard(language: AppLanguage, selected: PackageTier, onTier: (PackageTier) -> Unit) {
    val heading = hotelTr(language, "Выберите уровень отеля", "Choose hotel category", "Mehmonxona toifasini tanlang", "Меҳмонхона тоифасини танланг")
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(heading, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PackageTier.entries.forEach { tier ->
                val active = tier == selected
                val name = when (tier) {
                    PackageTier.ECONOMY -> hotelTr(language, "Эконом", "Economy", "Ekonom", "Эконом")
                    PackageTier.STANDARD -> hotelTr(language, "Стандарт", "Standard", "Standart", "Стандарт")
                    PackageTier.COMFORT -> hotelTr(language, "Комфорт", "Comfort", "Komfort", "Комфорт")
                    PackageTier.LUXURY -> hotelTr(language, "Люкс", "Luxury", "Lyuks", "Люкс")
                }
                Column(
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(17.dp))
                        .background(if (active) Color(0xFF007AFF) else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onTier(tier) }.padding(vertical = 12.dp, horizontal = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        val tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
                        Text("${tier.primaryHotelStars}", color = tint,
                            fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Icon(CupertinoSymbol.StarFill, null, Modifier.size(14.dp), tint)
                    }
                    Text(name, color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

/** SwiftUI HotelSelectionView parity; it is a separate route, never an inline list. */
@Composable
fun ConfiguratorHotelSelectionScreen(
    role: String,
    language: AppLanguage,
    journey: JourneyStore,
    catalog: HotelCatalogService,
    chrome: AppChromeStore,
) {
    val state by journey.state.collectAsState()
    val madinah = role.equals("madinah", true)
    var hotels by remember(role) { mutableStateOf<List<HotelSummary>>(emptyList()) }
    var loading by remember(role) { mutableStateOf(true) }
    var error by remember(role) { mutableStateOf<String?>(null) }

    LaunchedEffect(role, state.trip.packageTier) {
        loading = true
        error = null
        runCatching {
            hotels = catalog.listHotels(if (madinah) madinahAliases else listOf("Makkah", "Mecca", "Makka"))
        }.onFailure { error = it.message }
        loading = false
    }

    val allowed = state.trip.packageTier.selectableHotelStars.toSet()
    val filtered = remember(hotels, state.trip.packageTier) {
        hotels.filter { it.stars in allowed }.sortedWith(
            compareByDescending<HotelSummary> { it.stars == state.trip.packageTier.primaryHotelStars }
                .thenByDescending { it.hasFreshCatalogPrice }
                .thenByDescending { it.stars ?: 0 }
                .thenBy { it.name.lowercase() },
        )
    }
    val primary = filtered.filter { it.stars == state.trip.packageTier.primaryHotelStars }
    val superEconomy = if (state.trip.packageTier == PackageTier.ECONOMY) filtered.filter { it.stars == 1 } else emptyList()
    val selectedId = if (madinah) state.madinahHotel?.id else state.makkahHotel?.id

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = GeneratorGeometry.pagePadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            IumrahPressable(onClick = { chrome.back() }, modifier = Modifier.size(42.dp), cornerRadius = 21.dp, background = generatorCardColor()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ChevronLeft, null, Modifier.size(20.dp)) }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (madinah) hotelTr(language, "Выберите отель в Медине", "Choose a hotel in Madinah", "Madinadagi mehmonxonani tanlang", "Мадинадаги меҳмонхонани танланг")
                    else hotelTr(language, "Выберите отель в Мекке", "Choose a hotel in Makkah", "Makkadagi mehmonxonani tanlang", "Маккадаги меҳмонхонани танланг"),
                    fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp,
                )
                Text(hotelTr(language, "Выберите отель и номер для вашего личного пакета.", "Choose the hotel and room for your personal package.", "Shaxsiy paketingiz uchun mehmonxona va xonani tanlang.", "Шахсий пакетингиз учун меҳмонхона ва хонани танланг."), fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            }
        }
        item { HotelRefundPolicyCard(language) { chrome.openBookingPolicy("refund") } }

        when {
            loading && filtered.isEmpty() -> item { LoadingHotelCard(language) }
            filtered.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(CupertinoSymbol.Hotel, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
                    Text(error ?: hotelTr(language, "Нет доступных Primary Hotels.", "No Primary Hotels are available.", "Primary Hotels mavjud emas.", "Primary Hotels мавжуд эмас."), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                }
            }
            state.trip.packageTier == PackageTier.ECONOMY -> {
                if (primary.isNotEmpty()) {
                    item { HotelSectionHeader(hotelTr(language, "Эконом · 2★", "Economy · 2★", "Ekonom · 2★", "Эконом · 2★"), hotelTr(language, "Основной выбор категории — практичные 2★ отели.", "The main Economy selection: practical 2★ hotels.", "Ekonom toifasining asosiy tanlovi — amaliy 2★ mehmonxonalar.", "Эконом тоифасининг асосий танлови — амалий 2★ меҳмонхоналар.")) }
                    primary.forEach { hotel -> item(key = "primary-${hotel.id}") { SelectorHotelCard(hotel, selectedId == hotel.id, language) { chrome.openConfiguratorHotelDetail(hotel.id, role) } } }
                }
                if (superEconomy.isNotEmpty()) {
                    item { HotelSectionHeader("Super Economy · 1★", hotelTr(language, "Если важнее минимальная стоимость пакета — доступны и 1★ варианты.", "If the lowest package price matters most, 1★ options are also available.", "Paket narxini yanada kamaytirish muhim bo‘lsa, 1★ variantlar ham mavjud.", "Пакет нархини янада камайтириш муҳим бўлса, 1★ вариантлар ҳам мавжуд.")) }
                    superEconomy.forEach { hotel -> item(key = "super-${hotel.id}") { SelectorHotelCard(hotel, selectedId == hotel.id, language, badge = "Super Economy · 1★") { chrome.openConfiguratorHotelDetail(hotel.id, role) } } }
                }
            }
            else -> primary.forEach { hotel -> item(key = hotel.id) { SelectorHotelCard(hotel, selectedId == hotel.id, language) { chrome.openConfiguratorHotelDetail(hotel.id, role) } } }
        }
        item { Spacer(Modifier.height(42.dp)) }
    }
}

@Composable private fun HotelHeading(language: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(hotelTr(language, "PRIMARY HOTELS", "PRIMARY HOTELS", "PRIMARY HOTELS", "PRIMARY HOTELS"), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
        Text(hotelTr(language, "Ваши отели", "Your hotels", "Mehmonxonalaringiz", "Меҳмонхоналарингиз"), fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        Text(hotelTr(language, "Мы подобрали Primary Hotels вашего уровня. Можно оставить рекомендацию iumrah или выбрать другой вариант.", "We selected Primary Hotels for your trip level. Keep the iumrah recommendation or choose another stay.", "Safar darajangiz uchun Primary Hotels tanlandi. iumrah tavsiyasini qoldiring yoki boshqa variantni tanlang.", "Сафар даражангиз учун Primary Hotels танланди. iumrah тавсиясини қолдиринг ёки бошқа вариантни танланг."), fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .56f))
    }
}

@Composable private fun LoadingHotelCard(language: AppLanguage) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(generatorCardColor()).padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(hotelTr(language, "Подбираем Primary Hotel…", "Finding your Primary Hotel…", "Primary Hotel tanlanmoqda…", "Primary Hotel танланмоқда…"), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
}

@Composable private fun MissingHotelCard(language: AppLanguage, madinah: Boolean, onChoose: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(generatorCardColor()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (madinah) localizedMadinah(language) else localizedMakkah(language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(hotelTr(language, "Primary Hotel пока недоступен. Выберите отель из каталога.", "The Primary Hotel is not available yet. Choose a hotel from the catalogue.", "Primary Hotel hozircha mavjud emas. Katalogdan mehmonxona tanlang.", "Primary Hotel ҳозирча мавжуд эмас. Каталогдан меҳмонхона танланг."), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
        SoftHotelAction(language, CupertinoSymbol.Refresh, hotelTr(language, "Выбрать отель", "Choose hotel", "Mehmonxona tanlash", "Меҳмонхона танлаш"), Modifier.fillMaxWidth(), onChoose)
    }
}

@Composable
private fun PrimaryStayCard(
    language: AppLanguage,
    hotel: HotelSummary,
    roomName: String?,
    cityTitle: String,
    meals: PackageMealSelection,
    tier: PackageTier,
    isMadinah: Boolean,
    showMeals: Boolean,
    onMealChange: (PackageMealSelection) -> Unit,
    onViewHotel: () -> Unit,
    onChangeHotel: () -> Unit,
) {
    val fg = MaterialTheme.colorScheme.onSurface
    val imageUrl = AppConfig.absoluteUrl(hotel.coverImageURL)
    Column(
        Modifier.fillMaxWidth().shadow(18.dp, RoundedCornerShape(30.dp)).clip(RoundedCornerShape(30.dp)).background(generatorCardColor()).border(.7.dp, fg.copy(alpha = .06f), RoundedCornerShape(30.dp)),
    ) {
        Box(Modifier.fillMaxWidth().height(238.dp).padding(12.dp).clip(RoundedCornerShape(24.dp)).background(generatorRaisedColor())) {
            if (imageUrl != null) AsyncImage(imageUrl, hotel.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(CupertinoSymbol.Hotel, null, Modifier.align(Alignment.Center).size(44.dp), tint = fg.copy(alpha = .45f))
        }

        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.height(30.dp).clip(CircleShape).background(Color(0xFF74A187).copy(alpha = .16f)).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(CupertinoSymbol.Sparkles, null, Modifier.size(13.dp))
                    Text(hotelTr(language, "Рекомендуем iumrah", "iumrah Recommended", "iumrah tavsiyasi", "iumrah тавсияси"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(20.dp), tint = Color(0xFF74A187))
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${hotelTr(language, "Primary Hotel", "Primary Hotel", "Primary Hotel", "Primary Hotel")} · ${hotel.stars ?: tier.primaryHotelStars}★ · $cityTitle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg.copy(alpha = .56f))
                Text(hotel.name, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FactPill(CupertinoSymbol.Location, cityTitle)
                roomName?.takeIf { it.isNotBlank() }?.let { FactPill(CupertinoSymbol.Bed, it) }
            }

            if (showMeals) MealPlanCard(language, meals, tier, isMadinah, onMealChange)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftHotelAction(language, CupertinoSymbol.InfoCircle, hotelTr(language, "Посмотреть отель", "View hotel", "Mehmonxonani ko‘rish", "Меҳмонхонани кўриш"), Modifier.weight(1f), onViewHotel)
                SoftHotelAction(language, CupertinoSymbol.Refresh, hotelTr(language, "Сменить отель", "Change hotel", "Mehmonxonani almashtirish", "Меҳмонхонани алмаштириш"), Modifier.weight(1f), onChangeHotel)
            }
        }
    }
}

@Composable private fun FactPill(icon: CupertinoSymbol, text: String) {
    Row(Modifier.height(32.dp).clip(CircleShape).background(generatorRaisedColor()).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun SoftHotelAction(language: AppLanguage, icon: CupertinoSymbol, title: String, modifier: Modifier, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = modifier.height(52.dp), cornerRadius = 18.dp, background = generatorRaisedColor()) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun MealPlanCard(language: AppLanguage, meals: PackageMealSelection, tier: PackageTier, isMadinah: Boolean, onChange: (PackageMealSelection) -> Unit) {
    val price = tier.optionalMealUnitPriceUsd ?: 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(generatorRaisedColor().copy(alpha = .72f)).border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .045f), RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ForkKnife, null, Modifier.size(15.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hotelTr(language, "Питание в отеле", "Hotel meals", "Mehmonxonada ovqatlanish", "Меҳмонхонада овқатланиш"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (isMadinah) hotelTr(language, "Медина · завтрак и ужин", "Madinah · breakfast and dinner", "Madina · nonushta va kechki ovqat", "Мадина · нонушта ва кечки овқат") else hotelTr(language, "Мекка · завтрак, обед и ужин", "Makkah · breakfast, lunch and dinner", "Makka · nonushta, tushlik va kechki ovqat", "Макка · нонушта, тушлик ва кечки овқат"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
        }
        MealDivider()
        MealParityRow(
            language,
            CupertinoSymbol.CupSaucerFill,
            hotelTr(language, "Завтрак", "Breakfast", "Nonushta", "Нонушта"),
            hotelTr(language, "Включено · без доплаты", "Included · no extra charge", "Kiritilgan · qo‘shimcha to‘lovsiz", "Киритилган · қўшимча тўловсиз"),
            true,
            false,
        ) {}
        if (!isMadinah) {
            MealDivider(); MealParityRow(language, CupertinoSymbol.SunMaxFill, hotelTr(language, "Обед", "Lunch", "Tushlik", "Тушлик"), optionalMealText(language, price), meals.makkahLunch, true) { onChange(meals.copy(makkahLunch = it)) }
        }
        MealDivider(); MealParityRow(language, CupertinoSymbol.MoonStarsFill, hotelTr(language, "Ужин", "Dinner", "Kechki ovqat", "Кечки овқат"), optionalMealText(language, price), if (isMadinah) meals.madinahDinner else meals.makkahDinner, true) {
            if (isMadinah) onChange(meals.copy(madinahDinner = it)) else onChange(meals.copy(makkahDinner = it))
        }
    }
}

@Composable private fun MealDivider() { Divider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)) }

@Composable private fun MealParityRow(language: AppLanguage, icon: CupertinoSymbol, title: String, subtitle: String, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    IumrahPressable(onClick = { if (enabled) onChange(!selected) }, enabled = enabled, modifier = Modifier.fillMaxWidth().height(58.dp), cornerRadius = 0.dp, background = Color.Transparent) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f)) }
            if (enabled) IosToggle(selected) else Icon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(20.dp), tint = Color(0xFF74A187))
        }
    }
}

@Composable private fun IosToggle(on: Boolean) {
    Box(Modifier.width(51.dp).height(31.dp).clip(CircleShape).background(if (on) Color(0xFF74A187) else MaterialTheme.colorScheme.onSurface.copy(alpha = .16f)).padding(2.dp)) {
        Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).size(27.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable private fun HotelRefundPolicyCard(language: AppLanguage, onClick: () -> Unit) {
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        background = Color.Transparent,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(generatorCardColor())
                .border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), RoundedCornerShape(20.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(generatorRaisedColor()), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.ShieldCheck, null, Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(hotelTr(language, "Условия отеля", "Hotel refund policy", "Mehmonxona qaytarish siyosati", "Меҳмонхона қайтариш сиёсати"), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(hotelTr(language, "Условия возврата показываются до оплаты пакета.", "Refund terms are shown before package payment.", "Qaytarish shartlari paket to‘lovidan oldin ko‘rsatiladi.", "Қайтариш шартлари пакет тўловидан олдин кўрсатилади."), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f))
            }
            Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
        }
    }
}

@Composable private fun HotelSectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
}

@Composable private fun SelectorHotelCard(hotel: HotelSummary, selected: Boolean, language: AppLanguage, badge: String? = null, onClick: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    val label = if (selected) hotelTr(language, "Выбрано", "Selected", "Tanlangan", "Танланган") else badge
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp, background = Color.Transparent) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(generatorCardColor())
                .border(.7.dp, fg.copy(alpha = .075f), RoundedCornerShape(28.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(generatorRaisedColor()),
            ) {
                AppConfig.absoluteUrl(hotel.coverImageURL)?.let {
                    AsyncImage(it, hotel.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } ?: Icon(CupertinoSymbol.Hotel, null, Modifier.align(Alignment.Center).size(42.dp), tint = fg.copy(alpha = .38f))
                label?.let {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = .42f))
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(it, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(hotel.name, fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    hotel.stars?.let { stars ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(CupertinoSymbol.Star, null, Modifier.size(11.dp), tint = fg.copy(alpha = .55f))
                            Text("$stars", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = .55f))
                        }
                    }
                    hotel.rating?.let { rating ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(CupertinoSymbol.HeartFill, null, Modifier.size(11.dp), tint = fg.copy(alpha = .55f))
                            Text(String.format(java.util.Locale.US, "%.1f", rating), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = .55f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CupertinoSymbol.Location, null, Modifier.size(10.dp), tint = fg.copy(alpha = .55f))
                        Text(com.iumrah.beta.core.localization.L10n.city(hotel.city, language), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun primaryCandidate(hotels: List<HotelSummary>, tier: PackageTier): HotelSummary? {
    val exact = hotels.filter { it.stars == tier.primaryHotelStars }
    return exact.firstOrNull { it.hasFreshCatalogPrice } ?: exact.firstOrNull()
}

private val madinahAliases = listOf("Madinah", "Medina", "Madina", "Medinah", "Al Madinah", "Al Medina", "Madinah Al Munawwarah", "Al Madinah Al Munawwarah")
private fun localizedMakkah(l: AppLanguage) = hotelTr(l, "Мекка", "Makkah", "Makka", "Макка")
private fun localizedMadinah(l: AppLanguage) = hotelTr(l, "Медина", "Madinah", "Madina", "Мадина")
private fun optionalMealText(l: AppLanguage, price: Int) = hotelTr(l, "+$$price за человека / день", "+$$price per person / day", "+$$price kishi / kun", "+$$price киши / кун")
private fun publishedFallback(l: AppLanguage) = hotelTr(l, "Не удалось получить опубликованный рейс или актуальную цену отеля. Попробуйте ещё раз.", "The published flight or current hotel price could not be resolved. Please try again.", "E’lon qilingan reys yoki mehmonxonaning dolzarb narxini olib bo‘lmadi. Qayta urinib ko‘ring.", "Эълон қилинган рейс ёки меҳмонхонанинг долзарб нархини олиб бўлмади. Қайта уриниб кўринг.")
private fun hotelTr(l: AppLanguage, ru: String, en: String, uz: String, uzCy: String) = when (l) { AppLanguage.RUSSIAN -> ru; AppLanguage.ENGLISH -> en; AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> uzCy }
