package com.iumrah.beta.ui.ziyarats

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.ziyarat.ZiyaratFallbackCatalog
import com.iumrah.beta.data.ziyarat.ZiyaratService
import com.iumrah.beta.models.ziyarat.ZiyaratImage
import com.iumrah.beta.models.ziyarat.ZiyaratPlace
import com.iumrah.beta.models.ziyarat.ZiyaratRoute
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.map.IumrahMapLibreView
import com.iumrah.beta.ui.map.IumrahMapStyle
import com.iumrah.beta.ui.map.rememberIumrahMapController
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds

// iOS 2026-10-09: render an offline catalogue immediately; map and photos are opt-in.
// Existing booking-sheet implementation is intentionally kept independent.
private enum class StableCity(val apiName: String) { MADINAH("Madinah"), MAKKAH("Makkah") }

private fun t(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
private fun cityText(city: StableCity, language: AppLanguage): String = when (city) {
    StableCity.MADINAH -> t(language, "Медина", "Madinah", "Madina", "Мадина")
    StableCity.MAKKAH -> t(language, "Мекка", "Makkah", "Makka", "Макка")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZiyaratStableScreen(language: AppLanguage, chrome: AppChromeStore, service: ZiyaratService) {
    var city by remember { mutableStateOf(StableCity.MADINAH) }
    var route by remember { mutableStateOf(ZiyaratFallbackCatalog.route(city.apiName)) }
    var updating by remember { mutableStateOf(false) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var selectedPlace by remember { mutableStateOf<ZiyaratPlace?>(null) }
    var showMap by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        chrome.setImmersive(true)
        onDispose { chrome.setImmersive(false) }
    }
    LaunchedEffect(city, refreshNonce) {
        updating = true
        val requestedCity = city
        val updated = service.route(requestedCity.apiName)
        // LaunchedEffect cancels any earlier request on a city switch.
        if (city == requestedCity) {
            route = updated
            updating = false
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = chrome::back, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Icon(CupertinoSymbol.ChevronLeft, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(t(language, "Назад", "Back", "Orqaga", "Орқага"))
                }
                Spacer(Modifier.weight(1f))
                Text("iumrah Ziyarats", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(68.dp))
            }
            PullToRefreshBox(
                isRefreshing = updating,
                onRefresh = { refreshNonce++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("iumrah Ziyarats", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                            Text(
                                t(language, "Открывайте исторические места Мекки и Медины в удобном порядке.", "Explore historic sites in Makkah and Madinah at your own pace.", "Makka va Madinaning tarixiy joylarini qulay tartibda kashf eting.", "Макка ва Мадинанинг тарихий жойларини қулай тартибда кашф этинг."),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                            )
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            StableCity.entries.forEach { item ->
                                val active = item == city
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                                        .clickable {
                                            if (city != item) {
                                                city = item
                                                route = ZiyaratFallbackCatalog.route(item.apiName)
                                                selectedPlace = null
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(cityText(item, language), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(t(language, "Места посещения", "Places to visit", "Ziyorat joylari", "Зиёрат жойлари"), fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                Text(t(language, "История, фотографии и навигация", "History, photographs and directions", "Tarix, suratlar va yo‘nalish", "Тарих, суратлар ва йўналиш"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .clickable { showMap = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(CupertinoSymbol.Map, t(language, "Открыть карту", "Open map", "Xaritani ochish", "Харитани очиш"), Modifier.size(23.dp)) }
                        }
                    }
                    if (route.status == "offline" && !updating) {
                        item {
                            Text(
                                t(language, "Доступен встроенный каталог. Потяните вниз, чтобы обновить.", "Built-in places are available. Pull to refresh.", "Ichki katalog mavjud. Yangilash uchun pastga torting.", "Ички каталог мавжуд. Янгилаш учун пастга тортинг."),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(route.places, key = { it.id }) { place ->
                        StablePlaceRow(place, language, onClick = { selectedPlace = place })
                    }
                    if (route.places.isEmpty()) {
                        item { Text(t(language, "Места не найдены", "No places found", "Joylar topilmadi", "Жойлар топилмади")) }
                    }
                }
            }
        }
    }
    selectedPlace?.let { place ->
        ModalBottomSheet(onDismissRequest = { selectedPlace = null }) {
            StablePlaceDetail(language, place)
        }
    }
    if (showMap) {
        ModalBottomSheet(onDismissRequest = { showMap = false }) {
            StablePlaceMap(route, language, city, onPlaceChosen = { place -> showMap = false; selectedPlace = place })
        }
    }
}

@Composable
private fun StableThumbnail(photo: ZiyaratImage?, modifier: Modifier = Modifier) {
    if (photo == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Icon(CupertinoSymbol.Building, null, Modifier.size(34.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        ZiyaratImageThumb(photo, modifier)
    }
}

@Composable
private fun StablePlaceRow(place: ZiyaratPlace, language: AppLanguage, onClick: () -> Unit) {
    val content = place.localized(language)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(width = 100.dp, height = 108.dp).clip(RoundedCornerShape(16.dp))) {
            StableThumbnail(place.images.firstOrNull(), Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(content.title.ifEmpty { place.title }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(content.shortDescription, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(t(language, "Подробнее ↗", "View details ↗", "Batafsil ↗", "Батафсил ↗"), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StablePlaceDetail(language: AppLanguage, place: ZiyaratPlace) {
    val content = place.localized(language)
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 35.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(24.dp))) {
                StableThumbnail(place.images.firstOrNull(), Modifier.fillMaxSize())
            }
        }
        item { Text(content.title.ifEmpty { place.title }, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 33.sp) }
        if (place.titleArabic.isNotBlank()) item { Text(place.titleArabic, fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (content.shortDescription.isNotBlank()) item { Text(content.shortDescription, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        if (content.longDescription.isNotBlank() && content.longDescription != content.shortDescription) {
            item { Text(content.longDescription, fontSize = 15.sp, lineHeight = 23.sp) }
        }
        items(content.interestingFacts) { fact -> Text("•  $fact", fontSize = 14.sp) }
        if (content.visitNotes.isNotBlank()) item { Text(content.visitNotes, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (place.images.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(place.images.drop(1).take(7)) { photo ->
                        StableThumbnail(photo, Modifier.size(width = 210.dp, height = 140.dp).clip(RoundedCornerShape(16.dp)))
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    if (ZiyaratService.validCoordinate(place.latitude, place.longitude)) {
                        val point = "${place.latitude},${place.longitude}"
                        val google = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$point"))
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$point"))
                        // Do not crash when Google Maps is not installed.
                        val target = if (google.resolveActivity(context.packageManager) != null) google else fallback
                        runCatching { context.startActivity(target) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = CircleShape,
            ) {
                Text(t(language, "Построить маршрут", "Directions", "Yo‘nalish", "Йўналиш"))
            }
        }
    }
}

/** Construct MapLibre only after tapping the map button. No route downloads or animations. */
@Composable
private fun StablePlaceMap(route: ZiyaratRoute, language: AppLanguage, city: StableCity, onPlaceChosen: (ZiyaratPlace) -> Unit) {
    val controller = rememberIumrahMapController()
    val map = controller.map
    var chosen by remember { mutableStateOf<ZiyaratPlace?>(null) }
    LaunchedEffect(map) { if (map != null) controller.setStyle(IumrahMapStyle.STANDARD) }
    LaunchedEffect(map, controller.styleEpoch, route) {
        if (map == null || controller.styleEpoch == 0) return@LaunchedEffect
        map.clear()
        val valid = route.places.filter { ZiyaratService.validCoordinate(it.latitude, it.longitude) }
        valid.forEach { place ->
            map.addMarker(
                MarkerOptions().position(LatLng(place.latitude, place.longitude))
                    .title(place.localized(language).title.ifBlank { place.title }),
            )
        }
        if (valid.isNotEmpty()) {
            val points = valid.map { LatLng(it.latitude, it.longitude) }
            if (points.size == 1) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), 12.0))
            } else {
                val bounds = LatLngBounds.fromLatLngs(points)
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 70))
            }
        } else {
            val center = if (city == StableCity.MAKKAH) LatLng(21.4225, 39.8262) else LatLng(24.4672, 39.6100)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(center, 11.0))
        }
        map.setOnMarkerClickListener { marker ->
            chosen = valid.firstOrNull { it.latitude == marker.position.latitude && it.longitude == marker.position.longitude }
            true
        }
    }
    Column(Modifier.fillMaxWidth().height(570.dp)) {
        Text(cityText(city, language), Modifier.padding(horizontal = 20.dp, vertical = 14.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxSize()) {
            IumrahMapLibreView(controller, Modifier.fillMaxSize())
            chosen?.let { place ->
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                        .clickable { onPlaceChosen(place) }.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(place.localized(language).title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(CupertinoSymbol.ChevronRight, null, Modifier.size(20.dp))
                }
            }
        }
    }
}
