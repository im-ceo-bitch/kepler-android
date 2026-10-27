@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.trip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import com.iumrah.beta.ui.map.IumrahMapLibreView
import com.iumrah.beta.ui.map.IumrahMapStyle
import com.iumrah.beta.ui.map.rememberIumrahMapController
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AirportMapBootstrapCatalog
import com.iumrah.beta.data.flight.AirportSearchService
import com.iumrah.beta.models.flight.Airport
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** iOS AirportSelectorButton + AirportPickerView parity for the Generator. */
@Composable
fun ConfiguratorAirportSelector(
    language: AppLanguage,
    service: AirportSearchService,
    airport: Airport?,
    fallbackCode: String,
    onSelect: (Airport) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPicker by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locating = true
            scope.launch {
                resolveNearestConfiguratorAirport(context, service)?.let(onSelect)
                locating = false
            }
        }
    }

    fun locate() {
        if (locating) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            locating = true
            scope.launch {
                resolveNearestConfiguratorAirport(context, service)?.let(onSelect)
                locating = false
            }
        } else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(generatorRaisedColor()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable { showPicker = true }.padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .13f)), contentAlignment = Alignment.Center) {
                Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(17.dp), Color(0xFF007AFF))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(airportTitle(language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                if (airport != null) {
                    Text(airport.compactTitle, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(airport.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    Text(fallbackCode.ifBlank { airportSearchTitle(language) }.uppercase(), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        IumrahPressable(onClick = ::locate, modifier = Modifier.size(42.dp), cornerRadius = 99.dp, background = Color.Transparent) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (locating) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else Icon(CupertinoSymbol.Location, airportTitle(language), Modifier.size(15.dp))
            }
        }
    }

    if (showPicker) {
        ConfiguratorAirportPickerSheet(
            language = language,
            service = service,
            current = airport,
            fallbackCode = fallbackCode,
            onDismiss = { showPicker = false },
            onSelect = { selected -> onSelect(selected); showPicker = false },
        )
    }
}

@Composable
private fun ConfiguratorAirportPickerSheet(
    language: AppLanguage,
    service: AirportSearchService,
    current: Airport?,
    fallbackCode: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf(current?.iata ?: fallbackCode) }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var showGlobe by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length >= 2) {
            loading = true
            results = runCatching { service.search(clean, 12) }.getOrDefault(emptyList())
            loading = false
        } else results = emptyList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = generatorPageColor(),
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        dragHandle = null,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 34.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(airportSearchTitle(language), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
                    Text(airportSearchHint(language), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IumrahPressable(onClick = { showGlobe = true }, modifier = Modifier.size(42.dp), cornerRadius = 99.dp, background = generatorRaisedColor()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Globe, null, Modifier.size(17.dp)) }
                    }
                    IumrahPressable(onClick = onDismiss, modifier = Modifier.size(42.dp), cornerRadius = 99.dp, background = generatorRaisedColor()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Close, null, Modifier.size(15.dp)) }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(18.dp)).background(generatorRaisedColor()).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(CupertinoSymbol.Location, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium),
                    decorationBox = { inner ->
                        if (query.isBlank()) Text(airportSearchPlaceholder(language), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                        inner()
                    },
                )
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            if (query.isBlank() || results.isEmpty()) {
                IumrahPressable(
                    onClick = { showGlobe = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    cornerRadius = 22.dp,
                    background = generatorRaisedColor(),
                    shadowElevation = 0.dp,
                ) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(CupertinoSymbol.Globe, null, Modifier.size(17.dp), Color(0xFF007AFF))
                        Spacer(Modifier.width(8.dp))
                        Text(airportMapTitle(language), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEach { airport ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect(airport) }.padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                            Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(17.dp), Color(0xFF007AFF))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(airport.compactTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(airport.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (airport.iata.equals(current?.iata ?: fallbackCode, true)) Icon(CupertinoSymbol.Checkmark, null, Modifier.size(17.dp), Color(0xFF34C759))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showGlobe) {
        IumrahAirportMapDialog(
            language = language,
            service = service,
            current = current,
            fallbackCode = fallbackCode,
            onDismiss = { showGlobe = false },
            onSelect = { selected ->
                showGlobe = false
                onSelect(selected)
            },
        )
    }
}

@Composable
fun IumrahAirportMapDialog(
    language: AppLanguage,
    service: AirportSearchService,
    current: Airport?,
    fallbackCode: String,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var airports by remember(current) {
        mutableStateOf((AirportMapBootstrapCatalog.airports + listOfNotNull(current)).distinctBy { it.iata.uppercase() })
    }
    var selected by remember(current, fallbackCode) {
        mutableStateOf(current ?: AirportMapBootstrapCatalog.airport(fallbackCode))
    }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var resetMapNonce by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    fun resolvePoint(point: LatLng) {
        if (resolving) return
        resolving = true
        scope.launch {
            val airport = resolveAirportAtCoordinate(context, service, point.latitude, point.longitude)
            if (airport != null) {
                selected = airport
                airports = (airports + airport).distinctBy { it.iata.uppercase() }
            }
            resolving = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05070C))) {
            AirportMapLibreSurface(
                airports = airports,
                selected = selected,
                onSelect = { selected = it },
                onMapTap = ::resolvePoint,
                resetNonce = resetMapNonce,
                modifier = Modifier.fillMaxSize(),
            )

            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.Black.copy(alpha = .42f))
                        .border(.8.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(25.dp))
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IumrahPressable(
                        onClick = {
                            selected = current
                            resetMapNonce += 1
                        },
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 22.dp,
                        background = Color.White.copy(alpha = .10f),
                        shadowElevation = 0.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(CupertinoSymbol.Globe, null, Modifier.size(18.dp), Color.White)
                        }
                    }
                    Text(
                        airportMapTitle(language),
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IumrahPressable(
                        onClick = onDismiss,
                        modifier = Modifier.height(44.dp),
                        cornerRadius = 22.dp,
                        background = Color.White.copy(alpha = .10f),
                        shadowElevation = 0.dp,
                    ) {
                        Box(Modifier.height(44.dp).padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
                            Text(airportMapClose(language), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (loading || resolving) {
                    Row(
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp).clip(CircleShape).background(Color.Black.copy(alpha = .50f)).padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(if (resolving) airportMapResolving(language) else airportMapLoading(language), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    airports.forEach { airport ->
                        val active = selected?.iata.equals(airport.iata, true)
                        IumrahPressable(
                            onClick = { selected = airport },
                            modifier = Modifier.height(42.dp),
                            cornerRadius = 21.dp,
                            background = if (active) Color.White else Color.Black.copy(alpha = .48f),
                            shadowElevation = 0.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(airport.iata.uppercase(), color = if (active) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(airport.city, color = if (active) Color.Black.copy(alpha = .62f) else Color.White.copy(alpha = .72f), fontSize = 11.sp)
                            }
                        }
                    }
                }

                val choice = selected
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = .58f)).border(.8.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(28.dp)).padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (choice != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF0A84FF)), contentAlignment = Alignment.Center) {
                                Text(choice.iata.uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(choice.city, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text(choice.subtitle, color = Color.White.copy(alpha = .68f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        IumrahPressable(
                            onClick = { onSelect(choice) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            cornerRadius = 19.dp,
                            background = Color.White,
                            shadowElevation = 0.dp,
                        ) {
                            Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(airportMapChoose(language, choice.iata), modifier = Modifier.weight(1f), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(17.dp), Color.Black)
                            }
                        }
                    } else {
                        Text(airportMapTapHint(language), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(airportGlobeZoomHint(language), color = Color.White.copy(alpha = .66f), fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AirportMapLibreSurface(
    airports: List<Airport>,
    selected: Airport?,
    onSelect: (Airport) -> Unit,
    onMapTap: (LatLng) -> Unit,
    resetNonce: Int,
    modifier: Modifier = Modifier,
) {
    val controller = rememberIumrahMapController()
    val map = controller.map
    val pinPositions = remember { mutableStateMapOf<String, IntOffset>() }

    fun updatePins(activeMap: MapLibreMap? = map) {
        val resolvedMap = activeMap ?: return
        airports.forEach { airport ->
            val p = resolvedMap.projection.toScreenLocation(LatLng(airport.lat, airport.lon))
            pinPositions[airport.iata.uppercase()] = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
    }

    LaunchedEffect(map) {
        controller.setStyle(IumrahMapStyle.STANDARD)
    }

    LaunchedEffect(map, controller.styleEpoch) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0) return@LaunchedEffect
        val initial = selected?.let { LatLng(it.lat, it.lon) } ?: LatLng(28.0, 52.0)
        val zoom = if (selected != null) 5.4 else 3.2
        activeMap.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(initial).zoom(zoom).build(),
            ),
        )
        updatePins(activeMap)
    }

    LaunchedEffect(map, selected?.iata) {
        val activeMap = map ?: return@LaunchedEffect
        val airport = selected ?: return@LaunchedEffect
        activeMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(airport.lat, airport.lon))
                    .zoom(maxOf(activeMap.cameraPosition.zoom, 6.2))
                    .build(),
            ),
            520,
        )
    }

    LaunchedEffect(map, controller.styleEpoch, resetNonce) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0 || resetNonce <= 0) return@LaunchedEffect
        val center = currentMapCenter(selected)
        activeMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder().target(center).zoom(if (selected != null) 5.4 else 3.2).build(),
            ),
            650,
        )
    }

    DisposableEffect(map, airports) {
        val activeMap = map
        if (activeMap == null) return@DisposableEffect onDispose { }
        val moveListener = MapLibreMap.OnCameraMoveListener { updatePins(activeMap) }
        val idleListener = MapLibreMap.OnCameraIdleListener { updatePins(activeMap) }
        val clickListener = MapLibreMap.OnMapClickListener { point ->
            onMapTap(point)
            true
        }
        activeMap.addOnCameraMoveListener(moveListener)
        activeMap.addOnCameraIdleListener(idleListener)
        activeMap.addOnMapClickListener(clickListener)
        updatePins(activeMap)
        onDispose {
            activeMap.removeOnCameraMoveListener(moveListener)
            activeMap.removeOnCameraIdleListener(idleListener)
            activeMap.removeOnMapClickListener(clickListener)
        }
    }

    Box(modifier) {
        IumrahMapLibreView(controller, Modifier.fillMaxSize())
        airports.forEach { airport ->
            val point = pinPositions[airport.iata.uppercase()] ?: return@forEach
            val active = selected?.iata.equals(airport.iata, true)
            Column(
                Modifier
                    .offset { IntOffset(point.x - if (active) 25.dp.roundToPx() else 19.dp.roundToPx(), point.y - if (active) 60.dp.roundToPx() else 38.dp.roundToPx()) }
                    .clickable { onSelect(airport) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (active) {
                    Text(
                        airport.iata.uppercase(),
                        modifier = Modifier.padding(bottom = 5.dp).clip(CircleShape).background(Color.White).padding(horizontal = 9.dp, vertical = 5.dp),
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    Modifier.size(if (active) 42.dp else 34.dp).clip(RoundedCornerShape(if (active) 14.dp else 12.dp)).background(if (active) Color(0xFF007AFF) else Color.Black.copy(alpha = .78f)).border(2.dp, Color.White, RoundedCornerShape(if (active) 14.dp else 12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(if (active) 19.dp else 15.dp), Color.White)
                }
            }
        }
    }
}

private fun currentMapCenter(airport: Airport?): LatLng =
    airport?.let { LatLng(it.lat, it.lon) } ?: LatLng(28.0, 52.0)

private suspend fun resolveAirportAtCoordinate(
    context: Context,
    service: AirportSearchService,
    latitude: Double,
    longitude: Double,
): Airport? {
    val queries = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val place = Geocoder(context, Locale.ENGLISH).getFromLocation(latitude, longitude, 1)?.firstOrNull()
            listOfNotNull(place?.locality, place?.subAdminArea, place?.adminArea, place?.countryName)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy { it.lowercase(Locale.ENGLISH) }
        }.getOrDefault(emptyList())
    }
    val candidates = linkedMapOf<String, Airport>()
    for (query in queries) {
        runCatching { service.search(query, 12) }.getOrDefault(emptyList()).forEach { candidates[it.iata.uppercase()] = it }
    }
    return candidates.values.map { airport ->
        val distance = FloatArray(1)
        Location.distanceBetween(latitude, longitude, airport.lat, airport.lon, distance)
        airport to distance[0].toDouble()
    }.filter { it.second <= 120_000.0 }.minByOrNull { it.second }?.first
}

@SuppressLint("MissingPermission")
private suspend fun resolveNearestConfiguratorAirport(context: Context, service: AirportSearchService): Airport? {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    val location = withContext(Dispatchers.IO) {
        val known = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        known ?: withTimeoutOrNull(3500) { requestConfiguratorLocation(manager) }
    } ?: return null
    val queries = withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val place = Geocoder(context, Locale.ENGLISH).getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
            listOfNotNull(place?.locality, place?.subAdminArea, place?.adminArea, place?.countryName).map(String::trim).filter(String::isNotEmpty).distinctBy { it.lowercase(Locale.ENGLISH) }
        }.getOrDefault(emptyList())
    }
    val candidates = linkedMapOf<String, Airport>()
    for (query in queries) runCatching { service.search(query, 12) }.getOrDefault(emptyList()).forEach { candidates[it.iata.uppercase()] = it }
    return candidates.values.map { airport ->
        val distance = FloatArray(1)
        Location.distanceBetween(location.latitude, location.longitude, airport.lat, airport.lon, distance)
        airport to distance[0].toDouble()
    }.filter { it.second <= 250_000.0 }.minByOrNull { it.second }?.first
}

@SuppressLint("MissingPermission")
private suspend fun requestConfiguratorLocation(manager: LocationManager): Location? = suspendCancellableCoroutine { continuation ->
    val provider = when {
        runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) -> LocationManager.NETWORK_PROVIDER
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> LocationManager.GPS_PROVIDER
        else -> null
    }
    if (provider == null) { continuation.resume(null); return@suspendCancellableCoroutine }
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            manager.removeUpdates(this)
            if (continuation.isActive) continuation.resume(location)
        }
        @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }
    continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
    runCatching { manager.requestSingleUpdate(provider, listener, Looper.getMainLooper()) }
        .onFailure { if (continuation.isActive) continuation.resume(null) }
}


private fun airportMapTapHint(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Коснитесь аэропорта на карте"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Tap an airport on the map"; AppLanguage.UZBEK -> "Xaritadagi aeroportni bosing"; AppLanguage.UZBEK_CYRILLIC -> "Харитадаги аэропортни босинг"
}
private fun airportMapResolving(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Определяем аэропорт…"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Resolving airport…"; AppLanguage.UZBEK -> "Aeroport aniqlanmoqda…"; AppLanguage.UZBEK_CYRILLIC -> "Аэропорт аниқланмоқда…"
}
private fun airportMapLoading(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Загружаем аэропорты…"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Loading airports…"; AppLanguage.UZBEK -> "Aeroportlar yuklanmoqda…"; AppLanguage.UZBEK_CYRILLIC -> "Аэропортлар юкланмоқда…"
}

private fun airportMapTitle(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Выбрать на карте"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Choose on map"; AppLanguage.UZBEK -> "Xaritadan tanlash"; AppLanguage.UZBEK_CYRILLIC -> "Харитадан танлаш"
}
private fun airportGlobeHintTitle(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Выберите аэропорт на карте"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Choose an airport on the map"; AppLanguage.UZBEK -> "Xaritadan aeroport tanlang"; AppLanguage.UZBEK_CYRILLIC -> "Харитадан аэропорт танланг"
}
private fun airportGlobeZoomHint(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Перемещайте и масштабируйте карту. Можно нажать прямо на нужную точку."; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Pan and zoom the map, then tap the airport location."; AppLanguage.UZBEK -> "Xaritani suring va kattalashtiring, so‘ng aeroport joyini bosing."; AppLanguage.UZBEK_CYRILLIC -> "Харитани суринг ва катталаштиринг, сўнг аэропорт жойини босинг."
}
private fun airportMapClose(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Закрыть"
    AppLanguage.UZBEK -> "Yopish"
    AppLanguage.UZBEK_CYRILLIC -> "Ёпиш"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Close"
}

private fun airportMapChoose(language: AppLanguage, code: String) = when (language) {
    AppLanguage.RUSSIAN -> "Выбрать $code"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Choose $code"; AppLanguage.UZBEK -> "$code ni tanlash"; AppLanguage.UZBEK_CYRILLIC -> "$code ни танлаш"
}

private fun airportTitle(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Аэропорт вылета"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Departure airport"; AppLanguage.UZBEK -> "Jo‘nash aeroporti"; AppLanguage.UZBEK_CYRILLIC -> "Жўнаш аэропорти"
}
private fun airportSearchTitle(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Выберите аэропорт"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Choose an airport"; AppLanguage.UZBEK -> "Aeroportni tanlang"; AppLanguage.UZBEK_CYRILLIC -> "Аэропортни танланг"
}
private fun airportSearchHint(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Найдите город или код аэропорта"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Search by city or airport code"; AppLanguage.UZBEK -> "Shahar yoki aeroport kodi bo‘yicha qidiring"; AppLanguage.UZBEK_CYRILLIC -> "Шаҳар ёки аэропорт коди бўйича қидиринг"
}
private fun airportSearchPlaceholder(language: AppLanguage) = when (language) {
    AppLanguage.RUSSIAN -> "Например, TAS или Ташкент"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "For example, TAS or Tashkent"; AppLanguage.UZBEK -> "Masalan, TAS yoki Toshkent"; AppLanguage.UZBEK_CYRILLIC -> "Масалан, TAS ёки Тошкент"
}

private enum class AirportRouteSelectionMode { ORIGIN, DESTINATION }

/**
 * Android parity for iOS AirportRouteMapPickerView.
 * One full-screen map selects both departure and arrival airport and keeps the route visible.
 */
@Composable
fun IumrahAirportRouteMapDialog(
    language: AppLanguage,
    service: AirportSearchService,
    origin: Airport?,
    originCode: String,
    destination: Airport?,
    destinationCode: String,
    onDismiss: () -> Unit,
    onCommit: (Airport, Airport) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var airports by remember(origin, destination) {
        mutableStateOf((AirportMapBootstrapCatalog.airports + listOfNotNull(origin, destination)).distinctBy { it.iata.uppercase() })
    }
    var selectedOrigin by remember(origin, originCode) { mutableStateOf(origin ?: AirportMapBootstrapCatalog.airport(originCode)) }
    var selectedDestination by remember(destination, destinationCode) { mutableStateOf(destination ?: AirportMapBootstrapCatalog.airport(destinationCode)) }
    var mode by remember { mutableStateOf(if (selectedOrigin == null) AirportRouteSelectionMode.ORIGIN else AirportRouteSelectionMode.DESTINATION) }
    var loading by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(AirportRouteSelectionMode.ORIGIN) }

    fun choose(airport: Airport) {
        when (mode) {
            AirportRouteSelectionMode.ORIGIN -> {
                if (!selectedDestination?.iata.equals(airport.iata, true)) selectedOrigin = airport
                mode = AirportRouteSelectionMode.DESTINATION
            }
            AirportRouteSelectionMode.DESTINATION -> {
                if (!selectedOrigin?.iata.equals(airport.iata, true)) selectedDestination = airport
            }
        }
    }

    fun resolvePoint(point: LatLng) {
        if (resolving) return
        resolving = true
        scope.launch {
            val airport = resolveAirportAtCoordinate(context, service, point.latitude, point.longitude)
            if (airport != null) {
                airports = (airports + airport).distinctBy { it.iata.uppercase() }
                choose(airport)
            }
            resolving = false
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF05070C))) {
            AirportRouteMapLibreSurface(
                airports = airports,
                origin = selectedOrigin,
                destination = selectedDestination,
                mode = mode,
                onSelect = ::choose,
                onMapTap = ::resolvePoint,
                modifier = Modifier.fillMaxSize(),
            )

            Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.Black.copy(alpha = .48f))
                        .border(.8.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(26.dp)).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IumrahPressable(onClick = onDismiss, modifier = Modifier.size(44.dp), cornerRadius = 22.dp, background = Color.White.copy(alpha = .10f), shadowElevation = 0.dp) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Close, null, Modifier.size(16.dp), Color.White) }
                    }
                    Text(routeMapTitle(language), Modifier.weight(1f), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    IumrahPressable(
                        onClick = {
                            val old = selectedOrigin
                            selectedOrigin = selectedDestination
                            selectedDestination = old
                        },
                        modifier = Modifier.size(44.dp), cornerRadius = 22.dp, background = Color.White.copy(alpha = .10f), shadowElevation = 0.dp,
                    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.ArrowLeftRight, null, Modifier.size(18.dp), Color.White) } }
                }

                Spacer(Modifier.weight(1f))

                if (loading || resolving) {
                    Row(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = .52f)).padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(if (resolving) airportMapResolving(language) else airportMapLoading(language), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = .62f))
                        .border(.8.dp, Color.White.copy(alpha = .13f), RoundedCornerShape(30.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RouteAirportSelectorRow(
                        language = language,
                        title = routeMapFrom(language),
                        airport = selectedOrigin,
                        active = mode == AirportRouteSelectionMode.ORIGIN,
                        onClick = { mode = AirportRouteSelectionMode.ORIGIN },
                        onSearch = { searchMode = AirportRouteSelectionMode.ORIGIN; searchOpen = true },
                    )
                    RouteAirportSelectorRow(
                        language = language,
                        title = routeMapTo(language),
                        airport = selectedDestination,
                        active = mode == AirportRouteSelectionMode.DESTINATION,
                        onClick = { mode = AirportRouteSelectionMode.DESTINATION },
                        onSearch = { searchMode = AirportRouteSelectionMode.DESTINATION; searchOpen = true },
                    )
                    val from = selectedOrigin
                    val to = selectedDestination
                    IumrahPressable(
                        onClick = { if (from != null && to != null && !from.iata.equals(to.iata, true)) onCommit(from, to) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        cornerRadius = 19.dp,
                        background = if (from != null && to != null && !from.iata.equals(to.iata, true)) Color.White else Color.White.copy(alpha = .25f),
                        shadowElevation = 0.dp,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(routeMapUse(language), Modifier.weight(1f), color = if (from != null && to != null) Color.Black else Color.White.copy(alpha = .65f), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Icon(CupertinoSymbol.ArrowRight, null, Modifier.size(17.dp), if (from != null && to != null) Color.Black else Color.White.copy(alpha = .65f))
                        }
                    }
                }
            }
        }
    }

    if (searchOpen) {
        RouteAirportSearchSheet(
            language = language,
            service = service,
            onDismiss = { searchOpen = false },
            onSelect = { airport ->
                airports = (airports + airport).distinctBy { it.iata.uppercase() }
                if (searchMode == AirportRouteSelectionMode.ORIGIN) {
                    if (!selectedDestination?.iata.equals(airport.iata, true)) selectedOrigin = airport
                    mode = AirportRouteSelectionMode.DESTINATION
                } else if (!selectedOrigin?.iata.equals(airport.iata, true)) selectedDestination = airport
                searchOpen = false
            },
        )
    }
}

@Composable
private fun RouteAirportSelectorRow(
    language: AppLanguage,
    title: String,
    airport: Airport?,
    active: Boolean,
    onClick: () -> Unit,
    onSearch: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(if (active) Color.White.copy(alpha = .13f) else Color.White.copy(alpha = .07f))
            .border(.8.dp, if (active) Color(0xFF0A84FF).copy(alpha = .85f) else Color.White.copy(alpha = .08f), shape)
            .clickable(onClick = onClick).padding(start = 13.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(if (active) Color(0xFF0A84FF) else Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            Icon(if (title == routeMapFrom(language)) CupertinoSymbol.AirplaneTakeoff else CupertinoSymbol.AirplaneLand, null, Modifier.size(17.dp), Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), color = Color.White.copy(alpha = .55f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(airport?.let { "${it.iata.uppercase()} · ${it.city}" } ?: routeMapChooseAirport(language), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IumrahPressable(onClick = onSearch, modifier = Modifier.size(38.dp), cornerRadius = 19.dp, background = Color.White.copy(alpha = .08f), shadowElevation = 0.dp) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Menu, null, Modifier.size(15.dp), Color.White) }
        }
    }
}

@Composable
private fun RouteAirportSearchSheet(
    language: AppLanguage,
    service: AirportSearchService,
    onDismiss: () -> Unit,
    onSelect: (Airport) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Airport>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        if (query.trim().length >= 2) {
            loading = true
            results = runCatching { service.search(query.trim(), 12) }.getOrDefault(emptyList())
            loading = false
        } else results = emptyList()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = generatorPageColor(), dragHandle = null, shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(airportSearchTitle(language), Modifier.weight(1f), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                IumrahPressable(onClick = onDismiss, modifier = Modifier.size(40.dp), cornerRadius = 20.dp, background = generatorRaisedColor()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Close, null, Modifier.size(14.dp)) } }
            }
            Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(generatorRaisedColor()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.Menu, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
                Spacer(Modifier.width(8.dp))
                BasicTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp), decorationBox = { inner -> Box { if (query.isBlank()) Text(airportSearchHint(language), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f)); inner() } })
                if (loading) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
            }
            results.forEach { airport ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { onSelect(airport) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF007AFF).copy(alpha = .12f)), contentAlignment = Alignment.Center) { Icon(CupertinoSymbol.Airplane, null, Modifier.size(16.dp), Color(0xFF007AFF)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(airport.compactTitle, fontWeight = FontWeight.Bold); Text(airport.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun AirportRouteMapLibreSurface(
    airports: List<Airport>,
    origin: Airport?,
    destination: Airport?,
    mode: AirportRouteSelectionMode,
    onSelect: (Airport) -> Unit,
    onMapTap: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = rememberIumrahMapController()
    val map = controller.map
    val pinPositions = remember { mutableStateMapOf<String, IntOffset>() }

    fun updatePins(activeMap: MapLibreMap? = map) {
        val resolvedMap = activeMap ?: return
        airports.forEach { airport ->
            val p = resolvedMap.projection.toScreenLocation(LatLng(airport.lat, airport.lon))
            pinPositions[airport.iata.uppercase()] = IntOffset(p.x.roundToInt(), p.y.roundToInt())
        }
    }

    LaunchedEffect(map) { controller.setStyle(IumrahMapStyle.STANDARD) }
    LaunchedEffect(map, controller.styleEpoch, origin?.iata, destination?.iata) {
        val activeMap = map ?: return@LaunchedEffect
        if (controller.styleEpoch <= 0) return@LaunchedEffect
        val points = listOfNotNull(origin, destination)
        val center = when (points.size) {
            2 -> LatLng((points[0].lat + points[1].lat) / 2.0, (points[0].lon + points[1].lon) / 2.0)
            1 -> LatLng(points[0].lat, points[0].lon)
            else -> LatLng(30.0, 48.0)
        }
        activeMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(center).zoom(if (points.size == 2) 3.8 else 5.2).build()), 550)
        updatePins(activeMap)
    }
    DisposableEffect(map, airports) {
        val active = map ?: return@DisposableEffect onDispose { }
        val move = MapLibreMap.OnCameraMoveListener { updatePins(active) }
        val idle = MapLibreMap.OnCameraIdleListener { updatePins(active) }
        val click = MapLibreMap.OnMapClickListener { point -> onMapTap(point); true }
        active.addOnCameraMoveListener(move); active.addOnCameraIdleListener(idle); active.addOnMapClickListener(click); updatePins(active)
        onDispose { active.removeOnCameraMoveListener(move); active.removeOnCameraIdleListener(idle); active.removeOnMapClickListener(click) }
    }

    Box(modifier) {
        IumrahMapLibreView(controller, Modifier.fillMaxSize())
        val fromPoint = origin?.iata?.uppercase()?.let(pinPositions::get)
        val toPoint = destination?.iata?.uppercase()?.let(pinPositions::get)
        if (fromPoint != null && toPoint != null) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val start = androidx.compose.ui.geometry.Offset(fromPoint.x.toFloat(), fromPoint.y.toFloat())
                val end = androidx.compose.ui.geometry.Offset(toPoint.x.toFloat(), toPoint.y.toFloat())
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(start.x, start.y)
                    val lift = kotlin.math.min(220f, kotlin.math.abs(end.x - start.x) * .20f + 70f)
                    quadraticBezierTo((start.x + end.x) / 2f, kotlin.math.min(start.y, end.y) - lift, end.x, end.y)
                }
                drawPath(path, Color.White.copy(alpha = .28f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(path, Color(0xFF0A84FF), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }
        }
        airports.forEach { airport ->
            val point = pinPositions[airport.iata.uppercase()] ?: return@forEach
            val isOrigin = origin?.iata.equals(airport.iata, true)
            val isDestination = destination?.iata.equals(airport.iata, true)
            val active = (mode == AirportRouteSelectionMode.ORIGIN && isOrigin) || (mode == AirportRouteSelectionMode.DESTINATION && isDestination)
            Column(
                Modifier.offset { IntOffset(point.x - 20.dp.roundToPx(), point.y - 40.dp.roundToPx()) }.clickable { onSelect(airport) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (isOrigin || isDestination) Text(airport.iata.uppercase(), Modifier.padding(bottom = 4.dp).clip(CircleShape).background(Color.White).padding(horizontal = 8.dp, vertical = 4.dp), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Box(Modifier.size(if (isOrigin || isDestination) 40.dp else 30.dp).clip(RoundedCornerShape(12.dp)).background(when { active -> Color(0xFF34C759); isOrigin || isDestination -> Color(0xFF0A84FF); else -> Color.Black.copy(alpha = .76f) }).border(1.7.dp, Color.White, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(if (isDestination) CupertinoSymbol.AirplaneLand else CupertinoSymbol.AirplaneTakeoff, null, Modifier.size(if (isOrigin || isDestination) 18.dp else 13.dp), Color.White)
                }
            }
        }
    }
}

private fun routeMapTitle(language: AppLanguage) = when (language) { AppLanguage.RUSSIAN -> "Маршрут на карте"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Route on map"; AppLanguage.UZBEK -> "Xaritadagi yo‘nalish"; AppLanguage.UZBEK_CYRILLIC -> "Харитадаги йўналиш" }
private fun routeMapFrom(language: AppLanguage) = when (language) { AppLanguage.RUSSIAN -> "Откуда"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "From"; AppLanguage.UZBEK -> "Qayerdan"; AppLanguage.UZBEK_CYRILLIC -> "Қаердан" }
private fun routeMapTo(language: AppLanguage) = when (language) { AppLanguage.RUSSIAN -> "Куда"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "To"; AppLanguage.UZBEK -> "Qayerga"; AppLanguage.UZBEK_CYRILLIC -> "Қаерга" }
private fun routeMapUse(language: AppLanguage) = when (language) { AppLanguage.RUSSIAN -> "Использовать маршрут"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Use route"; AppLanguage.UZBEK -> "Yo‘nalishni tanlash"; AppLanguage.UZBEK_CYRILLIC -> "Йўналишни танлаш" }
private fun routeMapChooseAirport(language: AppLanguage) = when (language) { AppLanguage.RUSSIAN -> "Выберите аэропорт"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Choose airport"; AppLanguage.UZBEK -> "Aeroportni tanlang"; AppLanguage.UZBEK_CYRILLIC -> "Аэропортни танланг" }
