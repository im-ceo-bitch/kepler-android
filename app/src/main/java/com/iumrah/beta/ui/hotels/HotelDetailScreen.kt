package com.iumrah.beta.ui.hotels

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.navigation.HotelConfiguratorDeepLink
import com.iumrah.beta.core.share.IumrahPackageShare
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.StorefrontHotelDetailCache
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.models.hotel.HotelDetail
import com.iumrah.beta.models.hotel.HotelImage
import com.iumrah.beta.models.hotel.HotelRoom
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategory
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.map.IumrahMapLibreView
import com.iumrah.beta.ui.map.IumrahMapStyle
import com.iumrah.beta.ui.map.rememberIumrahMapController
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private val DetailPagePadding = 18.dp
private val DetailCardRadius = 28.dp

/** Summary stays usable when the public detail API is briefly unavailable. */
private fun HotelDetail.asStorefrontSummary(): HotelSummary = HotelSummary(
    id = id,
    name = name,
    city = city,
    stars = stars,
    rating = rating,
    reviewCount = reviewCount,
    status = status,
    coverImageURL = images.firstOrNull { it.isCover }?.url ?: images.firstOrNull()?.url,
    imageCount = images.size,
    roomCount = rooms.size,
    price = price,
    updatedAt = Instant.now().toString(),
)

@Composable
fun HotelDetailScreen(
    hotelId: String,
    language: AppLanguage,
    catalog: HotelCatalogService,
    packageEngine: RemotePackageEngineClient,
    journey: JourneyStore,
    onBack: () -> Unit,
    onOpenConfigurator: (String) -> Unit = {},
    autoOpenConfigurator: Boolean = false,
    sharedConfiguration: HotelConfiguratorDeepLink? = null,
    selectionRole: String? = null,
    onSelectionDone: () -> Unit = {},
    onOpenRefundPolicy: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val journeyState by journey.state.collectAsState()
    val origin = journeyState.trip.originCode.ifBlank { "TAS" }.uppercase(Locale.US)
    val favoritePrefs = remember { context.getSharedPreferences("iumrah_storefront_favorites", Context.MODE_PRIVATE) }
    val hotelCache = remember(context) { StorefrontHotelDetailCache(context) }

    var detail by remember(hotelId) { mutableStateOf<HotelDetail?>(null) }
    var categories by remember(hotelId) { mutableStateOf<List<IumrahRoomCategoryOption>>(emptyList()) }
    var packages by remember(hotelId, origin) { mutableStateOf<List<StorefrontPackageSnapshot>>(emptyList()) }
    var loading by remember(hotelId) { mutableStateOf(true) }
    var categoryLoading by remember(hotelId) { mutableStateOf(true) }
    var error by remember(hotelId) { mutableStateOf<String?>(null) }
    var categoryError by remember(hotelId) { mutableStateOf<String?>(null) }
    var galleryOpen by remember { mutableStateOf(false) }
    var careOpen by remember { mutableStateOf(false) }
    var packageShareError by remember { mutableStateOf<String?>(null) }
    var favorite by remember(hotelId) { mutableStateOf(hotelId in favoritePrefs.getStringSet("hotel_ids", emptySet()).orEmpty()) }
    var selectedPackage by remember(hotelId) { mutableIntStateOf(0) }
    var selectionSummary by remember(hotelId, selectionRole) { mutableStateOf<HotelSummary?>(null) }
    var didAutoOpenConfigurator by remember(hotelId, autoOpenConfigurator) { mutableStateOf(false) }

    suspend fun loadAll() {
        loading = detail == null
        error = null
        val detailResult = runCatching { catalog.hotelDetail(hotelId) }
        currentCoroutineContext().ensureActive()
        if (journey.state.value.trip.originCode.ifBlank { "TAS" }.uppercase(Locale.US) != origin) return
        val responseDetail = detailResult.getOrNull()
        if (responseDetail != null && responseDetail.id == hotelId) {
            detail = responseDetail
            selectionSummary = responseDetail.asStorefrontSummary()
            hotelCache.save(responseDetail)
        } else if (detail == null) {
            error = detailText(language, "load_error")
        }
        // A transient error must not erase valid previously displayed details.
        loading = false

        categoryLoading = true
        categoryError = null
        val categoryResult = runCatching { packageEngine.roomCategories(hotelId) }
        currentCoroutineContext().ensureActive()
        if (journey.state.value.trip.originCode.ifBlank { "TAS" }.uppercase(Locale.US) != origin) return
        categoryResult.onSuccess { categories = it }.onFailure {
            categoryError = detailText(language, "room_error")
            // Preserve previously received room categories on transient outages.
        }
        categoryLoading = false

        // Hotel First snapshots stay server-authoritative, without locally
        // estimated prices or accepting a blank progressive page as a reset.
        var packagePage = runCatching { catalog.storefrontPackages("hotel-first", origin, 300) }.getOrNull()
        currentCoroutineContext().ensureActive()
        if (journey.state.value.trip.originCode.ifBlank { "TAS" }.uppercase(Locale.US) != origin) return
        fun applyPackagePage() {
            val ready = packagePage?.items.orEmpty()
                .filter { usableHotelFirstSnapshot(it, hotelId) }
                .distinctBy { it.hotelFirstVariantIndex?.let { index -> "index-$index" } ?: it.hotelFirstVariant?.takeIf(String::isNotBlank)?.let { variant -> "variant-$variant" } ?: "package-${it.id}" }
                .sortedWith(
                    compareBy<StorefrontPackageSnapshot> { it.hotelFirstVariantIndex ?: 99 }
                        .thenBy { it.totalDays ?: Int.MAX_VALUE }
                        .thenBy { it.id },
                )
            // An empty server build cursor must never hide already-rendered
            // offers from the same departure airport.
            if (ready.isNotEmpty()) {
                packages = ready
                selectedPackage = selectedPackage.coerceIn(0, (ready.size - 1).coerceAtLeast(0))
            }
        }
        applyPackagePage()

        if (packagePage != null && packagePage?.complete != true && (packagePage?.refreshRecommended == true || packagePage?.complete == false)) {
            var cursor = maxOf(0, packagePage?.nextRefreshCursor ?: 0)
            var passes = 24
            packagePage?.expectedItemCount?.takeIf { it > 0 }?.let { expected ->
                val expectedHotels = kotlin.math.ceil(expected / 3.0).toInt()
                passes = minOf(24, maxOf(2, kotlin.math.ceil(expectedHotels / 3.0).toInt() * 2))
            }
            for (pass in 0 until passes) {
                currentCoroutineContext().ensureActive()
                if (journey.state.value.trip.originCode.ifBlank { "TAS" }.uppercase(Locale.US) != origin) return
                val refresh = runCatching { catalog.refreshStorefrontPackages("hotel-first", origin, cursor) }.getOrNull() ?: break
                currentCoroutineContext().ensureActive()
                packagePage = runCatching { catalog.storefrontPackages("hotel-first", origin, 300) }.getOrNull() ?: break
                currentCoroutineContext().ensureActive()
                applyPackagePage()
                if (packagePage?.complete == true || refresh.complete == true) break
                cursor = maxOf(0, refresh.nextRefreshCursor ?: packagePage?.nextRefreshCursor ?: cursor)
                delay(150)
            }
        }
    }

    LaunchedEffect(hotelId, origin) {
        // Match iOS HotelStorefrontStore detail snapshots: show the public
        // hotel information immediately, even if live network calls fail.
        val cached = hotelCache.restore(hotelId)
        currentCoroutineContext().ensureActive()
        if (cached != null && detail == null) {
            detail = cached
            selectionSummary = cached.asStorefrontSummary()
            loading = false
        }
        loadAll()
    }

    LaunchedEffect(autoOpenConfigurator, loading, packages, selectedPackage, sharedConfiguration) {
        if (autoOpenConfigurator && !loading && !didAutoOpenConfigurator) {
            packages.getOrNull(selectedPackage)?.let { packageSnapshot ->
                didAutoOpenConfigurator = true
                onOpenConfigurator(packageSnapshot.id)
            }
        }
    }

    val hotel = detail
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        HotelNativeBar(
            title = hotel?.name.orEmpty(),
            favorite = favorite,
            onBack = onBack,
            onShare = {
                val snapshot = packages.getOrNull(selectedPackage)
                if (snapshot != null) {
                    IumrahPackageShare.share(context, snapshot, null, language)
                } else {
                    packageShareError = detailText(language, "share_unavailable")
                }
            },
            onFavorite = {
                val values = favoritePrefs.getStringSet("hotel_ids", emptySet()).orEmpty().toMutableSet()
                favorite = !favorite
                if (favorite) values += hotelId else values -= hotelId
                favoritePrefs.edit().putStringSet("hotel_ids", values).apply()
            },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                hotel != null -> {
                    HotelDetailBody(
                        hotel = hotel,
                        categories = categories,
                        packages = packages,
                        selectedPackage = selectedPackage,
                        onPackageChange = { selectedPackage = it },
                        categoryLoading = categoryLoading,
                        categoryError = categoryError,
                        language = language,
                        onGallery = { galleryOpen = true },
                        onCare = { careOpen = true },
                        onOpenConfigurator = onOpenConfigurator,
                        onRetryRooms = { scope.launch { loadAll() } },
                        selectionMode = selectionRole != null,
                        selectedRoomId = if (selectionRole.equals("madinah", true)) journeyState.madinahRoom?.id else journeyState.makkahRoom?.id,
                        selectedCategoryId = if (selectionRole.equals("madinah", true)) journeyState.madinahRoomCategory?.id else journeyState.makkahRoomCategory?.id,
                        onSelectRoom = { room ->
                            selectionSummary?.let(journey::selectHotel)
                            journey.selectRoom(room, selectionRole.equals("madinah", true))
                        },
                        onSelectCategory = { option ->
                            selectionSummary?.let(journey::selectHotel)
                            journey.selectRoomCategory(option, selectionRole.equals("madinah", true))
                        },
                        onOpenRefundPolicy = onOpenRefundPolicy,
                    )
                }
                loading -> LoadingDetail(language)
                else -> ErrorDetail(error ?: detailText(language, "load_error")) { scope.launch { loadAll() } }
            }

            if (hotel != null && selectionRole != null) {
                val forMadinah = selectionRole.equals("madinah", true)
                val selectedHotelId = if (forMadinah) journeyState.madinahHotel?.id else journeyState.makkahHotel?.id
                val selectedName = if (selectedHotelId == hotelId) {
                    val room = if (forMadinah) journeyState.madinahRoom else journeyState.makkahRoom
                    val category = if (forMadinah) journeyState.madinahRoomCategory else journeyState.makkahRoomCategory
                    room?.name?.let { localizeRoomName(it, language) } ?: category?.let { roomCategoryName(it.category, language) }
                } else null
                if (!selectedName.isNullOrBlank()) {
                    SelectionConfirmationBar(
                        selectedName = selectedName,
                        language = language,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        onDone = onSelectionDone,
                    )
                }
            }
        }
    }

    if (galleryOpen && hotel != null) {
        HotelGalleryDialog(hotel = hotel, language = language, onDismiss = { galleryOpen = false })
    }
    if (careOpen) {
        HotelCareContactSheet(language = language, onDismiss = { careOpen = false })
    }
    packageShareError?.let { message ->
        AlertDialog(
            onDismissRequest = { packageShareError = null },
            title = { Text(detailText(language, "share_error_title"), fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { packageShareError = null }) { Text(detailText(language, "share_error_ok")) }
            },
        )
    }
}

@Composable
private fun HotelNativeBar(
    title: String,
    favorite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(54.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(CupertinoSymbol.ChevronLeft, "Back", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            fontSize = 15.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GlassIconButton(CupertinoSymbol.Share, "Share", onShare)
            GlassIconButton(if (favorite) CupertinoSymbol.HeartFill else CupertinoSymbol.Heart, "Favorite", onFavorite)
        }
    }
}

@Composable
private fun GlassIconButton(symbol: CupertinoSymbol, label: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .82f))
            .border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape)
            .clickable(interactionSource = source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CupertinoIcon(symbol, label, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HotelDetailBody(
    hotel: HotelDetail,
    categories: List<IumrahRoomCategoryOption>,
    packages: List<StorefrontPackageSnapshot>,
    selectedPackage: Int,
    onPackageChange: (Int) -> Unit,
    categoryLoading: Boolean,
    categoryError: String?,
    language: AppLanguage,
    onGallery: () -> Unit,
    onCare: () -> Unit,
    onOpenConfigurator: (String) -> Unit,
    onRetryRooms: () -> Unit,
    selectionMode: Boolean,
    selectedRoomId: String?,
    selectedCategoryId: String?,
    onSelectRoom: (HotelRoom) -> Unit,
    onSelectCategory: (IumrahRoomCategoryOption) -> Unit,
    onOpenRefundPolicy: () -> Unit,
) {
    val images = remember(hotel.images) { propertyImages(hotel.images) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = if (selectionMode) 128.dp else 48.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { HeroCarousel(hotel, images, language, onGallery) }
        item {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = DetailPagePadding, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(30.dp),
            ) {
                IdentitySection(hotel, language)
                if (selectionMode) HotelSelectionRefundPolicy(language, onOpenRefundPolicy)
                else PackageSection(packages, selectedPackage, onPackageChange, language, onOpenConfigurator)
                QualitySection(hotel, language)
                ReceptionClocks(language)
                PhotoOverview(hotel, language, onGallery)
                AmenitiesSection(hotel, language)
                PrimaryRoomSection(categories, categoryLoading, categoryError, language, onRetryRooms, selectionMode, selectedCategoryId, onSelectCategory)
                ActualRoomsSection(hotel, language, selectionMode, selectedRoomId, onSelectRoom)
                if (hotel.description.isNotBlank()) AboutSection(hotel, language)
                MapSection(hotel, language)
                PracticalSection(hotel, language)
                HotelCareShowcaseCard(language, onCare)
            }
        }
    }
}

@Composable
private fun HeroCarousel(hotel: HotelDetail, images: List<HotelImage>, language: AppLanguage, onGallery: () -> Unit) {
    val fallback = hotel.images.sortedBy { it.position }.firstOrNull()?.url
    val pager = rememberPagerState(pageCount = { maxOf(1, images.size) })
    Box(Modifier.fillMaxWidth().height(340.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (images.isNotEmpty()) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
                AsyncImage(
                    model = AppConfig.absoluteUrl(images[index].url),
                    contentDescription = hotel.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        } else if (fallback != null) {
            AsyncImage(AppConfig.absoluteUrl(fallback), hotel.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (images.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = .34f))
                    .clickable(onClick = onGallery)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.Photo, null, Modifier.size(16.dp), Color.White)
                Text("${pager.currentPage + 1}/${images.size}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (images.size > 1) {
            val current = pager.currentPage.coerceIn(0, images.lastIndex)
            val first = (current - 3).coerceAtLeast(0).coerceAtMost((images.size - 7).coerceAtLeast(0))
            val last = (first + 6).coerceAtMost(images.lastIndex)
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .height(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .24f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                for (index in first..last) {
                    val active = index == current
                    Box(
                        Modifier
                            .size(if (active) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (active) .98f else .56f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun IdentitySection(hotel: HotelDetail, language: AppLanguage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        hotel.stars?.let {
            Text("★".repeat(it), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = secondaryText())
        }
        Text(
            hotel.name,
            fontSize = 31.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.9).sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(16.dp), secondaryText())
            Text(localizedCity(hotel.city, language), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = secondaryText())
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CupertinoIcon(CupertinoSymbol.Hotel, null, Modifier.size(15.dp), secondaryText())
            Text("iumrah Hotels", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondaryText())
        }
        if (hotel.address.isNotBlank()) Text(hotel.address, fontSize = 15.sp, lineHeight = 20.sp, color = secondaryText())
    }
}

@Composable
private fun PackageSection(
    packages: List<StorefrontPackageSnapshot>,
    selected: Int,
    onSelected: (Int) -> Unit,
    language: AppLanguage,
    onOpenConfigurator: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (packages.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(28.dp)).background(cardColor()).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(detailText(language, "preparing_price"), color = secondaryText(), fontSize = 14.sp)
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(detailText(language, "trip_options"), fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.2).sp)
                Text(detailText(language, "ready_packages", packages.size), fontSize = 12.sp, lineHeight = 15.sp, color = secondaryText())
            }
            if (packages.size > 1) {
                Text(
                    detailText(language, "counter", selected + 1, packages.size),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondaryText(),
                )
            }
        }

        val pager = rememberPagerState(initialPage = selected.coerceIn(0, packages.lastIndex), pageCount = { packages.size })
        LaunchedEffect(selected) { if (pager.currentPage != selected) pager.animateScrollToPage(selected) }
        LaunchedEffect(pager.currentPage, pager.isScrollInProgress) {
            if (!pager.isScrollInProgress && pager.currentPage != selected) onSelected(pager.currentPage)
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(332.dp)) {
            val pageWidth = (maxWidth * .82f).coerceAtLeast(280.dp).coerceAtMost(maxWidth)
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSize = PageSize.Fixed(pageWidth),
                pageSpacing = 14.dp,
                beyondViewportPageCount = 1,
            ) { index ->
                PackageVariantCard(packages[index], index, language, onOpenConfigurator)
            }
        }

        if (packages.size > 1) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                packages.indices.forEach { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.5.dp)
                            .height(7.dp)
                            .width(if (index == selected) 22.dp else 7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (index == selected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = .25f)),
                    )
                }
            }
        }
    }
}

private data class HotelPackageAccent(val fill: Color, val text: Color)

private fun hotelPackageAccent(item: StorefrontPackageSnapshot, index: Int): HotelPackageAccent {
    val key = item.hotelFirstVariant ?: if (index == 0) "short" else if (index == 1) "balanced" else "extended"
    return when (key.lowercase(Locale.US)) {
        "short" -> HotelPackageAccent(Color(0xFF29B3FF), Color(0xFF004D9E))
        "balanced" -> HotelPackageAccent(Color(0xFFFF991A), Color(0xFF803800))
        else -> HotelPackageAccent(Color(0xFF9C5CFF), Color(0xFF471999))
    }
}

@Composable
private fun PackageVariantCard(
    item: StorefrontPackageSnapshot,
    index: Int,
    language: AppLanguage,
    onOpenConfigurator: (String) -> Unit,
) {
    val accent = hotelPackageAccent(item, index)
    val totalDays = (item.totalDays ?: 0).coerceAtLeast(1)
    val travelers = item.configuration?.let { (it.adults + it.children + it.infants).coerceAtLeast(1) } ?: 2
    val shape = RoundedCornerShape(27.dp)

    Column(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(accent.fill.copy(alpha = .22f))
            .border(.9.dp, accent.fill.copy(alpha = .42f), shape)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                variantTitle(item.hotelFirstVariant, index, language).uppercase(language.locale),
                modifier = Modifier.weight(1f),
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = .45.sp,
                color = accent.text.copy(alpha = .86f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.height(30.dp).clip(RoundedCornerShape(99.dp)).background(Color.White.copy(alpha = .60f)).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.CalendarClock, null, Modifier.size(11.dp), accent.text)
                Text("$totalDays ${detailText(language, "days_short")}", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, color = accent.text)
            }
        }

        Text(
            packageDateRange(item, language),
            fontSize = 22.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.45).sp,
            color = accent.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    money(item.pricePerPerson),
                    fontSize = 32.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.9).sp,
                    color = Color.Black,
                    maxLines = 1,
                )
                Text(detailText(language, "per_pilgrim"), fontSize = 12.sp, lineHeight = 15.sp, color = Color.Black.copy(alpha = .55f))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("$travelers", fontSize = 23.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(packageTravelersLabel(language), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.Black.copy(alpha = .55f))
            }
        }

        Text(
            packageTotalForText(item.totalPackagePrice, travelers, language),
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black.copy(alpha = .58f),
            maxLines = 2,
        )

        Spacer(Modifier.weight(1f))

        Row(
            Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black).clickable { onOpenConfigurator(item.id) }.padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            CupertinoIcon(CupertinoSymbol.Sliders, null, Modifier.size(17.dp), Color.White)
            Text(detailText(language, "open_configurator"), Modifier.weight(1f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), Color.White)
        }
    }
}

private fun packageDateRange(item: StorefrontPackageSnapshot, language: AppLanguage): String {
    fun render(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        return runCatching {
            val date = java.time.LocalDate.parse(raw.take(10))
            date.format(DateTimeFormatter.ofPattern("d MMM", language.locale))
        }.getOrElse { raw.take(10) }
    }
    return "${render(item.startDate ?: item.outbound?.departureAt)} — ${render(item.endDate ?: item.inbound?.departureAt)}"
}

private fun packageTravelersLabel(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "паломников"
    AppLanguage.ENGLISH -> "travelers"
    AppLanguage.UZBEK -> "ziyoratchi"
    AppLanguage.UZBEK_CYRILLIC -> "зиёратчи"
}

private fun packageTotalForText(total: Double?, travelers: Int, language: AppLanguage): String {
    val amount = money(total)
    return when (language) {
        AppLanguage.RUSSIAN -> "$amount за пакет на $travelers паломников"
        AppLanguage.ENGLISH -> "$amount total for $travelers pilgrims"
        AppLanguage.UZBEK -> "$amount · $travelers kishi uchun jami"
        AppLanguage.UZBEK_CYRILLIC -> "$amount · $travelers киши учун жами"
    }
}

@Composable
private fun QualitySection(hotel: HotelDetail, language: AppLanguage) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cardColor()).border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .05f), RoundedCornerShape(28.dp)).padding(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hotel.rating?.let { rating ->
            Column(
                Modifier.width(96.dp).height(88.dp).clip(RoundedCornerShape(24.dp)).background(raisedColor()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(String.format(Locale.US, "%.1f", rating), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Text(ratingTitle(rating, language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondaryText())
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(detailText(language, "selected_quality"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            hotel.reviewCount?.let { Text(detailText(language, "reviews", it), fontSize = 14.sp, color = secondaryText()) }
            Text(detailText(language, "quality_note"), fontSize = 12.sp, lineHeight = 16.sp, color = secondaryText())
        }
    }
}

@Composable
private fun ReceptionClocks(language: AppLanguage) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "time_title"))
        Text(detailText(language, "time_body"), fontSize = 14.sp, lineHeight = 19.sp, color = secondaryText())
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReceptionClock(detailText(language, "tashkent"), "Asia/Tashkent", Modifier.weight(1f))
                ReceptionClock(detailText(language, "makkah"), "Asia/Riyadh", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReceptionClock(detailText(language, "madinah"), "Asia/Riyadh", Modifier.weight(1f))
                ReceptionClock(detailText(language, "moscow"), "Europe/Moscow", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReceptionClock(city: String, zone: String, modifier: Modifier = Modifier) {
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(zone) { while (true) { tick = System.currentTimeMillis(); delay(30_000) } }
    val date = remember(tick, zone) { ZonedDateTime.ofInstant(Instant.ofEpochMilli(tick), ZoneId.of(zone)) }
    Column(
        modifier.height(118.dp).clip(RoundedCornerShape(20.dp)).background(cardColor()).border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .05f), RoundedCornerShape(20.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AnalogClock(date, Modifier.size(52.dp))
        Text(city, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondaryText(), maxLines = 1)
        Text(date.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 19.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AnalogClock(date: ZonedDateTime, modifier: Modifier) {
    val fg = MaterialTheme.colorScheme.onSurface
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) / 2 - 2.dp.toPx()
        drawCircle(fg.copy(alpha = .23f), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        for (i in 0 until 12) {
            val a = Math.toRadians((i * 30 - 90).toDouble())
            val a1 = Offset(c.x + kotlin.math.cos(a).toFloat() * r * .82f, c.y + kotlin.math.sin(a).toFloat() * r * .82f)
            val a2 = Offset(c.x + kotlin.math.cos(a).toFloat() * r * .92f, c.y + kotlin.math.sin(a).toFloat() * r * .92f)
            drawLine(fg.copy(alpha = .45f), a1, a2, 1.dp.toPx(), StrokeCap.Round)
        }
        fun hand(angleDegrees: Double, length: Float, width: Dp) {
            val a = Math.toRadians(angleDegrees - 90.0)
            val end = Offset(c.x + kotlin.math.cos(a).toFloat() * r * length, c.y + kotlin.math.sin(a).toFloat() * r * length)
            drawLine(fg, c, end, width.toPx(), StrokeCap.Round)
        }
        hand((date.hour % 12) * 30.0 + date.minute * .5, .52f, 2.dp)
        hand(date.minute * 6.0, .72f, 1.5.dp)
        drawCircle(fg, 2.dp.toPx(), c)
    }
}

@Composable
private fun PhotoOverview(hotel: HotelDetail, language: AppLanguage, onGallery: () -> Unit) {
    val photos = hotel.images.sortedWith(compareByDescending<HotelImage> { it.isCover }.thenBy { it.position })
    if (photos.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            SectionTitle(detailText(language, "photos"), Modifier.weight(1f))
            Text(
                detailText(language, "view_all_photos", photos.size),
                modifier = Modifier.clickable(onClick = onGallery).padding(vertical = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onGallery),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AsyncImage(AppConfig.absoluteUrl(photos[0].url), null, Modifier.weight(.64f).fillMaxHeight(), contentScale = ContentScale.Crop)
            Column(Modifier.weight(.36f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AsyncImage(AppConfig.absoluteUrl(photos.getOrNull(1)?.url ?: photos[0].url), null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Crop)
                AsyncImage(AppConfig.absoluteUrl(photos.getOrNull(2)?.url ?: photos[0].url), null, Modifier.weight(1f).fillMaxWidth(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun AmenitiesSection(hotel: HotelDetail, language: AppLanguage) {
    if (hotel.amenities.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "amenities"))
        hotel.amenities.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { amenity -> AmenityCard(amenity, language, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AmenityCard(raw: String, language: AppLanguage, modifier: Modifier = Modifier) {
    Row(
        modifier.height(58.dp).clip(RoundedCornerShape(18.dp)).background(cardColor()).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(raisedColor()), contentAlignment = Alignment.Center) {
            CupertinoIcon(amenitySymbol(raw), null, Modifier.size(16.dp), secondaryText())
        }
        Text(localizedAmenity(raw, language), Modifier.weight(1f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PrimaryRoomSection(
    categories: List<IumrahRoomCategoryOption>,
    loading: Boolean,
    error: String?,
    language: AppLanguage,
    onRetry: () -> Unit,
    selectionMode: Boolean,
    selectedCategoryId: String?,
    onSelect: (IumrahRoomCategoryOption) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "prepared_rooms"))
        Text(detailText(language, "prepared_rooms_body"), fontSize = 14.sp, lineHeight = 19.sp, color = secondaryText())
        when {
            loading && categories.isEmpty() -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(detailText(language, "rooms_loading"), color = secondaryText())
            }
            error != null && categories.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error, fontSize = 14.sp, color = secondaryText())
                SecondaryDetailButton(detailText(language, "retry"), onRetry)
            }
            else -> categories.forEach { RoomCategoryCard(it, language, selectionMode, selectedCategoryId == it.id, onSelect) }
        }
    }
}

@Composable
private fun RoomCategoryCard(
    option: IumrahRoomCategoryOption,
    language: AppLanguage,
    selectionMode: Boolean,
    selected: Boolean,
    onSelect: (IumrahRoomCategoryOption) -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(min = if (selectionMode) 214.dp else 168.dp).clip(shape).background(cardColor()).border(if (selected) 1.2.dp else .6.dp, if (selected) Color(0xFF74A187).copy(alpha = .62f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), shape).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(raisedColor()), contentAlignment = Alignment.Center) {
                CupertinoIcon(if (option.category == IumrahRoomCategory.DOUBLE) CupertinoSymbol.Bed else CupertinoSymbol.Persons, null, Modifier.size(22.dp), secondaryText())
            }
            Column(Modifier.weight(1f)) {
                Text(roomCategoryName(option.category, language), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                Text(roomCategoryBody(option.category, language), fontSize = 12.sp, lineHeight = 15.sp, color = secondaryText(), maxLines = 2)
            }
            if (selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(22.dp), Color(0xFF74A187))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactFact(CupertinoSymbol.Persons, option.maxGuests.toString())
            CompactFact(CupertinoSymbol.Bed, localizedBeds(option.category, language))
        }
        if (selectionMode) {
            RoomSelectionButton(if (selected) selectionText(language, "room_chosen") else selectionText(language, "choose_room"), selected) { onSelect(option) }
        }
    }
}

@Composable
private fun ActualRoomsSection(hotel: HotelDetail, language: AppLanguage, selectionMode: Boolean, selectedRoomId: String?, onSelect: (HotelRoom) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "hotel_rooms"))
        Text(detailText(language, "hotel_rooms_body"), fontSize = 14.sp, lineHeight = 19.sp, color = secondaryText())
        if (hotel.rooms.isEmpty()) {
            Text(detailText(language, "rooms_empty"), Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardColor()).padding(20.dp), fontSize = 14.sp, color = secondaryText())
        } else {
            hotel.rooms.forEach { room -> ActualRoomCard(room, hotel, language, selectionMode, selectedRoomId == room.id, onSelect) }
        }
    }
}

@Composable
private fun ActualRoomCard(room: HotelRoom, hotel: HotelDetail, language: AppLanguage, selectionMode: Boolean, selected: Boolean, onSelect: (HotelRoom) -> Unit) {
    val images = remember(room.id, hotel.images) { roomImages(room, hotel.images) }
    val pager = rememberPagerState(pageCount = { maxOf(1, images.size) })
    val cardHeight = if (selectionMode) 236.dp else 196.dp
    val shape = RoundedCornerShape(26.dp)

    BoxWithConstraints(Modifier.fillMaxWidth().height(cardHeight)) {
        val mediaWidth = (maxWidth * .36f).coerceIn(116.dp, 142.dp)
        Row(
            Modifier.fillMaxSize().shadow(5.dp, shape).clip(shape).background(cardColor())
                .border(if (selected) 1.2.dp else .6.dp, if (selected) Color(0xFF74A187).copy(alpha = .62f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), shape),
        ) {
            Box(Modifier.width(mediaWidth).fillMaxHeight().background(raisedColor())) {
                if (images.isNotEmpty()) {
                    HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
                        AsyncImage(AppConfig.absoluteUrl(images[index].url), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    if (images.size > 1) {
                        Column(
                            Modifier.fillMaxSize().padding(10.dp),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                Modifier.height(28.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .42f)).padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                CupertinoIcon(CupertinoSymbol.Photo, null, Modifier.size(11.dp), Color.White)
                                Text("${images.size}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                Modifier.height(23.dp).clip(RoundedCornerShape(99.dp)).background(Color.Black.copy(alpha = .28f)).padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                images.indices.forEach { index ->
                                    Box(
                                        Modifier.width(if (index == pager.currentPage) 12.dp else 5.dp).height(5.dp).clip(RoundedCornerShape(99.dp))
                                            .background(Color.White.copy(alpha = if (index == pager.currentPage) 1f else .42f)),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        CupertinoIcon(CupertinoSymbol.Bed, null, Modifier.size(28.dp), secondaryText())
                        Spacer(Modifier.height(9.dp))
                        Text(detailText(language, "hotel_rooms"), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondaryText(), textAlign = TextAlign.Center)
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(localizeRoomName(room.name, language), Modifier.weight(1f), fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.2).sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(19.dp), Color(0xFF74A187))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    room.maxGuests?.let { CompactFact(CupertinoSymbol.Persons, it.toString()) }
                    cleanFact(room.beds)?.let { CompactFact(CupertinoSymbol.Bed, localizeBedText(it, language)) }
                }
                room.sizeM2?.let { CompactFact(CupertinoSymbol.NumberSquare, String.format(Locale.US, "%.0f m²", it)) }
                room.description?.let { cleanRoomDescription(it) }?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, lineHeight = 15.sp, color = secondaryText(), maxLines = if (selectionMode) 2 else 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.weight(1f))
                if (selectionMode) RoomSelectionButton(if (selected) selectionText(language, "room_chosen") else selectionText(language, "choose_room"), selected) { onSelect(room) }
            }
        }
    }
}

@Composable
private fun RoomSelectionButton(title: String, selected: Boolean, onClick: () -> Unit) {
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    val bg = if (selected) Color(0xFF74A187) else raisedColor()
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg)
        CupertinoIcon(if (selected) CupertinoSymbol.Checkmark else CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp), fg)
    }
}

@Composable
private fun HotelSelectionRefundPolicy(language: AppLanguage, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardColor())
            .border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(raisedColor()),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(20.dp), secondaryText())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(selectionText(language, "refund_policy"), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Text(selectionText(language, "refund_policy_body"), fontSize = 12.sp, lineHeight = 16.sp, color = secondaryText())
        }
        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), secondaryText())
    }
}

@Composable
private fun CompactFact(icon: CupertinoSymbol, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(13.dp)).background(raisedColor()).padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CupertinoIcon(icon, null, Modifier.size(13.dp), secondaryText())
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = secondaryText(), maxLines = 1)
    }
}

@Composable
private fun AboutSection(hotel: HotelDetail, language: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(detailText(language, "about"))
        Text(hotel.description, Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardColor()).padding(18.dp), fontSize = 16.sp, lineHeight = 22.sp, color = secondaryText())
    }
}

@Composable
private fun MapSection(hotel: HotelDetail, language: AppLanguage) {
    val lat = hotel.latitude ?: return
    val lon = hotel.longitude ?: return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "location"))
        HotelLocationMap(
            latitude = lat,
            longitude = lon,
            title = hotel.name,
            modifier = Modifier.fillMaxWidth().height(245.dp).clip(RoundedCornerShape(28.dp)),
        )
        if (!hotel.googleMapsURL.isNullOrBlank()) {
            SecondaryActionRow(detailText(language, "open_map"), CupertinoSymbol.Location) {
                val uri = AppConfig.absoluteUrl(hotel.googleMapsURL)?.let(Uri::parse)
                if (uri != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            }
        }
    }
}

@Composable
private fun HotelLocationMap(
    latitude: Double,
    longitude: Double,
    title: String,
    modifier: Modifier = Modifier,
) {
    val controller = rememberIumrahMapController()
    val map = controller.map

    LaunchedEffect(map) {
        controller.setStyle(IumrahMapStyle.STANDARD)
    }
    LaunchedEffect(map, controller.styleEpoch, latitude, longitude, title) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0) return@LaunchedEffect
        val target = LatLng(latitude, longitude)
        activeMap.clear()
        activeMap.addMarker(MarkerOptions().position(target).title(title))
        activeMap.uiSettings.apply {
            isCompassEnabled = false
            isLogoEnabled = false
            isAttributionEnabled = true
        }
        val camera = CameraPosition.Builder().target(target).zoom(15.4).build()
        controller.mapView.post { activeMap.moveCamera(CameraUpdateFactory.newCameraPosition(camera)) }
    }

    IumrahMapLibreView(controller = controller, modifier = modifier)
}

@Composable
private fun PracticalSection(hotel: HotelDetail, language: AppLanguage) {
    val rows = buildList {
        hotel.checkIn?.takeIf { it.isNotBlank() }?.let { add(detailText(language, "checkin") to it) }
        hotel.checkOut?.takeIf { it.isNotBlank() }?.let { add(detailText(language, "checkout") to it) }
        hotel.propertyType?.takeIf { it.isNotBlank() }?.let { add(detailText(language, "property_type") to it) }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(detailText(language, "practical"))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cardColor()).padding(horizontal = 18.dp)) {
            rows.forEachIndexed { index, pair ->
                if (index > 0) Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.Top) {
                    Text(pair.first, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(pair.second, Modifier.weight(1f), fontSize = 14.sp, color = secondaryText(), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun HotelGalleryDialog(hotel: HotelDetail, language: AppLanguage, onDismiss: () -> Unit) {
    val photos = remember(hotel.images) { hotel.images.sortedWith(compareByDescending<HotelImage> { it.isCover }.thenBy { it.position }) }
    val categories = remember(photos) {
        buildList {
            add("all")
            photos.forEach { image ->
                val category = image.category.trim().lowercase(Locale.US)
                if (category.isNotBlank() && category !in this) add(category)
            }
        }
    }
    var selectedCategory by remember { mutableStateOf("all") }
    var viewerStart by remember { mutableStateOf<Int?>(null) }
    val filtered = remember(photos, selectedCategory) {
        if (selectedCategory == "all") photos else photos.filter { it.category.trim().lowercase(Locale.US) == selectedCategory }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(hotel.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    detailText(language, "done"),
                    modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = DetailPagePadding),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(categories.size) { index ->
                    val category = categories[index]
                    val selected = selectedCategory == category
                    val count = if (category == "all") photos.size else photos.count { it.category.trim().lowercase(Locale.US) == category }
                    Column(
                        Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (selected) raisedColor() else cardColor())
                            .border(if (selected) 1.2.dp else .7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) .38f else .07f), RoundedCornerShape(18.dp))
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 15.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(galleryCategoryTitle(category, language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(count.toString(), fontSize = 11.sp, color = secondaryText())
                    }
                }
            }

            Text(
                "${galleryCategoryTitle(selectedCategory, language)} (${filtered.size})",
                modifier = Modifier.padding(start = DetailPagePadding, end = DetailPagePadding, top = 20.dp, bottom = 14.dp),
                fontSize = 27.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.4).sp,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = DetailPagePadding, end = DetailPagePadding, bottom = 34.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                gridItemsIndexed(filtered, key = { _, image -> image.id }) { index, image ->
                    AsyncImage(
                        model = AppConfig.absoluteUrl(image.url),
                        contentDescription = hotel.name,
                        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(2.dp)).clickable { viewerStart = index },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }

    viewerStart?.let { startIndex ->
        HotelImageViewerDialog(
            images = filtered,
            initialIndex = startIndex,
            hotelName = hotel.name,
            onDismiss = { viewerStart = null },
        )
    }
}

@Composable
private fun HotelImageViewerDialog(
    images: List<HotelImage>,
    initialIndex: Int,
    hotelName: String,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)), pageCount = { maxOf(1, images.size) })
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (images.isNotEmpty()) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                    ZoomableHotelImage(image = images[index], contentDescription = hotelName)
                }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.Black.copy(alpha = .34f)).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(17.dp), Color.White)
                }
                Spacer(Modifier.weight(1f))
                if (images.isNotEmpty()) {
                    Text(
                        "${pager.currentPage + 1}/${images.size}",
                        modifier = Modifier.height(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .34f)).padding(horizontal = 12.dp),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableHotelImage(image: HotelImage, contentDescription: String) {
    var scale by remember(image.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(image.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(image.id) { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = nextScale
        if (nextScale <= 1.02f) {
            offsetX = 0f
            offsetY = 0f
        } else {
            offsetX += panChange.x
            offsetY += panChange.y
        }
    }
    AsyncImage(
        model = AppConfig.absoluteUrl(image.url),
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
            .transformable(transformState)
            .pointerInput(image.id) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offsetX = 0f; offsetY = 0f } else scale = 2f
                })
            },
        contentScale = ContentScale.Fit,
    )
}

private fun galleryCategoryTitle(category: String, language: AppLanguage): String {
    if (category == "all") return when (language) {
        AppLanguage.RUSSIAN -> "Все фото"
        AppLanguage.ENGLISH -> "All photos"
        AppLanguage.UZBEK -> "Barcha rasmlar"
        AppLanguage.UZBEK_CYRILLIC -> "Барча расмлар"
    }
    val normalized = category.lowercase(Locale.US)
    fun tr(ru: String, en: String, uz: String, uzCy: String) = when (language) {
        AppLanguage.RUSSIAN -> ru; AppLanguage.ENGLISH -> en; AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> uzCy
    }
    return when {
        "room" in normalized -> tr("Номера", "Rooms", "Xonalar", "Хоналар")
        "bath" in normalized -> tr("Ванная", "Bathroom", "Hammom", "Ҳаммом")
        "restaurant" in normalized || "food" in normalized -> tr("Ресторан", "Restaurant", "Restoran", "Ресторан")
        "lobby" in normalized || "reception" in normalized -> tr("Лобби", "Lobby", "Lobbi", "Лобби")
        "view" in normalized || "exterior" in normalized -> tr("Вид", "View", "Manzara", "Манзара")
        "facility" in normalized || "amenit" in normalized -> tr("Удобства", "Facilities", "Qulayliklar", "Қулайликлар")
        else -> category.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase(language.locale) else it.toString() }
    }
}


@Composable
private fun SelectionConfirmationBar(
    selectedName: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(selectionText(language, "selected_room"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondaryText())
            Text(selectedName, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onBackground).clickable(onClick = onDone).padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(selectionText(language, "done"), color = MaterialTheme.colorScheme.background, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}



@Composable
private fun GalleryCloseButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(Color.Black.copy(alpha = .48f))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(15.dp), Color.White)
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PrimaryDetailButton(text: String, icon: CupertinoSymbol, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(17.dp)).background(MaterialTheme.colorScheme.onBackground).clickable(onClick = onClick).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CupertinoIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.background)
        Text(text, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.background)
        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(16.dp), MaterialTheme.colorScheme.background)
    }
}

@Composable
private fun SecondaryDetailButton(text: String, onClick: () -> Unit) {
    Box(Modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(raisedColor()).clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryActionRow(text: String, icon: CupertinoSymbol, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(17.dp)).background(cardColor()).border(.6.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), RoundedCornerShape(17.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CupertinoIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
        Text(text, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), secondaryText())
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
}

@Composable private fun cardColor(): Color = MaterialTheme.colorScheme.surface
@Composable private fun raisedColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)
@Composable private fun secondaryText(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)

@Composable
private fun LoadingDetail(language: AppLanguage) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(Modifier.height(12.dp))
        Text(detailText(language, "loading"), color = secondaryText())
    }
}

@Composable
private fun ErrorDetail(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = secondaryText(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        SecondaryDetailButton("Retry", onRetry)
    }
}

private fun propertyImages(images: List<HotelImage>): List<HotelImage> {
    val sorted = images.sortedWith(compareByDescending<HotelImage> { it.isCover }.thenBy { it.position })
    val property = sorted.filter { it.roomName.isNullOrBlank() && !it.category.lowercase(Locale.US).contains("room") }
    return if (property.isEmpty()) sorted else property
}

private fun roomImages(room: HotelRoom, images: List<HotelImage>): List<HotelImage> {
    val nameTokens = room.name.lowercase(Locale.US).split(" ").filter { it.length >= 4 }.toSet()
    val matched = images.filter { image ->
        val hay = "${image.roomName.orEmpty()} ${image.label.orEmpty()} ${image.category}".lowercase(Locale.US)
        image.roomName?.equals(room.name, true) == true || nameTokens.any { it in hay } || "room" in image.category.lowercase(Locale.US)
    }
    return matched.sortedWith(compareByDescending<HotelImage> { it.isCover }.thenBy { it.position }).take(8)
}

private fun isPublicPackageIdDetail(value: String): Boolean = value.length == 10 && value.all(Char::isDigit)

private fun snapshotMatchesHotel(item: StorefrontPackageSnapshot, hotelId: String): Boolean {
    val anchor = item.hotelFirstAnchorHotelId ?: item.makkahHotelId ?: item.madinahHotelId
    return anchor.equals(hotelId, true)
}

private fun usableHotelFirstSnapshot(item: StorefrontPackageSnapshot, hotelId: String): Boolean =
    item.entryMode.equals("hotel-first", true) &&
        item.status.equals("ready", true) &&
        isPublicPackageIdDetail(item.id) &&
        snapshotMatchesHotel(item, hotelId) &&
        (item.pricePerPerson ?: 0.0) > 0.0 &&
        (item.totalPackagePrice ?: 0.0) > 0.0

private fun variantOrder(value: String?): Int = when (value?.lowercase(Locale.US)) { "short" -> 0; "balanced" -> 1; else -> 2 }

private fun money(value: Double?): String = if (value == null || !value.isFinite()) "—" else String.format(Locale.US, "$%.0f", value)

private fun packageDates(item: StorefrontPackageSnapshot, language: AppLanguage): String {
    val a = item.startDate.orEmpty()
    val b = item.endDate.orEmpty()
    return if (a.isNotBlank() && b.isNotBlank()) "$a → $b · ${item.totalDays ?: 0} ${detailText(language, "days_short")}" else "${item.totalDays ?: 0} ${detailText(language, "days_short")}"
}

private fun packageStay(item: StorefrontPackageSnapshot, language: AppLanguage): String {
    val m = item.makkahNights ?: 0
    val d = item.madinahNights ?: 0
    return when {
        m > 0 && d > 0 -> "Makkah $m · Madinah $d ${detailText(language, "nights_short")}"
        m > 0 -> "Makkah $m ${detailText(language, "nights_short")}"
        d > 0 -> "Madinah $d ${detailText(language, "nights_short")}"
        else -> "${item.totalNights ?: 0} ${detailText(language, "nights_short")}"
    }
}

private fun variantTitle(value: String?, index: Int, language: AppLanguage): String {
    val key = value ?: if (index == 0) "short" else if (index == 1) "balanced" else "extended"
    return detailText(language, when (key.lowercase(Locale.US)) { "short" -> "variant_short"; "balanced" -> "variant_balanced"; else -> "variant_extended" })
}

private fun localizedTier(raw: String?, language: AppLanguage): String = when (raw?.lowercase(Locale.US)) {
    "economy" -> when(language){ AppLanguage.RUSSIAN->"Эконом"; AppLanguage.ENGLISH->"Economy"; AppLanguage.UZBEK->"Ekonom"; AppLanguage.UZBEK_CYRILLIC->"Эконом" }
    "comfort" -> when(language){ AppLanguage.RUSSIAN->"Комфорт"; AppLanguage.ENGLISH->"Comfort"; AppLanguage.UZBEK->"Komfort"; AppLanguage.UZBEK_CYRILLIC->"Комфорт" }
    "luxury" -> when(language){ AppLanguage.RUSSIAN->"Люкс"; AppLanguage.ENGLISH->"Luxury"; AppLanguage.UZBEK->"Lyuks"; AppLanguage.UZBEK_CYRILLIC->"Люкс" }
    else -> when(language){ AppLanguage.RUSSIAN->"Стандарт"; AppLanguage.ENGLISH->"Standard"; AppLanguage.UZBEK->"Standart"; AppLanguage.UZBEK_CYRILLIC->"Стандарт" }
}

private fun ratingTitle(rating: Double, language: AppLanguage): String = detailText(
    language,
    when {
        rating >= 9 -> "rating_exceptional"
        rating >= 8 -> "rating_very_good"
        rating >= 7 -> "rating_good"
        else -> "selected_quality"
    },
)

private fun localizedCity(city: String, language: AppLanguage): String {
    val c = city.lowercase(Locale.US)
    return when {
        c.contains("makk") || c.contains("mecc") -> when(language){AppLanguage.RUSSIAN->"Мекка";AppLanguage.ENGLISH->"Makkah";AppLanguage.UZBEK->"Makka";AppLanguage.UZBEK_CYRILLIC->"Макка"}
        c.contains("mad") -> when(language){AppLanguage.RUSSIAN->"Медина";AppLanguage.ENGLISH->"Madinah";AppLanguage.UZBEK->"Madina";AppLanguage.UZBEK_CYRILLIC->"Мадина"}
        else -> city
    }
}

private fun roomCategoryName(category: IumrahRoomCategory, language: AppLanguage): String = when(category) {
    IumrahRoomCategory.DOUBLE -> when(language){AppLanguage.RUSSIAN->"Двухместный номер";AppLanguage.ENGLISH->"Double room";AppLanguage.UZBEK->"Ikki kishilik xona";AppLanguage.UZBEK_CYRILLIC->"Икки кишилик хона"}
    IumrahRoomCategory.TRIPLE -> when(language){AppLanguage.RUSSIAN->"Трёхместный номер";AppLanguage.ENGLISH->"Triple room";AppLanguage.UZBEK->"Uch kishilik xona";AppLanguage.UZBEK_CYRILLIC->"Уч кишилик хона"}
    IumrahRoomCategory.QUADRUPLE -> when(language){AppLanguage.RUSSIAN->"Четырёхместный номер";AppLanguage.ENGLISH->"Quadruple room";AppLanguage.UZBEK->"To‘rt kishilik xona";AppLanguage.UZBEK_CYRILLIC->"Тўрт кишилик хона"}
}

private fun roomCategoryBody(category: IumrahRoomCategory, language: AppLanguage): String = when(category) {
    IumrahRoomCategory.DOUBLE -> when(language){AppLanguage.RUSSIAN->"Для двух паломников";AppLanguage.ENGLISH->"Prepared for two pilgrims";AppLanguage.UZBEK->"Ikki ziyoratchi uchun";AppLanguage.UZBEK_CYRILLIC->"Икки зиёратчи учун"}
    IumrahRoomCategory.TRIPLE -> when(language){AppLanguage.RUSSIAN->"Для трёх паломников";AppLanguage.ENGLISH->"Prepared for three pilgrims";AppLanguage.UZBEK->"Uch ziyoratchi uchun";AppLanguage.UZBEK_CYRILLIC->"Уч зиёратчи учун"}
    IumrahRoomCategory.QUADRUPLE -> when(language){AppLanguage.RUSSIAN->"Для четырёх паломников";AppLanguage.ENGLISH->"Prepared for four pilgrims";AppLanguage.UZBEK->"To‘rt ziyoratchi uchun";AppLanguage.UZBEK_CYRILLIC->"Тўрт зиёратчи учун"}
}

private fun localizedBeds(category: IumrahRoomCategory, language: AppLanguage): String = when(category) {
    IumrahRoomCategory.DOUBLE -> when(language){AppLanguage.RUSSIAN->"2 места";AppLanguage.ENGLISH->"2 beds";AppLanguage.UZBEK->"2 joy";AppLanguage.UZBEK_CYRILLIC->"2 жой"}
    IumrahRoomCategory.TRIPLE -> when(language){AppLanguage.RUSSIAN->"3 места";AppLanguage.ENGLISH->"3 beds";AppLanguage.UZBEK->"3 joy";AppLanguage.UZBEK_CYRILLIC->"3 жой"}
    IumrahRoomCategory.QUADRUPLE -> when(language){AppLanguage.RUSSIAN->"4 места";AppLanguage.ENGLISH->"4 beds";AppLanguage.UZBEK->"4 joy";AppLanguage.UZBEK_CYRILLIC->"4 жой"}
}

private fun localizeRoomName(raw: String, language: AppLanguage): String {
    val normalized = raw.lowercase(Locale.US).replace(Regex("\\s+"), " ").trim()
    fun tr(ru: String, en: String, uz: String, uzCy: String) = when (language) {
        AppLanguage.RUSSIAN -> ru; AppLanguage.ENGLISH -> en; AppLanguage.UZBEK -> uz; AppLanguage.UZBEK_CYRILLIC -> uzCy
    }
    return when {
        "twin" in normalized && "city view" in normalized -> tr("Twin · Вид на город", "Twin Room · City View", "Twin xona · Shahar manzarasi", "Twin хона · Шаҳар манзараси")
        "king" in normalized && "city view" in normalized -> tr("King · Вид на город", "King Room · City View", "King xona · Shahar manzarasi", "King хона · Шаҳар манзараси")
        "double" in normalized && "city view" in normalized -> tr("Двухместный · Вид на город", "Double Room · City View", "Ikki kishilik xona · Shahar manzarasi", "Икки кишилик хона · Шаҳар манзараси")
        else -> raw
    }
}
private fun localizeBedText(raw: String, language: AppLanguage): String = raw

private fun cleanFact(raw: String?): String? {
    val value = raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    if (value.isBlank() || value.length > 80) return null
    val normalized = " ${value.lowercase(Locale.US)} "
    val polluted = listOf(" sar ", "current price", "previous price", "select room", "% off", "taxes", "non-refundable")
    return value.takeIf { polluted.none(normalized::contains) }
}

private fun cleanRoomDescription(raw: String): String? {
    val value = raw.replace(Regex("\\s+"), " ").trim()
    if (value.isBlank()) return null
    val bad = listOf("the current price", "the previous price", "select room", "non-refundable", "total includes taxes", "% off", "our lowest price")
    if (bad.any { value.lowercase(Locale.US).contains(it) }) return null
    return if (value.length <= 190) value else value.take(187).substringBeforeLast(" ") + "…"
}

private fun amenitySymbol(raw: String): CupertinoSymbol {
    val n = raw.lowercase(Locale.US)
    return when {
        "wifi" in n || "internet" in n -> CupertinoSymbol.SignalWave
        "parking" in n || "car" in n -> CupertinoSymbol.Car
        "breakfast" in n || "restaurant" in n || "food" in n || "room service" in n -> CupertinoSymbol.ForkKnife
        "airport" in n || "shuttle" in n || "transfer" in n -> CupertinoSymbol.Airplane
        "family" in n || "children" in n -> CupertinoSymbol.Persons
        "spa" in n || "fitness" in n || "gym" in n -> CupertinoSymbol.Sparkles
        "luggage" in n || "baggage" in n -> CupertinoSymbol.Suitcase
        else -> CupertinoSymbol.Checkmark
    }
}

private fun localizedAmenity(raw: String, language: AppLanguage): String {
    val normalized = raw.lowercase(Locale.US)
    val key = when {
        "breakfast" in normalized -> "amenity_breakfast"
        "transfer" in normalized || "shuttle" in normalized -> "amenity_transfer"
        "wifi" in normalized || "wi-fi" in normalized -> "amenity_wifi"
        "parking" in normalized -> "amenity_parking"
        "restaurant" in normalized -> "amenity_restaurant"
        "air condition" in normalized -> "amenity_air_conditioning"
        "family" in normalized -> "amenity_family_rooms"
        "24" in normalized || "reception" in normalized -> "amenity_reception"
        "lift" in normalized || "elevator" in normalized -> "amenity_elevator"
        "laundry" in normalized -> "amenity_laundry"
        "coffee" in normalized || "cafe" in normalized -> "amenity_coffee_shop"
        "concierge" in normalized -> "amenity_concierge"
        "spa" in normalized -> "amenity_spa"
        "dry clean" in normalized -> "amenity_dry_cleaning"
        "luggage" in normalized || "baggage" in normalized -> "amenity_luggage_storage"
        "fitness" in normalized || "gym" in normalized -> "amenity_fitness_center"
        "room service" in normalized -> "amenity_room_service"
        "non-smoking" in normalized || "non smoking" in normalized -> "amenity_non_smoking"
        else -> null
    }
    return key?.let { com.iumrah.beta.core.localization.L10n.text(it, language) } ?: raw
}

private fun shareHotelDetail(context: Context, hotel: HotelDetail) {
    val text = buildString {
        append(hotel.name)
        if (hotel.city.isNotBlank()) append(" · ${hotel.city}")
        if (hotel.address.isNotBlank()) append("\n${hotel.address}")
        hotel.googleMapsURL?.takeIf { it.isNotBlank() }?.let { AppConfig.absoluteUrl(it)?.let { url -> append("\n$url") } }
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, hotel.name))
}

private fun selectionText(language: AppLanguage, key: String): String = when (key) {
    "selected_room" -> when(language){ AppLanguage.RUSSIAN->"Выбранный номер"; AppLanguage.ENGLISH->"Selected room"; AppLanguage.UZBEK->"Tanlangan xona"; AppLanguage.UZBEK_CYRILLIC->"Танланган хона" }
    "done" -> when(language){ AppLanguage.RUSSIAN->"Готово"; AppLanguage.ENGLISH->"Done"; AppLanguage.UZBEK->"Tayyor"; AppLanguage.UZBEK_CYRILLIC->"Тайёр" }
    "room_chosen" -> when(language){ AppLanguage.RUSSIAN->"Номер выбран"; AppLanguage.ENGLISH->"Room chosen"; AppLanguage.UZBEK->"Xona tanlandi"; AppLanguage.UZBEK_CYRILLIC->"Хона танланди" }
    "choose_room" -> when(language){ AppLanguage.RUSSIAN->"Выбрать номер"; AppLanguage.ENGLISH->"Choose room"; AppLanguage.UZBEK->"Xonani tanlash"; AppLanguage.UZBEK_CYRILLIC->"Хонани танлаш" }
    "refund_policy" -> when(language){ AppLanguage.RUSSIAN->"Условия отеля"; AppLanguage.ENGLISH->"Hotel refund policy"; AppLanguage.UZBEK->"Mehmonxona qaytarish siyosati"; AppLanguage.UZBEK_CYRILLIC->"Меҳмонхона қайтариш сиёсати" }
    else -> when(language){ AppLanguage.RUSSIAN->"Условия возврата показываются до оплаты пакета."; AppLanguage.ENGLISH->"Refund terms are shown before package payment."; AppLanguage.UZBEK->"Qaytarish shartlari paket to‘lovidan oldin ko‘rsatiladi."; AppLanguage.UZBEK_CYRILLIC->"Қайтариш шартлари пакет тўловидан олдин кўрсатилади." }
}

private fun detailText(language: AppLanguage, key: String, vararg args: Any): String {
    val value = when (language) {
        AppLanguage.RUSSIAN -> mapOf(
            "load_error" to "Не удалось загрузить данные отеля.", "room_error" to "Не удалось загрузить категории номеров.",
            "preparing_price" to "Готовим актуальную цену пакета…", "trip_options" to "Варианты поездки", "ready_packages" to "Готовых пакетов с этим отелем: %d", "counter" to "%d из %d",
            "your_umrah" to "Ваша Умра с этим отелем", "days_short" to "дн.", "nights_short" to "ноч.", "ready_variant" to "Готовый пакет на %d дн. · его состав и цена закреплены за этим Package ID.",
            "per_pilgrim" to "на паломника", "services" to "Перелёт + отели + сервисы", "open_configurator" to "Открыть конфигуратор", "variant_short" to "Короткая поездка", "variant_balanced" to "Оптимальная поездка", "variant_extended" to "Больше дней в Умре",
            "selected_quality" to "Отобран iumrah", "reviews" to "%d отзывов", "quality_note" to "iumrah отбирает и мониторит отели, подходящие нашим паломникам.", "rating_exceptional" to "Превосходно", "rating_very_good" to "Очень хорошо", "rating_good" to "Хорошо",
            "time_title" to "Время на ресепшене", "time_body" to "Сверяйте местное время перед звонком в отель или Care.", "tashkent" to "Ташкент", "makkah" to "Мекка", "madinah" to "Медина", "moscow" to "Москва",
            "photos" to "Фотографии", "view_all_photos" to "Все фото · %d", "amenities" to "Что есть в отеле", "prepared_rooms" to "Номера iumrah", "prepared_rooms_body" to "Подготовленные категории размещения для паломников iumrah.", "rooms_loading" to "Загружаем номера…", "retry" to "Повторить",
            "hotel_rooms" to "Номера отеля", "hotel_rooms_body" to "Фактические варианты из каталога отеля.", "rooms_empty" to "В каталоге пока нет вариантов номеров.", "about" to "Об отеле", "location" to "Расположение", "open_map" to "Открыть карту", "practical" to "Полезная информация", "checkin" to "Заезд", "checkout" to "Выезд", "property_type" to "Тип размещения",
            "care_title" to "Поддержка по этому отелю", "care_body" to "Если нужен совет по отелю до бронирования, iumrah Care поможет с выбором.", "care_dialog" to "Свяжитесь с iumrah Care по вопросу этого отеля.", "call" to "Позвонить", "close" to "Закрыть", "done" to "Готово", "share_unavailable" to "Не удалось подготовить пакет для отправки. Обновите цены и попробуйте ещё раз.", "share_error_title" to "Не удалось поделиться", "share_error_ok" to "Понятно", "loading" to "Загружаем данные отеля…",
        )[key]
        AppLanguage.ENGLISH -> mapOf(
            "load_error" to "Could not load hotel details.", "room_error" to "Could not load room categories.",
            "preparing_price" to "Preparing the current package price…", "trip_options" to "Trip options", "ready_packages" to "%d ready packages with this hotel", "counter" to "%d of %d",
            "your_umrah" to "Your Umrah with this hotel", "days_short" to "days", "nights_short" to "nights", "ready_variant" to "Ready %d-day package · its composition and price are fixed to this Package ID.",
            "per_pilgrim" to "per pilgrim", "services" to "Flight + hotels + services", "open_configurator" to "Open Configurator", "variant_short" to "Short trip", "variant_balanced" to "Balanced trip", "variant_extended" to "Longer Umrah",
            "selected_quality" to "Selected by iumrah", "reviews" to "%d reviews", "quality_note" to "iumrah selects and monitors hotels that suit our pilgrims.", "rating_exceptional" to "Exceptional", "rating_very_good" to "Very good", "rating_good" to "Good",
            "time_title" to "Reception time", "time_body" to "Check local time before calling the hotel or Care.", "tashkent" to "Tashkent", "makkah" to "Makkah", "madinah" to "Madinah", "moscow" to "Moscow",
            "photos" to "Photos", "view_all_photos" to "View all · %d", "amenities" to "What this hotel offers", "prepared_rooms" to "iumrah rooms", "prepared_rooms_body" to "Room categories prepared for iumrah pilgrims.", "rooms_loading" to "Loading rooms…", "retry" to "Retry",
            "hotel_rooms" to "Hotel rooms", "hotel_rooms_body" to "Actual room options from the hotel catalog.", "rooms_empty" to "Room options are not available in the catalog yet.", "about" to "About the hotel", "location" to "Location", "open_map" to "Open map", "practical" to "Good to know", "checkin" to "Check-in", "checkout" to "Check-out", "property_type" to "Property type",
            "care_title" to "Support for this hotel", "care_body" to "If you need advice before booking, iumrah Care can help with the hotel choice.", "care_dialog" to "Contact iumrah Care about this hotel.", "call" to "Call", "close" to "Close", "done" to "Done", "share_unavailable" to "The package could not be prepared for sharing. Refresh pricing and try again.", "share_error_title" to "Could not share", "share_error_ok" to "OK", "loading" to "Loading hotel details…",
        )[key]
        AppLanguage.UZBEK -> mapOf(
            "load_error" to "Mehmonxona ma’lumotlarini yuklab bo‘lmadi.", "room_error" to "Xona toifalarini yuklab bo‘lmadi.",
            "preparing_price" to "Joriy paket narxi tayyorlanmoqda…", "trip_options" to "Safar variantlari", "ready_packages" to "Shu mehmonxona bilan tayyor paketlar: %d", "counter" to "%d / %d",
            "your_umrah" to "Umrangiz — shu mehmonxona bilan", "days_short" to "kun", "nights_short" to "tun", "ready_variant" to "%d kunlik tayyor paket · tarkibi va narxi shu Package ID uchun saqlangan.",
            "per_pilgrim" to "bir ziyoratchiga", "services" to "Parvoz + mehmonxonalar + xizmatlar", "open_configurator" to "Configuratorni ochish", "variant_short" to "Qisqa safar", "variant_balanced" to "Optimal safar", "variant_extended" to "Umrada ko‘proq kun",
            "selected_quality" to "iumrah tanlovi", "reviews" to "%d ta sharh", "quality_note" to "iumrah ziyoratchilarimizga mos mehmonxonalarni tanlaydi va kuzatadi.", "rating_exceptional" to "Ajoyib", "rating_very_good" to "Juda yaxshi", "rating_good" to "Yaxshi",
            "time_title" to "Resepsion vaqti", "time_body" to "Mehmonxona yoki Care’ga qo‘ng‘iroqdan oldin mahalliy vaqtni tekshiring.", "tashkent" to "Toshkent", "makkah" to "Makka", "madinah" to "Madina", "moscow" to "Moskva",
            "photos" to "Rasmlar", "view_all_photos" to "Barchasi · %d", "amenities" to "Mehmonxona imkoniyatlari", "prepared_rooms" to "iumrah xonalari", "prepared_rooms_body" to "iumrah ziyoratchilari uchun tayyorlangan joylashuv toifalari.", "rooms_loading" to "Xonalar yuklanmoqda…", "retry" to "Qayta urinish",
            "hotel_rooms" to "Mehmonxona xonalari", "hotel_rooms_body" to "Katalogdagi haqiqiy xona variantlari.", "rooms_empty" to "Katalogda hozircha xona variantlari yo‘q.", "about" to "Mehmonxona haqida", "location" to "Joylashuv", "open_map" to "Xaritani ochish", "practical" to "Muhim ma’lumot", "checkin" to "Kirish", "checkout" to "Chiqish", "property_type" to "Joylashuv turi",
            "care_title" to "Shu mehmonxona bo‘yicha yordam", "care_body" to "Bron qilishdan oldin maslahat kerak bo‘lsa, iumrah Care yordam beradi.", "care_dialog" to "Shu mehmonxona bo‘yicha iumrah Care bilan bog‘laning.", "call" to "Qo‘ng‘iroq", "close" to "Yopish", "done" to "Tayyor", "share_unavailable" to "Paketni ulashish uchun tayyorlab bo‘lmadi. Narxlarni yangilang va qayta urinib ko‘ring.", "share_error_title" to "Ulashib bo‘lmadi", "share_error_ok" to "Tushunarli", "loading" to "Mehmonxona ma’lumotlari yuklanmoqda…",
        )[key]
        AppLanguage.UZBEK_CYRILLIC -> mapOf(
            "load_error" to "Меҳмонхона маълумотларини юклаб бўлмади.", "room_error" to "Хона тоифаларини юклаб бўлмади.",
            "preparing_price" to "Жорий пакет нархи тайёрланмоқда…", "trip_options" to "Сафар вариантлари", "ready_packages" to "Шу меҳмонхона билан тайёр пакетлар: %d", "counter" to "%d / %d",
            "your_umrah" to "Умрангиз — шу меҳмонхона билан", "days_short" to "кун", "nights_short" to "тун", "ready_variant" to "%d кунлик тайёр пакет · таркиби ва нархи шу Package ID учун сақланган.",
            "per_pilgrim" to "бир зиёратчига", "services" to "Парвоз + меҳмонхоналар + хизматлар", "open_configurator" to "Configuratorни очиш", "variant_short" to "Қисқа сафар", "variant_balanced" to "Оптимал сафар", "variant_extended" to "Умрада кўпроқ кун",
            "selected_quality" to "iumrah танлови", "reviews" to "%d та шарҳ", "quality_note" to "iumrah зиёратчиларимизга мос меҳмонхоналарни танлайди ва кузатади.", "rating_exceptional" to "Ажойиб", "rating_very_good" to "Жуда яхши", "rating_good" to "Яхши",
            "time_title" to "Ресепсион вақти", "time_body" to "Меҳмонхона ёки Care’га қўнғироқдан олдин маҳаллий вақтни текширинг.", "tashkent" to "Тошкент", "makkah" to "Макка", "madinah" to "Мадина", "moscow" to "Москва",
            "photos" to "Расмлар", "view_all_photos" to "Барчаси · %d", "amenities" to "Меҳмонхона имкониятлари", "prepared_rooms" to "iumrah хоналари", "prepared_rooms_body" to "iumrah зиёратчилари учун тайёрланган жойлашув тоифалари.", "rooms_loading" to "Хоналар юкланмоқда…", "retry" to "Қайта уриниш",
            "hotel_rooms" to "Меҳмонхона хоналари", "hotel_rooms_body" to "Каталогдаги ҳақиқий хона вариантлари.", "rooms_empty" to "Каталогда ҳозирча хона вариантлари йўқ.", "about" to "Меҳмонхона ҳақида", "location" to "Жойлашув", "open_map" to "Харитани очиш", "practical" to "Муҳим маълумот", "checkin" to "Кириш", "checkout" to "Чиқиш", "property_type" to "Жойлашув тури",
            "care_title" to "Шу меҳмонхона бўйича ёрдам", "care_body" to "Брон қилишдан олдин маслаҳат керак бўлса, iumrah Care ёрдам беради.", "care_dialog" to "Шу меҳмонхона бўйича iumrah Care билан боғланинг.", "call" to "Қўнғироқ", "close" to "Ёпиш", "done" to "Тайёр", "share_unavailable" to "Пакетни улашиш учун тайёрлаб бўлмади. Нархларни янгиланг ва қайта уриниб кўринг.", "share_error_title" to "Улашиб бўлмади", "share_error_ok" to "Тушунарли", "loading" to "Меҳмонхона маълумотлари юкланмоқда…",
        )[key]
    } ?: key
    return if (args.isEmpty()) value else String.format(language.locale, value, *args)
}
