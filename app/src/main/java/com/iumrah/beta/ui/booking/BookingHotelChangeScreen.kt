package com.iumrah.beta.ui.booking

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.hotel.HotelCatalogService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.models.hotel.HotelSummary
import com.iumrah.beta.models.hotel.IumrahRoomCategoryOption
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.launch

@Composable
fun BookingHotelChangeScreen(
    bookingID: String,
    role: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    catalog: HotelCatalogService,
    packageEngine: RemotePackageEngineClient,
    chrome: AppChromeStore,
) {
    val city = if (role.equals("madinah", true)) "Madinah" else "Makkah"
    val scope = rememberCoroutineScope()
    val haptic = LocalView.current
    var hotels by remember { mutableStateOf<List<HotelSummary>>(emptyList()) }
    var selectedHotel by remember { mutableStateOf<HotelSummary?>(null) }
    var categories by remember { mutableStateOf<List<IumrahRoomCategoryOption>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        hotels = runCatching { catalog.listHotels(city) }
            .onFailure { error = it.message }
            .getOrDefault(emptyList())
        loading = false
    }

    LaunchedEffect(city) { load() }
    LaunchedEffect(selectedHotel?.id) {
        categories = selectedHotel?.id?.let { id -> runCatching { packageEngine.roomCategories(id) }.getOrDefault(emptyList()) }.orEmpty()
    }

    Column(Modifier.fillMaxSize().background(bookingIosPage()).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            IumrahPressable(
                onClick = chrome::back,
                modifier = Modifier.align(Alignment.CenterStart).size(40.dp),
                cornerRadius = 20.dp,
                background = bookingIosRaised(),
                pressedScale = .94f,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CupertinoIcon(CupertinoSymbol.Close, "Close", Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 46.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        bookingText(language, "Изменить отель", "Change hotel", "Mehmonxonani almashtirish", "Меҳмонхонани алмаштириш"),
                        fontSize = 30.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-.5).sp,
                    )
                    Text(
                        bookingText(language, "Выберите другой отель и категорию номера. Booking ID сохранится.", "Choose another hotel and room category. The same Booking ID is preserved.", "Boshqa mehmonxona va xona kategoriyasini tanlang. Booking ID saqlanadi.", "Бошқа меҳмонхона ва хона категориясини танланг. Booking ID сақланади."),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                    )
                }
            }

            if (loading && hotels.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) } }
            }

            items(hotels, key = { it.id }) { hotel ->
                val selected = selectedHotel?.id == hotel.id
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IumrahPressable(
                        onClick = { selectedHotel = if (selected) null else hotel; IumrahHaptics.selection(haptic) },
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 28.dp,
                        background = bookingIosCard(),
                        pressedScale = .988f,
                    ) {
                        Column(
                            Modifier.fillMaxWidth()
                                .border(if (selected) 1.3.dp else .7.dp, if (selected) MaterialTheme.colorScheme.onBackground.copy(alpha = .28f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .075f), RoundedCornerShape(28.dp))
                                .padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(11.dp),
                        ) {
                            Box(
                                Modifier.fillMaxWidth().height(168.dp).clip(RoundedCornerShape(22.dp)).background(bookingIosRaised()),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppConfig.absoluteUrl(hotel.coverImageURL)?.let {
                                    AsyncImage(it, hotel.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } ?: CupertinoIcon(CupertinoSymbol.Hotel, null, Modifier.size(38.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(hotel.name, fontSize = 19.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        val metadataTint = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f)
                                        hotel.stars?.let { stars ->
                                            Text("$stars", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = metadataTint)
                                            CupertinoIcon(CupertinoSymbol.StarFill, null, Modifier.size(12.dp), metadataTint)
                                        }
                                        hotel.rating?.let { Text(String.format("%.1f", it), fontSize = 12.sp, color = metadataTint) }
                                        if (city.isNotBlank()) Text(city, fontSize = 12.sp, color = metadataTint)
                                    }
                                }
                                if (selected) BookingIconBadge(CupertinoSymbol.CheckCircleFill, Color(0xFF34C759), 38.dp, 15.dp, 19.dp, circle = true)
                                else CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
                            }
                        }
                    }

                    AnimatedVisibility(selected) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            categories.forEach { category ->
                                IumrahPressable(
                                    onClick = {
                                        if (!busy) {
                                            busy = true
                                            error = null
                                            scope.launch {
                                                runCatching { bookingStore.updateHotel(bookingID, role, hotel, null, category) }
                                                    .onSuccess { busy = false; IumrahHaptics.success(haptic); chrome.back() }
                                                    .onFailure { busy = false; error = it.message }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                                    cornerRadius = 22.dp,
                                    background = bookingIosRaised(),
                                    pressedScale = .988f,
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        BookingIconBadge(CupertinoSymbol.Bed, Color(0xFF5856D6), 42.dp, 17.dp, 14.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(category.displayName, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                                            Text(category.bedConfiguration, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                                        }
                                        Text("${category.maxGuests}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            error?.let { message ->
                item {
                    BookingCard {
                        Text(message, fontSize = 13.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f))
                        Spacer(Modifier.height(12.dp))
                        BookingSecondaryAction(bookingText(language, "Повторить", "Retry", "Qayta urinish", "Қайта уриниш"), onClick = { scope.launch { load() } })
                    }
                }
            }
        }
    }
}
