package com.iumrah.beta.ui.ziyarats

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableStateMapOf
import com.iumrah.beta.ui.map.IumrahMapLibreView
import com.iumrah.beta.ui.map.IumrahMapStyle
import com.iumrah.beta.ui.map.rememberIumrahMapController
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.ziyarat.ZiyaratFallbackCatalog
import com.iumrah.beta.data.ziyarat.ZiyaratService
import com.iumrah.beta.models.ziyarat.ZiyaratImage
import com.iumrah.beta.models.ziyarat.ZiyaratPlace
import com.iumrah.beta.models.ziyarat.ZiyaratRoute
import com.iumrah.beta.models.ziyarat.ZiyaratSeedData
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import kotlin.math.max
import kotlin.math.min

private enum class JourneyCity { MADINAH, MAKKAH }
private enum class ZiyaratTab { JOURNEY, PLACES, ROUTE, MAP }
private enum class ZiyaratMapMode { STANDARD, SATELLITE }
private enum class PanelLevel { COMPACT, CARD, FULL }

@Composable
fun ZiyaratJourneyScreen(
    language: AppLanguage,
    chrome: AppChromeStore,
    service: ZiyaratService = remember { ZiyaratService() },
) {
    // iOS Ziyarats rebuild: preserve existing navigation entry point.
    // This no longer constructs a map, animates route pins or waits on network at launch.
    ZiyaratStableScreen(language = language, chrome = chrome, service = service)
}

@Composable
private fun ZiyaratTopChrome(
    language: AppLanguage,
    city: JourneyCity,
    mapMode: ZiyaratMapMode,
    expanded: Boolean,
    onCity: (JourneyCity) -> Unit,
    onMap: () -> Unit,
    onBack: () -> Unit,
    onFit: () -> Unit,
) {
    if (expanded) return
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .96f)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(CupertinoSymbol.ChevronLeft, "Back", modifier = Modifier.size(18.dp)) }
            Text("iumrah Ziyarats", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(38.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                Modifier.shadow(12.dp, CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .97f)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CityChip(city == JourneyCity.MADINAH, zt(language, "Медина", "Madinah", "Madina", "Мадина")) { onCity(JourneyCity.MADINAH) }
                CityChip(city == JourneyCity.MAKKAH, zt(language, "Мекка", "Makkah", "Makka", "Макка")) { onCity(JourneyCity.MAKKAH) }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.width(54.dp).shadow(12.dp, RoundedCornerShape(27.dp)).clip(RoundedCornerShape(27.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = .97f)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onMap), contentAlignment = Alignment.Center) {
                    Icon(if (mapMode == ZiyaratMapMode.STANDARD) CupertinoSymbol.Route else CupertinoSymbol.Globe, null, modifier = Modifier.size(20.dp), tint = IumrahColors.SystemBlue)
                }
                Divider(Modifier.padding(horizontal = 13.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                Box(Modifier.fillMaxWidth().height(50.dp).clickable(onClick = onFit), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.Location, null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun CityChip(selected: Boolean, text: String, action: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(CircleShape).clickable(onClick = action).padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ZiyaratMapSurface(
    language: AppLanguage,
    route: ZiyaratRoute,
    selectedPlace: ZiyaratPlace?,
    showRoute: Boolean,
    showPins: Boolean,
    mode: ZiyaratMapMode,
    revealedStops: Int,
    fitRouteNonce: Int,
    onSelect: (ZiyaratPlace) -> Unit,
) {
    val ordered = route.places.sortedBy { it.routeOrder }
    val controller = rememberIumrahMapController()
    val map = controller.map
    val pinPositions = remember { mutableStateMapOf<String, IntOffset>() }

    fun updatePinPositions(activeMap: MapLibreMap? = map) {
        val resolvedMap = activeMap ?: return
        ordered.forEach { place ->
            val point = resolvedMap.projection.toScreenLocation(LatLng(place.latitude, place.longitude))
            pinPositions[place.id] = IntOffset(point.x.roundToInt(), point.y.roundToInt())
        }
    }

    fun fitRoute(activeMap: MapLibreMap? = map, animate: Boolean = true) {
        val resolvedMap = activeMap ?: return
        if (ordered.isEmpty()) return
        val points = ordered.map { LatLng(it.latitude, it.longitude) }
        val update = if (points.size == 1) {
            CameraUpdateFactory.newLatLngZoom(points.first(), 14.8)
        } else {
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.fromLatLngs(points),
                58,
                170,
                58,
                330,
            )
        }
        controller.mapView.post {
            if (animate) resolvedMap.animateCamera(update, 650) else resolvedMap.moveCamera(update)
        }
    }

    fun redrawRoute(activeMap: MapLibreMap? = map) {
        val resolvedMap = activeMap ?: return
        resolvedMap.clear()
        if (!showRoute || ordered.size < 2) return
        val points = ordered.map { LatLng(it.latitude, it.longitude) }
        resolvedMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(android.graphics.Color.argb(235, 255, 255, 255))
                .width(8f),
        )
        resolvedMap.addPolyline(
            PolylineOptions()
                .addAll(points)
                .color(android.graphics.Color.rgb(0, 122, 255))
                .width(4.5f),
        )
    }

    LaunchedEffect(map, mode) {
        controller.setStyle(
            if (mode == ZiyaratMapMode.STANDARD) IumrahMapStyle.STANDARD else IumrahMapStyle.SATELLITE,
        )
    }

    LaunchedEffect(map, controller.styleEpoch, route.id, showRoute) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0) return@LaunchedEffect
        redrawRoute(activeMap)
        updatePinPositions(activeMap)
        if (selectedPlace == null) fitRoute(activeMap, animate = false)
    }

    LaunchedEffect(map, route.id, fitRouteNonce) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0) return@LaunchedEffect
        fitRoute(activeMap, animate = fitRouteNonce > 0)
    }

    LaunchedEffect(map, selectedPlace?.id) {
        val activeMap = map ?: return@LaunchedEffect
        val place = selectedPlace ?: return@LaunchedEffect
        val camera = CameraPosition.Builder()
            .target(LatLng(place.latitude, place.longitude))
            .zoom(maxOf(activeMap.cameraPosition.zoom, 14.7))
            .build()
        activeMap.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 520)
    }

    DisposableEffect(map, route.id) {
        val activeMap = map
        if (activeMap == null) return@DisposableEffect onDispose { }
        val moveListener = MapLibreMap.OnCameraMoveListener { updatePinPositions(activeMap) }
        val idleListener = MapLibreMap.OnCameraIdleListener { updatePinPositions(activeMap) }
        activeMap.addOnCameraMoveListener(moveListener)
        activeMap.addOnCameraIdleListener(idleListener)
        updatePinPositions(activeMap)
        onDispose {
            activeMap.removeOnCameraMoveListener(moveListener)
            activeMap.removeOnCameraIdleListener(idleListener)
        }
    }

    Box(Modifier.fillMaxSize()) {
        IumrahMapLibreView(controller = controller, modifier = Modifier.fillMaxSize())

        if (showPins) {
            ordered.take(revealedStops.coerceAtLeast(0)).forEach { place ->
                val point = pinPositions[place.id] ?: return@forEach
                val selected = selectedPlace?.id == place.id
                Column(
                    Modifier
                        .offset {
                            IntOffset(
                                point.x - if (selected) 20.dp.roundToPx() else 17.dp.roundToPx(),
                                point.y - if (selected) 65.dp.roundToPx() else 31.dp.roundToPx(),
                            )
                        }
                        .clickable { onSelect(place) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    AnimatedVisibility(selected) {
                        Text(
                            place.localized(language).title,
                            modifier = Modifier
                                .shadow(6.dp, CircleShape)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f), CircleShape)
                                .padding(horizontal = 11.dp, vertical = 7.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        Modifier
                            .size(if (selected) 40.dp else 33.dp)
                            .clip(CircleShape)
                            .background(if (selected) IumrahColors.SystemBlue else Color.Black.copy(alpha = .84f))
                            .border(if (selected) 3.dp else 2.5.dp, Color.White.copy(alpha = .96f), CircleShape)
                            .shadow(5.dp, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            place.routeOrder.toString(),
                            color = Color.White,
                            fontSize = if (selected) 15.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZiyaratBottomPanel(
    modifier: Modifier = Modifier,
    language: AppLanguage,
    route: ZiyaratRoute,
    selectedPlace: ZiyaratPlace?,
    activeTab: ZiyaratTab,
    panelLevel: PanelLevel,
    mapMode: ZiyaratMapMode,
    showRoute: Boolean,
    showPins: Boolean,
    loading: Boolean,
    onTab: (ZiyaratTab) -> Unit,
    onPanelLevel: (PanelLevel) -> Unit,
    onSelectPlace: (ZiyaratPlace) -> Unit,
    onBackPlace: () -> Unit,
    onShowRoute: () -> Unit,
    onMapMode: (ZiyaratMapMode) -> Unit,
    onShowRouteLine: (Boolean) -> Unit,
    onShowPins: (Boolean) -> Unit,
    onFitRoute: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val cardHeight = (screenHeight * .48f).coerceIn(360.dp, 470.dp)
    val fullHeight = minOf(screenHeight - 12.dp, maxOf(cardHeight + 150.dp, screenHeight - 48.dp))
    val target = when (panelLevel) {
        PanelLevel.COMPACT -> 108.dp
        PanelLevel.CARD -> cardHeight
        PanelLevel.FULL -> fullHeight
    }
    val height by animateDpAsState(target, tween(260), label = "ziyarat-panel")
    var dragTotal by remember { mutableFloatStateOf(0f) }
    Column(
        modifier.fillMaxWidth().height(height)
            .shadow(20.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .985f)),
    ) {
        Box(
            Modifier.fillMaxWidth().height(24.dp).pointerInput(panelLevel) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, amount -> dragTotal += amount },
                    onDragEnd = {
                        val next = when {
                            dragTotal < -70f -> when (panelLevel) { PanelLevel.COMPACT -> PanelLevel.CARD; PanelLevel.CARD -> PanelLevel.FULL; PanelLevel.FULL -> PanelLevel.FULL }
                            dragTotal > 70f -> when (panelLevel) { PanelLevel.FULL -> PanelLevel.CARD; PanelLevel.CARD -> PanelLevel.COMPACT; PanelLevel.COMPACT -> PanelLevel.COMPACT }
                            else -> panelLevel
                        }
                        dragTotal = 0f
                        onPanelLevel(next)
                    },
                    onDragCancel = { dragTotal = 0f },
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(36.dp).height(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .22f)))
        }

        if (panelLevel != PanelLevel.COMPACT) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (activeTab) {
                    ZiyaratTab.JOURNEY -> JourneyPanel(language, route, loading, onSelectPlace, onShowRoute)
                    ZiyaratTab.PLACES -> if (selectedPlace != null) PlaceDetailPanel(language, selectedPlace, panelLevel == PanelLevel.FULL, onBackPlace, { onPanelLevel(PanelLevel.FULL) }) else PlacesPanel(language, route, onSelectPlace)
                    ZiyaratTab.ROUTE -> RoutePanel(language, route, loading, onSelectPlace) { onPanelLevel(PanelLevel.COMPACT) }
                    ZiyaratTab.MAP -> MapSettingsPanel(language, mapMode, showRoute, showPins, onMapMode, onShowRouteLine, onShowPins) { onPanelLevel(PanelLevel.COMPACT) }
                }
            }
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        ZiyaratTabBar(language, activeTab, onTab)
    }
}


@Composable
private fun JourneyPanel(language: AppLanguage, route: ZiyaratRoute, loading: Boolean, onSelect: (ZiyaratPlace) -> Unit, onShowRoute: () -> Unit) {
    val ordered = route.places.sortedBy { it.routeOrder }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("iumrah Ziyarats", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                Text(if (route.city.lowercase().contains("makk")) zt(language, "Зиярат Мекки", "Makkah Ziyarat", "Makka ziyorati", "Макка зиёрати") else zt(language, "Зиярат Медины", "Medina Ziyarat", "Madina ziyorati", "Мадина зиёрати"), fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                Text(zt(language, "Священные и исторические места в одной поездке", "Sacred and historic places in one journey", "Muqaddas va tarixiy joylar bitta yo‘nalishda", "Муқаддас ва тарихий жойлар битта йўналишда"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            }
        }
        item { JourneyMetrics(language, ordered.size, route.estimatedMinutes) }
        if (loading) item { Box(Modifier.fillMaxWidth().height(74.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) } }
        ordered.firstOrNull()?.let { first -> item { NextStopRow(language, first) { onSelect(first) } } }
        item {
            IumrahPressable(onClick = onShowRoute, modifier = Modifier.fillMaxWidth().height(48.dp), cornerRadius = 24.dp, background = IumrahColors.SystemBlue) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(CupertinoSymbol.Route, null, modifier = Modifier.size(19.dp), tint = Color.White)
                    Text(zt(language, "Показать маршрут", "Show route", "Yo‘nalishni ko‘rsatish", "Йўналишни кўрсатиш"), modifier = Modifier.padding(start = 9.dp), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun JourneyMetrics(language: AppLanguage, stops: Int, minutes: Int) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        JourneyMetric(CupertinoSymbol.Location, stops.toString(), zt(language, "мест", "stops", "joy", "жой"), Modifier.weight(1f))
        Divider(Modifier.width(1.dp).height(38.dp))
        JourneyMetric(CupertinoSymbol.Hourglass, if (minutes > 0) if (minutes >= 60) "~${minutes / 60}h" else "$minutes min" else "—", zt(language, "всего", "total", "jami", "жами"), Modifier.weight(1f))
        Divider(Modifier.width(1.dp).height(38.dp))
        JourneyMetric(CupertinoSymbol.Car, zt(language, "Авто", "Car", "Avto", "Авто"), zt(language, "поездка", "journey", "sayohat", "саёҳат"), Modifier.weight(1f))
    }
}

@Composable
private fun JourneyMetric(icon: CupertinoSymbol, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .66f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    }
}

@Composable
private fun NextStopRow(language: AppLanguage, place: ZiyaratPlace, onClick: () -> Unit) {
    val content = place.localized(language)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        ZiyaratImageThumb(place.images.firstOrNull(), Modifier.size(64.dp).clip(RoundedCornerShape(17.dp)))
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(zt(language, "Первая остановка", "First stop", "Birinchi bekat", "Биринчи бекат"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IumrahColors.SystemBlue)
            Text(content.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${place.durationMinutes} ${zt(language, "мин", "min", "daq", "дақ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
        }
        Icon(CupertinoSymbol.ChevronRight, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .34f))
    }
}

@Composable
private fun PlacesPanel(language: AppLanguage, route: ZiyaratRoute, onSelect: (ZiyaratPlace) -> Unit) {
    val ordered = route.places.sortedBy { it.routeOrder }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp)) {
        item {
            Text(zt(language, "Все места", "All places", "Barcha joylar", "Барча жойлар"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("${ordered.size} ${zt(language, "мест", "stops", "joy", "жой")}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
        }
        itemsIndexed(ordered) { index, place ->
            PlaceListRow(language, place) { onSelect(place) }
            if (index < ordered.lastIndex) Divider(Modifier.padding(start = 90.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
        }
    }
}

@Composable
private fun PlaceListRow(language: AppLanguage, place: ZiyaratPlace, onClick: () -> Unit) {
    val content = place.localized(language)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            ZiyaratImageThumb(place.images.firstOrNull(), Modifier.width(72.dp).height(60.dp).clip(RoundedCornerShape(16.dp)))
            Box(Modifier.align(Alignment.TopStart).offset((-4).dp, (-5).dp).size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = .84f)).border(2.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                Text(place.routeOrder.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(content.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(content.shortDescription, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(CupertinoSymbol.ChevronRight, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .34f))
    }
}

@Composable
private fun RoutePanel(language: AppLanguage, route: ZiyaratRoute, loading: Boolean, onSelect: (ZiyaratPlace) -> Unit, onShowOnMap: () -> Unit) {
    val ordered = route.places.sortedBy { it.routeOrder }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(zt(language, "Маршрут зиярата", "Ziyarat route", "Ziyorat yo‘nalishi", "Зиёрат йўналиши"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(zt(language, "Священные и исторические места в одной поездке", "Sacred and historic places in one journey", "Muqaddas va tarixiy joylar bitta yo‘nalishda", "Муқаддас ва тарихий жойлар битта йўналишда"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(ordered) { index, place ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (index == 0) IumrahColors.SystemBlue else Color.Black.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
                        Text(place.routeOrder.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    if (index < ordered.lastIndex) Box(Modifier.width(2.dp).height(40.dp).background(IumrahColors.SystemBlue.copy(alpha = .32f)))
                }
                val c = place.localized(language)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(c.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("${visitText(language, place.visitType)} · ${place.durationMinutes} ${zt(language, "мин", "min", "daq", "дақ")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
                Icon(CupertinoSymbol.ChevronRight, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
            }
        }
        item {
            IumrahPressable(onClick = onShowOnMap, modifier = Modifier.fillMaxWidth().height(48.dp), cornerRadius = 24.dp, background = IumrahColors.SystemBlue) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(CupertinoSymbol.Location, null, modifier = Modifier.size(19.dp), tint = Color.White)
                    Text(zt(language, "Показать на карте", "Show on map", "Xaritada ko‘rsatish", "Харитада кўрсатиш"), Modifier.padding(start = 8.dp), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MapSettingsPanel(
    language: AppLanguage,
    mode: ZiyaratMapMode,
    showRoute: Boolean,
    showPins: Boolean,
    onMode: (ZiyaratMapMode) -> Unit,
    onShowRoute: (Boolean) -> Unit,
    onShowPins: (Boolean) -> Unit,
    onFitRoute: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(zt(language, "Режим карты", "Map mode", "Xarita rejimi", "Харита режими"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(zt(language, "Выберите вид карты и то, что показывать", "Choose the map view and what to display", "Xarita ko‘rinishi va ko‘rsatiladigan ma’lumotlarni tanlang", "Харита кўриниши ва кўрсатиладиган маълумотларни танланг"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MapModeChoice(zt(language, "Стандарт", "Standard", "Standart", "Стандарт"), CupertinoSymbol.Route, mode == ZiyaratMapMode.STANDARD, Modifier.weight(1f)) { onMode(ZiyaratMapMode.STANDARD) }
            MapModeChoice(zt(language, "Спутник", "Satellite", "Sun’iy yo‘ldosh", "Сунъий йўлдош"), CupertinoSymbol.Globe, mode == ZiyaratMapMode.SATELLITE, Modifier.weight(1f)) { onMode(ZiyaratMapMode.SATELLITE) }
        }
        ToggleRow(zt(language, "Линия маршрута", "Route line", "Yo‘nalish chizig‘i", "Йўналиш чизиғи"), CupertinoSymbol.Route, showRoute) { onShowRoute(!showRoute) }
        ToggleRow(zt(language, "Метки мест", "Place markers", "Joy belgilari", "Жой белгилари"), CupertinoSymbol.Location, showPins) { onShowPins(!showPins) }
        IumrahPressable(onClick = onFitRoute, modifier = Modifier.fillMaxWidth().height(48.dp), cornerRadius = 24.dp, background = MaterialTheme.colorScheme.surfaceVariant) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(CupertinoSymbol.Location, null, modifier = Modifier.size(19.dp), tint = IumrahColors.SystemBlue)
                Text(zt(language, "Показать весь маршрут", "Fit entire route", "Butun yo‘nalishni ko‘rsatish", "Бутун йўналишни кўрсатиш"), Modifier.padding(start = 8.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun MapModeChoice(title: String, icon: CupertinoSymbol, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).border(if (selected) 1.5.dp else .7.dp, if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface)
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ToggleRow(title: String, icon: CupertinoSymbol, enabled: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).clickable(onClick = action).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(19.dp), tint = IumrahColors.SystemBlue)
        Text(title, Modifier.padding(start = 11.dp).weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.width(42.dp).height(26.dp).clip(CircleShape).background(if (enabled) IumrahColors.SystemGreen else Color(0xFFD1D1D6)).padding(3.dp)) {
            Box(Modifier.size(20.dp).offset(x = if (enabled) 16.dp else 0.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun PlaceDetailPanel(language: AppLanguage, place: ZiyaratPlace, expanded: Boolean, onBack: () -> Unit, onExpand: () -> Unit) {
    val context = LocalContext.current
    val content = place.localized(language)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ChevronLeft, null, modifier = Modifier.size(15.dp)) }
                Text(zt(language, "Остановка ${place.routeOrder}", "Stop ${place.routeOrder}", "${place.routeOrder}-bekat", "${place.routeOrder}-бекат"), modifier = Modifier.padding(start = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
                Spacer(Modifier.weight(1f))
                if (!expanded) Text(zt(language, "Подробнее", "More", "Batafsil", "Батафсил"), modifier = Modifier.clickable(onClick = onExpand), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = IumrahColors.SystemBlue)
            }
        }
        item { PlaceGallery(place, expanded) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(content.title, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.55).sp)
                if (place.titleArabic.isNotBlank()) Text(place.titleArabic, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                item { MetaCapsule(CupertinoSymbol.Persons, visitText(language, place.visitType)) }
                item { MetaCapsule(CupertinoSymbol.Hourglass, "${place.durationMinutes} ${zt(language, "мин", "min", "daq", "дақ")}") }
                item { MetaCapsule(categoryIcon(place.category), categoryTitle(language, place.category)) }
            }
        }
        if (content.longDescription.isNotBlank()) item { InfoSection(zt(language, "О месте", "About", "Joy haqida", "Жой ҳақида"), content.longDescription) }
        if (content.interestingFacts.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(zt(language, "Что интересно здесь", "What’s interesting here", "Bu yerda nimalar qiziq", "Бу ерда нималар қизиқ"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                content.interestingFacts.forEachIndexed { index, fact ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
                        Text(fact, Modifier.padding(start = 11.dp).weight(1f), fontSize = 14.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        if (content.visitNotes.isNotBlank()) item { InfoSection(zt(language, "Как посетить", "Visit notes", "Tashrif", "Ташриф"), content.visitNotes) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(zt(language, "Точная точка", "Exact point", "Aniq nuqta", "Аниқ нуқта"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (place.mapLabel.isNotBlank()) place.mapLabel else place.address, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .54f))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(String.format("%.5f, %.5f", place.latitude, place.longitude), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                    Spacer(Modifier.weight(1f))
                    Icon(CupertinoSymbol.CheckCircleFill, null, modifier = Modifier.size(17.dp), tint = IumrahColors.SystemBlue)
                }
            }
        }
        item {
            IumrahPressable(
                onClick = {
                    val uri = Uri.parse("geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(${Uri.encode(content.title)})")
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                cornerRadius = 24.dp,
                background = IumrahColors.SystemBlue,
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(CupertinoSymbol.Location, null, modifier = Modifier.size(19.dp), tint = Color.White)
                    Text(zt(language, "Открыть в картах", "Open in Maps", "Xaritada ochish", "Харитада очиш"), modifier = Modifier.padding(start = 8.dp), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PlaceGallery(place: ZiyaratPlace, expanded: Boolean) {
    if (place.images.isEmpty()) return
    val pager = rememberPagerState(pageCount = { place.images.size })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().height(if (expanded) 260.dp else 190.dp).clip(RoundedCornerShape(26.dp))) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index -> ZiyaratImageThumb(place.images[index], Modifier.fillMaxSize()) }
            Text("${pager.currentPage + 1} / ${place.images.size}", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .52f)).padding(horizontal = 9.dp, vertical = 6.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
        if (place.images.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(place.images) { index, image ->
                    ZiyaratImageThumb(image, Modifier.width(62.dp).height(48.dp).clip(RoundedCornerShape(12.dp)).border(if (index == pager.currentPage) 2.dp else 0.dp, if (index == pager.currentPage) IumrahColors.SystemBlue else Color.Transparent, RoundedCornerShape(12.dp)))
                }
            }
        }
    }
}

@Composable
internal fun ZiyaratImageThumb(image: ZiyaratImage?, modifier: Modifier) {
    val local = image?.url?.let(::localZiyaratDrawable)
    if (local != null) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(local), null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        AsyncImage(model = AppConfig.absoluteUrl(image?.url), contentDescription = null, modifier = modifier.background(Color(0xFFE9E9ED)), contentScale = ContentScale.Crop)
    }
}

private fun localZiyaratDrawable(raw: String): Int? = when (raw.removePrefix("asset:")) {
    "ZiyaratQuba1" -> R.drawable.ziyarat_quba_1
    "ZiyaratQuba2" -> R.drawable.ziyarat_quba_2
    "ZiyaratQuba3" -> R.drawable.ziyarat_quba_3
    "ZiyaratQuba4" -> R.drawable.ziyarat_quba_4
    "ZiyaratQuba5" -> R.drawable.ziyarat_quba_5
    else -> null
}

@Composable
private fun MetaCapsule(icon: CupertinoSymbol, text: String) {
    Row(Modifier.height(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
        Text(text, Modifier.padding(start = 6.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 16.sp, lineHeight = 22.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .84f))
    }
}

@Composable
private fun ZiyaratTabBar(language: AppLanguage, active: ZiyaratTab, onTab: (ZiyaratTab) -> Unit) {
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        ZiyaratTab.entries.forEach { tab ->
            val selected = active == tab
            Column(Modifier.weight(1f).fillMaxHeight().clickable { onTab(tab) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(tabIcon(tab), null, modifier = Modifier.size(21.dp), tint = if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = .70f))
                Text(tabTitle(language, tab), fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = if (selected) IumrahColors.SystemBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = .70f))
            }
        }
    }
}

private fun tabIcon(tab: ZiyaratTab): CupertinoSymbol = when (tab) {
    ZiyaratTab.JOURNEY -> CupertinoSymbol.Persons
    ZiyaratTab.PLACES -> CupertinoSymbol.Grid
    ZiyaratTab.ROUTE -> CupertinoSymbol.Route
    ZiyaratTab.MAP -> CupertinoSymbol.Location
}
private fun tabTitle(language: AppLanguage, tab: ZiyaratTab): String = when (tab) {
    ZiyaratTab.JOURNEY -> zt(language, "Поездка", "Journey", "Sayohat", "Саёҳат")
    ZiyaratTab.PLACES -> zt(language, "Места", "Places", "Joylar", "Жойлар")
    ZiyaratTab.ROUTE -> zt(language, "Маршрут", "Route", "Yo‘nalish", "Йўналиш")
    ZiyaratTab.MAP -> zt(language, "Карта", "Map", "Xarita", "Харита")
}

@Composable
private fun EmptyZiyaratOverlay(language: AppLanguage, modifier: Modifier) {
    Column(modifier.shadow(12.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(CupertinoSymbol.Location, null, modifier = Modifier.size(24.dp))
        Text(zt(language, "Пока нет мест", "No places yet", "Hozircha joylar yo‘q", "Ҳозирча жойлар йўқ"), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(zt(language, "Опубликованные в iumrah Business точки появятся здесь автоматически.", "Places published in iumrah Business will appear here automatically.", "iumrah Business’da chop etilgan joylar bu yerda avtomatik paydo bo‘ladi.", "iumrah Business’да чоп этилган жойлар бу ерда автоматик пайдо бўлади."), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), textAlign = TextAlign.Center)
    }
}

private fun visitText(language: AppLanguage, raw: String): String = when (raw) {
    "enter" -> zt(language, "Заходим", "Enter", "Kiramiz", "Кирамиз")
    "view" -> zt(language, "Осмотр", "View", "Ko‘ramiz", "Кўрамиз")
    "pass" -> zt(language, "Проездом", "Pass by", "Yo‘lda", "Йўлда")
    else -> zt(language, "Остановка", "Stop", "To‘xtash", "Тўхташ")
}
private fun categoryTitle(language: AppLanguage, raw: String): String = when (raw) {
    "mosque" -> zt(language, "Мечеть", "Mosque", "Masjid", "Масжид")
    "mountain" -> zt(language, "Гора", "Mountain", "Tog‘", "Тоғ")
    "garden" -> zt(language, "Сад", "Garden", "Bog‘", "Боғ")
    "cemetery" -> zt(language, "Кладбище", "Cemetery", "Qabriston", "Қабристон")
    "restaurant" -> zt(language, "Ресторан", "Restaurant", "Restoran", "Ресторан")
    "museum" -> zt(language, "Музей", "Museum", "Muzey", "Музей")
    else -> zt(language, "Место", "Place", "Joy", "Жой")
}
private fun categoryIcon(raw: String): CupertinoSymbol = when (raw) {
    "mosque", "museum" -> CupertinoSymbol.Building
    "garden", "cemetery" -> CupertinoSymbol.LeafFill
    "restaurant" -> CupertinoSymbol.ForkKnife
    else -> CupertinoSymbol.Location
}
private fun cityTitle(language: AppLanguage, city: JourneyCity): String = if (city == JourneyCity.MADINAH) zt(language, "Медина", "Madinah", "Madina", "Мадина") else zt(language, "Мекка", "Makkah", "Makka", "Макка")
private fun zt(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

/**
 * Booking-facing Ziyarats catalog. Starts from the same offline seed as iOS.
 * The standalone Ziyarats entry opens the safer on-demand map instead of an eager map.
 */
@Composable
fun BookingZiyaratCatalogScreen(
    language: AppLanguage,
    chrome: AppChromeStore,
    service: ZiyaratService = remember { ZiyaratService() },
) {
    var city by remember { mutableStateOf(JourneyCity.MAKKAH) }
    var route by remember(city) { mutableStateOf(ZiyaratFallbackCatalog.route(if (city == JourneyCity.MAKKAH) "Makkah" else "Madinah")) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(city) {
        loading = true
        route = service.route(if (city == JourneyCity.MAKKAH) "Makkah" else "Madinah")
        loading = false
    }

    val places = remember(route) { route.places.sortedBy { it.routeOrder } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(54.dp)) {
            IumrahPressable(
                onClick = chrome::back,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(38.dp),
                cornerRadius = 19.dp,
                background = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.ChevronLeft, null, Modifier.size(15.dp))
                }
            }
            Text(
                "iumrah Ziyarats",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f))
                .padding(3.dp),
        ) {
            listOf(JourneyCity.MAKKAH, JourneyCity.MADINAH).forEach { item ->
                val active = item == city
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { city = item },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (item == JourneyCity.MAKKAH) zt(language, "Мекка", "Makkah", "Makka", "Макка")
                        else zt(language, "Медина", "Madinah", "Madina", "Мадина"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        Column(
            Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                zt(language, "Места Вашей программы", "Places in your program", "Dasturingizdagi joylar", "Дастурингиздаги жойлар"),
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.5).sp,
            )
            Text(
                zt(
                    language,
                    "Листайте карточки — фото и подробности каждого места уже собраны здесь.",
                    "Swipe through the cards — photos and details for every stop are already here.",
                    "Kartalarni suring — har bir joyning fotosi va tafsilotlari shu yerda.",
                    "Карталарни суринг — ҳар бир жойнинг фотоси ва тафсилотлари шу ерда.",
                ),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
            )
        }

        when {
            loading && places.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            places.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(CupertinoSymbol.Map, null, Modifier.size(30.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                    Text(
                        zt(language, "Места пока не опубликованы", "No places published yet", "Joylar hali e’lon qilinmagan", "Жойлар ҳали эълон қилинмаган"),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                    )
                }
            }
            else -> {
                val pager = rememberPagerState(pageCount = { places.size })
                LaunchedEffect(city, places.size) {
                    if (places.isNotEmpty() && pager.currentPage != 0) pager.scrollToPage(0)
                }
                HorizontalPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 12.dp,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                ) { page ->
                    BookingZiyaratCarouselCard(language, places[page])
                }
            }
        }
    }
}

@Composable
private fun BookingZiyaratCarouselCard(language: AppLanguage, place: ZiyaratPlace) {
    val content = place.localized(language)
    val primary = place.images.sortedBy { it.position }.firstOrNull()
    val shape = RoundedCornerShape(30.dp)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 18.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f), shape),
        contentPadding = PaddingValues(bottom = 22.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                ZiyaratImageThumb(primary, Modifier.fillMaxSize())
                if (place.images.size > 1) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .height(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = .52f))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(CupertinoSymbol.Photo, null, Modifier.size(13.dp), Color.White)
                        Text(place.images.size.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Column(
                Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color.Black), contentAlignment = Alignment.Center) {
                        Text(place.routeOrder.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        content.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-.35).sp,
                    )
                }

                if (place.titleArabic.isNotBlank()) {
                    Text(place.titleArabic, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaCapsule(CupertinoSymbol.Clock, zt(language, "${place.durationMinutes} мин", "${place.durationMinutes} min", "${place.durationMinutes} daq", "${place.durationMinutes} дақ"))
                    MetaCapsule(categoryIcon(place.category), categoryTitle(language, place.category))
                }

                Text(content.shortDescription, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)

                if (content.longDescription.isNotBlank()) {
                    InfoSection(zt(language, "О месте", "About", "Joy haqida", "Жой ҳақида"), content.longDescription)
                }

                if (content.interestingFacts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(zt(language, "Интересные факты", "Interesting facts", "Qiziqarli faktlar", "Қизиқарли фактлар"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        content.interestingFacts.forEach { fact ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(IumrahColors.SystemBlue))
                                Text(fact, modifier = Modifier.weight(1f), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f))
                            }
                        }
                    }
                }

                if (content.visitNotes.isNotBlank()) {
                    InfoSection(zt(language, "Во время посещения", "During the visit", "Tashrif paytida", "Ташриф пайтида"), content.visitNotes)
                }
            }
        }
    }
}
