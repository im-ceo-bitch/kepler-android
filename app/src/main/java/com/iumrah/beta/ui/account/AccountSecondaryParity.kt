package com.iumrah.beta.ui.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahFontFamily
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahBookingStatusVisual
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.settings.AppAppearance
import com.iumrah.beta.core.settings.AppSettingsStore
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.account.IumrahTravelerForm
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.models.notification.ClientSystemNotification
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.media.LoopingRawVideo
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val ApCare = Color(0xFF30B0C7)
private val ApGray = Color(0xFF8E8E93)
private val ApGreen = Color(0xFF34C759)
private val ApOrange = Color(0xFFFF9500)
private val ApRed = Color(0xFFFF3B30)
private val ApBlue = Color(0xFF1677FF)
private val ApPad = 18.dp

private fun apTr(l: AppLanguage, en: String, ru: String, uz: String, cy: String): String = when (l) {
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, l)
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

@Composable
private fun ApNavPage(
    title: String,
    chrome: AppChromeStore,
    largeTitle: Boolean = false,
    bottom: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = ApPad, end = ApPad, top = 10.dp, bottom = bottom),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(if (largeTitle) 86.dp else 48.dp), verticalAlignment = if (largeTitle) Alignment.Bottom else Alignment.CenterVertically) {
                Surface(onClick = { chrome.back() }, shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)) {
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        CupertinoIcon(CupertinoSymbol.ChevronLeft, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (largeTitle) {
                    Spacer(Modifier.width(12.dp))
                    Text(title, Modifier.weight(1f).padding(bottom = 2.dp), fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
                } else {
                    Text(title, Modifier.weight(1f).padding(horizontal = 12.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Spacer(Modifier.width(38.dp))
                }
            }
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(18.dp), content = content) }
    }
}

@Composable
private fun ApCard(
    radius: androidx.compose.ui.unit.Dp = 28.dp,
    padding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(radius),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(padding), content = content)
    }
}

@Composable
private fun ApBadge(icon: CupertinoSymbol, tint: Color, size: androidx.compose.ui.unit.Dp = 52.dp, symbol: androidx.compose.ui.unit.Dp = 21.dp, radius: androidx.compose.ui.unit.Dp = 17.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(tint.copy(alpha = .105f)), contentAlignment = Alignment.Center) {
        CupertinoIcon(icon, null, Modifier.size(symbol), tint)
    }
}

@Composable
private fun ApSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(2.dp)) {
        labels.forEachIndexed { index, label ->
            Surface(
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f).height(32.dp),
                shape = RoundedCornerShape(9.dp),
                color = if (index == selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (index == selected) 1.dp else 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label, fontSize = 12.sp, fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium, color = if (index == selected) MaterialTheme.colorScheme.onSurface else ApGray)
                }
            }
        }
    }
}

private fun isPastTrip(session: StoredBookingSession): Boolean = session.effectiveStatus.uppercase() in setOf("COMPLETED", "CANCELLED")

private fun tripTint(status: String): Color = IumrahBookingStatusVisual.color(status)

private fun tripIcon(status: String): CupertinoSymbol = when (status.uppercase()) {
    "COMPLETED" -> CupertinoSymbol.CheckCircleFill
    "CANCELLED" -> CupertinoSymbol.ExclamationCircle
    "IN_TRIP" -> CupertinoSymbol.Location
    "READY_TO_TRAVEL", "DOCUMENTS_READY" -> CupertinoSymbol.AirplaneTakeoff
    "BOOKING_CONFIRMED", "PAID" -> CupertinoSymbol.ShieldCheck
    else -> CupertinoSymbol.Hourglass
}

private fun statusLabel(status: String, language: AppLanguage): String = when (status.uppercase()) {
    "AVAILABILITY_CHECK", "NEW" -> apTr(language, "Availability check", "Проверка наличия", "Mavjudlik tekshiruvi", "Мавжудлик текшируви")
    "PAYMENT_PENDING", "AVAILABILITY_CONFIRMED" -> apTr(language, "Waiting payment", "Ожидание оплаты", "To‘lov kutilmoqda", "Тўлов кутилмоқда")
    "BOOKING_CONFIRMED", "PAID" -> apTr(language, "Confirmed", "Подтверждено", "Tasdiqlandi", "Тасдиқланди")
    "READY_TO_TRAVEL", "DOCUMENTS_READY" -> apTr(language, "Ready to travel", "Готово к поездке", "Safarga tayyor", "Сафарга тайёр")
    "IN_TRIP" -> apTr(language, "In trip", "В поездке", "Safarda", "Сафарда")
    "COMPLETED" -> apTr(language, "Completed", "Завершено", "Yakunlandi", "Якунланди")
    "CANCELLED" -> apTr(language, "Cancelled", "Отменено", "Bekor qilindi", "Бекор қилинди")
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

private fun prettyDate(raw: String, language: AppLanguage): String {
    val date = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return raw
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru", "RU")
        AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn-UZ")
        AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl-UZ")
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> language.locale
    }
    return date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
}

@Composable
fun AccountTripsHistoryScreen(
    language: AppLanguage,
    bookingStore: BookingStore,
    chrome: AppChromeStore,
    initialPast: Boolean = false,
) {
    val bookingState by bookingStore.state.collectAsState()
    var past by remember { mutableStateOf(initialPast) }
    val sessions = remember(bookingState.sessions, past) {
        bookingState.sessions.filter { isPastTrip(it) == past }.sortedByDescending { it.booking.input.startDate }
    }
    ApNavPage(apTr(language, "My trips", "Мои поездки", "Safarlarim", "Сафарларим"), chrome, largeTitle = true) {
        ApSegmented(
            listOf(apTr(language, "Active", "Активные", "Faol", "Фаол"), apTr(language, "Past", "Прошлые", "Oldingi", "Олдинги")),
            if (past) 1 else 0,
        ) { past = it == 1 }

        if (sessions.isEmpty()) {
            Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ApBadge(if (past) CupertinoSymbol.Refresh else CupertinoSymbol.Suitcase, ApGray, 54.dp, 24.dp, 18.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(if (past) apTr(language, "No past trips yet", "История пока пуста", "Tarix hozircha bo‘sh", "Тарих ҳозирча бўш") else apTr(language, "No active trips", "Нет активных поездок", "Faol safar yo‘q", "Фаол сафар йўқ"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (past) apTr(language, "Completed and cancelled trips will stay here.", "Завершённые и отменённые поездки будут храниться здесь.", "Yakunlangan va bekor qilingan safarlar shu yerda saqlanadi.", "Якунланган ва бекор қилинган сафарлар шу ерда сақланади.") else apTr(language, "A new booking will appear here automatically.", "Новая бронь появится здесь автоматически.", "Yangi bron shu yerda avtomatik paydo bo‘ladi.", "Янги брон шу ерда автоматик пайдо бўлади."), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            sessions.forEach { session ->
                val tint = tripTint(session.effectiveStatus)
                Surface(
                    onClick = { chrome.openBookingDetail(session.id) },
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ApBadge(tripIcon(session.effectiveStatus), tint, 46.dp, 18.dp, 16.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("${session.booking.route.originCode} → ${session.booking.route.outboundDestination}", fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                                Text("${prettyDate(session.booking.input.startDate, language)} — ${prettyDate(session.booking.input.endDate, language)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.padding(top = 15.dp).size(13.dp), ApGray.copy(alpha = .65f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(statusLabel(session.effectiveStatus, language), Modifier.clip(CircleShape).background(tint.copy(alpha = .10f)).padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tint)
                            Text(session.displayBookingNumber, Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private data class CompanionItem(val bookingID: String, val tripTitle: String, val traveler: IumrahTravelerForm)

@Composable
fun AccountTravelCompanionsParityScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    bookingStore: BookingStore,
    accountService: IumrahAccountService,
    chrome: AppChromeStore,
) {
    val bookingState by bookingStore.state.collectAsState()
    val bearer = accountStore.bearerToken
    var items by remember { mutableStateOf<List<CompanionItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookingState.sessions, bearer) {
        loading = true
        error = null
        val loaded = mutableListOf<CompanionItem>()
        bookingState.sessions.forEach { session ->
            runCatching { accountService.checkout(session.id, bookingStore.headersFor(session)) }.getOrNull()?.travelers?.forEach { traveler ->
                val hotel = session.booking.hotelNames.makkah.trim()
                val tripTitle = hotel.ifBlank { "${session.booking.route.originCode} → ${session.booking.route.outboundDestination}" }
                loaded += CompanionItem(session.id, tripTitle, traveler)
            }
        }
        items = loaded.sortedWith(compareBy<CompanionItem> { it.tripTitle }.thenBy { it.traveler.position })
        if (loaded.isEmpty() && bookingState.sessions.isNotEmpty()) error = apTr(language, "Travelers could not be loaded. Pull down to try again.", "Не удалось загрузить участников. Попробуйте открыть страницу снова.", "Sayohatchilarni yuklab bo‘lmadi. Sahifani qayta ochib ko‘ring.", "Саёҳатчиларни юклаб бўлмади. Саҳифани қайта очиб кўринг.")
        loading = false
    }

    ApNavPage(apTr(language, "Who is traveling with you", "Кто едет с Вами", "Siz bilan kim bormoqda", "Сиз билан ким бормоқда"), chrome, largeTitle = true, bottom = 42.dp) {
        Text(
            apTr(
                language,
                "Attach the passport information page for each traveler. A clear passport photo is enough for booking operations.",
                "Прикрепите страницу загранпаспорта с данными для каждого паломника. Чёткой фотографии паспорта достаточно для оформления бронирования.",
                "Har bir ziyoratchining ma’lumotlar sahifasi tushirilgan pasport rasmini biriktiring. Aniq pasport rasmi bronni rasmiylashtirish uchun yetarli.",
                "Ҳар бир зиёратчининг маълумотлар саҳифаси туширилган паспорт расмини бириктиринг. Аниқ паспорт расми бронни расмийлаштириш учун етарли.",
            ),
            modifier = Modifier.padding(horizontal = 2.dp),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f)),
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painterResource(R.drawable.travel_companions_cover),
                        null,
                        Modifier.fillMaxWidth().height(106.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        ApBadge(CupertinoSymbol.Passport, ApBlue)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(apTr(language, "Passport first", "Сначала паспорт", "Avval pasport", "Аввал паспорт"), fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                            Text(
                                apTr(
                                    language,
                                    "Use a photo of the passport page with the holder photo and all personal data visible.",
                                    "Нужна передняя страница паспорта: фотография владельца и все данные должны полностью попадать в кадр.",
                                    "Pasportning egasi rasmi va barcha ma’lumotlar ko‘rinadigan old sahifasini suratga oling.",
                                    "Паспортнинг эгаси расми ва барча маълумотлар кўринадиган олд саҳифасини суратга олинг.",
                                ),
                                fontSize = 14.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ApOrange.copy(alpha = .10f)).padding(14.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(17.dp), ApOrange)
                        Text(
                            apTr(
                                language,
                                "Make sure every passport field is sharp and readable. We use these details to issue airline tickets, hotel bookings and the rest of the trip documents.",
                                "Обратите внимание: все данные паспорта должны быть чёткими и видимыми. Они используются для оформления авиабилетов, бронирования отеля и остальных документов поездки.",
                                "E’tibor bering: pasportdagi barcha ma’lumotlar aniq va to‘liq ko‘rinsin. Ular aviachipta, mehmonxona va safar hujjatlarini rasmiylashtirish uchun ishlatiladi.",
                                "Эътибор беринг: паспортдаги барча маълумотлар аниқ ва тўлиқ кўринсин. Улар авиачипта, меҳмонхона ва сафар ҳужжатларини расмийлаштириш учун ишлатилади.",
                            ),
                            Modifier.weight(1f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        when {
            loading && items.isEmpty() -> ApCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Column { Text(apTr(language, "Loading travelers", "Загружаем участников", "Sayohatchilar yuklanmoqda", "Саёҳатчилар юкланмоқда"), fontWeight = FontWeight.Bold); Text(apTr(language, "This usually takes a few seconds.", "Обычно это занимает несколько секунд.", "Bu odatda bir necha soniya davom etadi.", "Бу одатда бир неча сония давом этади."), fontSize = 12.sp, color = ApGray) }
                }
            }
            items.isEmpty() -> ApCard {
                ApBadge(CupertinoSymbol.PlusPerson, ApCare)
                Spacer(Modifier.height(12.dp))
                Text(apTr(language, "No companions yet", "Попутчики пока не добавлены", "Hozircha hamrohlar yo‘q", "Ҳозирча ҳамроҳлар йўқ"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(
                    apTr(
                        language,
                        "When your booking includes another traveler, their passport card will appear here automatically.",
                        "Когда в бронировании появится ещё один паломник, его карточка паспорта автоматически появится здесь.",
                        "Bronga yana bir ziyoratchi qo‘shilganda, uning pasport kartasi shu yerda avtomatik paydo bo‘ladi.",
                        "Бронга яна бир зиёратчи қўшилганда, унинг паспорт картаси шу ерда автоматик пайдо бўлади.",
                    ),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> items.forEach { item -> CompanionCard(language, item, chrome) }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 4.dp), fontSize = 12.sp, color = ApOrange) }
    }
}

private fun relationTitle(value: String?, position: Int, language: AppLanguage): String = when (value?.lowercase()) {
    "self" -> apTr(language, "You", "Вы", "Siz", "Сиз")
    "spouse" -> apTr(language, "Spouse", "Муж или жена", "Turmush o‘rtog‘i", "Турмуш ўртоғи")
    "mother" -> apTr(language, "Mother", "Мама", "Ona", "Она")
    "father" -> apTr(language, "Father", "Папа", "Ota", "Ота")
    "brother" -> apTr(language, "Brother", "Брат", "Aka yoki uka", "Ака ёки ука")
    "sister" -> apTr(language, "Sister", "Сестра", "Opa yoki singil", "Опа ёки сингил")
    "child" -> apTr(language, "Child", "Ребёнок", "Farzand", "Фарзанд")
    "relative" -> apTr(language, "Relative", "Родственник", "Qarindosh", "Қариндош")
    "friend" -> apTr(language, "Friend", "Друг или подруга", "Do‘st", "Дўст")
    else -> if (position == 1) apTr(language, "You", "Вы", "Siz", "Сиз") else apTr(language, "Traveler", "Участник поездки", "Sayohatchi", "Саёҳатчи")
}

private fun relationIcon(value: String?): CupertinoSymbol = when (value?.lowercase()) {
    "self" -> CupertinoSymbol.PersonCircle
    "spouse" -> CupertinoSymbol.HeartFill
    "child" -> CupertinoSymbol.PersonCircle
    else -> CupertinoSymbol.Persons
}

@Composable
private fun CompanionCard(language: AppLanguage, item: CompanionItem, chrome: AppChromeStore) {
    val traveler = item.traveler
    val name = listOf(traveler.firstName, traveler.middleName, traveler.lastName)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { apTr(language, "Traveler ${traveler.position}", "Паломник ${traveler.position}", "Ziyoratchi ${traveler.position}", "Зиёратчи ${traveler.position}") }
    val passportReady = traveler.hasPassport
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f)),
    ) {
        Column {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    ApBadge(CupertinoSymbol.Passport, if (passportReady) ApGreen else ApCare)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(relationTitle(traveler.relationship, traveler.position, language).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = .4.sp)
                        Text(name, fontSize = 21.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(item.tripTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                    CupertinoIcon(if (passportReady) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.ExclamationCircle, null, Modifier.size(22.dp), if (passportReady) ApGreen else ApOrange)
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    CupertinoIcon(CupertinoSymbol.Passport, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface)
                    Text(apTr(language, "Passport", "Загранпаспорт", "Pasport", "Паспорт"), fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (passportReady) apTr(language, "Attached", "Прикреплён", "Biriktirilgan", "Бириктирилган") else apTr(language, "Photo required", "Нужно фото", "Rasm kerak", "Расм керак"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (passportReady) ApGreen else ApOrange,
                        textAlign = TextAlign.End,
                    )
                }

                Text(
                    if (passportReady) {
                        apTr(language, "The passport is ready for booking operations. You can review or replace the photo at any time.", "Паспорт готов для оформления. При необходимости можно проверить или заменить фотографию.", "Pasport bronni rasmiylashtirish uchun tayyor. Rasmni istalgan payt tekshirish yoki almashtirish mumkin.", "Паспорт бронни расмийлаштириш учун тайёр. Расмни исталган пайт текшириш ёки алмаштириш мумкин.")
                    } else {
                        apTr(language, "A clear photo of the passport information page is enough. Manual fields are optional.", "Достаточно чёткой фотографии страницы паспорта с данными. Ручное заполнение полей необязательно.", "Pasport ma’lumotlar sahifasining aniq rasmi yetarli. Maydonlarni qo‘lda to‘ldirish ixtiyoriy.", "Паспорт маълумотлар саҳифасининг аниқ расми етарли. Майдонларни қўлда тўлдириш ихтиёрий.")
                    },
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Surface(onClick = { chrome.openPilgrimCheckout(item.bookingID) }, color = Color.Black) {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (passportReady) apTr(language, "Review passport", "Проверить паспорт", "Pasportni tekshirish", "Паспортни текшириш") else apTr(language, "Attach passport", "Прикрепить паспорт", "Pasportni biriktirish", "Паспортни бириктириш"),
                        Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(16.dp), Color.White)
                }
            }
        }
    }
}
@Composable
private fun CompanionFact(icon: CupertinoSymbol, title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        CupertinoIcon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurface)
        Text(title, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}

private enum class RecoveryStep { EMAIL, CODE, PASSWORD, SUCCESS }

@Composable
fun AccountPasswordRecoveryScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(RecoveryStep.EMAIL) }
    var email by remember { mutableStateOf("") }
    var challengeID by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var restoredID by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val title = apTr(language, "Password recovery", "Восстановление пароля", "Parolni tiklash", "Паролни тиклаш")
    ApNavPage(title, chrome) {
        ApBadge(
            when (step) { RecoveryStep.EMAIL -> CupertinoSymbol.Key; RecoveryStep.CODE -> CupertinoSymbol.Mail; RecoveryStep.PASSWORD -> CupertinoSymbol.Refresh; RecoveryStep.SUCCESS -> CupertinoSymbol.ShieldCheck },
            if (step == RecoveryStep.SUCCESS) ApGreen else ApCare,
            64.dp, 28.dp, 20.dp,
        )

        if (step == RecoveryStep.CODE) {
            LoopingRawVideo("password_recovery_confirmation", Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(24.dp))) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Mail, null, Modifier.size(44.dp), ApCare) }
            }
        }

        when (step) {
            RecoveryStep.EMAIL -> {
                RecoveryHeading(apTr(language, "Reset with email", "Восстановление по почте", "Email orqali tiklash", "Email орқали тиклаш"), apTr(language, "Enter the verified email connected to your iumrah account. We will send a six-digit code.", "Введите подтверждённую почту, подключённую к аккаунту iumrah. Мы отправим шестизначный код.", "iumrah akkauntingizga ulangan tasdiqlangan emailni kiriting. Olti xonali kod yuboramiz.", "iumrah аккаунтингизга уланган тасдиқланган emailни киритинг. Олти хонали код юборамиз."))
                RecoveryField(CupertinoSymbol.Mail, email, { email = it }, "name@example.com", KeyboardType.Email)
                RecoveryPrimary(apTr(language, "Send recovery code", "Отправить код", "Tiklash kodini yuborish", "Тиклаш кодини юбориш"), CupertinoSymbol.ArrowRight, busy, email.contains('@')) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { accountStore.startPasswordRecovery(email.trim(), language.localeTag) }
                            .onSuccess { challengeID = it.challengeID; code = ""; password = ""; confirm = ""; step = RecoveryStep.CODE }
                            .onFailure { error = it.message ?: "Error" }
                        busy = false
                    }
                }
            }
            RecoveryStep.CODE -> {
                RecoveryHeading(apTr(language, "Confirm your email", "Подтвердите почту", "Emailni tasdiqlang", "Emailни тасдиқланг"), apTr(language, "We sent a six-digit confirmation code to your email. Enter it below to continue to the password step.", "Мы отправили шестизначный код подтверждения на вашу почту. Введите его ниже, чтобы перейти к смене пароля.", "Emailingizga olti xonali tasdiqlash kodi yuborildi. Parol bosqichiga o‘tish uchun uni quyida kiriting.", "Emailингизга олти хонали тасдиқлаш коди юборилди. Парол босқичига ўтиш учун уни қуйида киритинг."))
                Text(email.trim(), Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(horizontal = 14.dp, vertical = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                RecoveryField(CupertinoSymbol.NumberSquare, code, { code = it.filter(Char::isDigit).take(6) }, "000000", KeyboardType.Number)
                RecoveryPrimary(apTr(language, "Continue", "Продолжить", "Davom etish", "Давом этиш"), CupertinoSymbol.ArrowRight, false, code.length == 6) { error = null; step = RecoveryStep.PASSWORD }
                TextButton(onClick = { step = RecoveryStep.EMAIL; challengeID = ""; code = ""; error = null }, modifier = Modifier.fillMaxWidth()) { Text(apTr(language, "Use another email", "Указать другую почту", "Boshqa emaildan foydalanish", "Бошқа emailдан фойдаланиш"), fontWeight = FontWeight.SemiBold) }
            }
            RecoveryStep.PASSWORD -> {
                RecoveryHeading(apTr(language, "Create a new password", "Создайте новый пароль", "Yangi parol yarating", "Янги парол яратинг"), apTr(language, "Your email is confirmed. Choose a new password for the iumrah account linked to $email.", "Почта подтверждена. Придумайте новый пароль для аккаунта iumrah, связанного с $email.", "Email tasdiqlandi. $email bilan bog‘langan iumrah akkaunti uchun yangi parol yarating.", "Email тасдиқланди. $email билан боғланган iumrah аккаунти учун янги парол яратинг."))
                RecoveryField(CupertinoSymbol.Lock, password, { password = it }, apTr(language, "New password", "Новый пароль", "Yangi parol", "Янги парол"), KeyboardType.Password, secure = true)
                RecoveryField(CupertinoSymbol.Refresh, confirm, { confirm = it }, apTr(language, "Confirm password", "Повторите пароль", "Parolni takrorlang", "Паролни такрорланг"), KeyboardType.Password, secure = true)
                RecoveryPrimary(apTr(language, "Set new password", "Установить новый пароль", "Yangi parol o‘rnatish", "Янги парол ўрнатиш"), CupertinoSymbol.ShieldCheck, busy, password.length >= 8 && password == confirm) {
                    busy = true; error = null
                    scope.launch {
                        runCatching { accountStore.confirmPasswordRecovery(challengeID, code, password) }
                            .onSuccess { restoredID = it.iumrahID; step = RecoveryStep.SUCCESS }
                            .onFailure { error = it.message ?: "Error" }
                        busy = false
                    }
                }
                TextButton(onClick = { step = RecoveryStep.CODE; error = null }, modifier = Modifier.fillMaxWidth()) { Text(apTr(language, "Back to confirmation code", "Вернуться к коду подтверждения", "Tasdiqlash kodiga qaytish", "Тасдиқлаш кодига қайтиш"), fontWeight = FontWeight.SemiBold) }
            }
            RecoveryStep.SUCCESS -> {
                RecoveryHeading(apTr(language, "Password updated", "Пароль изменён", "Parol yangilandi", "Парол янгиланди"), apTr(language, "All previous sessions were securely ended. Sign in again with your email or iumrah ID $restoredID.", "Все предыдущие сеансы безопасно завершены. Войдите снова по почте или iumrah ID $restoredID.", "Barcha oldingi seanslar xavfsiz tugatildi. Email yoki $restoredID iumrah ID bilan qayta kiring.", "Барча олдинги сеанслар хавфсиз тугатилди. Email ёки $restoredID iumrah ID билан қайта киринг."))
                RecoveryPrimary(apTr(language, "Return to sign in", "Вернуться ко входу", "Kirishga qaytish", "Киришга қайтиш"), CupertinoSymbol.ArrowRight, false, true) { chrome.back() }
            }
        }
        error?.let { Text(it, Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ApRed.copy(alpha = .08f)).padding(14.dp), fontSize = 12.sp, color = ApRed) }
    }
}

@Composable
private fun RecoveryHeading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
        Text(body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RecoveryField(icon: CupertinoSymbol, value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType, secure: Boolean = false) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CupertinoIcon(icon, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontFamily = if (keyboard == KeyboardType.Number) com.iumrah.beta.core.design.IumrahFontFamily else IumrahFontFamily),
                decorationBox = { inner -> Box { if (value.isEmpty()) Text(placeholder, color = ApGray, fontSize = 16.sp); inner() } },
            )
        }
    }
}

@Composable
private fun RecoveryPrimary(title: String, icon: CupertinoSymbol, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = enabled && !busy, shape = RoundedCornerShape(19.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White, disabledContainerColor = Color.Black.copy(alpha = .25f))) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White) else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold); CupertinoIcon(icon, null, Modifier.size(17.dp), Color.White) }
        }
    }
}

private data class PolicyBlock(val icon: CupertinoSymbol, val title: String, val body: String, val badge: String? = null, val tint: Color = ApCare)

@Composable
fun AccountPolicyParityScreen(kind: String, language: AppLanguage, chrome: AppChromeStore) {
    val isRefund = kind == "refund"
    val isPrivacy = kind == "privacy"
    val title = when {
        isRefund -> apTr(language, "Refund Policy", "Политика возврата", "Qaytarish siyosati", "Қайтариш сиёсати")
        isPrivacy -> apTr(language, "Privacy Policy", "Политика конфиденциальности", "Maxfiylik siyosati", "Махфийлик сиёсати")
        else -> apTr(language, "Payment Security", "Безопасность оплаты", "To‘lov xavfsizligi", "Тўлов хавфсизлиги")
    }
    val hero = when {
        isRefund -> apTr(language, "Each travel component has its own terms. iumrah shows them before payment and applies them to your booking.", "Каждый компонент поездки имеет собственные условия. iumrah показывает их до оплаты и применяет к конкретному бронированию.", "Safarning har bir komponenti o‘z shartlariga ega. iumrah ularni to‘lovdan oldin ko‘rsatadi va broningizga qo‘llaydi.", "Сафарнинг ҳар бир компоненти ўз шартларига эга. iumrah уларни тўловдан олдин кўрсатади ва бронга қўллайди.")
        isPrivacy -> apTr(language, "A clear explanation of the data iumrah needs for your account, booking, journey and support.", "Понятно о том, какие данные нужны iumrah для аккаунта, бронирования, поездки и поддержки.", "iumrah akkaunt, bron, safar va yordam uchun qaysi ma’lumotlardan foydalanishini aniq tushuntiradi.", "iumrah аккаунт, брон, сафар ва ёрдам учун қайси маълумотлардан фойдаланишини аниқ тушунтиради.")
        else -> apTr(language, "In the first release, payments are temporarily handled manually. Payment details, invoice and proof of payment are tied to the booking.", "В первой версии платежи временно оформляются вручную. Реквизиты, инвойс и подтверждение оплаты привязаны к конкретному бронированию.", "Birinchi versiyada to‘lovlar vaqtincha qo‘lda amalga oshiriladi. Rekvizitlar, invoice va to‘lov tasdig‘i aniq bron bilan bog‘lanadi.", "Биринчи версияда тўловлар вақтинча қўлда амалга оширилади. Реквизитлар, invoice ва тўлов тасдиғи аниқ брон билан боғланади.")
    }
    val blocks = policyBlocks(kind, language)
    ApNavPage(title, chrome) {
        ApCard {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ApBadge(if (isRefund) CupertinoSymbol.UndoCircle else if (isPrivacy) CupertinoSymbol.HandRaised else CupertinoSymbol.CreditCard, if (isRefund) ApOrange else if (isPrivacy) ApCare else ApOrange)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(title, fontSize = 25.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold); Text(hero, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        blocks.forEach { block -> PolicyCard(block) }
    }
}

private fun policyBlocks(kind: String, l: AppLanguage): List<PolicyBlock> = when (kind) {
    "privacy" -> listOf(
        PolicyBlock(CupertinoSymbol.IdentityCard, apTr(l,"Data we use","Какие данные используются","Qaysi ma’lumotlar ishlatiladi","Қайси маълумотлар ишлатилади"), apTr(l,"Profile and iumrah ID data; pilgrim and trip details; passport and other documents you submit for travel processing; contact details; booking data; uploaded payment receipts; and technical/security data needed to operate the account and prevent abuse.","Данные профиля и iumrah ID; сведения о паломниках и поездке; паспортные и иные документы, которые Вы сами загружаете для оформления; контакты; данные бронирования; загруженные чеки; технические и защитные данные, необходимые для работы аккаунта и предотвращения злоупотреблений.","Profil va iumrah ID ma’lumotlari; ziyoratchi va safar ma’lumotlari; pasport va boshqa hujjatlar; aloqa, bron, chek va xavfsizlik ma’lumotlari.","Профил ва iumrah ID маълумотлари; зиёратчи ва сафар маълумотлари; паспорт ва бошқа ҳужжатлар; алоқа, брон, чек ва хавфсизлик маълумотлари.")),
        PolicyBlock(CupertinoSymbol.ShieldCheck, apTr(l,"Why we use it","Зачем это нужно","Nima uchun kerak","Нима учун керак"), apTr(l,"To create and protect your account, build the Umrah package, arrange selected services, contact you about the journey, provide support, verify payment, prepare documents and fulfil the booking.","Чтобы создать и защитить аккаунт, собрать Umrah-пакет, оформить выбранные услуги, связаться с Вами по поездке, предоставить поддержку, подтвердить оплату, подготовить документы и выполнять обязательства по бронированию.","Akkaunt yaratish va himoyalash, Umra paketini tuzish va bron majburiyatlarini bajarish uchun.","Аккаунт яратиш ва ҳимоялаш, Умра пакетини тузиш ва брон мажбуриятларини бажариш учун.")),
        PolicyBlock(CupertinoSymbol.Route, apTr(l,"Sharing with providers","Передача поставщикам","Hamkorlarga uzatish","Ҳамкорларга узатиш"), apTr(l,"Only data needed to deliver the journey may be shared with the relevant airline, hotel, transfer, visa or service provider.","Только необходимые для исполнения поездки данные могут передаваться соответствующей авиакомпании, отелю, перевозчику, визовому или иному сервисному партнёру.","Safarni bajarish uchun zarur ma’lumotlargina tegishli hamkorga uzatiladi.","Сафарни бажариш учун зарур маълумотларгина тегишли ҳамкорга узатилади.")),
        PolicyBlock(CupertinoSymbol.LockShield, apTr(l,"How data is protected","Как защищаются данные","Ma’lumotlar qanday himoyalanadi","Маълумотлар қандай ҳимояланади"), apTr(l,"The app connects over HTTPS. Account and booking access is protected by iumrah ID authentication, sessions and booking-specific tokens. Passwords are not stored in plain text.","Приложение обращается к iumrah через HTTPS. Доступ защищён iumrah ID, сеансами и токенами конкретного бронирования. Пароль не хранится в открытом виде.","Ilova HTTPS orqali ishlaydi; kirish iumrah ID, sessiya va bron tokenlari bilan himoyalangan.","Илова HTTPS орқали ишлайди; кириш iumrah ID, сессия ва брон токенлари билан ҳимояланган.")),
        PolicyBlock(CupertinoSymbol.TrashSlash, apTr(l,"Retention and deletion","Хранение и удаление","Saqlash va o‘chirish","Сақлаш ва ўчириш"), apTr(l,"Data is retained for as long as needed for the trip, support, security and required records. Access, correction or deletion requests can be sent through iumrah Care.","Данные хранятся столько, сколько необходимо для поездки, поддержки, безопасности и обязательного учёта. Запрос на доступ, исправление или удаление можно направить через iumrah Care.","Ma’lumotlar zarur muddatgacha saqlanadi; kirish, tuzatish yoki o‘chirish so‘rovini iumrah Care orqali yuborish mumkin.","Маълумотлар зарур муддатгача сақланади; кириш, тузатиш ёки ўчириш сўровини iumrah Care орқали юбориш мумкин."))
    )
    "refund" -> listOf(
        PolicyBlock(CupertinoSymbol.Airplane, apTr(l,"Flight","Авиабилет","Aviachipta","Авиачипта"), apTr(l,"The base package uses the most affordable fares. Tickets are non-refundable unless the selected fare explicitly says otherwise.","В базовый пакет включаются наиболее доступные тарифы. Такие билеты по умолчанию считаются невозвратными, если на конкретном тарифе прямо не указано иное.","Asosiy paketdagi aviachiptalar odatda qaytarilmaydi, agar tarifda boshqacha ko‘rsatilmagan bo‘lsa.","Асосий пакетдаги авиачипталар одатда қайтарилмайди, агар тарифда бошқача кўрсатилмаган бўлса."), apTr(l,"Non-refundable by default","По умолчанию без возврата","Odatda qaytarilmaydi","Одатда қайтарилмайди"), ApBlue),
        PolicyBlock(CupertinoSymbol.Building, apTr(l,"Hotel","Отель","Mehmonxona","Меҳмонхона"), apTr(l,"The base package uses a non-refundable hotel rate unless a specific option shows free cancellation.","Базовая цена пакета использует невозвратный тариф отеля, если в карточке конкретного варианта не указана бесплатная отмена.","Asosiy paketda mehmonxona tarifi qaytarilmaydi, agar bepul bekor qilish ko‘rsatilmagan bo‘lsa.","Асосий пакетда меҳмонхона тарифи қайтарилмайди, агар бепул бекор қилиш кўрсатилмаган бўлса."), apTr(l,"Non-refundable by default","По умолчанию без возврата","Odatda qaytarilmaydi","Одатда қайтарилмайди"), Color(0xFFAF52DE)),
        PolicyBlock(CupertinoSymbol.Car, apTr(l,"Transfer","Трансфер","Transfer","Трансфер"), apTr(l,"Cancel at least 5 days (120 hours) before vehicle pickup for a full refund. Later cancellations carry a \$100 fee; after service begins or no-show, it is non-refundable.","Отмена не позднее чем за 5 суток (120 часов) до подачи автомобиля — полный возврат. Позже удерживается \$100; после начала услуги или при неявке возврата нет.","Kamida 5 kun oldin bekor qilinsa to‘liq qaytariladi; keyin \$100 ushlab qolinadi.","Камида 5 кун олдин бекор қилинса тўлиқ қайтарилади; кейин \$100 ушлаб қолинади."), apTr(l,"Free until 5 days before","Бесплатно до 5 дней","5 kun oldin bepul","5 кун олдин бепул"), ApOrange),
        PolicyBlock(CupertinoSymbol.HeartFill, apTr(l,"iumrah services","Сервисы iumrah","iumrah xizmatlari","iumrah хизматлари"), apTr(l,"Unused iumrah services are fully refundable before that service begins. A service already started or delivered is treated as used.","Неиспользованные сервисы iumrah возвращаются полностью до начала соответствующей услуги. Уже начатая услуга считается использованной.","Foydalanilmagan iumrah xizmatlari boshlanishidan oldin to‘liq qaytariladi.","Фойдаланилмаган iumrah хизматлари бошланишидан олдин тўлиқ қайтарилади."), apTr(l,"Refundable before service starts","Возврат до начала услуги","Xizmat boshlanguncha qaytariladi","Хизмат бошлангунча қайтарилади"), ApCare),
        PolicyBlock(CupertinoSymbol.CalendarClock, apTr(l,"Terms are fixed at booking","Условия фиксируются при бронировании","Shartlar bron paytida belgilanadi","Шартлар брон пайтида белгиланади"), apTr(l,"If a specific fare, hotel rate or supplier shows different terms, those terms take priority and are displayed before payment.","Если конкретный авиатариф, отель или поставщик показывает другие условия, именно они имеют приоритет и отображаются перед оплатой.","Aniq tarif yoki hamkor boshqa shart ko‘rsatsa, o‘sha shartlar ustuvor bo‘ladi.","Аниқ тариф ёки ҳамкор бошқа шарт кўрсатса, ўша шартлар устувор бўлади."))
    )
    else -> listOf(
        PolicyBlock(CupertinoSymbol.ExclamationCircle, apTr(l,"Manual payment in the first release","Ручная оплата в первой версии","Birinchi versiyada qo‘lda to‘lov","Биринчи версияда қўлда тўлов"), apTr(l,"Payment is handled manually while the payment gateway is being introduced. Use only the details shown inside your booking.","Пока платёжный шлюз внедряется, оплата оформляется вручную. Используйте только реквизиты, показанные внутри Вашего бронирования.","To‘lov shlyuzi joriy etilguncha to‘lov qo‘lda amalga oshiriladi. Faqat bron ichidagi rekvizitlardan foydalaning.","Тўлов шлюзи жорий этилгунча тўлов қўлда амалга оширилади. Фақат брон ичидаги реквизитлардан фойдаланинг."), tint = ApOrange),
        PolicyBlock(CupertinoSymbol.NumberSquare, apTr(l,"Payment details stay inside the booking","Реквизиты только внутри бронирования","Rekvizitlar bron ichida","Реквизитлар брон ичида"), apTr(l,"Every payment instruction, invoice and confirmation is tied to a specific booking. Never use payment details received from an unrelated chat or third party.","Каждая инструкция, инвойс и подтверждение привязаны к конкретной брони. Не используйте реквизиты, полученные вне Вашего бронирования.","Har bir to‘lov ko‘rsatmasi aniq bronga bog‘langan.","Ҳар бир тўлов кўрсатмаси аниқ бронга боғланган.")),
        PolicyBlock(CupertinoSymbol.ShieldCheck, apTr(l,"Payment confirmation","Подтверждение оплаты","To‘lovni tasdiqlash","Тўловни тасдиқлаш"), apTr(l,"Upload the receipt only inside the booking. iumrah links the receipt, amount and booking before the status changes.","Загружайте чек только внутри бронирования. iumrah связывает чек, сумму и бронь до изменения статуса.","Chekni faqat bron ichida yuklang.","Чекни фақат брон ичида юкланг.")),
        PolicyBlock(CupertinoSymbol.LockShield, apTr(l,"Account protection","Защита аккаунта","Akkaunt himoyasi","Аккаунт ҳимояси"), apTr(l,"Your payment status and documents are visible only in the protected account or booking context.","Статус оплаты и документы доступны только в защищённом контексте аккаунта или бронирования.","To‘lov holati va hujjatlar faqat himoyalangan akkaunt yoki bron ichida ko‘rinadi.","Тўлов ҳолати ва ҳужжатлар фақат ҳимояланган аккаунт ёки брон ичида кўринади."))
    )
}

@Composable
private fun PolicyCard(block: PolicyBlock) {
    ApCard(radius = 26.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            ApBadge(block.icon, block.tint, 46.dp, 18.dp, 15.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(block.title, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
                block.badge?.let { Text(it, Modifier.clip(CircleShape).background(block.tint.copy(alpha = .10f)).padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = block.tint) }
                Text(block.body, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSignalsParityScreen(
    language: AppLanguage,
    notifications: ClientNotificationStore,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    val state by notifications.state.collectAsState()
    val scope = rememberCoroutineScope()
    var page by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { notifications.refresh(accountStore.bearerToken) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            if (!refreshing) scope.launch {
                refreshing = true
                try { notifications.refresh(accountStore.bearerToken) }
                finally { refreshing = false }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
    ApNavPage("iumrah Signal", chrome) {
        SignalPageSwitcher(language, page) { page = it }
        when (page) {
            0 -> {
                SignalHero(language, state.unreadCount, state.inbox.size)
                val unread = state.inbox.filter { !it.isRead }
                val earlier = state.inbox.filter { it.isRead }
                if (state.inbox.isEmpty()) {
                    ApCard(radius = 26.dp) {
                        ApBadge(CupertinoSymbol.BellSignal, ApBlue, 48.dp, 19.dp, 16.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(apTr(language, "No notifications yet", "Пока нет уведомлений", "Hali bildirishnomalar yo‘q", "Ҳали билдиришномалар йўқ"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(apTr(language, "When iumrah sends a Signal, it will appear here and on the Home screen.", "Когда iumrah отправит Signal, он появится здесь и на главной странице.", "iumrah Signal yuborganda, u shu yerda va Asosiy sahifada ko‘rinadi.", "iumrah Signal юборганда, у шу ерда ва Асосий саҳифада кўринади."), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    if (unread.isNotEmpty()) SignalSection(language, apTr(language,"New","Новые","Yangi","Янги"), apTr(language,"Needs your attention","То, что стоит посмотреть","E’tibor berish kerak","Эътибор бериш керак"), unread, true, notifications, accountStore, chrome, scope, state.dismissedHomeIDs)
                    if (earlier.isNotEmpty()) SignalSection(language, apTr(language,"Earlier","Ранее","Avvalgi","Аввалги"), apTr(language,"Already opened","Уже просмотрено","Ko‘rib chiqilgan","Кўриб чиқилган"), earlier, false, notifications, accountStore, chrome, scope, state.dismissedHomeIDs)
                }
            }
            1 -> {
                SignalPageHeading(
                    apTr(language,"Next-trip reminders","Напоминания о следующей поездке","Keyingi safar eslatmalari","Кейинги сафар эслатмалари"),
                    apTr(language,"Plan your Umrah and choose exactly when iumrah should remind you as the journey gets closer.","Запланируйте Umrah и выберите, когда именно iumrah должен напоминать Вам по мере приближения поездки.","Umrani rejalashtiring va safar yaqinlashgani sari iumrah qachon eslatishini o‘zingiz belgilang.","Умрани режалаштиринг ва сафар яқинлашгани сари iumrah қачон эслатишини ўзингиз белгиланг.")
                )
                com.iumrah.beta.ui.planning.UmrahPlanReminderCenter(language) { chrome.openUmrahPlan() }
            }
            else -> {
                SignalPageHeading(
                    "Telegram",
                    apTr(language,"Connect a booking once and receive status, payment, confirmation and document updates in Telegram.","Подключите бронирование один раз и получайте в Telegram изменения статуса, оплаты, подтверждения и документов.","Bronni bir marta ulang va Telegram’da status, to‘lov, tasdiq hamda hujjat yangiliklarini oling.","Бронни бир марта уланг ва Telegram’да статус, тўлов, тасдиқ ҳамда ҳужжат янгиликларини олинг.")
                )
                ApCard(radius = 26.dp) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        ApBadge(CupertinoSymbol.Send, Color(0xFF269ED9), 48.dp, 20.dp, 16.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(apTr(language,"Booking status in Telegram","Статус бронирования в Telegram","Bron holati Telegram’da","Брон ҳолати Telegram’да"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(apTr(language,"The secure one-time link is created for a specific booking and expires automatically.","Безопасная одноразовая ссылка создаётся для конкретной брони и автоматически истекает.","Xavfsiz bir martalik havola aniq bron uchun yaratiladi va avtomatik tugaydi.","Хавфсиз бир марталик ҳавола аниқ брон учун яратилади ва автоматик тугайди."), fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { chrome.openAccountTelegramIntegration() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF269ED9))) {
                        Text(apTr(language,"Connect Telegram","Подключить Telegram","Telegram’ni ulash","Telegram’ни улаш"), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), Color.White)
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SignalPageSwitcher(language: AppLanguage, selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        Triple(apTr(language,"Signal","Сигнал","Signal","Сигнал"), CupertinoSymbol.BellBadge, 0),
        Triple(apTr(language,"Reminders","Напоминания","Eslatmalar","Эслатмалар"), CupertinoSymbol.CalendarClock, 1),
        Triple("Telegram", CupertinoSymbol.Send, 2),
    )
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        labels.forEach { (title, icon, index) ->
            Surface(onClick = { onSelect(index) }, modifier = Modifier.weight(1f).height(45.dp), shape = RoundedCornerShape(17.dp), color = if (selected == index) MaterialTheme.colorScheme.surface.copy(alpha=.96f) else Color.Transparent, shadowElevation = if (selected == index) 1.dp else 0.dp) {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (index == 2) {
                        Image(painterResource(R.drawable.iumrah_telegram_mark), null, Modifier.size(20.dp))
                    } else {
                        CupertinoIcon(icon, null, Modifier.size(13.dp), if (selected == index) MaterialTheme.colorScheme.onSurface else ApGray)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = if (selected == index) MaterialTheme.colorScheme.onSurface else ApGray, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SignalPageHeading(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 27.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(body, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SignalHero(language: AppLanguage, unread: Int, total: Int) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF12358F), Color(0xFF2B63E0), Color(0xFF5E4FCA)))).padding(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(17.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("iumrah Signal", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .7.sp, color = Color.White.copy(alpha=.72f))
                    Text(apTr(language,"Everything important about your journey","Всё важное по вашей поездке","Safaringiz bo‘yicha barcha muhim xabarlar","Сафарингиз бўйича барча муҳим хабарлар"), fontSize = 29.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(apTr(language,"Booking changes, trip updates and reminders stay here even after a push notification disappears.","Изменения бронирования, новости поездки и напоминания остаются здесь, даже когда push уже исчез.","Bron o‘zgarishlari, safar yangiliklari va eslatmalar push yo‘qolgandan keyin ham shu yerda qoladi.","Брон ўзгаришлари, сафар янгиликлари ва эслатмалар push йўқолгандан кейин ҳам шу ерда қолади."), fontSize = 13.sp, lineHeight = 18.sp, color = Color.White.copy(alpha=.78f))
                }
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha=.10f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.BellBadge, null, Modifier.size(20.dp), Color.White) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalStatChip("$unread", apTr(language,"new","новых","yangi","янги"), unread > 0)
                SignalStatChip("$total", apTr(language,"total","всего","jami","жами"), false)
            }
        }
    }
}

@Composable
private fun SignalStatChip(value: String, title: String, emphasized: Boolean) {
    Row(Modifier.clip(CircleShape).background(Color.White.copy(alpha=if(emphasized).16f else .09f)).padding(horizontal=13.dp, vertical=8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
        Text(title, color = Color.White.copy(alpha = if (emphasized) 1f else .78f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SignalSection(
    language: AppLanguage,
    title: String,
    subtitle: String,
    rows: List<ClientSystemNotification>,
    unreadSection: Boolean,
    notifications: ClientNotificationStore,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
    scope: kotlinx.coroutines.CoroutineScope,
    dismissed: Set<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) { Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = ApGray) }
            Text("${rows.size}", Modifier.clip(CircleShape).background((if(unreadSection) ApBlue else ApGray).copy(alpha=.10f)).padding(horizontal=10.dp, vertical=7.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, color = if(unreadSection) ApBlue else ApGray)
        }
        rows.forEach { n -> SignalParityCard(language, n, dismissed.contains(n.id), notifications, accountStore, chrome, scope) }
    }
}

// Mirrors AccountNotificationsView.destinationIcon and its locale-sensitive date formatter.
private fun signalDestinationIcon(destination: String): CupertinoSymbol = when (destination) {
    "hotels" -> CupertinoSymbol.BuildingFill
    "bookings", "booking" -> CupertinoSymbol.SuitcaseFill
    "care" -> CupertinoSymbol.HeartFill
    "account" -> CupertinoSymbol.PersonCircleFill
    else -> CupertinoSymbol.Home
}

private fun signalDestinationTitle(language: AppLanguage, destination: String): String = when (destination) {
    "hotels" -> apTr(language,"Hotels","Отели","Mehmonxonalar","Меҳмонхоналар")
    "bookings" -> apTr(language,"Trips","Поездки","Safarlar","Сафарлар")
    "booking" -> apTr(language,"Trip details","Детали поездки","Safar tafsilotlari","Сафар тафсилотлари")
    "care" -> "iumrah Care"
    "account" -> "Account"
    else -> apTr(language,"Home","Главная","Asosiy","Асосий")
}

private fun signalTimestamp(language: AppLanguage, raw: String): String {
    val instant = runCatching { Instant.parse(raw) }
        .recoverCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull() ?: return raw
    val local = instant.atZone(ZoneId.systemDefault())
    val locale = language.locale
    val day = local.toLocalDate()
    val time = local.format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return when (day) {
        LocalDate.now() -> "${apTr(language,"Today","Сегодня","Bugun","Бугун")}, $time"
        LocalDate.now().minusDays(1) -> "${apTr(language,"Yesterday","Вчера","Kecha","Кеча")}, $time"
        else -> local.format(DateTimeFormatter.ofPattern("d MMM · HH:mm", locale))
    }
}

@Composable
private fun SignalParityCard(language: AppLanguage, n: ClientSystemNotification, hidden: Boolean, notifications: ClientNotificationStore, accountStore: IumrahAccountStore, chrome: AppChromeStore, scope: kotlinx.coroutines.CoroutineScope) {
    val unread = !n.isRead
    val tint = if (unread) ApBlue else ApGray
    Surface(shape = RoundedCornerShape(26.dp), color = if(unread) ApBlue.copy(alpha=.065f) else MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(1.dp, if(unread) ApBlue.copy(alpha=.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha=.055f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ApBadge(signalDestinationIcon(n.destination), tint, 44.dp, 16.dp, 14.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row { Text(n.title, Modifier.weight(1f), fontSize = 17.sp, lineHeight = 20.sp, fontWeight = if(unread) FontWeight.Bold else FontWeight.SemiBold); if(unread){Spacer(Modifier.width(8.dp));Box(Modifier.padding(top=6.dp).size(8.dp).clip(CircleShape).background(ApBlue))} }
                    Text(signalTimestamp(language, n.sentAt ?: n.createdAt), fontSize = 11.sp, color = ApGray)
                }
            }
            Text(n.body, fontSize = 14.sp, lineHeight = 20.sp, color = if(unread) MaterialTheme.colorScheme.onSurface.copy(alpha=.78f) else ApGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(signalDestinationTitle(language, n.destination), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ApGray)
                if (hidden) {
                    Spacer(Modifier.width(8.dp))
                    Text(apTr(language,"Hidden on Home","Скрыто на главной","Asosiyda yashirilgan","Асосийда яширилган"), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = ApGray,
                        modifier = Modifier.clip(CircleShape).background(ApGray.copy(alpha = .10f)).padding(horizontal = 8.dp, vertical = 5.dp))
                }
                Spacer(Modifier.weight(1f))
                Surface(onClick = { if(hidden) notifications.restoreToHome(n.id) else notifications.dismissFromHome(n.id) }, shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)) { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { CupertinoIcon(if(hidden) CupertinoSymbol.Eye else CupertinoSymbol.EyeSlash, null, Modifier.size(14.dp), ApGray) } }
            }
            Button(onClick = {
                scope.launch { notifications.markOpened(n, accountStore.bearerToken) }
                when (n.destination) {
                    "hotels" -> chrome.navigate(AppTab.HOTELS)
                    "bookings" -> chrome.navigate(AppTab.BOOKING)
                    "care" -> chrome.navigate(AppTab.CARE)
                    "account" -> chrome.navigate(AppTab.ACCOUNT)
                    "booking" -> n.destinationBookingID?.let(chrome::openBookingDetail) ?: chrome.navigate(AppTab.BOOKING)
                    else -> chrome.navigate(AppTab.HOME)
                }
            }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = if(unread) ApBlue else MaterialTheme.colorScheme.onSurface.copy(alpha=.07f), contentColor = if(unread) Color.White else MaterialTheme.colorScheme.onSurface)) {
                Text(apTr(language,"Open","Открыть","Ochish","Очиш"), Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(15.dp), if(unread) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun AccountAppearanceParityScreen(language: AppLanguage, settingsStore: AppSettingsStore, chrome: AppChromeStore) {
    val settings by settingsStore.state.collectAsState()
    val icons = listOf(
        Triple("blue", "Blue", R.drawable.iumrah_icon_blue_preview),
        Triple("cyan", "Cyan", R.drawable.iumrah_icon_cyan_preview),
        Triple("deep", "Deep", R.drawable.iumrah_icon_deep_preview),
        Triple("world", "World", R.drawable.iumrah_icon_world_preview),
        Triple("makkah", "Makkah", R.drawable.iumrah_icon_makkah_preview),
    )
    ApNavPage(apTr(language,"Appearance","Оформление","Ko‘rinish","Кўриниш"), chrome) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceSectionTitle(
                apTr(language,"Theme","Тема","Mavzu","Мавзу"),
                apTr(language,"Choose how iumrah looks on this Android phone.","Выберите, как iumrah выглядит на этом Android-телефоне.","iumrah ushbu Android telefonda qanday ko‘rinishini tanlang.","iumrah ушбу Android телефонда қандай кўринишини танланг.")
            )
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=.06f))) {
                Column {
                    AppearanceParityRow(CupertinoSymbol.HalfCircle, apTr(language,"System","Системная","Tizim","Тизим"), settings.appearance == AppAppearance.SYSTEM) { settingsStore.setAppearance(AppAppearance.SYSTEM) }
                    HorizontalDivider(Modifier.padding(start=70.dp))
                    AppearanceParityRow(CupertinoSymbol.Sun, apTr(language,"Light","Светлая","Yorug‘","Ёруғ"), settings.appearance == AppAppearance.LIGHT) { settingsStore.setAppearance(AppAppearance.LIGHT) }
                    HorizontalDivider(Modifier.padding(start=70.dp))
                    AppearanceParityRow(CupertinoSymbol.Moon, apTr(language,"Dark","Тёмная","Qorong‘i","Қоронғи"), settings.appearance == AppAppearance.DARK) { settingsStore.setAppearance(AppAppearance.DARK) }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppearanceSectionTitle(
                apTr(language,"App icon","Иконка приложения","Ilova ikonkasi","Илова иконкаси"),
                apTr(language,"Choose how iumrah appears on your Home Screen.","Выберите, как iumrah выглядит на главном экране.","iumrah bosh ekranda qanday ko‘rinishini tanlang.","iumrah бош экранда қандай кўринишини танланг.")
            )
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha=.06f))) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        icons.forEach { (key, name, res) ->
                            val selected = settings.launcherIcon == key
                            Column(Modifier.width(92.dp).clickable(enabled = !selected) { settingsStore.setLauncherIcon(key) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Box {
                                    Image(painterResource(res), name, Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).border(if(selected) 1.5.dp else 1.dp, if(selected) MaterialTheme.colorScheme.onSurface.copy(alpha=.30f) else MaterialTheme.colorScheme.onSurface.copy(alpha=.07f), RoundedCornerShape(22.dp)), contentScale = ContentScale.Crop)
                                    if (selected) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha=.82f)).border(1.dp, Color.White.copy(alpha=.24f), CircleShape), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(12.dp), Color.White) }
                                }
                                Text(name, fontSize = 11.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.SemiBold, color = if(selected) MaterialTheme.colorScheme.onSurface else ApGray, maxLines = 1)
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable(enabled = settings.launcherIcon != "standard") { settingsStore.setLauncherIcon("standard") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Refresh, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(apTr(language,"Standard icon","Стандартная иконка","Standart ikonka","Стандарт иконка"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(apTr(language,"Restore the original iumrah icon","Вернуть исходную иконку iumrah","Asl iumrah ikonkasini qaytarish","Асл iumrah иконкасини қайтариш"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        CupertinoIcon(if(settings.launcherIcon == "standard") CupertinoSymbol.CheckCircleFill else CupertinoSymbol.ChevronRight, null, Modifier.size(if(settings.launcherIcon == "standard") 20.dp else 13.dp), if(settings.launcherIcon == "standard") ApCare else ApGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSectionTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal=4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.25).sp)
        Text(subtitle, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceParityRow(icon: CupertinoSymbol, title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min=62.dp).clickable(onClick=onClick).padding(horizontal=16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        ApBadge(icon, ApCare, 42.dp, 17.dp, 14.dp)
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if(selected) CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(21.dp), ApCare)
    }
}

@Composable
fun AccountProfileEditorParityScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    val state by accountStore.state.collectAsState()
    val profile = state.account
    val scope = rememberCoroutineScope()
    var firstName by remember(profile) { mutableStateOf(profile?.firstName.orEmpty()) }
    var lastName by remember(profile) { mutableStateOf(profile?.lastName.orEmpty()) }
    var phone by remember(profile) { mutableStateOf(profile?.phone.orEmpty()) }
    var email by remember(profile) { mutableStateOf(profile?.email.orEmpty()) }
    var telegram by remember(profile) { mutableStateOf(profile?.telegram.orEmpty()) }
    var whatsapp by remember(profile) { mutableStateOf(profile?.whatsapp.orEmpty()) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = ApPad, end = ApPad, top = 12.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { chrome.back() }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(apTr(language, "Close", "Закрыть", "Yopish", "Ёпиш"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.weight(1f))
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    apTr(language, "Account details", "Данные аккаунта", "Akkaunt ma’lumotlari", "Аккаунт маълумотлари"),
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.45).sp,
                )
                Text(
                    apTr(
                        language,
                        "These details are reused for future Iumrah trips.",
                        "Эти данные будут использоваться для Ваших следующих поездок Iumrah.",
                        "Bu ma’lumotlar keyingi Iumrah safarlarida ishlatiladi.",
                        "Бу маълумотлар кейинги Iumrah сафарларида ишлатилади.",
                    ),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountParityField(firstName, { firstName = it }, apTr(language,"First name","Имя","Ism","Исм"))
                    AccountParityField(lastName, { lastName = it }, apTr(language,"Last name","Фамилия","Familiya","Фамилия"))
                    AccountParityField(phone, { phone = it }, apTr(language,"Phone","Телефон","Telefon","Телефон"), KeyboardType.Phone)
                    AccountParityField(email, { email = it }, "Email", KeyboardType.Email)
                    AccountParityField(telegram, { telegram = it }, "Telegram")
                    AccountParityField(whatsapp, { whatsapp = it }, "WhatsApp", KeyboardType.Phone)
                }
            }
        }
        message?.let { text ->
            item {
                Text(text, fontSize = 13.sp, color = if (text == apTr(language,"Saved","Сохранено","Saqlandi","Сақланди")) ApGreen else ApRed)
            }
        }
        item {
            Button(
                onClick = {
                    saving = true
                    message = null
                    scope.launch {
                        runCatching {
                            accountStore.updateProfile(firstName.trim(), lastName.trim(), phone.trim(), email.trim(), telegram.trim(), whatsapp.trim())
                        }.onSuccess {
                            message = apTr(language,"Saved","Сохранено","Saqlandi","Сақланди")
                            chrome.back()
                        }.onFailure {
                            message = it.message ?: apTr(language,"Could not save account details.","Не удалось сохранить данные аккаунта.","Akkaunt ma’lumotlarini saqlab bo‘lmadi.","Аккаунт маълумотларини сақлаб бўлмади.")
                        }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = firstName.trim().isNotEmpty() && lastName.trim().isNotEmpty() && !saving,
                shape = RoundedCornerShape(19.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 18.dp),
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                }
                CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(18.dp), Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    apTr(language,"Save account details","Сохранить данные аккаунта","Akkaunt ma’lumotlarini saqlash","Аккаунт маълумотларини сақлаш"),
                    Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
private fun AccountParityField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        placeholder = { Text(placeholder, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .035f),
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .025f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}
