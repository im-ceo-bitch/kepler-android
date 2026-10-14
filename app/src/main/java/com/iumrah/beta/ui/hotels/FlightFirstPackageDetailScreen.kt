@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.hotels

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.share.IumrahPackageShare
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.navigation.HotelConfiguratorDeepLink
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.domain.trip.FlightFareScope
import com.iumrah.beta.domain.trip.FlightTripType
import com.iumrah.beta.domain.trip.JourneyScope
import com.iumrah.beta.domain.trip.PackageTier
import com.iumrah.beta.domain.trip.PackageMealSelection
import com.iumrah.beta.domain.trip.SaudiArrivalAirport
import com.iumrah.beta.domain.trip.TripDraft
import com.iumrah.beta.models.flight.FlightDirection
import com.iumrah.beta.models.flight.LiveFlightCandidate
import com.iumrah.beta.models.flight.LiveFlightJourneyCandidate
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.StorefrontFlightLeg
import com.iumrah.beta.models.hotel.StorefrontFlightOption
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.CupertinoRatingStars
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.ceil

private enum class PackageLegKind { OUTBOUND, INBOUND }
private enum class PackageInfoSheet { VISA, GUIDE, CARE, TRUST }
private data class PackageFlightChoice(val id: String, val leg: StorefrontFlightLeg)

@Composable
fun FlightFirstPackageDetailScreen(
    packageId: String,
    language: AppLanguage,
    service: HotelCatalogService,
    journey: JourneyStore,
    chrome: AppChromeStore,
    sharedConfiguration: HotelConfiguratorDeepLink? = null,
) {
    val context = LocalContext.current
    var snapshot by remember(packageId) { mutableStateOf<StorefrontPackageSnapshot?>(null) }
    var board by remember(packageId) { mutableStateOf<List<StorefrontFlightOption>>(emptyList()) }
    var makkahHotel by remember(packageId) { mutableStateOf<HotelSummary?>(null) }
    var madinahHotel by remember(packageId) { mutableStateOf<HotelSummary?>(null) }
    var madinahCandidates by remember(packageId) { mutableStateOf<List<HotelSummary>>(emptyList()) }
    var routeScope by remember(packageId) { mutableStateOf(JourneyScope.MAKKAH_ONLY) }
    var firstSaudiCity by remember(packageId) { mutableStateOf(SaudiArrivalAirport.JEDDAH) }
    var loading by remember(packageId) { mutableStateOf(true) }
    var error by remember(packageId) { mutableStateOf<String?>(null) }
    var quote by remember(packageId) { mutableStateOf<PackageQuote?>(null) }
    var repricing by remember(packageId) { mutableStateOf(false) }

    var outboundChoice by remember(packageId) { mutableStateOf<PackageFlightChoice?>(null) }
    var inboundChoice by remember(packageId) { mutableStateOf<PackageFlightChoice?>(null) }
    var picker by remember { mutableStateOf<PackageLegKind?>(null) }
    var infoSheet by remember { mutableStateOf<PackageInfoSheet?>(null) }

    var adults by rememberSaveable(packageId) { mutableStateOf(2) }
    var children by rememberSaveable(packageId) { mutableStateOf(0) }
    var infants by rememberSaveable(packageId) { mutableStateOf(0) }
    var rooms by rememberSaveable(packageId) { mutableStateOf(1) }
    var makkahLunch by rememberSaveable(packageId) { mutableStateOf(false) }
    var makkahDinner by rememberSaveable(packageId) { mutableStateOf(false) }
    var madinahDinner by rememberSaveable(packageId) { mutableStateOf(false) }

    LaunchedEffect(packageId, sharedConfiguration) {
        loading = true
        error = null
        runCatching {
            val loaded = service.storefrontPackage(packageId)
            val flightBoard = service.storefrontFlightBoard(loaded.originCode)
            val hotels = service.listHotels(listOf("Makkah", "Mecca", "Mekka")) + service.listHotels(listOf("Madinah", "Medina", "Madina"))
            Triple(loaded, flightBoard.options, hotels)
        }.onSuccess { (loaded, options, hotels) ->
            snapshot = loaded
            board = options
            makkahHotel = hotels.firstOrNull { it.id == loaded.makkahHotelId || it.id == loaded.hotelFirstAnchorHotelId }
            madinahCandidates = hotels.filter { isMadinahCity(it.city) }
            val restoredScope = sharedConfiguration?.scope
                ?: if ((loaded.madinahNights ?: 0) > 0) JourneyScope.MAKKAH_AND_MADINAH else JourneyScope.MAKKAH_ONLY
            routeScope = restoredScope
            firstSaudiCity = sharedConfiguration?.firstSaudiCity
                ?: if (loaded.outbound?.destination.equals("MED", true)) SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH
            val tier = PackageTier.entries.firstOrNull { it.wireValue.equals(loaded.tier, true) } ?: PackageTier.STANDARD
            madinahHotel = if (restoredScope == JourneyScope.MAKKAH_AND_MADINAH) {
                hotels.firstOrNull { it.id == loaded.madinahHotelId } ?: defaultMadinahHotel(madinahCandidates, tier)
            } else null
            val config = loaded.configuration
            var restoredAdults = (sharedConfiguration?.adults ?: config?.adults ?: 2).coerceIn(1, 9)
            var restoredChildren = (sharedConfiguration?.children ?: config?.children ?: 0).coerceIn(0, 8)
            var restoredInfants = (sharedConfiguration?.infants ?: config?.infants ?: 0).coerceIn(0, 4)
            val overflow = (restoredAdults + restoredChildren + restoredInfants - 9).coerceAtLeast(0)
            if (overflow > 0) {
                val infantReduction = minOf(restoredInfants, overflow)
                restoredInfants -= infantReduction
                restoredChildren = (restoredChildren - (overflow - infantReduction)).coerceAtLeast(0)
            }
            adults = restoredAdults
            children = restoredChildren
            infants = restoredInfants
            rooms = (sharedConfiguration?.rooms ?: config?.rooms ?: 1).coerceIn(1, 9)
            val restoredMeals = sharedConfiguration?.mealSelection
            makkahLunch = restoredMeals?.makkahLunch ?: config?.makkahLunch ?: false
            makkahDinner = restoredMeals?.makkahDinner ?: config?.makkahDinner ?: false
            madinahDinner = restoredMeals?.madinahDinner ?: config?.madinahDinner ?: false

            val restoredOutboundID = sharedConfiguration?.outboundOptionId ?: loaded.outboundOfferId
            val restoredInboundID = sharedConfiguration?.inboundOptionId ?: loaded.inboundOfferId
            val originalOutbound = restoredOutboundID?.let { resolvePackageLeg(options, it, true) }
                ?: loaded.outbound ?: resolvePackageLeg(options, loaded.outboundOfferId, true)
            val originalInbound = restoredInboundID?.let { resolvePackageLeg(options, it, false) }
                ?: loaded.inbound ?: resolvePackageLeg(options, loaded.inboundOfferId, false)
            if (originalOutbound != null && originalInbound != null) {
                outboundChoice = PackageFlightChoice(restoredOutboundID ?: loaded.outboundOfferId ?: packageId, originalOutbound)
                inboundChoice = PackageFlightChoice(restoredInboundID ?: loaded.inboundOfferId ?: packageId, originalInbound)
            }
            quote = loaded.packageQuoteFallback()
            loading = false
        }.onFailure {
            loading = false
            error = it.message ?: detailText(language, "load_error")
        }
    }

    val loaded = snapshot
    val currentOutbound = outboundChoice?.leg
    val currentInbound = inboundChoice?.leg
    val outboundId = outboundChoice?.id
    val inboundId = inboundChoice?.id

    LaunchedEffect(
        loaded?.id,
        outboundId,
        inboundId,
        adults,
        children,
        infants,
        rooms,
        makkahLunch,
        makkahDinner,
        madinahDinner,
        routeScope,
        firstSaudiCity,
        madinahHotel?.id,
    ) {
        val value = loaded ?: return@LaunchedEffect
        val out = currentOutbound ?: return@LaunchedEffect
        val inbound = currentInbound ?: return@LaunchedEffect
        val outID = outboundId ?: return@LaunchedEffect
        val inID = inboundId ?: return@LaunchedEffect
        delay(240)
        repricing = true
        val stay = packageStayNights(out, inbound, routeScope == JourneyScope.MAKKAH_AND_MADINAH)
        runCatching {
            service.storefrontPackageQuote(
                snapshot = value,
                outbound = out,
                inbound = inbound,
                outboundOfferId = outID,
                inboundOfferId = inID,
                adults = adults,
                children = children,
                infants = infants,
                rooms = rooms,
                makkahLunch = makkahLunch,
                makkahDinner = makkahDinner,
                madinahDinner = madinahDinner,
                transferVehicle = value.configuration?.transferVehicle,
                haramainEnabled = value.configuration?.haramainEnabled ?: false,
                haramainFareClass = value.configuration?.haramainFareClass ?: "economy",
                haramainTicketCount = value.configuration?.haramainTicketCount ?: (adults + children),
                makkahHotelIdOverride = makkahHotel?.id,
                madinahHotelIdOverride = madinahHotel?.id,
                makkahNightsOverride = stay.first,
                madinahNightsOverride = stay.second,
                includeMadinahOverride = routeScope == JourneyScope.MAKKAH_AND_MADINAH,
            )
        }.onSuccess { quote = it }.onFailure { if (quote == null) error = it.message }
        repricing = false
    }

    if (loading && loaded == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (loaded == null) {
        PackageLoadFailure(language, error, chrome::back)
        return
    }

    val images = remember(loaded.id, loaded.hotelImages, loaded.imageUrl) {
        buildList {
            loaded.hotelImages.filter { it.isNotBlank() }.forEach { if (it !in this) add(it) }
            loaded.imageUrl?.takeIf { it.isNotBlank() }?.let { if (it !in this) add(it) }
        }
    }
    val currentQuote = quote ?: loaded.packageQuoteFallback()
    val canBook = quote != null && currentOutbound != null && currentInbound != null && makkahHotel != null && (routeScope != JourneyScope.MAKKAH_AND_MADINAH || madinahHotel != null)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PackageTopBar(
            language = language,
            onBack = chrome::back,
            onShare = { IumrahPackageShare.share(context, loaded, currentQuote, language) },
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
        item { PackageHero(loaded, currentOutbound, currentInbound, currentQuote, images, language) }
        if (loaded.entryMode.equals("hotel-first", true)) {
            item {
                HotelFirstRouteCard(
                    language = language,
                    scope = routeScope,
                    firstSaudiCity = firstSaudiCity,
                    onFirstCity = { city ->
                        val routeFlights = selectBestFlightsForRoute(board, loaded.originCode, routeScope, city)
                        if (routeFlights == null) {
                            error = detailText(language, "no_alternatives")
                        } else {
                            firstSaudiCity = city
                            outboundChoice = routeFlights.first
                            inboundChoice = routeFlights.second
                            error = null
                        }
                    },
                    onAddMadinah = { city ->
                        val tier = PackageTier.entries.firstOrNull { it.wireValue.equals(loaded.tier, true) } ?: PackageTier.STANDARD
                        val selected = defaultMadinahHotel(madinahCandidates, tier)
                        val routeFlights = selectBestFlightsForRoute(board, loaded.originCode, JourneyScope.MAKKAH_AND_MADINAH, city)
                        if (selected == null) {
                            error = routeText(language, "madinah_unavailable")
                        } else if (routeFlights == null) {
                            error = detailText(language, "no_alternatives")
                        } else {
                            routeScope = JourneyScope.MAKKAH_AND_MADINAH
                            firstSaudiCity = city
                            madinahHotel = selected
                            outboundChoice = routeFlights.first
                            inboundChoice = routeFlights.second
                            error = null
                        }
                    },
                    onRemoveMadinah = {
                        val routeFlights = selectBestFlightsForRoute(board, loaded.originCode, JourneyScope.MAKKAH_ONLY, SaudiArrivalAirport.JEDDAH)
                        if (routeFlights == null) {
                            error = detailText(language, "no_alternatives")
                        } else {
                            routeScope = JourneyScope.MAKKAH_ONLY
                            firstSaudiCity = SaudiArrivalAirport.JEDDAH
                            madinahHotel = null
                            madinahDinner = false
                            outboundChoice = routeFlights.first
                            inboundChoice = routeFlights.second
                            error = null
                        }
                    },
                )
            }
        }
        item { PackageOverviewCard(loaded, currentOutbound, currentInbound, language, routeScope) }

        item { DetailSectionHeader(detailText(language, "flights"), "iumrah Flights Scanner") }
        currentOutbound?.let { leg ->
            item {
                PackageFlightLegCard(
                    leg = leg,
                    direction = detailText(language, "outbound"),
                    language = language,
                    onChange = { picker = PackageLegKind.OUTBOUND },
                )
            }
        }
        currentInbound?.let { leg ->
            item {
                PackageFlightLegCard(
                    leg = leg,
                    direction = detailText(language, "return"),
                    language = language,
                    onChange = { picker = PackageLegKind.INBOUND },
                )
            }
        }

        item { DetailSectionHeader(detailText(language, "hotels"), "iumrah Hotels") }
        makkahHotel?.let { hotel ->
            item {
                val nights = if (currentOutbound != null && currentInbound != null) packageStayNights(currentOutbound, currentInbound, routeScope == JourneyScope.MAKKAH_AND_MADINAH).first else (loaded.makkahNights ?: 1)
                PackageHotelCard(hotel, nights, language) { chrome.openHotel(hotel.id) }
            }
        } ?: item {
            PackageHotelFallbackCard(loaded.hotelName ?: "iumrah Hotel", "Makkah", loaded.hotelStars, loaded.makkahNights ?: 1, loaded.hotelImages.firstOrNull(), language)
        }
        if (routeScope == JourneyScope.MAKKAH_AND_MADINAH) {
            val stay = if (currentOutbound != null && currentInbound != null) packageStayNights(currentOutbound, currentInbound, true) else Pair(maxOf(1, loaded.makkahNights ?: 1), maxOf(1, loaded.madinahNights ?: 1))
            madinahHotel?.let { hotel ->
                item {
                    PackageHotelCard(hotel, stay.second, language) { chrome.openHotel(hotel.id) }
                }
            } ?: item {
                PackageHotelFallbackCard(loaded.hotelSecondaryName ?: detailText(language, "madinah_hotel"), "Madinah", loaded.hotelStars, stay.second, loaded.hotelImages.drop(1).firstOrNull(), language)
            }
        }

        item { DetailSectionHeader(detailText(language, "included"), "iumrah") }
        item {
            IncludedServicesCard(
                language = language,
                includeMadinah = routeScope == JourneyScope.MAKKAH_AND_MADINAH,
                makkahLunch = makkahLunch,
                makkahDinner = makkahDinner,
                madinahDinner = madinahDinner,
                onMakkahLunch = { makkahLunch = it },
                onMakkahDinner = { makkahDinner = it },
                onMadinahDinner = { madinahDinner = it },
                onVisa = { infoSheet = PackageInfoSheet.VISA },
                onGuide = { infoSheet = PackageInfoSheet.GUIDE },
                onCare = { infoSheet = PackageInfoSheet.CARE },
            )
        }

        item {
            TravelersCard(
                language = language,
                adults = adults,
                children = children,
                infants = infants,
                rooms = rooms,
                onAdults = { adults = it },
                onChildren = { children = it },
                onInfants = { infants = it },
                onRooms = { rooms = it },
                onInvite = { IumrahPackageShare.share(context, loaded, currentQuote, language) },
            )
        }

        item { RefundPolicyCard(language) }
        item { PurchaseTrustCard(language) { infoSheet = PackageInfoSheet.TRUST } }
        item { PriceCard(currentQuote, repricing, adults + children + infants, language) }
        error?.takeIf { it.isNotBlank() }?.let { message ->
            item { Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp)) }
        }
        item {
            IumrahPrimaryButton(
                title = if (repricing) detailText(language, "repricing") else detailText(language, "book"),
                enabled = canBook && !repricing,
                onClick = {
                    val out = currentOutbound ?: return@IumrahPrimaryButton
                    val inbound = currentInbound ?: return@IumrahPrimaryButton
                    val valueQuote = currentQuote ?: return@IumrahPrimaryButton
                    val mHotel = makkahHotel ?: return@IumrahPrimaryButton
                    val trip = buildTrip(
                        snapshot = loaded,
                        outbound = out,
                        inbound = inbound,
                        adults = adults,
                        children = children,
                        infants = infants,
                        rooms = rooms,
                        mealSelection = PackageMealSelection(makkahLunch, makkahDinner, madinahDinner),
                        scopeOverride = routeScope,
                        firstSaudiCityOverride = firstSaudiCity,
                    )
                    val flight = buildJourneyCandidate(loaded, out, inbound, outboundId ?: packageId, inboundId ?: packageId)
                    journey.applyStorefrontPackage(trip, mHotel, madinahHotel, flight, valueQuote)
                    chrome.openBookingCheckout()
                },
            )
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(14.dp), Color(0xFF30B0C7))
                    Text(detailText(language, "generated"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF30B0C7))
                }
                if (loaded.id.matches(Regex("^\\d{10}$"))) {
                    Text("Package ID · ${loaded.id}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
                }
            }
        }
    }
    }

    picker?.let { kind ->
        FlightPickerSheet(
            kind = kind,
            language = language,
            current = if (kind == PackageLegKind.OUTBOUND) currentOutbound else currentInbound,
            choices = flightChoices(board, if (kind == PackageLegKind.OUTBOUND) currentOutbound else currentInbound),
            onDismiss = { picker = null },
            onSelect = { choice ->
                if (kind == PackageLegKind.OUTBOUND) outboundChoice = choice else inboundChoice = choice
                picker = null
            },
        )
    }

    infoSheet?.let { sheet ->
        PackageInformationSheet(sheet, language) { infoSheet = null }
    }
}

@Composable
private fun PackageTopBar(language: AppLanguage, onBack: () -> Unit, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(44.dp).padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IumrahPressable(onClick = onBack, modifier = Modifier.size(40.dp), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.ChevronLeft, detailText(language, "back"), Modifier.size(18.dp)) }
        }
        Text(detailText(language, "title"), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        IumrahPressable(onClick = onShare, modifier = Modifier.size(40.dp), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Share, detailText(language, "share"), Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun PackageHero(
    snapshot: StorefrontPackageSnapshot,
    outbound: StorefrontFlightLeg?,
    inbound: StorefrontFlightLeg?,
    quote: PackageQuote?,
    images: List<String>,
    language: AppLanguage,
) {
    val pageCount = maxOf(1, images.size)
    val pager = rememberPagerState(pageCount = { pageCount })
    Box(Modifier.fillMaxWidth().height(236.dp).clip(RoundedCornerShape(32.dp)).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(32.dp))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (images.isNotEmpty()) {
                AsyncImage(model = images[page], contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.iumrah_flights_showcase_v2),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .05f), Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CupertinoIcon(CupertinoSymbol.Sliders, null, Modifier.size(14.dp), Color.White.copy(alpha = .9f))
                Text("iumrah Configurator", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = .9f))
            }
            Text(
                if (outbound != null && inbound != null) "${outbound.origin} → ${outbound.destination} · ${inbound.origin} → ${inbound.destination}" else snapshot.routeSummary.orEmpty(),
                fontSize = 25.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(money(quote?.pricePerPerson?.toDouble() ?: snapshot.pricePerPerson), fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(detailText(language, "per_person_short"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .78f), modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        if (pageCount > 1) {
            Row(
                Modifier.align(Alignment.TopEnd).padding(16.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .28f)).padding(horizontal = 10.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(pageCount) { index ->
                    Box(Modifier.width(if (index == pager.currentPage) 18.dp else 6.dp).height(6.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = if (index == pager.currentPage) .95f else .42f)))
                }
            }
        }
    }
}

@Composable
private fun HotelFirstRouteCard(
    language: AppLanguage,
    scope: JourneyScope,
    firstSaudiCity: SaudiArrivalAirport,
    onFirstCity: (SaudiArrivalAirport) -> Unit,
    onAddMadinah: (SaudiArrivalAirport) -> Unit,
    onRemoveMadinah: () -> Unit,
) {
    var choosingFirstCity by rememberSaveable { mutableStateOf(false) }
    IumrahCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(CupertinoSymbol.Route, Color(0xFF30B0C7), 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(routeText(language, "title"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (scope == JourneyScope.MAKKAH_AND_MADINAH) routeText(language, "both_subtitle") else routeText(language, "makkah_subtitle"),
                    fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f),
                )
            }
        }
        if (scope == JourneyScope.MAKKAH_AND_MADINAH) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            Text(routeText(language, "first_city").uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
                RouteSegment(
                    title = routeText(language, "jeddah"),
                    selected = firstSaudiCity == SaudiArrivalAirport.JEDDAH,
                    modifier = Modifier.weight(1f),
                ) { onFirstCity(SaudiArrivalAirport.JEDDAH) }
                RouteSegment(
                    title = routeText(language, "madinah"),
                    selected = firstSaudiCity == SaudiArrivalAirport.MADINAH,
                    modifier = Modifier.weight(1f),
                ) { onFirstCity(SaudiArrivalAirport.MADINAH) }
            }
            IumrahPressable(
                onClick = onRemoveMadinah,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                background = Color.Transparent,
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(routeText(language, "remove"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
            }
        } else {
            IumrahPressable(
                onClick = { choosingFirstCity = !choosingFirstCity },
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                background = Color(0xFF30B0C7),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CupertinoIcon(CupertinoSymbol.Plus, null, Modifier.size(16.dp), Color.White)
                    Text(routeText(language, "add"), Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    CupertinoIcon(if (choosingFirstCity) CupertinoSymbol.ChevronDown else CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), Color.White.copy(alpha = .9f))
                }
            }
        }
    }
    if (choosingFirstCity) {
        ModalBottomSheet(onDismissRequest = { choosingFirstCity = false }, containerColor = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(routeText(language, "choose_first"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                RouteChoice(routeText(language, "jeddah"), Modifier.fillMaxWidth()) { choosingFirstCity = false; onAddMadinah(SaudiArrivalAirport.JEDDAH) }
                RouteChoice(routeText(language, "madinah"), Modifier.fillMaxWidth()) { choosingFirstCity = false; onAddMadinah(SaudiArrivalAirport.MADINAH) }
            }
        }
    }
}

@Composable
private fun RouteSegment(title: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(11.dp)).background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent).clickable(onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .92f else .55f))
    }
}

@Composable
private fun RouteChoice(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = modifier, cornerRadius = 14.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
        Text(title, Modifier.fillMaxWidth().padding(vertical = 11.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PackageOverviewCard(snapshot: StorefrontPackageSnapshot, outbound: StorefrontFlightLeg?, inbound: StorefrontFlightLeg?, language: AppLanguage, scopeValue: JourneyScope) {
    val days = if (outbound != null && inbound != null) {
        runCatching {
            val start = LocalDate.parse(outbound.departureAt.take(10))
            val end = LocalDate.parse(inbound.departureAt.take(10))
            java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
        }.getOrDefault(snapshot.totalDays ?: 1)
    } else snapshot.totalDays ?: 1
    val scope = if (scopeValue == JourneyScope.MAKKAH_AND_MADINAH) detailText(language, "makkah_madinah") else detailText(language, "makkah_only")
    IumrahCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(CupertinoSymbol.AirplaneTakeoff, Color(0xFF2679FF))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$days ${detailText(language, "days")} · $scope", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(tierTitle(snapshot.tier, language), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f))
            }
        }
        if (outbound != null && inbound != null && !outbound.origin.equals("TAS", true) && inbound.destination.equals("TAS", true)) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CupertinoIcon(CupertinoSymbol.Route, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                Text(detailText(language, "return_fallback").replace("%s", outbound.origin), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            }
        }
    }
}

@Composable
private fun DetailSectionHeader(title: String, eyebrow: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow.uppercase(), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
        Text(title, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp)
    }
}

@Composable
private fun PackageFlightLegCard(leg: StorefrontFlightLeg, direction: String, language: AppLanguage, onChange: () -> Unit) {
    var expanded by remember(leg) { mutableStateOf(false) }
    IumrahCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PackageAirlineLogo(leg.airlineCode, 50)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(direction.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
                    Text("${leg.origin} → ${leg.destination}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${leg.airline} ${leg.flightNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${dayText(leg.departureAt, language)} · ${clockText(leg.departureAt)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(18.dp), Color(0xFF2679FF))
                    Text(if (leg.stops == 0) detailText(language, "direct") else "${leg.stops} ${detailText(language, "stops")}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                    CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .32f))
                }
            }
            IumrahPressable(onClick = onChange, modifier = Modifier.height(34.dp), cornerRadius = 17.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                Box(Modifier.padding(horizontal = 11.dp), contentAlignment = Alignment.Center) { Text(detailText(language, "change"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        AnimatedVisibility(expanded) {
            Column {
                HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                FlightFact(detailText(language, "departure_airport"), leg.origin)
                FlightFact(detailText(language, "arrival_airport"), leg.destination)
                FlightFact(detailText(language, "departure"), fullDateTime(leg.departureAt, language))
                FlightFact(detailText(language, "arrival"), fullDateTime(leg.arrivalAt, language))
                FlightFact(detailText(language, "duration"), durationText(leg.durationMinutes))
                FlightFact(detailText(language, "cabin"), leg.cabinClass.ifBlank { "—" }.replaceFirstChar { it.uppercase() })
                FlightFact(detailText(language, "flight_number"), leg.flightNumber)
            }
        }
    }
}

@Composable
private fun PackageAirlineLogo(codeRaw: String?, size: Int) {
    val code = codeRaw?.uppercase(Locale.US)?.takeIf { it.length == 2 }
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (code != null) {
            AsyncImage(
                model = "https://www.gstatic.com/flights/airline_logos/70px/$code.png",
                contentDescription = code,
                modifier = Modifier.size((size - 8).dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size((size * .42f).dp), Color(0xFF2679FF))
        }
    }
}

@Composable private fun FlightFact(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1.35f))
    }
}

@Composable
private fun PackageHotelCard(hotel: HotelSummary, nights: Int, language: AppLanguage, onOpen: () -> Unit) {
    PackageHotelFallbackCard(hotel.name, hotel.city, hotel.stars, nights, hotel.coverImageURL, language, onOpen)
}

@Composable
private fun PackageHotelFallbackCard(name: String, city: String, stars: Int?, nights: Int, image: String?, language: AppLanguage, onOpen: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(25.dp)).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(25.dp)).clickable(enabled = onOpen != null) { onOpen?.invoke() },
    ) {
        Box(Modifier.width(118.dp).fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            if (!image.isNullOrBlank()) AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else CupertinoIcon(CupertinoSymbol.Building, null, Modifier.size(28.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
        }
        Column(Modifier.weight(1f).fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(name, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                stars?.let { CupertinoRatingStars(it, size = 11.dp) }
                Text(city, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.Moon, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                Text("$nights ${detailText(language, "nights")}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.Bed, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                Text(detailText(language, "choose_room"), Modifier.padding(start = 6.dp).weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
            }
        }
    }
}

@Composable
private fun IncludedServicesCard(
    language: AppLanguage,
    includeMadinah: Boolean,
    makkahLunch: Boolean,
    makkahDinner: Boolean,
    madinahDinner: Boolean,
    onMakkahLunch: (Boolean) -> Unit,
    onMakkahDinner: (Boolean) -> Unit,
    onMadinahDinner: (Boolean) -> Unit,
    onVisa: () -> Unit,
    onGuide: () -> Unit,
    onCare: () -> Unit,
) {
    var mealsExpanded by rememberSaveable { mutableStateOf(false) }
    IumrahCard(padding = 0) {
        ServiceRow(CupertinoSymbol.Document, detailText(language, "visa"), detailText(language, "visa_sub"), true, onVisa)
        CardDivider()
        ServiceRow(CupertinoSymbol.Car, detailText(language, "transfer"), "Kia Carnival", false, null)
        CardDivider()
        ServiceRow(CupertinoSymbol.ForkKnife, detailText(language, "meals"), detailText(language, "meals_sub"), true) { mealsExpanded = !mealsExpanded }
        AnimatedVisibility(mealsExpanded) {
            Column(Modifier.padding(start = 54.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                MealToggle(detailText(language, "makkah_lunch"), makkahLunch, onMakkahLunch)
                MealToggle(detailText(language, "makkah_dinner"), makkahDinner, onMakkahDinner)
                if (includeMadinah) MealToggle(detailText(language, "madinah_dinner"), madinahDinner, onMadinahDinner)
            }
        }
        CardDivider()
        ServiceRow(CupertinoSymbol.PersonCircle, detailText(language, "guide"), detailText(language, "guide_sub"), true, onGuide)
        CardDivider()
        ServiceRow(CupertinoSymbol.Location, detailText(language, "makkah_ziyarat"), null, false, null)
        if (includeMadinah) { CardDivider(); ServiceRow(CupertinoSymbol.Location, detailText(language, "madinah_ziyarat"), null, false, null) }
        CardDivider()
        ServiceRow(CupertinoSymbol.HeartFill, "iumrah Care", detailText(language, "care_sub"), true, onCare)
    }
}

@Composable
private fun ServiceRow(icon: CupertinoSymbol, title: String, subtitle: String?, chevron: Boolean, action: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = action != null) { action?.invoke() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconBadge(icon, if (icon == CupertinoSymbol.HeartFill) Color(0xFFFF5C77) else Color(0xFF30B0C7), 40)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f)) }
        }
        if (chevron) CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
        else CupertinoIcon(CupertinoSymbol.CheckCircle, null, Modifier.size(16.dp), Color(0xFF30B0C7))
    }
}

@Composable private fun CardDivider() = HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))

@Composable
private fun MealToggle(title: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        IumrahPressable(onClick = { onChange(!enabled) }, modifier = Modifier.width(48.dp).height(28.dp), cornerRadius = 14.dp, background = if (enabled) Color(0xFF30B0C7) else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize().padding(3.dp)) {
                Box(Modifier.align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart).size(22.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun TravelersCard(
    language: AppLanguage,
    adults: Int,
    children: Int,
    infants: Int,
    rooms: Int,
    onAdults: (Int) -> Unit,
    onChildren: (Int) -> Unit,
    onInfants: (Int) -> Unit,
    onRooms: (Int) -> Unit,
    onInvite: () -> Unit,
) {
    IumrahCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CupertinoIcon(CupertinoSymbol.Persons, null, Modifier.size(18.dp))
            Text(detailText(language, "travelers"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text(detailText(language, "travelers_body"), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        CounterRow(detailText(language, "adults"), adults, 1, maxOf(1, 9 - children - infants), onAdults)
        CardDividerFull()
        CounterRow(detailText(language, "children"), children, 0, maxOf(0, 9 - adults - infants), onChildren)
        CardDividerFull()
        CounterRow(detailText(language, "infants"), infants, 0, minOf(4, maxOf(0, 9 - adults - children)), onInfants)
        CardDividerFull()
        CounterRow(detailText(language, "rooms"), rooms, 1, 9, onRooms)
        Spacer(Modifier.height(4.dp))
        IumrahPressable(onClick = onInvite, modifier = Modifier.fillMaxWidth().height(52.dp), cornerRadius = 18.dp, background = Color.Black) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.PlusPerson, null, Modifier.size(18.dp), Color.White)
                Text(detailText(language, "invite"), Modifier.padding(start = 10.dp).weight(1f), color = Color.White, fontWeight = FontWeight.Bold)
                CupertinoIcon(CupertinoSymbol.Share, null, Modifier.size(17.dp), Color.White)
            }
        }
    }
}

@Composable private fun CardDividerFull() = HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))

@Composable
private fun CounterRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        IumrahPressable(onClick = { if (value > min) onChange(value - 1) }, modifier = Modifier.size(34.dp), cornerRadius = 17.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Minus, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (value > min) 1f else .28f)) }
        }
        Text("$value", Modifier.width(42.dp), textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        IumrahPressable(onClick = { if (value < max) onChange(value + 1) }, modifier = Modifier.size(34.dp), cornerRadius = 17.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Plus, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = if (value < max) 1f else .28f)) }
        }
    }
}

@Composable
private fun RefundPolicyCard(language: AppLanguage) {
    IumrahCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(CupertinoSymbol.UndoCircle, Color(0xFF2679FF), 42)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(detailText(language, "refund_title"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detailText(language, "refund_body"), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            }
        }
    }
}

@Composable
private fun PurchaseTrustCard(language: AppLanguage, onOpen: () -> Unit) {
    IumrahPressable(onClick = onOpen, modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(CupertinoSymbol.ShieldCheck, Color(0xFF30B0C7), 42)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(detailText(language, "trust_title"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(detailText(language, "trust_body"), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
        }
    }
}

@Composable
private fun PriceCard(quote: PackageQuote?, repricing: Boolean, travelers: Int, language: AppLanguage) {
    IumrahCard {
        Text(detailText(language, "final_price").uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(money(quote?.totalPackagePrice?.toDouble()), fontSize = 36.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (repricing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            else Text(detailText(language, "total_for").replace("%d", maxOf(1, travelers).toString()), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CupertinoIcon(CupertinoSymbol.PersonCircle, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            Text("${money(quote?.pricePerPerson?.toDouble())} · ${detailText(language, "per_person_long")}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
    }
}

@Composable
private fun FlightPickerSheet(
    kind: PackageLegKind,
    language: AppLanguage,
    current: StorefrontFlightLeg?,
    choices: List<PackageFlightChoice>,
    onDismiss: () -> Unit,
    onSelect: (PackageFlightChoice) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (kind == PackageLegKind.OUTBOUND) detailText(language, "choose_outbound") else detailText(language, "choose_return"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(detailText(language, "flight_picker_note"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
                IumrahPressable(onClick = onDismiss, modifier = Modifier.size(42.dp), cornerRadius = 21.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(17.dp)) }
                }
            }
            if (choices.isEmpty()) {
                Text(detailText(language, "no_alternatives"), Modifier.fillMaxWidth().padding(vertical = 26.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            } else {
                choices.take(18).forEach { choice ->
                    val selected = current == choice.leg
                    IumrahPressable(onClick = { onSelect(choice) }, modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, background = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(CupertinoSymbol.Airplane, Color(0xFF2679FF), 42)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${choice.leg.origin} → ${choice.leg.destination}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${choice.leg.airline} ${choice.leg.flightNumber} · ${dayText(choice.leg.departureAt, language)} ${clockText(choice.leg.departureAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) CupertinoIcon(CupertinoSymbol.CheckCircle, null, Modifier.size(18.dp), Color(0xFF30B0C7))
                            else CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .28f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageInformationSheet(kind: PackageInfoSheet, language: AppLanguage, onDismiss: () -> Unit) {
    val (icon, title, body) = when (kind) {
        PackageInfoSheet.VISA -> Triple(CupertinoSymbol.Document, detailText(language, "visa"), detailText(language, "visa_info"))
        PackageInfoSheet.GUIDE -> Triple(CupertinoSymbol.PersonCircle, detailText(language, "guide"), detailText(language, "guide_info"))
        PackageInfoSheet.CARE -> Triple(CupertinoSymbol.HeartFill, "iumrah Care", detailText(language, "care_info"))
        PackageInfoSheet.TRUST -> Triple(CupertinoSymbol.ShieldCheck, detailText(language, "trust_title"), detailText(language, "trust_info"))
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 38.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            IconBadge(icon, if (kind == PackageInfoSheet.CARE) Color(0xFFFF5C77) else Color(0xFF30B0C7), 50)
            Text(title, fontSize = 28.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 15.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            IumrahPrimaryButton(detailText(language, "close"), onClick = onDismiss)
        }
    }
}

@Composable
private fun IumrahCard(padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f), RoundedCornerShape(24.dp)).padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun IconBadge(symbol: CupertinoSymbol, tint: Color, size: Int = 46) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .32f).dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        CupertinoIcon(symbol, null, Modifier.size((size * .4f).dp), tint)
    }
}

@Composable
private fun PackageLoadFailure(language: AppLanguage, error: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconBadge(CupertinoSymbol.ExclamationCircle, Color(0xFFFF9F0A), 54)
        Spacer(Modifier.height(14.dp))
        Text(detailText(language, "load_failed"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(error.orEmpty(), Modifier.padding(top = 7.dp, bottom = 18.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), textAlign = TextAlign.Center)
        IumrahPrimaryButton(detailText(language, "back"), onClick = onBack)
    }
}

private fun isMadinahCity(city: String): Boolean {
    val value = city.lowercase(Locale.US)
    return value.contains("madin") || value.contains("medin")
}

private fun defaultMadinahHotel(hotels: List<HotelSummary>, tier: PackageTier): HotelSummary? {
    val exact = hotels.filter { (it.stars ?: 0) == tier.primaryHotelStars && it.hasFreshCatalogPrice }
    return exact.firstOrNull() ?: hotels.firstOrNull { it.hasFreshCatalogPrice } ?: hotels.firstOrNull { (it.stars ?: 0) == tier.primaryHotelStars } ?: hotels.firstOrNull()
}

private fun selectBestFlightsForRoute(
    options: List<StorefrontFlightOption>,
    originCode: String,
    scope: JourneyScope,
    firstSaudiCity: SaudiArrivalAirport,
): Pair<PackageFlightChoice, PackageFlightChoice>? {
    val origin = originCode.uppercase()
    val outboundDestination = if (scope == JourneyScope.MAKKAH_ONLY) "JED" else firstSaudiCity.iata
    val returnOrigin = when {
        scope == JourneyScope.MAKKAH_ONLY -> "JED"
        firstSaudiCity == SaudiArrivalAirport.MADINAH -> "JED"
        else -> "MED"
    }
    fun candidates(from: String, to: String): List<PackageFlightChoice> = buildList {
        options.forEach { option ->
            val legs = buildList { add(option.outbound); option.inbound?.let(::add) }
            legs.filter { it.origin.equals(from, true) && it.destination.equals(to, true) }
                .forEach { add(PackageFlightChoice(option.id, it)) }
        }
    }.distinctBy { "${it.id}:${it.leg.flightNumber}:${it.leg.departureAt}" }.sortedBy { it.leg.departureAt }
    val out = candidates(origin, outboundDestination).firstOrNull() ?: return null
    val inbound = candidates(returnOrigin, origin).firstOrNull()
        ?: (if (origin != "TAS") candidates(returnOrigin, "TAS").firstOrNull() else null)
        ?: return null
    return out to inbound
}

private fun packageStayNights(outbound: StorefrontFlightLeg, inbound: StorefrontFlightLeg, includeMadinah: Boolean): Pair<Int, Int> {
    val start = parseDay(outbound.arrivalAt)
    val end = parseDay(inbound.departureAt)
    val total = java.time.temporal.ChronoUnit.DAYS.between(start, end).toInt().coerceAtLeast(1)
    if (!includeMadinah || total <= 1) return total to 0
    val makkah = ceil(total * .6).toInt().coerceIn(1, total - 1)
    return makkah to (total - makkah).coerceAtLeast(1)
}

private fun routeText(language: AppLanguage, key: String): String {
    val values = when (language) {
        AppLanguage.RUSSIAN -> mapOf(
            "title" to "Маршрут поездки", "makkah_subtitle" to "Сейчас пакет собран только для Мекки", "both_subtitle" to "Мекка и Медина включены в один маршрут",
            "first_city" to "Первый город", "jeddah" to "Сначала Мекка", "madinah" to "Сначала Медина", "add" to "Добавить Медину",
            "remove" to "Убрать Медину из пакета", "choose_first" to "С какого города начать поездку?", "madinah_unavailable" to "Сейчас не удалось подобрать отель iumrah в Медине."
        )
        AppLanguage.ENGLISH -> mapOf(
            "title" to "Trip route", "makkah_subtitle" to "The package currently covers Makkah only", "both_subtitle" to "Makkah and Madinah are included in one route",
            "first_city" to "First city", "jeddah" to "Makkah first", "madinah" to "Madinah first", "add" to "Add Madinah",
            "remove" to "Remove Madinah from package", "choose_first" to "Which city should come first?", "madinah_unavailable" to "An iumrah hotel in Madinah is not available right now."
        )
        AppLanguage.UZBEK -> mapOf(
            "title" to "Safar yo‘nalishi", "makkah_subtitle" to "Hozir paket faqat Makka uchun", "both_subtitle" to "Makka va Madina bitta yo‘nalishga qo‘shilgan",
            "first_city" to "Birinchi shahar", "jeddah" to "Avval Makka", "madinah" to "Avval Madina", "add" to "Madinani qo‘shish",
            "remove" to "Madinani paketdan olib tashlash", "choose_first" to "Safarni qaysi shahardan boshlaysiz?", "madinah_unavailable" to "Hozir Madinada iumrah mehmonxonasini tanlab bo‘lmadi."
        )
        AppLanguage.UZBEK_CYRILLIC -> mapOf(
            "title" to "Сафар йўналиши", "makkah_subtitle" to "Ҳозир пакет фақат Макка учун", "both_subtitle" to "Макка ва Мадина битта йўналишга қўшилган",
            "first_city" to "Биринчи шаҳар", "jeddah" to "Аввал Макка", "madinah" to "Аввал Мадина", "add" to "Мадинани қўшиш",
            "remove" to "Мадинани пакетдан олиб ташлаш", "choose_first" to "Сафарни қайси шаҳардан бошлайсиз?", "madinah_unavailable" to "Ҳозир Мадинада iumrah меҳмонхонасини танлаб бўлмади."
        )
    }
    return values[key] ?: key
}

private fun resolvePackageLeg(options: List<StorefrontFlightOption>, id: String?, outbound: Boolean): StorefrontFlightLeg? {
    val option = options.firstOrNull { it.id == id } ?: return null
    return if (outbound) option.outbound else option.inbound ?: option.outbound
}

private fun flightChoices(options: List<StorefrontFlightOption>, reference: StorefrontFlightLeg?): List<PackageFlightChoice> {
    if (reference == null) return emptyList()
    val values = mutableListOf<PackageFlightChoice>()
    options.forEach { option ->
        val legs = buildList { add(option.outbound); option.inbound?.let(::add) }
        legs.filter { it.origin.equals(reference.origin, true) && it.destination.equals(reference.destination, true) }
            .forEach { values += PackageFlightChoice(option.id, it) }
    }
    return values.distinctBy { "${it.id}:${it.leg.flightNumber}:${it.leg.departureAt}" }.sortedBy { it.leg.departureAt }
}

private fun StorefrontPackageSnapshot.packageQuoteFallback(): PackageQuote? {
    val total = totalPackagePrice ?: return null
    val pp = pricePerPerson ?: return null
    return PackageQuote(BigDecimal.valueOf(total), BigDecimal.valueOf(pp), currency, isEstimated)
}

private fun buildTrip(
    snapshot: StorefrontPackageSnapshot,
    outbound: StorefrontFlightLeg,
    inbound: StorefrontFlightLeg,
    adults: Int,
    children: Int,
    infants: Int,
    rooms: Int,
    mealSelection: PackageMealSelection,
    scopeOverride: JourneyScope? = null,
    firstSaudiCityOverride: SaudiArrivalAirport? = null,
): TripDraft {
    val tier = PackageTier.entries.firstOrNull { it.wireValue.equals(snapshot.tier, true) } ?: PackageTier.STANDARD
    val includeMadinah = (snapshot.madinahNights ?: 0) > 0
    return TripDraft(
        origin = outbound.origin.uppercase(),
        arrivalAirport = firstSaudiCityOverride ?: if (outbound.destination.equals("MED", true)) SaudiArrivalAirport.MADINAH else SaudiArrivalAirport.JEDDAH,
        departureDate = parseDay(outbound.departureAt),
        saudiArrivalDate = parseDay(outbound.arrivalAt),
        returnDate = parseDay(inbound.departureAt),
        adults = adults,
        children = children,
        infants = infants,
        rooms = rooms,
        hotelStars = when (tier) { PackageTier.ECONOMY -> 2; PackageTier.STANDARD -> 3; PackageTier.COMFORT -> 4; PackageTier.LUXURY -> 5 },
        packageTier = tier,
        mealSelection = mealSelection,
        scope = scopeOverride ?: if (includeMadinah) JourneyScope.MAKKAH_AND_MADINAH else JourneyScope.MAKKAH_ONLY,
        hotelFirstStayPolicy = if (snapshot.entryMode.equals("hotel-first", true)) true else null,
        flightTripType = FlightTripType.ROUND_TRIP,
    )
}

private fun buildJourneyCandidate(
    snapshot: StorefrontPackageSnapshot,
    outbound: StorefrontFlightLeg,
    inbound: StorefrontFlightLeg,
    outboundId: String,
    inboundId: String,
): LiveFlightJourneyCandidate {
    val provider = if (!snapshot.providerItineraryId.isNullOrBlank() && outboundId == snapshot.outboundOfferId && inboundId == snapshot.inboundOfferId) snapshot.providerItineraryId
        else if (outboundId == inboundId) "curated:$outboundId" else "curated:$outboundId+$inboundId"
    val now = Instant.now()
    fun candidate(id: String, leg: StorefrontFlightLeg, direction: FlightDirection): LiveFlightCandidate = LiveFlightCandidate(
        id = id,
        sourceID = "iumrah-storefront",
        sourceName = "iumrah Flights Scanner",
        direction = direction,
        airline = leg.airline,
        flightNumber = leg.flightNumber,
        origin = leg.origin,
        destination = leg.destination,
        departureAt = OffsetDateTime.parse(leg.departureAt).toInstant(),
        arrivalAt = OffsetDateTime.parse(leg.arrivalAt).toInstant(),
        stops = leg.stops,
        durationMinutes = leg.durationMinutes,
        observedFare = BigDecimal.ONE,
        observedCurrency = snapshot.currency,
        fareScope = FlightFareScope.PER_PASSENGER,
        observedAt = now,
        airlineCode = leg.airlineCode,
        providerItineraryID = provider,
        cabinClass = leg.cabinClass,
    )
    val out = candidate(outboundId, outbound, FlightDirection.outbound)
    val back = candidate(inboundId, inbound, FlightDirection.inbound)
    return LiveFlightJourneyCandidate(
        id = "storefront:${snapshot.id}:$outboundId:$inboundId",
        sourceID = "iumrah-storefront",
        sourceName = "iumrah Flights Scanner",
        totalFare = BigDecimal.ONE,
        currency = snapshot.currency,
        fareScope = FlightFareScope.PER_PASSENGER,
        observedAt = now,
        providerItineraryID = provider.orEmpty(),
        outbound = out,
        inbound = back,
    )
}

private fun parseDay(raw: String): LocalDate = runCatching { OffsetDateTime.parse(raw).toLocalDate() }.getOrElse { LocalDate.parse(raw.take(10)) }
private fun clockText(raw: String): String = runCatching { OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("HH:mm")) }.getOrElse { raw.takeLast(5) }
private fun fullDateTime(raw: String, language: AppLanguage): String = runCatching {
    val locale = when (language) { AppLanguage.RUSSIAN -> Locale("ru"); AppLanguage.ENGLISH -> Locale.ENGLISH; AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn"); AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl") }
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", locale))
}.getOrElse { raw }
private fun dayText(raw: String, language: AppLanguage): String = runCatching {
    val locale = when (language) { AppLanguage.RUSSIAN -> Locale("ru"); AppLanguage.ENGLISH -> Locale.ENGLISH; AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn"); AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl") }
    OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("d MMM", locale))
}.getOrElse { raw.take(10) }
private fun durationText(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${minutes % 60}m"
private fun money(value: Double?): String = value?.let { "$${String.format(Locale.US, "%.0f", it)}" } ?: "—"

private fun tierTitle(raw: String?, language: AppLanguage): String {
    val key = raw?.lowercase() ?: "standard"
    return when (language) {
        AppLanguage.RUSSIAN -> when (key) { "economy" -> "Economy"; "comfort" -> "Comfort"; "luxury" -> "Luxury"; else -> "Standard" }
        AppLanguage.ENGLISH -> key.replaceFirstChar { it.uppercase() }
        AppLanguage.UZBEK -> key.replaceFirstChar { it.uppercase() }
        AppLanguage.UZBEK_CYRILLIC -> key.replaceFirstChar { it.uppercase() }
    }
}

private fun detailText(language: AppLanguage, key: String): String = when (language) {
    AppLanguage.RUSSIAN -> mapOf(
        "title" to "Пакет Умры", "back" to "Назад", "share" to "Поделиться", "per_person_short" to "за человека", "flights" to "Перелёты", "hotels" to "Отели", "included" to "Что включено", "outbound" to "Туда", "return" to "Обратно", "change" to "Изменить", "makkah_madinah" to "Мекка + Медина", "makkah_only" to "Только Мекка", "days" to "дней", "nights" to "ноч.", "return_fallback" to "Для этого пакета обратный рейс приходит в Ташкент, потому что подходящего возврата в %s в выбранном диапазоне нет.", "direct" to "прямой", "stops" to "пересад.", "departure_airport" to "Аэропорт вылета", "arrival_airport" to "Аэропорт прилёта", "departure" to "Вылет", "arrival" to "Прилёт", "duration" to "В пути", "cabin" to "Класс", "flight_number" to "Рейс", "choose_room" to "Выбрать номер", "madinah_hotel" to "Отель в Медине", "visa" to "Виза", "visa_sub" to "Визовая поддержка включена в пакет", "transfer" to "Трансфер", "meals" to "Питание", "meals_sub" to "Завтрак включён · дополнительные блюда на выбор", "guide" to "Сопровождение", "guide_sub" to "Поддержка iumrah во время поездки", "makkah_ziyarat" to "Зиярат в Мекке", "madinah_ziyarat" to "Зиярат в Медине", "care_sub" to "Помощь до, во время и после поездки", "makkah_lunch" to "Мекка · обед", "makkah_dinner" to "Мекка · ужин", "madinah_dinner" to "Медина · ужин", "travelers" to "Паломники", "travelers_body" to "Измените состав группы — iumrah пересчитает тот же пакет на сервере.", "adults" to "Взрослые", "children" to "Дети", "infants" to "Младенцы", "rooms" to "Номера", "invite" to "Пригласить паломника", "refund_title" to "Политика возврата", "refund_body" to "Условия возврата применяются к пакету целиком и показываются до оплаты.", "trust_title" to "Доверие и подтверждение бронирования", "trust_body" to "Прямой контакт с основателем · инвойс и чек · ответственность iumrah за отель и iumrah Services", "final_price" to "Итоговая цена", "total_for" to "итого для %d", "per_person_long" to "за человека", "repricing" to "Пересчитываем пакет…", "book" to "Забронировать поездку", "generated" to "Сгенерировано iumrah Configurator", "choose_outbound" to "Выберите рейс туда", "choose_return" to "Выберите обратный рейс", "flight_picker_note" to "Показываем опубликованные рейсы. Компонентная цена билета скрыта — изменяется только итог пакета.", "no_alternatives" to "Подходящих опубликованных рейсов сейчас нет.", "visa_info" to "Визовая поддержка входит в пакет. Финальные требования и документы подтверждаются до оплаты.", "guide_info" to "iumrah помогает координировать поездку и остаётся на связи по вопросам пакета.", "care_info" to "iumrah Care сопровождает бронирование и помогает по поездке через поддержку iumrah.", "trust_info" to "После бронирования вы получаете подтверждение, инвойс/чек и единый канал поддержки по услугам iumrah.", "close" to "Закрыть", "load_failed" to "Пакет не удалось открыть", "load_error" to "Не удалось загрузить пакет."
    )[key] ?: key
    AppLanguage.ENGLISH -> mapOf(
        "title" to "Umrah package", "back" to "Back", "share" to "Share", "per_person_short" to "per person", "flights" to "Flights", "hotels" to "Hotels", "included" to "What's included", "outbound" to "Outbound", "return" to "Return", "change" to "Change", "makkah_madinah" to "Makkah + Madinah", "makkah_only" to "Makkah only", "days" to "days", "nights" to "nights", "return_fallback" to "This package returns to Tashkent because no suitable flight back to %s is available in the package window.", "direct" to "direct", "stops" to "stops", "departure_airport" to "Departure airport", "arrival_airport" to "Arrival airport", "departure" to "Departure", "arrival" to "Arrival", "duration" to "Duration", "cabin" to "Cabin", "flight_number" to "Flight", "choose_room" to "Choose room", "madinah_hotel" to "Madinah hotel", "visa" to "Visa", "visa_sub" to "Visa support is included in your package", "transfer" to "Transfer", "meals" to "Meals", "meals_sub" to "Breakfast included · optional meals available", "guide" to "Accompaniment", "guide_sub" to "iumrah support during your journey", "makkah_ziyarat" to "Makkah ziyarat", "madinah_ziyarat" to "Madinah ziyarat", "care_sub" to "Help before, during and after your journey", "makkah_lunch" to "Makkah · lunch", "makkah_dinner" to "Makkah · dinner", "madinah_dinner" to "Madinah · dinner", "travelers" to "Travelers", "travelers_body" to "Change your party and iumrah will reprice the same package on the server.", "adults" to "Adults", "children" to "Children", "infants" to "Infants", "rooms" to "Rooms", "invite" to "Invite a pilgrim", "refund_title" to "Refund policy", "refund_body" to "Refund terms apply to the whole package and are shown before payment.", "trust_title" to "Booking trust and confirmation", "trust_body" to "Direct founder contact · invoice and receipt · iumrah responsibility for the hotel and iumrah Services", "final_price" to "Final price", "total_for" to "total for %d", "per_person_long" to "per person", "repricing" to "Recalculating package…", "book" to "Book this trip", "generated" to "Generated by iumrah Configurator", "choose_outbound" to "Choose outbound flight", "choose_return" to "Choose return flight", "flight_picker_note" to "Published flights only. The component airfare stays hidden — only your package total changes.", "no_alternatives" to "No matching published flights are available right now.", "visa_info" to "Visa support is included in the package. Final document requirements are confirmed before payment.", "guide_info" to "iumrah helps coordinate the journey and stays available for package support.", "care_info" to "iumrah Care supports your booking and helps throughout the journey.", "trust_info" to "After booking you receive confirmation, invoice/receipt and one iumrah support channel for included services.", "close" to "Close", "load_failed" to "The package could not be opened", "load_error" to "Could not load the package."
    )[key] ?: key
    AppLanguage.UZBEK -> mapOf(
        "title" to "Umra paketi", "back" to "Orqaga", "share" to "Ulashish", "per_person_short" to "kishi uchun", "flights" to "Parvozlar", "hotels" to "Mehmonxonalar", "included" to "Nimalar kiradi", "outbound" to "Borish", "return" to "Qaytish", "change" to "O‘zgartirish", "makkah_madinah" to "Makka + Madina", "makkah_only" to "Faqat Makka", "days" to "kun", "nights" to "tun", "return_fallback" to "Bu paket Toshkentga qaytadi, chunki paket oralig‘ida %s ga mos qaytish reysi topilmadi.", "direct" to "to‘g‘ridan-to‘g‘ri", "stops" to "ulanish", "departure_airport" to "Jo‘nash aeroporti", "arrival_airport" to "Yetib borish aeroporti", "departure" to "Jo‘nash", "arrival" to "Yetib kelish", "duration" to "Yo‘lda", "cabin" to "Klass", "flight_number" to "Reys", "choose_room" to "Xonani tanlash", "madinah_hotel" to "Madina mehmonxonasi", "visa" to "Viza", "visa_sub" to "Viza yordami paketga kiritilgan", "transfer" to "Transfer", "meals" to "Ovqatlanish", "meals_sub" to "Nonushta kiritilgan · qo‘shimcha ovqatlar tanlanadi", "guide" to "Hamrohlik", "guide_sub" to "Safar davomida iumrah yordami", "makkah_ziyarat" to "Makka ziyorati", "madinah_ziyarat" to "Madina ziyorati", "care_sub" to "Safardan oldin, davomida va keyin yordam", "makkah_lunch" to "Makka · tushlik", "makkah_dinner" to "Makka · kechki ovqat", "madinah_dinner" to "Madina · kechki ovqat", "travelers" to "Ziyoratchilar", "travelers_body" to "Guruh tarkibini o‘zgartiring — iumrah shu paketni serverda qayta hisoblaydi.", "adults" to "Kattalar", "children" to "Bolalar", "infants" to "Chaqaloqlar", "rooms" to "Xonalar", "invite" to "Ziyoratchini taklif qilish", "refund_title" to "Qaytarish siyosati", "refund_body" to "Qaytarish shartlari butun paketga tegishli va to‘lovdan oldin ko‘rsatiladi.", "trust_title" to "Bron ishonchi va tasdig‘i", "trust_body" to "Asoschi bilan bevosita aloqa · invoice va chek · iumrah Services uchun javobgarlik", "final_price" to "Yakuniy narx", "total_for" to "%d kishi uchun jami", "per_person_long" to "kishi uchun", "repricing" to "Paket qayta hisoblanmoqda…", "book" to "Safarni bron qilish", "generated" to "iumrah Configurator yaratdi", "choose_outbound" to "Borish reysini tanlang", "choose_return" to "Qaytish reysini tanlang", "flight_picker_note" to "Faqat e’lon qilingan reyslar. Chipta komponent narxi yashirin — faqat paket jami o‘zgaradi.", "no_alternatives" to "Hozir mos e’lon qilingan reyslar yo‘q.", "visa_info" to "Viza yordami paketga kiradi. Yakuniy hujjat talablari to‘lovdan oldin tasdiqlanadi.", "guide_info" to "iumrah safarni muvofiqlashtirishga yordam beradi va paket bo‘yicha aloqada qoladi.", "care_info" to "iumrah Care bron va safar davomida yordam beradi.", "trust_info" to "Brondan keyin tasdiq, invoice/chek va xizmatlar bo‘yicha yagona iumrah aloqa kanali beriladi.", "close" to "Yopish", "load_failed" to "Paketni ochib bo‘lmadi", "load_error" to "Paketni yuklab bo‘lmadi."
    )[key] ?: key
    AppLanguage.UZBEK_CYRILLIC -> mapOf(
        "title" to "Умра пакети", "back" to "Орқага", "share" to "Улашиш", "per_person_short" to "киши учун", "flights" to "Парвозлар", "hotels" to "Меҳмонхоналар", "included" to "Нималар киради", "outbound" to "Бориш", "return" to "Қайтиш", "change" to "Ўзгартириш", "makkah_madinah" to "Макка + Мадина", "makkah_only" to "Фақат Макка", "days" to "кун", "nights" to "тун", "return_fallback" to "Бу пакет Тошкентга қайтади, чунки пакет оралиғида %s га мос қайтиш рейси топилмади.", "direct" to "тўғридан-тўғри", "stops" to "уланиш", "departure_airport" to "Жўнаш аэропорти", "arrival_airport" to "Етиб бориш аэропорти", "departure" to "Жўнаш", "arrival" to "Етиб келиш", "duration" to "Йўлда", "cabin" to "Класс", "flight_number" to "Рейс", "choose_room" to "Хонани танлаш", "madinah_hotel" to "Мадина меҳмонхонаси", "visa" to "Виза", "visa_sub" to "Виза ёрдами пакетга киритилган", "transfer" to "Трансфер", "meals" to "Овқатланиш", "meals_sub" to "Нонушта киритилган · қўшимча овқатлар танланади", "guide" to "Ҳамроҳлик", "guide_sub" to "Сафар давомида iumrah ёрдами", "makkah_ziyarat" to "Макка зиёрати", "madinah_ziyarat" to "Мадина зиёрати", "care_sub" to "Сафардан олдин, давомида ва кейин ёрдам", "makkah_lunch" to "Макка · тушлик", "makkah_dinner" to "Макка · кечки овқат", "madinah_dinner" to "Мадина · кечки овқат", "travelers" to "Зиёратчилар", "travelers_body" to "Гуруҳ таркибини ўзгартиринг — iumrah шу пакетни серверда қайта ҳисоблайди.", "adults" to "Катталар", "children" to "Болалар", "infants" to "Чақалоқлар", "rooms" to "Хоналар", "invite" to "Зиёратчини таклиф қилиш", "refund_title" to "Қайтариш сиёсати", "refund_body" to "Қайтариш шартлари бутун пакетга тегишли ва тўловдан олдин кўрсатилади.", "trust_title" to "Брон ишончи ва тасдиғи", "trust_body" to "Асосчи билан бевосита алоқа · invoice ва чек · iumrah Services учун жавобгарлик", "final_price" to "Якуний нарх", "total_for" to "%d киши учун жами", "per_person_long" to "киши учун", "repricing" to "Пакет қайта ҳисобланмоқда…", "book" to "Сафарни брон қилиш", "generated" to "iumrah Configurator яратди", "choose_outbound" to "Бориш рейсини танланг", "choose_return" to "Қайтиш рейсини танланг", "flight_picker_note" to "Фақат эълон қилинган рейслар. Чипта компонент нархи яширин — фақат пакет жами ўзгаради.", "no_alternatives" to "Ҳозир мос эълон қилинган рейслар йўқ.", "visa_info" to "Виза ёрдами пакетга киради. Якуний ҳужжат талаблари тўловдан олдин тасдиқланади.", "guide_info" to "iumrah сафарни мувофиқлаштиришга ёрдам беради ва пакет бўйича алоқада қолади.", "care_info" to "iumrah Care брон ва сафар давомида ёрдам беради.", "trust_info" to "Брондан кейин тасдиқ, invoice/чек ва хизматлар бўйича ягона iumrah алоқа канали берилади.", "close" to "Ёпиш", "load_failed" to "Пакетни очиб бўлмади", "load_error" to "Пакетни юклаб бўлмади."
    )[key] ?: key
}
