package com.iumrah.beta.ui.booking

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.network.APIClient
import com.iumrah.beta.core.design.IumrahBookingStatusVisual
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.chat.IumrahPublicProfile
import com.iumrah.beta.models.booking.BookingGeneratorFlightSnapshot
import com.iumrah.beta.models.booking.BookingHotelSelectionSnapshot
import com.iumrah.beta.models.booking.BookingItineraryItem
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BookingDetailScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    accountStore: IumrahAccountStore,
    accountService: IumrahAccountService,
    chrome: AppChromeStore,
    initialPage: BookingPrimaryPageAndroid = BookingPrimaryPageAndroid.BOOKING,
) {
    val storeState by bookingStore.state.collectAsState()
    val session = storeState.sessions.firstOrNull { it.id == bookingID }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val careService = remember { ChatService(APIClient()) }
    var careProfile by remember(bookingID) { mutableStateOf<IumrahPublicProfile?>(null) }
    var selectedPage by remember(bookingID, initialPage) { mutableStateOf(initialPage) }
    var itinerary by remember(bookingID) { mutableStateOf<List<BookingItineraryItem>>(emptyList()) }
    var telegram by remember(session?.telegram) { mutableStateOf(session?.telegram.orEmpty()) }
    var whatsapp by remember(session?.whatsapp) { mutableStateOf(session?.whatsapp.orEmpty()) }
    var editContacts by remember { mutableStateOf(false) }
    var deletePrompt by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookingID) {
        careProfile = runCatching { careService.loadCareProfile() }.getOrNull()
        val refreshed = bookingStore.refresh(bookingID) ?: return@LaunchedEffect
        itinerary = runCatching {
            bookingStore.service.fetchItinerary(bookingID, bookingStore.headersFor(refreshed))
        }.getOrDefault(emptyList()).let { localizeServerItinerary(it, language) }
    }

    if (session == null) {
        Box(
            Modifier.fillMaxSize().background(bookingIosPage()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(32.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                Text(L10n.text("detail_not_found", language), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }

    if (deletePrompt) {
        AlertDialog(
            onDismissRequest = { deletePrompt = false },
            title = { Text(bookingText(language, "Удалить бронирование?", "Delete booking?", "Bronni o‘chirasizmi?", "Бронни ўчирасизми?")) },
            text = { Text(bookingText(language, "Это действие нельзя отменить.", "This action cannot be undone.", "Bu amalni bekor qilib bo‘lmaydi.", "Бу амални бекор қилиб бўлмайди.")) },
            confirmButton = {
                TextButton(onClick = {
                    deletePrompt = false
                    scope.launch {
                        runCatching { bookingStore.deleteBooking(bookingID) }
                            .onSuccess { chrome.back() }
                            .onFailure { error = it.message }
                    }
                }) { Text(bookingText(language, "Удалить", "Delete", "O‘chirish", "Ўчириш"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletePrompt = false }) { Text(bookingText(language, "Отмена", "Cancel", "Bekor qilish", "Бекор қилиш")) } },
            shape = RoundedCornerShape(28.dp),
            containerColor = bookingIosCard(),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(bookingIosPage())
            .statusBarsPadding(),
    ) {
        BookingNavigationBar(
            title = L10n.text("booking_detail_title", language),
            subtitle = buildString {
                append(bookingText(language, "Бронь ", "Booking ", "Bron ", "Брон "))
                append(session.displayBookingNumber)
                session.pilgrimID?.takeIf { it.isNotBlank() }?.let { append(" · iumrah ID ").append(it) }
            },
            chrome = chrome,
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BOOKING_PAGE_PADDING.dp)
                .padding(top = 12.dp, bottom = 56.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookingPageSwitcher(language = language, selected = selectedPage, onSelected = { selectedPage = it })

            if (selectedPage == BookingPrimaryPageAndroid.BOOKING) {
                BookingIdentityStrip(session, language)
                IumrahBookingDomeCard(session = session, language = language)
                BookingStatusHero(session, language, chrome)
                if (session.effectiveStatus.uppercase() == "PAYMENT_PENDING") {
                    BookingManualPaymentNotice(language)
                    BookingRefundPolicyCompact(language)
                    BookingInvoiceCompact(session, language)
                }
                BookingMetaCard(session, language)

                BookingItineraryCalendarAndroid(
                    language = language,
                    session = session,
                    remoteItems = itinerary,
                    fullScreen = false,
                )

                BookingFlightFirstComponents(session, language, chrome)

                BookingContactCard(
                    session = session,
                    language = language,
                    telegram = telegram,
                    whatsapp = whatsapp,
                    editing = editContacts,
                    onEditing = { editContacts = it },
                    onTelegram = { telegram = it },
                    onWhatsapp = { whatsapp = it },
                    onSave = {
                        scope.launch {
                            runCatching { bookingStore.updateContacts(bookingID, telegram, whatsapp) }
                                .onSuccess { editContacts = false }
                                .onFailure { error = it.message }
                        }
                    },
                )

                if (session.pendingChangeConfirmation == true) {
                    BookingPendingConfirmationCard(language)
                }

                IumrahPressable(
                    onClick = { deletePrompt = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    cornerRadius = 20.dp,
                    background = MaterialTheme.colorScheme.error.copy(alpha = .07f),
                    pressedScale = .985f,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        CupertinoIcon(CupertinoSymbol.TrashSlash, null, Modifier.size(18.dp), MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            L10n.text("booking_cancel_and_delete", language),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                BookingCareBalanceCard(
                    language = language,
                    careProfile = careProfile,
                    onCall = {
                        val digits = "+998508898845".filter { it.isDigit() || it == '+' }
                        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits"))) }
                    },
                    onTelegram = {
                        careProfile?.telegram?.let { raw ->
                            val cleaned = raw.trim().removePrefix("https://t.me/").removePrefix("http://t.me/").removePrefix("t.me/").trim('@', '/', ' ')
                            if (cleaned.isNotBlank()) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$cleaned"))) }
                        }
                    },
                    onChat = { chrome.openBookingChat(bookingID) },
                )

                BookingTelegramCompactCard(session, language)
            } else if (selectedPage == BookingPrimaryPageAndroid.STATUS) {
                PilgrimCheckoutEmbedded(
                    bookingID = bookingID,
                    language = language,
                    session = session,
                    bookingStore = bookingStore,
                    accountStore = accountStore,
                    accountService = accountService,
                    chrome = chrome,
                )
            } else {
                BookingItineraryCalendarAndroid(
                    language = language,
                    session = session,
                    remoteItems = itinerary,
                    fullScreen = true,
                )
            }

            error?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
private fun BookingIdentityStrip(session: StoredBookingSession, language: AppLanguage) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(bookingIosRaised().copy(alpha = .72f))
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdentityBlock(
            label = bookingText(language, "Booking ID", "Booking ID", "Booking ID", "Booking ID"),
            value = session.displayBookingNumber,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.padding(horizontal = 10.dp).width(.7.dp).height(42.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .11f)))
        IdentityBlock(
            label = "iumrah ID",
            value = session.pilgrimID?.takeIf { it.isNotBlank() } ?: "—",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun IdentityBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
            Text(value, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(34.dp).clip(CircleShape).background(bookingIosCard()), contentAlignment = Alignment.Center) {
            CupertinoIcon(CupertinoSymbol.Copy, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = if (value == "—") .28f else .72f))
        }
    }
}

@Composable
private fun BookingStatusHero(session: StoredBookingSession, language: AppLanguage, chrome: AppChromeStore) {
    val status = session.effectiveStatus.uppercase()
    val (symbol, tint) = bookingStatusVisual(status)
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    session.travelerName?.takeIf { it.isNotBlank() } ?: L10n.text("booking_your_trip", language),
                    fontSize = 27.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.35).sp,
                )
                Text(bookingStatusText(status, language), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
            }
            BookingIconBadge(symbol = symbol, tint = tint, size = 52.dp, symbolSize = 22.dp, radius = 26.dp, circle = true)
        }

        Spacer(Modifier.height(14.dp))
        Text(L10n.text("detail_updates", language), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))

        BookingLifecycleTimer(session, language)?.let { phase ->
            Spacer(Modifier.height(14.dp))
            LifecycleTimerCard(phase, language)
        }

        when (status) {
            "PAYMENT_PENDING" -> {
                Spacer(Modifier.height(14.dp))
                IumrahPressable(
                    onClick = { chrome.openBookingSecurity(session.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    cornerRadius = 22.dp,
                    background = bookingIosRaised(),
                    pressedScale = .985f,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                            CupertinoIcon(CupertinoSymbol.LockShield, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(bookingText(language, "Подтвердить личность", "Confirm identity", "Shaxsni tasdiqlash", "Шахсни тасдиқлаш"), fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text("iUmrah Security · ${bookingText(language, "защищённое бронирование", "protected booking", "himoyalangan bron", "ҳимояланган брон")}", fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                        }
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .35f))
                    }
                }
                Spacer(Modifier.height(10.dp))
                IumrahPressable(
                    onClick = { chrome.openPilgrimCheckout(session.id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp),
                    cornerRadius = 22.dp,
                    background = Color.Black,
                    pressedScale = .985f,
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                            CupertinoIcon(CupertinoSymbol.IdentityCard, null, Modifier.size(18.dp), Color.White)
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(bookingText(language, "Заполнить данные и оплатить", "Complete details and pay", "Ma’lumotlarni to‘ldirish va to‘lash", "Маълумотларни тўлдириш ва тўлаш"), color = Color.White, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(bookingText(language, "iumrah ID · анкеты · реквизиты · чек", "iumrah ID · pilgrim forms · payment · receipt", "iumrah ID · anketalar · to‘lov · chek", "iumrah ID · анкеталар · тўлов · чек"), color = Color.White.copy(alpha = .72f), fontSize = 12.sp, lineHeight = 15.sp)
                        }
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), Color.White.copy(alpha = .88f))
                    }
                }
            }
            "BOOKING_CONFIRMED", "READY_TO_TRAVEL", "IN_TRIP" -> {
                Spacer(Modifier.height(14.dp))
                IumrahPressable(
                    onClick = { chrome.openPilgrimCheckout(session.id) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    cornerRadius = 17.dp,
                    background = bookingIosRaised(),
                    pressedScale = .985f,
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CupertinoIcon(CupertinoSymbol.Document, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onBackground)
                        Spacer(Modifier.width(9.dp))
                        Text(bookingText(language, "Данные и документы поездки", "Trip details and documents", "Safar ma’lumotlari va hujjatlar", "Сафар маълумотлари ва ҳужжатлар"), Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
                    }
                }
            }
        }
    }
}

private data class BookingDetailLifecyclePhase(val kind: String, val deadlineMillis: Long, val tint: Color, val symbol: CupertinoSymbol)

@Composable
private fun BookingLifecycleTimer(session: StoredBookingSession, language: AppLanguage): BookingDetailLifecyclePhase? {
    val status = session.effectiveStatus.uppercase()
    fun parsed(raw: String?): Long? = raw?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    fun deadline(explicit: String?, start: String?, seconds: Long): Long? = parsed(explicit) ?: parsed(start)?.plus(seconds * 1000)
    return when (status) {
        "NEW", "AVAILABILITY_CHECK" -> deadline(
            session.availabilityDeadlineAt,
            session.availabilityStartedAt ?: session.latestStatusTimestamp(setOf("availability_check", "new")) ?: session.booking.createdAt,
            6 * 60 * 60,
        )?.let { BookingDetailLifecyclePhase("availability", it, Color(0xFFF2A900), CupertinoSymbol.Hourglass) }
        "PAYMENT_PENDING" -> if (!session.paymentReceivedAt.isNullOrBlank()) {
            deadline(session.paymentConfirmationDeadlineAt, session.paymentReceivedAt, 10 * 60)?.let {
                BookingDetailLifecyclePhase("confirmation", it, Color(0xFF34C759), CupertinoSymbol.CheckCircle)
            }
        } else {
            deadline(
                session.priceLockExpiresAt,
                session.priceLockStartedAt ?: session.latestStatusTimestamp(setOf("payment_pending")) ?: session.booking.updatedAt,
                30 * 60,
            )?.let { BookingDetailLifecyclePhase("price", it, Color(0xFF007AFF), CupertinoSymbol.CalendarClock) }
        }
        "PAID", "BOOKING_CONFIRMED" -> deadline(
            session.documentsDeadlineAt,
            session.documentsStartedAt ?: session.latestStatusTimestamp(setOf("booking_confirmed", "paid")) ?: session.booking.updatedAt,
            24 * 60 * 60,
        )?.let { BookingDetailLifecyclePhase("documents", it, Color(0xFF5856D6), CupertinoSymbol.Document) }
        else -> null
    }
}

@Composable
private fun LifecycleTimerCard(phase: BookingDetailLifecyclePhase, language: AppLanguage) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase.deadlineMillis) {
        while (true) { delay(1000); now = System.currentTimeMillis() }
    }
    val remaining = (phase.deadlineMillis - now).coerceAtLeast(0)
    val expired = remaining <= 0
    val seconds = remaining / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val countdown = "%02d:%02d:%02d".format(h, m, s)
    val title = when (phase.kind) {
        "availability" -> bookingText(language, if (expired) "Проверка занимает дольше обычного" else "До максимального срока проверки", if (expired) "The check is taking longer than usual" else "Until the maximum check time", "Mavjudlik tekshiruvi", "Мавжудлик текшируви")
        "price" -> bookingText(language, if (expired) "Срок фиксации цены завершён" else "Цена зафиксирована ещё", if (expired) "Price hold has ended" else "Price held for", "Narx saqlanishi", "Нарх сақланиши")
        "confirmation" -> bookingText(language, "Подтверждаем оплату", "Confirming payment", "To‘lov tasdiqlanmoqda", "Тўлов тасдиқланмоқда")
        else -> bookingText(language, "Плановый срок подготовки", "Planned preparation time", "Tayyorlash muddati", "Тайёрлаш муддати")
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.onBackground.copy(alpha = .035f)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(if (expired) "00:00:00" else countdown, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = phase.tint)
            }
            BookingIconBadge(phase.symbol, phase.tint, size = 40.dp, symbolSize = 17.dp, radius = 14.dp)
        }
        Text(
            when (phase.kind) {
                "availability" -> bookingText(language, "Обычно подтверждение занимает 1–2 часа. Статус обновится автоматически.", "Confirmation usually takes 1–2 hours. Status updates automatically.", "Tasdiqlash odatda 1–2 soat davom etadi. Holat avtomatik yangilanadi.", "Тасдиқлаш одатда 1–2 соат давом этади. Ҳолат автоматик янгиланади.")
                "price" -> bookingText(language, "Авиабилеты имеют динамическую стоимость и позже могут потребовать повторной проверки.", "Flights have dynamic pricing and may require a fresh check later.", "Aviachiptalar narxi dinamik va keyin qayta tekshiruv talab qilishi mumkin.", "Авиачипталар нархи динамик ва кейин қайта текширув талаб қилиши мумкин.")
                "confirmation" -> bookingText(language, "Оплата получена. Проверка обычно занимает до 10 минут.", "Payment received. Verification usually takes up to 10 minutes.", "To‘lov qabul qilindi. Tekshiruv odatda 10 daqiqagacha.", "Тўлов қабул қилинди. Текширув одатда 10 дақиқагача.")
                else -> bookingText(language, "Доступные документы обычно готовятся в течение 24 часов.", "Available documents are usually prepared within 24 hours.", "Hujjatlar odatda 24 soat ichida tayyorlanadi.", "Ҳужжатлар одатда 24 соат ичида тайёрланади.")
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
    }
}

@Composable
internal fun BookingManualPaymentNotice(language: AppLanguage) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFFFCC00).copy(alpha = .14f)).border(.8.dp, Color(0xFFFFCC00).copy(alpha = .38f), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Gear, Color(0xFFFF9500), 42.dp, 17.dp, 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bookingText(language, "Временная ручная оплата", "Temporary manual payment", "Vaqtinchalik qo‘lda to‘lov", "Вақтинчалик қўлда тўлов"), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                Text(bookingText(language, "Первые 35 дней запуска", "First 35 days after launch", "Ishga tushgandan keyingi dastlabki 35 kun", "Ишга тушгандан кейинги дастлабки 35 кун"), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
        Text(bookingText(language, "Платёжный провайдер проходит серверное подключение. Пока оплата выполняется вручную только по реквизитам внутри Вашего бронирования.", "The payment provider is undergoing server integration. Until then, payment is made manually only using the details inside your booking.", "To‘lov provayderi serverga ulanmoqda. Hozircha to‘lov faqat bron ichidagi rekvizitlar bo‘yicha qo‘lda amalga oshiriladi.", "Тўлов провайдери серверга уланмоқда. Ҳозирча тўлов фақат брон ичидаги реквизитлар бўйича қўлда амалга оширилади."), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .56f))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { CupertinoIcon(CupertinoSymbol.Document, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.52f)); Text(bookingText(language,"Инвойс","Invoice","Invoice","Invoice"), fontSize=12.sp, fontWeight=FontWeight.SemiBold, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.52f)) }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) { CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.52f)); Text(bookingText(language,"Чек","Receipt","Chek","Чек"), fontSize=12.sp, fontWeight=FontWeight.SemiBold, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.52f)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(bookingText(language,"Как защищена ручная оплата","How manual payment is protected","Qo‘lda to‘lov qanday himoyalanadi","Қўлда тўлов қандай ҳимояланади"), Modifier.weight(1f), fontSize=12.sp, lineHeight=15.sp, fontWeight=FontWeight.Bold)
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(11.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.30f))
        }
    }
}

@Composable
internal fun BookingRefundPolicyCompact(language: AppLanguage) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bookingIosCard()).border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha=.075f), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookingIconBadge(CupertinoSymbol.UndoCircle, Color(0xFFFF9500), 40.dp, 16.dp, 13.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(bookingText(language,"Политика возврата пакета","Package refund policy","Paketni qaytarish siyosati","Пакетни қайтариш сиёсати"), fontSize=14.sp, lineHeight=18.sp, fontWeight=FontWeight.SemiBold)
            Text(bookingText(language,"Условия возврата зависят от компонентов бронирования","Refund terms depend on the booking components","Qaytarish shartlari bron qismlariga bog‘liq","Қайтариш шартлари брон қисмларига боғлиқ"), fontSize=12.sp, lineHeight=15.sp, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.52f), maxLines=2)
        }
        CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.28f))
    }
}

@Composable
internal fun BookingInvoiceCompact(session: StoredBookingSession, language: AppLanguage) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bookingIosCard()).border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha=.075f), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Document, Color(0xFF007AFF), 40.dp, 17.dp, 14.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bookingText(language,"Инвойс бронирования","Booking invoice","Bron invoice","Брон invoice"), fontSize=14.sp, lineHeight=18.sp, fontWeight=FontWeight.SemiBold)
                Text(bookingText(language,"Формируется из сохранённых данных этой поездки и доступен для сохранения в PDF.","Generated from the saved booking snapshot and available to save as a PDF.","Saqlangan bron ma’lumotlaridan yaratiladi va PDF sifatida saqlanishi mumkin.","Сақланган брон маълумотларидан яратилади ва PDF сифатида сақланиши мумкин."), fontSize=12.sp, lineHeight=15.sp, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.52f))
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CupertinoIcon(CupertinoSymbol.ArrowDown, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.75f))
            Spacer(Modifier.width(8.dp))
            Text(bookingText(language,"Сохранить инвойс PDF","Save invoice PDF","Invoice PDF saqlash","Invoice PDF сақлаш"), Modifier.weight(1f), fontSize=14.sp, lineHeight=18.sp, fontWeight=FontWeight.SemiBold)
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.28f))
        }
    }
}

@Composable
private fun BookingMetaCard(session: StoredBookingSession, language: AppLanguage) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale.US).apply { currency = Currency.getInstance("USD"); maximumFractionDigits = 0 } }
    BookingCard {
        SummaryRow(L10n.text("detail_dates", language), "${L10n.date(session.booking.input.startDate, language)} — ${L10n.date(session.booking.input.endDate, language)}")
        Spacer(Modifier.height(13.dp))
        SummaryRow(L10n.text("travelers", language), session.booking.input.travelers.totalPeople.toString())
        Spacer(Modifier.height(13.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = .10f))
        Spacer(Modifier.height(13.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(L10n.text("booking_total_package", language), fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Text(L10n.text("booking_all_in_one_price", language), fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(money.format(session.booking.totalUsd), fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp)
        }
    }
}

@Composable
private fun SummaryRow(title: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
private fun BookingCareBalanceCard(
    language: AppLanguage,
    careProfile: IumrahPublicProfile?,
    onCall: () -> Unit,
    onTelegram: () -> Unit,
    onChat: () -> Unit,
) {
    val telegramReady = !careProfile?.telegram.isNullOrBlank()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = bookingIosCard(),
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(.8.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .08f)),
    ) {
        Column {
            Image(
                painter = painterResource(R.drawable.care_price_support),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(182.dp).background(Color.Black),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(17.dp), Color(0xFF34C759))
                    Text(
                        bookingText(language, "Мы рядом", "We are with you", "Biz yoningizdamiz", "Биз ёнингиздамиз"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                    )
                }
                Text(
                    bookingText(language, "iumrah Care проверит баланс вашей поездки", "iumrah Care will review your journey balance", "iumrah Care safaringiz muvozanatini tekshiradi", "iumrah Care сафарингиз мувозанатини текширади"),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    bookingText(
                        language,
                        "Если по бронированию, рейсам, отелю или маршруту появились вопросы — свяжитесь с нами удобным способом. Команда видит контекст Вашей брони, поэтому не придётся заново объяснять всю поездку.",
                        "If anything about your booking, flights, hotel or route is unclear, contact us in the way that is most convenient for you. The team sees your booking context and can help without making you explain the trip again.",
                        "Bron, reys, mehmonxona yoki yo‘nalish bo‘yicha savol tug‘ilsa, o‘zingizga qulay usulda bog‘laning. Jamoa bron kontekstini ko‘radi, safarni boshidan qayta tushuntirishingiz shart emas.",
                        "Брон, рейс, меҳмонхона ёки йўналиш бўйича савол туғилса, ўзингизга қулай усулда боғланинг. Жамоа брон контекстини кўради, сафарни бошидан қайта тушунтиришингиз шарт эмас.",
                    ),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CareContactButton(
                        title = bookingText(language, "Позвонить", "Call", "Qo‘ng‘iroq", "Қўнғироқ"),
                        symbol = CupertinoSymbol.Phone,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onCall,
                    )
                    CareContactButton(
                        title = "Telegram",
                        symbol = CupertinoSymbol.Send,
                        enabled = telegramReady,
                        modifier = Modifier.weight(1f),
                        onClick = onTelegram,
                    )
                }
                Surface(onClick = onChat, shape = RoundedCornerShape(17.dp), color = Color.Black) {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CupertinoIcon(CupertinoSymbol.Message, null, Modifier.size(17.dp), Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            bookingText(language, "Открыть чат iumrah", "Open iumrah chat", "iumrah chatini ochish", "iumrah чатини очиш"),
                            Modifier.weight(1f),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CareContactButton(
    title: String,
    symbol: CupertinoSymbol,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .055f),
    ) {
        Row(Modifier.height(48.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CupertinoIcon(symbol, null, Modifier.size(15.dp), if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = .32f))
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = .32f), maxLines = 1)
        }
    }
}

@Composable
private fun BookingItineraryCard(items: List<BookingItineraryItem>, language: AppLanguage) {
    val sorted = remember(items) { items.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder }) }
    val days = remember(sorted) { sorted.map { it.dateLocal }.filter { it.isNotBlank() }.distinct() }
    var selectedDay by remember(days) { mutableStateOf(days.firstOrNull()) }
    val selectedItems = remember(sorted, selectedDay) { sorted.filter { it.dateLocal == selectedDay } }
    val locale = remember(language) { Locale.forLanguageTag(language.localeTag) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(bookingIosCard())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(30.dp))
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    bookingText(language, "Расписание поездки", "Trip schedule", "Safar jadvali", "Сафар жадвали"),
                    fontSize = 25.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.4).sp,
                )
                Text(
                    bookingText(language, "По дням — прилёт, Умра, зияраты и трансферы", "Arrival, Umrah, visits and transfers by day", "Kunlar bo‘yicha parvoz, Umra, ziyorat va transferlar", "Кунлар бўйича парвоз, Умра, зиёрат ва трансферлар"),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
            }
            BookingIconBadge(CupertinoSymbol.CalendarClock, Color(0xFF007AFF), size = 42.dp, symbolSize = 18.dp, radius = 21.dp, circle = true)
        }

        if (days.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Spacer(Modifier.width(1.dp))
                days.forEach { day ->
                    val selected = day == selectedDay
                    val parsed = runCatching { LocalDate.parse(day) }.getOrNull()
                    val dayNumber = parsed?.format(DateTimeFormatter.ofPattern("dd")) ?: day.takeLast(2)
                    val weekday = parsed?.dayOfWeek?.getDisplayName(TextStyle.SHORT, locale)?.replace(".", "") ?: ""
                    IumrahPressable(
                        onClick = { selectedDay = day },
                        modifier = Modifier.width(58.dp).height(64.dp),
                        cornerRadius = 19.dp,
                        background = if (selected) bookingIosPrimary().copy(alpha = .88f) else bookingIosRaised().copy(alpha = .76f),
                        pressedScale = .965f,
                    ) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(dayNumber, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = if (selected) bookingIosPrimaryText() else MaterialTheme.colorScheme.onBackground)
                            Spacer(Modifier.height(3.dp))
                            Text(weekday, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) bookingIosPrimaryText().copy(alpha = .78f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                        }
                    }
                }
                Spacer(Modifier.width(1.dp))
            }
        }

        if (selectedItems.isEmpty()) {
            Text(
                bookingText(language, "На этот день пока нет запланированных событий.", "No scheduled events for this day yet.", "Bu kun uchun hali rejalashtirilgan tadbir yo‘q.", "Бу кун учун ҳали режалаштирилган тадбир йўқ."),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                selectedItems.forEach { item ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(bookingIosRaised().copy(alpha = .72f)).padding(13.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(13.dp),
                    ) {
                        BookingIconBadge(CupertinoSymbol.CalendarClock, Color(0xFF007AFF), size = 40.dp, symbolSize = 16.dp, radius = 14.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                            item.subtitle.takeIf { it.isNotBlank() }?.let {
                                Text(it, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                            }
                            item.location.takeIf { it.isNotBlank() }?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    CupertinoIcon(CupertinoSymbol.Location, null, Modifier.size(10.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
                                    Text(it, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingFlightFirstComponents(session: StoredBookingSession, language: AppLanguage, chrome: AppChromeStore) {
    val trace = session.booking.generatorTrace
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        val outbound = trace?.outbound
        val inbound = trace?.inbound
        if (outbound != null) {
            BookingSectionHeader(bookingText(language, "Авиабилеты", "Flights", "Aviachiptalar", "Авиачипталар"), eyebrow = "iumrah Flights")
            BookingFlightCard(outbound, language, bookingText(language, "Туда", "Outbound", "Borish", "Бориш"))
        }
        if (inbound != null) {
            BookingFlightCard(inbound, language, bookingText(language, "Обратно", "Return", "Qaytish", "Қайтиш"))
        }
        if (outbound == null && inbound == null) {
            BookingSectionHeader(bookingText(language, "Авиабилеты", "Flights", "Aviachiptalar", "Авиачипталар"), eyebrow = "iumrah Flights")
            BookingCard { Text(session.booking.flight, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
        }

        if (session.hotelSelection != null || session.madinahHotelSelection != null || session.booking.hotelNames.makkah.isNotBlank()) {
            BookingSectionHeader(bookingText(language, "Отели", "Hotels", "Mehmonxonalar", "Меҳмонхоналар"), eyebrow = "iumrah Hotels")
            BookingHotelCard(
                language = language,
                cityTitle = bookingText(language, "Мекка", "Makkah", "Makka", "Макка"),
                snapshot = session.hotelSelection,
                fallbackName = session.booking.hotelNames.makkah,
                nights = session.booking.stay.makkahNights,
                onChange = { chrome.openBookingHotelChange(session.id, "makkah") },
            )
            if (session.booking.input.includeMadinah) {
                BookingHotelCard(
                    language = language,
                    cityTitle = bookingText(language, "Медина", "Madinah", "Madina", "Мадина"),
                    snapshot = session.madinahHotelSelection,
                    fallbackName = session.booking.hotelNames.madinah,
                    nights = session.booking.stay.madinahNights ?: 0,
                    onChange = { chrome.openBookingHotelChange(session.id, "madinah") },
                )
            }
        }

        BookingSectionHeader(bookingText(language, "Что включено", "What's included", "Nimalar kiradi", "Нималар киради"), eyebrow = "iumrah")
        BookingIncludedServicesCard(session, language)
    }
}

@Composable
private fun BookingIncludedServicesCard(session: StoredBookingSession, language: AppLanguage) {
    data class Included(val icon: CupertinoSymbol, val tint: Color, val title: String, val subtitle: String)
    val rows = buildList {
        add(Included(CupertinoSymbol.Car, Color(0xFF007AFF), bookingText(language, "Трансфер по маршруту", "Route transfer", "Yo‘nalish transferi", "Йўналиш трансфери"), bookingText(language, "Аэропорт, отели и ключевые точки поездки", "Airport, hotels and key trip points", "Aeroport, mehmonxonalar va asosiy nuqtalar", "Аэропорт, меҳмонхоналар ва асосий нуқталар")))
        val guideName = session.guide?.displayName?.takeIf { it.isNotBlank() }
        add(Included(CupertinoSymbol.ShieldCheck, Color(0xFF5856D6), "iumrah Guide", guideName?.let { bookingText(language, "Гид: $it", "Guide: $it", "Gid: $it", "Гид: $it") } ?: bookingText(language, "Сопровождение и координация поездки", "Journey guidance and coordination", "Safarni kuzatish va muvofiqlashtirish", "Сафарни кузатиш ва мувофиқлаштириш")))
        val makkahZ = session.ziyaratMakkahOverride ?: session.booking.customization?.ziyaratMakkah ?: false
        val madinahZ = session.ziyaratMadinahOverride ?: session.booking.customization?.ziyaratMadinah ?: false
        if (makkahZ || madinahZ) {
            val cities = buildList { if (makkahZ) add(bookingText(language,"Мекка","Makkah","Makka","Макка")); if (madinahZ) add(bookingText(language,"Медина","Madinah","Madina","Мадина")) }.joinToString(" · ")
            add(Included(CupertinoSymbol.Location, Color(0xFFFF9500), bookingText(language, "Зияраты", "Ziyarats", "Ziyoratlar", "Зиёратлар"), cities))
        }
        val esim = session.esimOverride ?: session.booking.customization?.esim ?: false
        if (esim) add(Included(CupertinoSymbol.SignalWave, Color(0xFF30B0C7), "iumrah eSIM", bookingText(language, "Связь в Саудовской Аравии внутри поездки", "Connectivity in Saudi Arabia inside your trip", "Saudiya Arabistonida safar ichidagi aloqa", "Саудия Арабистонида сафар ичидаги алоқа")))
        add(Included(CupertinoSymbol.HeartFill, BookingCareLight, "iumrah Care", bookingText(language, "Поддержка по поездке и бронированию", "Trip and booking support", "Safar va bron bo‘yicha yordam", "Сафар ва брон бўйича ёрдам")))
        if (session.booking.customization?.meals == true) add(Included(CupertinoSymbol.ForkKnife, Color(0xFFFF9500), bookingText(language, "Питание", "Meals", "Ovqatlanish", "Овқатланиш"), bookingText(language, "Включено по выбранной категории пакета", "Included according to your package category", "Tanlangan paket toifasi bo‘yicha kiritilgan", "Танланган пакет тоифаси бўйича киритилган")))
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bookingIosCard()).border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(28.dp))
    ) {
        rows.forEachIndexed { index, row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookingIconBadge(row.icon, row.tint, size = 44.dp, symbolSize = 17.dp, radius = 15.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(row.title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(row.subtitle, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                }
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .28f))
            }
            if (index != rows.lastIndex) HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .08f))
        }
    }
}

@Composable
private fun BookingFlightCard(flight: BookingGeneratorFlightSnapshot, language: AppLanguage, label: String) {
    var expanded by remember(flight.candidateId, flight.flightNumbers) { mutableStateOf(false) }
    BookingCard {
        IumrahPressable(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), cornerRadius = 0.dp, background = Color.Transparent, pressedScale = .99f) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BookingIconBadge(CupertinoSymbol.Airplane, Color(0xFF007AFF), size = 50.dp, symbolSize = 20.dp, radius = 17.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(label.uppercase(), fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
                    Text("${flight.origin.uppercase()} → ${flight.destination.uppercase()}", fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                    Text("${flight.airline} · ${flight.flightNumbers}", fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(bookingFlightDayClock(flight.departureAt, language), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(17.dp), Color(0xFF007AFF))
                    Text(if ((flight.stops ?: 0) == 0) bookingText(language, "прямой", "direct", "to‘g‘ridan-to‘g‘ri", "тўғридан-тўғри") else "${flight.stops} stop", fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
                    CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(11.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .28f))
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = .08f))
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                BookingFlightFact(bookingText(language, "Аэропорт вылета", "Departure airport", "Jo‘nash aeroporti", "Жўнаш аэропорти"), flight.origin.uppercase())
                BookingFlightFact(bookingText(language, "Аэропорт прилёта", "Arrival airport", "Yetib borish aeroporti", "Етиб бориш аэропорти"), flight.destination.uppercase())
                BookingFlightFact(bookingText(language, "Вылет", "Departure", "Jo‘nash", "Жўнаш"), bookingFullDateTime(flight.departureAt, language))
                BookingFlightFact(bookingText(language, "Прилёт", "Arrival", "Yetib kelish", "Етиб келиш"), bookingFullDateTime(flight.arrivalAt, language))
                flight.durationMinutes?.let { BookingFlightFact(bookingText(language, "В пути", "Duration", "Yo‘lda", "Йўлда"), "${it / 60}h ${it % 60}m") }
            }
        }
    }
}

@Composable
private fun BookingFlightFact(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(title, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 210.dp))
    }
}

private fun bookingFlightDayClock(raw: String, language: AppLanguage): String = runCatching {
    val odt = OffsetDateTime.parse(raw)
    val locale = Locale.forLanguageTag(language.localeTag)
    "${odt.format(DateTimeFormatter.ofPattern("d MMM", locale))} · ${odt.format(DateTimeFormatter.ofPattern("HH:mm", locale))}"
}.getOrDefault(raw)

private fun bookingFullDateTime(raw: String, language: AppLanguage): String = runCatching {
    val odt = OffsetDateTime.parse(raw)
    odt.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.forLanguageTag(language.localeTag)))
}.getOrDefault(raw)

@Composable
private fun FlightPoint(code: String, raw: String, modifier: Modifier, end: Boolean = false) {
    val time = raw.takeIf { it.isNotBlank() }?.let { runCatching { java.time.OffsetDateTime.parse(it).toLocalTime().toString().take(5) }.getOrDefault("—") } ?: "—"
    Column(modifier, horizontalAlignment = if (end) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(code.uppercase(), fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.4).sp)
        Text(time, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
    }
}

@Composable
private fun BookingHotelCard(
    language: AppLanguage,
    cityTitle: String,
    snapshot: BookingHotelSelectionSnapshot?,
    fallbackName: String,
    nights: Int,
    onChange: () -> Unit,
) {
    IumrahPressable(
        onClick = onChange,
        modifier = Modifier.fillMaxWidth().height(150.dp),
        cornerRadius = 25.dp,
        background = bookingIosCard(),
        pressedScale = .985f,
    ) {
        Row(
            Modifier.fillMaxSize().border(.6.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), RoundedCornerShape(25.dp)).clip(RoundedCornerShape(25.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(Modifier.fillMaxHeight().weight(.34f)) {
                val mediaWidth = maxWidth.coerceIn(108.dp, 124.dp)
                Box(Modifier.width(mediaWidth).fillMaxHeight().background(bookingIosRaised()), contentAlignment = Alignment.Center) {
                    val image = snapshot?.coverImageURL?.let(AppConfig::absoluteUrl)
                    if (!image.isNullOrBlank()) AsyncImage(image, fallbackName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else CupertinoIcon(CupertinoSymbol.Hotel, null, Modifier.size(28.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .42f))
                }
            }
            Column(Modifier.weight(.66f).fillMaxHeight().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(snapshot?.hotelName?.takeIf { it.isNotBlank() } ?: fallbackName, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(cityTitle, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CupertinoIcon(CupertinoSymbol.MoonStarsFill, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                    Text("$nights ${bookingText(language, "ноч.", "nights", "tun", "тун")}", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupertinoIcon(CupertinoSymbol.Bed, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .70f))
                    Text(snapshot?.roomName?.takeIf { it.isNotBlank() } ?: bookingText(language, "Открыть отель", "Open hotel", "Mehmonxonani ochish", "Меҳмонхонани очиш"), Modifier.weight(1f), fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(11.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .28f))
                }
            }
        }
    }
}

@Composable
private fun ServicePill(symbol: CupertinoSymbol, text: String) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(bookingIosRaised()).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        CupertinoIcon(symbol, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        Text(text, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .62f))
    }
}

@Composable
private fun BookingContactCard(
    session: StoredBookingSession,
    language: AppLanguage,
    telegram: String,
    whatsapp: String,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onTelegram: (String) -> Unit,
    onWhatsapp: (String) -> Unit,
    onSave: () -> Unit,
) {
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(L10n.text("booking_contacts", language), Modifier.weight(1f), fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            IumrahPressable(
                onClick = { onEditing(!editing) },
                modifier = Modifier.height(34.dp),
                cornerRadius = 17.dp,
                background = bookingIosRaised(),
                pressedScale = .97f,
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupertinoIcon(if (editing) CupertinoSymbol.Close else CupertinoSymbol.Gear, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .68f))
                    Text(if (editing) bookingText(language, "Закрыть", "Close", "Yopish", "Ёпиш") else L10n.text("booking_contact_edit", language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (editing) {
            BookingContactField("Telegram", telegram, onTelegram)
            Spacer(Modifier.height(10.dp))
            BookingContactField("WhatsApp", whatsapp, onWhatsapp)
            Spacer(Modifier.height(12.dp))
            BookingPrimaryAction(L10n.text("booking_save_changes", language), onSave)
        } else {
            ContactRow(CupertinoSymbol.Send, "Telegram", telegram.ifBlank { "—" })
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = .07f))
            Spacer(Modifier.height(11.dp))
            ContactRow(CupertinoSymbol.Message, "WhatsApp", whatsapp.ifBlank { "—" })
        }
    }
}

@Composable
private fun BookingContactField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        placeholder = { Text(label, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = bookingIosRaised().copy(alpha = .60f),
            unfocusedContainerColor = bookingIosRaised().copy(alpha = .60f),
            focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .10f),
            unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = .06f),
        ),
    )
}

@Composable
private fun ContactRow(symbol: CupertinoSymbol, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        BookingIconBadge(symbol, Color(0xFF007AFF), size = 38.dp, symbolSize = 15.dp, radius = 13.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 11.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
            Text(value, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BookingPendingConfirmationCard(language: AppLanguage) {
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Hourglass, Color(0xFFF2A900), size = 44.dp, symbolSize = 17.dp, radius = 15.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bookingText(language, "Изменения отправлены", "Changes sent", "O‘zgarishlar yuborildi", "Ўзгаришлар юборилди"), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(bookingText(language, "Команда iumrah проверит обновления и подтвердит их.", "The iumrah team will review and confirm the updates.", "iumrah jamoasi o‘zgarishlarni tekshiradi va tasdiqlaydi.", "iumrah жамоаси ўзгаришларни текширади ва тасдиқлайди."), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
    }
}

@Composable
private fun BookingTelegramCompactCard(session: StoredBookingSession, language: AppLanguage) {
    BookingCard(radius = 24.dp, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF229ED9)), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Send, null, Modifier.size(23.dp), Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Telegram", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(bookingText(language, "Подключите уведомления по бронированию", "Connect booking notifications", "Bron bildirishnomalarini ulang", "Брон билдиришномаларини уланг"), fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 2)
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
        }
    }
}

private fun bookingStatusVisual(status: String): Pair<CupertinoSymbol, Color> {
    val icon = when (status.uppercase()) {
        "AVAILABILITY_CHECK", "NEW" -> CupertinoSymbol.Hourglass
        "PAYMENT_PENDING", "AVAILABILITY_CONFIRMED" -> CupertinoSymbol.CreditCard
        "PAID", "BOOKING_CONFIRMED" -> CupertinoSymbol.CheckCircleFill
        "DOCUMENTS_READY", "READY_TO_TRAVEL" -> CupertinoSymbol.ShieldCheck
        "IN_TRIP" -> CupertinoSymbol.Airplane
        "COMPLETED" -> CupertinoSymbol.CheckCircleFill
        else -> CupertinoSymbol.ExclamationCircle
    }
    return icon to IumrahBookingStatusVisual.color(status)
}

private fun bookingStatusText(status: String, language: AppLanguage): String = when (status) {
    "AVAILABILITY_CHECK", "NEW" -> bookingText(language, "Проверка наличия", "Availability check", "Mavjudlik tekshiruvi", "Мавжудлик текшируви")
    "PAYMENT_PENDING" -> bookingText(language, "Наличие подтверждено · ожидаем оплату и данные", "Availability confirmed · payment and details required", "Mavjudlik tasdiqlandi · to‘lov va ma’lumotlar kerak", "Мавжудлик тасдиқланди · тўлов ва маълумотлар керак")
    "BOOKING_CONFIRMED" -> bookingText(language, "Оплачено · бронирование подтверждено", "Paid · booking confirmed", "To‘langan · bron tasdiqlandi", "Тўланган · брон тасдиқланди")
    "READY_TO_TRAVEL" -> bookingText(language, "Документы готовы · готово к поездке", "Documents ready · ready to travel", "Hujjatlar tayyor · safarga tayyor", "Ҳужжатлар тайёр · сафарга тайёр")
    "IN_TRIP" -> bookingText(language, "Паломник в поездке", "Pilgrim in trip", "Ziyoratchi safarda", "Зиёратчи сафарда")
    "COMPLETED" -> bookingText(language, "Завершено", "Completed", "Yakunlandi", "Якунланди")
    "CANCELLED" -> bookingText(language, "Отменено", "Cancelled", "Bekor qilingan", "Бекор қилинган")
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
