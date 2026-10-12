package com.iumrah.beta.ui.booking

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.IumrahBookingStatusVisual
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.chat.IumrahPublicProfile
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.account.IumrahCheckoutResponse
import com.iumrah.beta.models.booking.BookingGeneratorFlightSegmentSnapshot
import com.iumrah.beta.models.booking.BookingGeneratorFlightSnapshot
import com.iumrah.beta.models.booking.BookingItineraryItem
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Currency
import java.util.Locale
import kotlinx.coroutines.delay

private enum class BookingPanel { BOOKING, STATUS, SCHEDULE }

private const val IOS_PAGE_PADDING = 18
private val iOSGreen = Color(0xFF34C759)
private val iOSOrange = Color(0xFFFF9500)
private val iOSBlue = Color(0xFF007AFF)
private val iOSTeal = Color(0xFF30B0C7)
private val iOSIndigo = Color(0xFF5856D6)
private val iOSRed = Color(0xFFFF3B30)
private val iOSYellow = Color(0xFFFFCC00)
private val iOSCyan = Color(0xFF32ADE6)

@Composable
fun BookingsHomeScreen(
    language: AppLanguage,
    bookingStore: BookingStore,
    accountService: IumrahAccountService,
    chatService: ChatService,
    notifications: ClientNotificationStore,
    chrome: AppChromeStore,
) {
    val bookingState by bookingStore.state.collectAsState()
    val notificationState by notifications.state.collectAsState()
    var panel by remember { mutableStateOf(BookingPanel.BOOKING) }
    var checkout by remember { mutableStateOf<IumrahCheckoutResponse?>(null) }
    var itinerary by remember { mutableStateOf<List<BookingItineraryItem>>(emptyList()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var activeGuideProfile by remember { mutableStateOf<IumrahPublicProfile?>(null) }

    val activeSessions = bookingState.sessions.filterNot {
        it.effectiveStatus.uppercase() in setOf("COMPLETED", "CANCELLED")
    }
    val activeSession = activeSessions.firstOrNull()

    suspend fun refresh() {
        bookingState.sessions.toList().forEach { session ->
            runCatching { bookingStore.refresh(session.id) }
        }
        activeSession?.let { session ->
            checkout = runCatching {
                accountService.checkout(session.id, bookingStore.headersFor(session))
            }.getOrNull()
            itinerary = runCatching {
                bookingStore.service.fetchItinerary(session.id, bookingStore.headersFor(session))
            }.getOrDefault(emptyList()).let { localizeServerItinerary(it, language) }
        } ?: run {
            checkout = null
            itinerary = emptyList()
        }
    }

    LaunchedEffect(activeSession?.id, bookingState.sessions.size) {
        refresh()
        while (true) {
            delay(60_000)
            refresh()
        }
    }

    LaunchedEffect(activeSession?.guide?.id) {
        activeGuideProfile = null
        val guideID = activeSession?.guide?.id.orEmpty()
        if (guideID.isNotBlank()) {
            activeGuideProfile = runCatching { chatService.loadTeamProfile(guideID) }.getOrNull()
        }
    }

    AnimatedContent(
        targetState = activeSession?.id,
        transitionSpec = { fadeIn().togetherWith(fadeOut()) },
        label = "booking-root-parity",
    ) { bookingID ->
        // Render the session tied to AnimatedContent's target state, not an
        // outer mutable selection that may change mid-transition.
        val displayedSession = activeSessions.firstOrNull { it.id == bookingID }
        if (displayedSession == null) {
            EmptyBookingHome(
                language = language,
                unreadCount = notificationState.unreadCount,
                chrome = chrome,
            )
        } else {
            ActiveBookingHome(
                language = language,
                session = displayedSession,
                otherSessions = activeSessions.filterNot { it.id == displayedSession.id },
                panel = panel,
                onPanel = { panel = it },
                checkout = checkout,
                itinerary = itinerary,
                unreadCount = notificationState.unreadCount,
                activeGuideProfile = activeGuideProfile,
                chrome = chrome,
                onDelete = { id ->
                    deleteError = null
                    runCatching { bookingStore.deleteBooking(id) }
                        .onFailure { deleteError = it.message }
                },
                deleteError = deleteError,
            )
        }
    }
}

@Composable
private fun ActiveBookingHome(
    language: AppLanguage,
    session: StoredBookingSession,
    otherSessions: List<StoredBookingSession>,
    panel: BookingPanel,
    onPanel: (BookingPanel) -> Unit,
    checkout: IumrahCheckoutResponse?,
    itinerary: List<BookingItineraryItem>,
    unreadCount: Int,
    activeGuideProfile: IumrahPublicProfile?,
    chrome: AppChromeStore,
    onDelete: suspend (String) -> Unit,
    deleteError: String?,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bookingPageColor()),
        contentPadding = PaddingValues(
            start = IOS_PAGE_PADDING.dp,
            end = IOS_PAGE_PADDING.dp,
            top = 10.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            BookingRootHeader(
                language = language,
                title = L10n.text("tab_booking", language),
                usesBrandLogo = true,
                showsMakkahTime = false,
                unreadCount = unreadCount,
                chrome = chrome,
            )
            Spacer(Modifier.height(16.dp))
            JourneyTravelInfoPanel(language = language)
            Spacer(Modifier.height(18.dp))
            BookingPanelPicker(language, panel, onPanel)
            Spacer(Modifier.height(12.dp))
            BookingIdentity(language, session)
            Spacer(Modifier.height(18.dp))
        }

        if (panel == BookingPanel.BOOKING) {
            item {
                BookingProgress(language, session, chrome)
                Spacer(Modifier.height(38.dp))
            }
        } else if (panel == BookingPanel.STATUS) {
            item {
                BookingTimerOverview(language, session)
                Spacer(Modifier.height(28.dp))
                BookingFulfillmentCenter(language, session, checkout, activeGuideProfile, chrome)
                Spacer(Modifier.height(34.dp))

                if (shouldShowTravelReadyFlights(session)) {
                    BookingStatusFlights(language, session, chrome)
                    Spacer(Modifier.height(34.dp))
                }

                TripPlanPreview(language, session, itinerary) { onPanel(BookingPanel.SCHEDULE) }
                Spacer(Modifier.height(34.dp))
                TripManagement(language, session, chrome)
                Spacer(Modifier.height(if (otherSessions.isNotEmpty()) 36.dp else 12.dp))
            }
        } else {
            item {
                BookingItineraryCalendarAndroid(
                    language = language,
                    session = session,
                    remoteItems = itinerary,
                    fullScreen = true,
                )
                Spacer(Modifier.height(28.dp))
            }
        }

        if (otherSessions.isNotEmpty()) {
            item {
                SectionHeader(
                    t(language, "Другие поездки", "Other trips", "Boshqa safarlar", "Бошқа сафарлар"),
                    null,
                )
                Spacer(Modifier.height(16.dp))
            }
            items(otherSessions, key = { it.id }) { other ->
                CompactBookingCard(language, other, chrome, onDelete)
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        deleteError?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.error.copy(alpha = .08f),
                            RoundedCornerShape(18.dp),
                        )
                        .padding(14.dp),
                )
            }
        }

        item {
            Spacer(Modifier.height(28.dp))
            RootTelegramCompactCard(language, session, chrome)
        }
    }
}

@Composable
private fun RootTelegramCompactCard(language: AppLanguage, session: StoredBookingSession, chrome: AppChromeStore) {
    IumrahPressable(
        onClick = chrome::openBookingTelegramIntegration,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        background = bookingCardColor(),
        pressedScale = .985f,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF229ED9)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(CupertinoSymbol.Send, null, Modifier.size(23.dp), Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Telegram", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    t(language, "Подключите уведомления по бронированию", "Connect booking notifications", "Bron bildirishnomalarini ulang", "Брон билдиришномаларини уланг"),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                    maxLines = 2,
                )
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
        }
    }
}

@Composable
private fun BookingRootHeader(
    language: AppLanguage,
    title: String,
    usesBrandLogo: Boolean,
    showsMakkahTime: Boolean,
    unreadCount: Int,
    chrome: AppChromeStore,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(showsMakkahTime) {
        if (!showsMakkahTime) return@LaunchedEffect
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val foreground = MaterialTheme.colorScheme.onBackground
    val surface = bookingRaisedColor()
    val dark = MaterialTheme.colorScheme.background.iosLuminance() < .45f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (usesBrandLogo) {
            Image(
                painter = painterResource(
                    if (dark) R.drawable.iumrah_header_wordmark_dark
                    else R.drawable.iumrah_header_wordmark_light,
                ),
                contentDescription = "iumrah",
                modifier = Modifier
                    .width(180.dp)
                    .height(46.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else {
            Text(
                title,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IumrahPressable(
                    onClick = chrome::openNotifications,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 23.dp,
                    background = surface,
                    pressedScale = .94f,
                    shadowElevation = 0.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CupertinoIcon(
                            if (unreadCount > 0) CupertinoSymbol.BellBadge else CupertinoSymbol.Bell,
                            contentDescription = t(language, "Уведомления", "Notifications", "Bildirishnomalar", "Билдиришномалар"),
                            modifier = Modifier.size(18.dp),
                            tint = foreground,
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 3.dp, end = 2.dp)
                                    .height(16.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(iOSRed)
                                    .border(.8.dp, Color.White, RoundedCornerShape(999.dp))
                                    .padding(horizontal = if (unreadCount > 9) 4.dp else 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (unreadCount > 9) "9+" else unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                IumrahPressable(
                    onClick = chrome::openSidebar,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 23.dp,
                    background = surface,
                    pressedScale = .94f,
                    shadowElevation = 0.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CupertinoIcon(
                            CupertinoSymbol.Menu,
                            contentDescription = t(language, "Меню", "Menu", "Menyu", "Меню"),
                            modifier = Modifier.size(18.dp),
                            tint = foreground,
                        )
                    }
                }
            }

            if (showsMakkahTime) {
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.text("makkah_time", language),
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground.copy(alpha = .55f),
                )
                Text(
                    makkahTime(now),
                    fontSize = 20.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = foreground.copy(alpha = .55f),
                )
            }
        }
    }
}

@Composable
private fun BookingIdentity(language: AppLanguage, session: StoredBookingSession) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(94.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(bookingRaisedColor())
                .border(
                    .7.dp,
                    MaterialTheme.colorScheme.onBackground.copy(alpha = .065f),
                    RoundedCornerShape(28.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(
                CupertinoSymbol.SuitcaseFill,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
            )
        }

        Spacer(Modifier.height(17.dp))
        Text(
            t(language, "Ваша Umrah", "Your Umrah", "Sizning Umrangiz", "Сизнинг Умрангиз"),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "${session.booking.route.originCode} → ${session.booking.route.outboundDestination}",
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.9).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            "${L10n.date(session.booking.input.startDate, language)} – ${L10n.date(session.booking.input.endDate, language)}",
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(17.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdentityPill(shortBookingNumber(language, session.displayBookingNumber))
            IdentityPill(
                pilgrimCount(language, session.booking.input.travelers.totalPeople),
                CupertinoSymbol.Persons,
            )
        }
        session.travelerName?.trim()?.takeIf { it.isNotEmpty() }?.let { traveler ->
            Spacer(Modifier.height(11.dp))
            Text(
                traveler,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun IdentityPill(text: String, icon: CupertinoSymbol? = null) {
    Row(
        modifier = Modifier
            .height(31.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bookingRaisedColor())
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            CupertinoIcon(
                it,
                null,
                Modifier.size(12.dp),
                MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            )
        }
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
    }
}

@Composable
private fun BookingPanelPicker(
    language: AppLanguage,
    selected: BookingPanel,
    onSelect: (BookingPanel) -> Unit,
) {
    SegmentedPicker(
        items = listOf(
            BookingPanel.BOOKING to t(language, "Бронирование", "Booking", "Bron", "Брон"),
            BookingPanel.STATUS to t(language, "Статус", "Status", "Holat", "Ҳолат"),
            BookingPanel.SCHEDULE to t(language, "Расписание", "Schedule", "Jadval", "Жадвал"),
        ),
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SegmentedPicker(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    val view = LocalView.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .06f))
            .border(.55.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .045f), RoundedCornerShape(11.dp))
            .padding(2.dp),
    ) {
        items.forEach { (value, label) ->
            val active = value == selected
            IumrahPressable(
                onClick = {
                    if (!active) IumrahHaptics.selection(view)
                    onSelect(value)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                cornerRadius = 9.dp,
                background = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (active) 1.dp else 0.dp,
                pressedScale = .985f,
                haptic = false,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class ProgressStage(
    val key: String,
    val titleRu: String,
    val titleEn: String,
    val titleUz: String,
    val titleCy: String,
    val subtitleRu: String,
    val subtitleEn: String,
    val subtitleUz: String,
    val subtitleCy: String,
    val cardTitleRu: String = titleRu,
    val cardTitleEn: String = titleEn,
    val cardTitleUz: String = titleUz,
    val cardTitleCy: String = titleCy,
    val cardBodyRu: String = subtitleRu,
    val cardBodyEn: String = subtitleEn,
    val cardBodyUz: String = subtitleUz,
    val cardBodyCy: String = subtitleCy,
)

private val progressStages = listOf(
    ProgressStage(
        "CREATED",
        "Пакет создан", "Package created", "Paket yaratildi", "Пакет яратилди",
        "Поездка добавлена в iumrah", "Trip added to iumrah", "Safar iumrah'ga qo‘shildi", "Сафар iumrah'га қўшилди",
    ),
    ProgressStage(
        "AVAILABILITY_CHECK",
        "Проверка наличия", "Availability check", "Mavjudlik tekshiruvi", "Мавжудлик текшируви",
        "Подтверждаем перелёт, отель и услуги", "Confirming flight, hotel and services", "Parvoz, mehmonxona va xizmatlar tasdiqlanmoqda", "Парвоз, меҳмонхона ва хизматлар тасдиқланмоқда",
        "Проверяем ваш пакет", "Checking your package", "Paketingiz tekshirilmoqda", "Пакетингиз текширилмоқда",
        "iumrah подтверждает выбранные позиции. Пока от вас ничего не требуется.", "iumrah is confirming the selected items. No action is required from you yet.", "iumrah tanlangan xizmatlarni tasdiqlamoqda. Hozircha sizdan hech narsa talab qilinmaydi.", "iumrah танланган хизматларни тасдиқламоқда. Ҳозирча сиздан ҳеч нарса талаб қилинмайди.",
    ),
    ProgressStage(
        "PAYMENT_PENDING",
        "Оплата и данные паломников", "Payment and pilgrim details", "To‘lov va ziyoratchi ma’lumotlari", "Тўлов ва зиёратчи маълумотлари",
        "Наличие подтверждено · требуется действие", "Availability confirmed · action required", "Mavjudlik tasdiqlandi · amal kerak", "Мавжудлик тасдиқланди · амал керак",
        "Наличие подтверждено", "Availability confirmed", "Mavjudlik tasdiqlandi", "Мавжудлик тасдиқланди",
        "Проверьте данные паломников и перейдите к оплате, чтобы закрепить бронирование.", "Review pilgrim details and continue to payment to secure the booking.", "Bronni mustahkamlash uchun ziyoratchilar ma’lumotlarini tekshiring va to‘lovga o‘ting.", "Бронни мустаҳкамлаш учун зиёратчилар маълумотларини текширинг ва тўловга ўтинг.",
    ),
    ProgressStage(
        "BOOKING_CONFIRMED",
        "Бронирование подтверждено", "Booking confirmed", "Bron tasdiqlandi", "Брон тасдиқланди",
        "Позиции закреплены за вами", "Your trip components are secured", "Safar xizmatlari siz uchun band qilindi", "Сафар хизматлари сиз учун банд қилинди",
        cardBodyRu = "Перелёт, проживание и выбранные услуги закреплены. Все детали доступны внутри бронирования.",
        cardBodyEn = "Flight, stay and selected services are secured. Full details are available inside the booking.",
        cardBodyUz = "Parvoz, yashash va tanlangan xizmatlar band qilindi. Barcha tafsilotlar bron ichida mavjud.",
        cardBodyCy = "Парвоз, яшаш ва танланган хизматлар банд қилинди. Барча тафсилотлар брон ичида мавжуд.",
    ),
    ProgressStage(
        "READY_TO_TRAVEL",
        "Документы готовы", "Documents ready", "Hujjatlar tayyor", "Ҳужжатлар тайёр",
        "Всё готово к поездке", "Everything is ready for travel", "Safar uchun hammasi tayyor", "Сафар учун ҳаммаси тайёр",
        "Готово к поездке", "Ready to travel", "Safarga tayyor", "Сафарга тайёр",
        "Проверьте билеты, бронирования и документы перед выездом.", "Review tickets, reservations and travel documents before departure.", "Jo‘nashdan oldin chiptalar, bronlar va hujjatlarni tekshiring.", "Жўнашдан олдин чипталар, бронлар ва ҳужжатларни текширинг.",
    ),
    ProgressStage(
        "IN_TRIP",
        "Паломник в поездке", "Pilgrim in trip", "Ziyoratchi safarda", "Зиёратчи сафарда",
        "iumrah сопровождает вашу поездку", "iumrah is accompanying your trip", "iumrah safaringizga hamroh", "iumrah сафарингизга ҳамроҳ",
        "Ваша Umrah идёт", "Your Umrah is underway", "Umrangiz davom etmoqda", "Умрангиз давом этмоқда",
        "Маршрут, отель, расписание и помощь iumrah остаются под рукой на протяжении поездки.", "Your route, hotel, schedule and iumrah support stay close throughout the trip.", "Yo‘nalish, mehmonxona, jadval va iumrah yordami safar davomida doimo yoningizda.", "Йўналиш, меҳмонхона, жадвал ва iumrah ёрдами сафар давомида доимо ёнингизда.",
    ),
    ProgressStage(
        "COMPLETED",
        "Поездка завершена", "Trip completed", "Safar yakunlandi", "Сафар якунланди",
        "История поездки сохранена", "Your trip history is saved", "Safar tarixi saqlandi", "Сафар тарихи сақланди",
        cardBodyRu = "Бронирование и история поездки останутся доступны в iumrah.",
        cardBodyEn = "The booking and trip history remain available in iumrah.",
        cardBodyUz = "Bron va safar tarixi iumrah'da saqlanadi.",
        cardBodyCy = "Брон ва сафар тарихи iumrah'да сақланади.",
    ),
)

@Composable
private fun BookingProgress(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
) {
    val current = progressIndex(session.effectiveStatus)
    val cancelled = session.effectiveStatus.equals("CANCELLED", true)

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SectionHeader(
            t(language, "Статус бронирования", "Booking status", "Bron holati", "Брон ҳолати"),
            if (cancelled) {
                t(language, "Отменено", "Cancelled", "Bekor qilingan", "Бекор қилинган")
            } else {
                progressCounter(language, current, progressStages.size)
            },
        )

        Column(Modifier.fillMaxWidth()) {
            progressStages.forEachIndexed { index, stage ->
                ProcessStep(
                    language = language,
                    stage = if (cancelled && index == current) cancelledStage(language) else stage,
                    session = session,
                    index = index,
                    current = current,
                    completed = if (cancelled) index == 0 else index < current,
                    active = index == current,
                    future = index > current,
                    isLast = index == progressStages.lastIndex,
                    chrome = chrome,
                    cancelled = cancelled,
                )
            }
        }
    }
}

@Composable
private fun ProcessStep(
    language: AppLanguage,
    stage: ProgressStage,
    session: StoredBookingSession,
    index: Int,
    current: Int,
    completed: Boolean,
    active: Boolean,
    future: Boolean,
    isLast: Boolean,
    chrome: AppChromeStore,
    cancelled: Boolean,
) {
    val tint = statusColor(session.effectiveStatus)
    val nodeColor = when {
        active -> tint
        completed -> iOSGreen
        else -> MaterialTheme.colorScheme.onBackground.copy(alpha = .38f)
    }
    val lineColor = if (completed) iOSGreen.copy(alpha = .36f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = .12f)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier.width(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .then(
                        if (completed || active) Modifier.background(nodeColor)
                        else Modifier
                            .background(bookingPageColor())
                            .border(
                                1.6.dp,
                                MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                                CircleShape,
                            ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    completed -> CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(10.dp), Color.White)
                    active -> Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(activeNodeForeground(session.effectiveStatus)),
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .weight(1f, fill = true)
                        .background(lineColor),
                )
            }
        }
        Spacer(Modifier.width(17.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stageTitle(language, stage),
                fontSize = if (active) 18.sp else 17.sp,
                lineHeight = 22.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                color = if (future) MaterialTheme.colorScheme.onBackground.copy(alpha = .42f)
                else MaterialTheme.colorScheme.onBackground,
            )

            stageTimestamp(index, current, session, cancelled)?.let { timestamp ->
                Text(
                    compactTimestamp(timestamp, language),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .34f),
                )
            }

            if (active) {
                Text(
                    activeStageSubtitle(language, session, stage),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
                Spacer(Modifier.height(4.dp))
                ActiveStageCard(language, session, stage, chrome)
                Spacer(Modifier.height(18.dp))
            } else if (!isLast) {
                Spacer(Modifier.height(15.dp))
            }
        }
    }
}

@Composable
private fun ActiveStageCard(
    language: AppLanguage,
    session: StoredBookingSession,
    stage: ProgressStage,
    chrome: AppChromeStore,
) {
    val tint = statusColor(session.effectiveStatus)
    val shape = RoundedCornerShape(28.dp)
    val availabilityActive = session.effectiveStatus.uppercase() in setOf("NEW", "AVAILABILITY_CHECK")
    val iconScale = if (availabilityActive) {
        val transition = rememberInfiniteTransition(label = "availability-hourglass")
        val scale by transition.animateFloat(
            initialValue = .92f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "availability-hourglass-scale",
        )
        scale
    } else 1f

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bookingCardColor())
            .background(
                Brush.linearGradient(
                    listOf(tint.copy(alpha = .13f), tint.copy(alpha = .025f), Color.Transparent),
                ),
            )
            .border(.8.dp, tint.copy(alpha = .22f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(
                    statusIcon(session.effectiveStatus),
                    null,
                    Modifier
                        .size(15.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    activeNodeForeground(session.effectiveStatus),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    activeCardTitle(language, session, stage),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.25).sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    activeCardBody(language, session, stage),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }
        }

        LifecycleTimerPanel(language, session, tint)

        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .05f)),
        )

        Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
            ProgressFact(
                t(language, "Маршрут", "Route", "Yo‘nalish", "Йўналиш"),
                "${session.booking.route.originCode} → ${session.booking.route.outboundDestination}",
            )
            ProgressFact(
                t(language, "Даты", "Dates", "Sanalar", "Саналар"),
                "${L10n.date(session.booking.input.startDate, language)} – ${L10n.date(session.booking.input.endDate, language)}",
            )
            session.booking.hotelNames.makkah.trim().takeIf { it.isNotEmpty() }?.let { hotel ->
                ProgressFact(
                    t(language, "Отель", "Hotel", "Mehmonxona", "Меҳмонхона"),
                    hotel,
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    t(language, "На паломника", "Per pilgrim", "Bir ziyoratchiga", "Бир зиёратчига"),
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
                Text(
                    formatPrice(session.booking.perPilgrimUsd),
                    fontSize = 24.sp,
                    lineHeight = 29.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                pilgrimCount(language, session.booking.input.travelers.totalPeople),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            )
        }

        StatusCardActions(language, session, chrome)
    }
}

@Composable
private fun LifecycleTimerPanel(
    language: AppLanguage,
    session: StoredBookingSession,
    tint: Color,
) {
    val phase = lifecyclePhase(session) ?: return
    var now by remember(session.id, phase.deadlineMs) { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(session.id, phase.deadlineMs) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val remaining = (phase.deadlineMs - now).coerceAtLeast(0L)
    val expired = remaining <= 0L

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .035f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    lifecycleTimerTitle(language, phase.kind, expired),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    lifecycleCountdown(remaining),
                    fontSize = 31.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.7).sp,
                )
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(tint.copy(alpha = .09f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(phase.symbol, null, Modifier.size(17.dp), tint)
            }
        }
        Text(
            lifecycleTimerFootnote(language, phase.kind, expired),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
        )
    }
}

@Composable
private fun StatusCardActions(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusButton(
            title = t(language, "Открыть статус бронирования", "Open booking status", "Bron holatini ochish", "Брон ҳолатини очиш"),
            primary = true,
        ) { chrome.openPilgrimCheckout(session.id) }
        StatusButton(
            title = t(language, "Открыть бронирование", "Open booking", "Bronni ochish", "Бронни очиш"),
            primary = false,
        ) { chrome.openBookingDetail(session.id) }
    }
}

@Composable
private fun StatusButton(title: String, primary: Boolean, onClick: () -> Unit) {
    val bg = if (primary) Color.Black else bookingRaisedColor()
    val fg = if (primary) Color.White else MaterialTheme.colorScheme.onBackground
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (!primary) Modifier.border(
                    .7.dp,
                    MaterialTheme.colorScheme.onBackground.copy(alpha = .06f),
                    RoundedCornerShape(18.dp),
                ) else Modifier,
            ),
        cornerRadius = 18.dp,
        background = bg,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = fg)
            Spacer(Modifier.weight(1f))
            CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), fg)
        }
    }
}

@Composable
private fun ProgressFact(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            title,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            modifier = Modifier.width(74.dp),
        )
        Text(
            value,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookingTimerOverview(language: AppLanguage, session: StoredBookingSession) {
    val phase = lifecyclePhase(session) ?: return
    val tint = statusColor(session.effectiveStatus)
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bookingCardColor())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(tint.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (session.effectiveStatus.uppercase() in setOf("AVAILABILITY_CHECK", "PAYMENT_PENDING", "BOOKING_CONFIRMED")) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = tint,
                        strokeWidth = 2.dp,
                    )
                } else {
                    CupertinoIcon(CupertinoSymbol.CalendarClock, null, Modifier.size(20.dp), tint)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    t(language, "Работа идёт", "Work is in progress", "Jarayon davom etmoqda", "Жараён давом этмоқда"),
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    t(language, "Вы можете закрыть приложение — статус обновится автоматически.", "You can close the app — the status will update automatically.", "Ilovani yopishingiz mumkin — holat avtomatik yangilanadi.", "Иловани ёпишингиз мумкин — ҳолат автоматик янгиланади."),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
            }
        }
        LifecycleTimerPanel(language, session, tint)
    }
}

@Composable
private fun BookingFulfillmentCenter(
    language: AppLanguage,
    session: StoredBookingSession,
    checkout: IumrahCheckoutResponse?,
    guideProfile: IumrahPublicProfile?,
    chrome: AppChromeStore,
) {
    val passportCount = checkout?.travelers?.count { it.hasPassport } ?: 0
    val total = checkout?.travelers?.size ?: session.booking.input.travelers.totalPeople
    val passportsReady = total > 0 && passportCount == total
    val receiptReady = !checkout?.receipts.isNullOrEmpty()
    val documents = checkout?.documents.orEmpty()
    val paidOrLater = session.effectiveStatus.uppercase() in setOf("BOOKING_CONFIRMED", "DOCUMENTS_READY", "READY_TO_TRAVEL", "IN_TRIP", "COMPLETED")
    val documentEnabled = paidOrLater || documents.isNotEmpty()
    val guideEnabled = session.guide != null || session.effectiveStatus.uppercase() in setOf("BOOKING_CONFIRMED", "DOCUMENTS_READY", "READY_TO_TRAVEL", "IN_TRIP", "COMPLETED")

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        SectionHeader(
            t(language, "Что нужно сделать", "What to do next", "Keyingi qadamlar", "Кейинги қадамлар"),
            null,
        )
        Text(
            t(language, "Каждый этап открывается тогда, когда он нужен. Сейчас достаточно прикрепить паспорта — без KYC и длинных анкет.", "Each stage opens when it is needed. For now, attaching the passports is enough — no KYC or long forms.", "Har bir bosqich kerak bo‘lganda ochiladi. Hozir pasportlarni biriktirishning o‘zi yetarli — KYC va uzun anketalarsiz.", "Ҳар бир босқич керак бўлганда очилади. Ҳозир паспортларни бириктиришнинг ўзи етарли — KYC ва узун анкеталарсиз."),
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )

        BookingActionCard(
            icon = CupertinoSymbol.Passport,
            tint = iOSBlue,
            title = t(language, "Прикрепить паспорта", "Attach passports", "Pasportlarni biriktirish", "Паспортларни бириктириш"),
            body = if (passportsReady) {
                t(language, "Паспорта всех паломников прикреплены. Можно переходить к следующему этапу.", "Every pilgrim passport is attached. You are ready for the next stage.", "Barcha ziyoratchilar pasporti biriktirilgan. Keyingi bosqichga tayyor.", "Барча зиёратчилар паспорти бириктирилган. Кейинги босқичга тайёр.")
            } else {
                t(language, "Прикреплено: $passportCount из $total. Для каждого паломника нужна чёткая фотография страницы с данными.", "Attached: $passportCount of $total. Each pilgrim needs a clear photo of the passport information page.", "Biriktirilgan: $passportCount/$total. Har bir ziyoratchi uchun pasport ma’lumotlar sahifasining aniq rasmi kerak.", "Бириктирилган: $passportCount/$total. Ҳар бир зиёратчи учун паспорт маълумотлар саҳифасининг аниқ расми керак.")
            },
            action = if (passportsReady) t(language, "Проверить паспорта", "Review passports", "Pasportlarni tekshirish", "Паспортларни текшириш") else t(language, "Прикрепить паспорта", "Attach passports", "Pasportlarni biriktirish", "Паспортларни бириктириш"),
            ready = passportsReady,
        ) { chrome.openPilgrimCheckout(session.id) }

        val paymentLocked = session.effectiveStatus.uppercase() == "AVAILABILITY_CHECK"
        BookingActionCard(
            icon = if (paymentLocked) CupertinoSymbol.Lock else CupertinoSymbol.CreditCard,
            tint = iOSGreen,
            title = t(language, "Оплата", "Payment", "To‘lov", "Тўлов"),
            body = if (paymentLocked) {
                t(language, "Оплата откроется после подтверждения наличия. Пока ничего оплачивать не нужно.", "Payment opens after availability is confirmed. Nothing needs to be paid yet.", "To‘lov mavjudlik tasdiqlangach ochiladi. Hozircha hech narsa to‘lash shart emas.", "Тўлов мавжудлик тасдиқлангач очилади. Ҳозирча ҳеч нарса тўлаш шарт эмас.")
            } else if (receiptReady) {
                t(language, "Оплата получена. Чек сохранён в бронировании.", "Payment received. The receipt is saved with the booking.", "To‘lov qabul qilindi. Chek bronda saqlandi.", "Тўлов қабул қилинди. Чек бронда сақланди.")
            } else {
                t(language, "Наличие подтверждено. Откройте реквизиты, оплатите и прикрепите чек.", "Availability is confirmed. Open payment details, pay and attach the receipt.", "Mavjudlik tasdiqlandi. Rekvizitlarni oching, to‘lang va chekni biriktiring.", "Мавжудлик тасдиқланди. Реквизитларни очинг, тўланг ва чекни бириктиринг.")
            },
            action = if (paymentLocked) t(language, "Откроется после подтверждения", "Opens after confirmation", "Tasdiqdan keyin ochiladi", "Тасдиқдан кейин очилади") else if (receiptReady) t(language, "Открыть оплату", "Open payment", "To‘lovni ochish", "Тўловни очиш") else t(language, "Перейти к оплате", "Go to payment", "To‘lovga o‘tish", "Тўловга ўтиш"),
            ready = receiptReady,
            enabled = !paymentLocked,
        ) { chrome.openPilgrimCheckout(session.id) }

        BookingActionCard(
            icon = if (documentEnabled) CupertinoSymbol.Document else CupertinoSymbol.Lock,
            tint = iOSCyan,
            title = t(language, "Документы поездки", "Travel documents", "Safar hujjatlari", "Сафар ҳужжатлари"),
            body = if (documents.isNotEmpty()) {
                t(language, "Готово документов: ${documents.size}. Авиабилеты, подтверждения отеля и остальные файлы находятся внутри.", "Documents ready: ${documents.size}. Tickets, hotel confirmations and the other files are inside.", "Tayyor hujjatlar: ${documents.size}. Chiptalar, mehmonxona tasdiqlari va boshqa fayllar shu yerda.", "Тайёр ҳужжатлар: ${documents.size}. Чипталар, меҳмонхона тасдиқлари ва бошқа файллар шу ерда.")
            } else {
                t(language, "Авиабилеты, виза и номера бронирований будут доступны после оплаты и подтверждения бронирования.", "Airline tickets, visa and booking references become available after payment and booking confirmation.", "Aviachiptalar, viza va bron raqamlari to‘lov hamda bron tasdiqlangach ochiladi.", "Авиачипталар, виза ва брон рақамлари тўлов ҳамда брон тасдиқлангач очилади.")
            },
            action = if (documentEnabled) t(language, "Посмотреть документы", "View documents", "Hujjatlarni ko‘rish", "Ҳужжатларни кўриш") else t(language, "Откроется после оплаты", "Opens after payment", "To‘lovdan keyin ochiladi", "Тўловдан кейин очилади"),
            ready = documents.isNotEmpty(),
            enabled = documentEnabled,
        ) { chrome.openPilgrimCheckout(session.id) }

        session.guide?.let { guide ->
            AssignedGuideStatusCard(language, guide.displayName, guideProfile, onClick = { chrome.openBookingGuideTransfer(session.id) })
        }

        BookingActionCard(
            icon = if (guideEnabled) CupertinoSymbol.ShieldCheck else CupertinoSymbol.Lock,
            tint = iOSIndigo,
            title = t(language, "Гид и трансфер", "Guide & transfer", "Gid va transfer", "Гид ва трансфер"),
            body = if (session.guide != null) {
                t(language, "Гид назначен. Здесь доступны его контакты, данные трансфера и фото для быстрой встречи в аэропорту.", "Your guide is assigned. Contacts, transfer details and the airport recognition photo are available here.", "Gid tayinlangan. Kontaktlar, transfer ma’lumotlari va aeroportda tezroq topish uchun rasm shu yerda.", "Гид тайинланган. Контактлар, трансфер маълумотлари ва аэропортда тезроқ топиш учун расм шу ерда.")
            } else {
                t(language, "Контакты гида и детали встречи откроются после подтверждения бронирования и назначения команды.", "Guide contacts and meeting details open after the booking is confirmed and the team is assigned.", "Gid kontaktlari va kutib olish ma’lumotlari bron tasdiqlanib, jamoa tayinlangach ochiladi.", "Гид контактлари ва кутиб олиш маълумотлари брон тасдиқланиб, жамоа тайинлангач очилади.")
            },
            action = if (guideEnabled) t(language, "Открыть данные встречи", "Open meeting details", "Kutib olish ma’lumotlarini ochish", "Кутиб олиш маълумотларини очиш") else t(language, "Откроется после подтверждения", "Opens after confirmation", "Tasdiqdan keyin ochiladi", "Тасдиқдан кейин очилади"),
            ready = session.guide != null,
            enabled = guideEnabled,
        ) { chrome.openBookingGuideTransfer(session.id) }

        IumrahPressable(
            onClick = { chrome.openBookingPolicy("refund") },
            modifier = Modifier.height(34.dp),
            cornerRadius = 17.dp,
            background = Color.Transparent,
            shadowElevation = 0.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CupertinoIcon(CupertinoSymbol.Document, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
                Text(t(language, "Условия возврата", "Refund policy", "Qaytarish shartlari", "Қайтариш шартлари"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
    }
}

@Composable
private fun AssignedGuideStatusCard(
    language: AppLanguage,
    guideName: String,
    profile: IumrahPublicProfile?,
    onClick: () -> Unit,
) {
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth(), cornerRadius = 26.dp, background = bookingCardColor(), pressedScale = .985f) {
        Column(Modifier.fillMaxWidth().border(.75.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .095f), RoundedCornerShape(26.dp)).padding(17.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                val photo = AppConfig.absoluteUrl(profile?.photoURL)
                if (!photo.isNullOrBlank()) {
                    AsyncImage(model = photo, contentDescription = null, modifier = Modifier.size(60.dp).clip(CircleShape).background(bookingRaisedColor()), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(bookingRaisedColor()), contentAlignment = Alignment.Center) {
                        CupertinoIcon(CupertinoSymbol.PersonCircle, null, Modifier.size(29.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(guideName.ifBlank { t(language, "Ваш гид", "Your guide", "Gidingiz", "Гидингиз") }, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(16.dp), iOSBlue)
                    }
                    Text(t(language, "Главный гид iumrah · стаж 4 года", "Lead iumrah guide · 4 years experience", "iumrah bosh gidi · 4 yil tajriba", "iumrah бош гиди · 4 йил тажриба"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                }
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
            }
            Row(Modifier.fillMaxWidth().background(bookingRaisedColor(), RoundedCornerShape(15.dp)).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CupertinoIcon(CupertinoSymbol.Phone, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Spacer(Modifier.width(8.dp))
                Text(t(language, "Контакты", "Contacts", "Kontaktlar", "Контактлар"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                Spacer(Modifier.weight(1f))
                Text(t(language, "Гид и трансфер", "Guide & transfer", "Gid va transfer", "Гид ва трансфер"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                Spacer(Modifier.width(6.dp))
                CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .45f))
            }
        }
    }
}

@Composable
private fun BookingActionCard(
    icon: CupertinoSymbol,
    tint: Color,
    title: String,
    body: String,
    action: String,
    ready: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bookingCardColor())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background((if (ready) iOSGreen else tint).copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(
                    if (ready) CupertinoSymbol.CheckCircle else icon,
                    null,
                    Modifier.size(21.dp),
                    if (ready) iOSGreen else tint,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    body,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }
        }
        IumrahPressable(
            onClick = { if (enabled) onClick() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            cornerRadius = 17.dp,
            background = if (enabled) bookingPrimaryButtonColor() else bookingRaisedColor(),
            shadowElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    action,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) bookingPrimaryButtonTextColor() else MaterialTheme.colorScheme.onBackground.copy(alpha = .42f),
                )
                Spacer(Modifier.weight(1f))
                CupertinoIcon(
                    CupertinoSymbol.ArrowRight,
                    null,
                    Modifier.size(15.dp),
                    if (enabled) bookingPrimaryButtonTextColor() else MaterialTheme.colorScheme.onBackground.copy(alpha = .36f),
                )
            }
        }
    }
}

private fun shouldShowTravelReadyFlights(session: StoredBookingSession): Boolean =
    session.effectiveStatus.uppercase() in setOf("READY_TO_TRAVEL", "IN_TRIP")

@Composable
private fun BookingStatusFlights(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
) {
    val outbound = session.booking.generatorTrace?.outbound
    val inbound = session.booking.generatorTrace?.inbound
    val shape = RoundedCornerShape(26.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bookingCardColor())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iOSBlue.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(19.dp), iOSBlue)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    t(language, "Ваши авиабилеты", "Your flights", "Aviachiptalaringiz", "Авиачипталарингиз"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    t(language, "Полные данные рейсов закреплены в статусе поездки.", "Full flight details stay attached to your trip status.", "Parvozning to‘liq ma’lumotlari safar holatida saqlanadi.", "Парвознинг тўлиқ маълумотлари сафар ҳолатида сақланади."),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
            }
        }

        if (outbound != null) {
            StatusFlightCard(language, outbound, t(language, "Туда", "Outbound", "Borish", "Бориш"))
        } else {
            LegacyFlightCard(
                t(language, "Туда", "Outbound", "Borish", "Бориш"),
                "${session.booking.route.originCode} → ${session.booking.route.outboundDestination}",
                session.booking.input.startDate,
                session.booking.flight,
                language,
            )
        }

        if (inbound != null) {
            StatusFlightCard(language, inbound, t(language, "Обратно", "Return", "Qaytish", "Қайтиш"))
        } else {
            LegacyFlightCard(
                t(language, "Обратно", "Return", "Qaytish", "Қайтиш"),
                "${session.booking.route.returnOrigin} → ${session.booking.route.originCode}",
                session.booking.input.endDate,
                session.booking.flight,
                language,
            )
        }

        IumrahPressable(
            onClick = chrome::openFlights,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            cornerRadius = 18.dp,
            background = bookingPrimaryButtonColor(),
            shadowElevation = 0.dp,
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.SignalWave, null, Modifier.size(17.dp), bookingPrimaryButtonTextColor())
                Text(
                    t(language, "Отслеживать рейс", "Track flight", "Reysni kuzatish", "Рейсни кузатиш"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = bookingPrimaryButtonTextColor(),
                )
                Spacer(Modifier.weight(1f))
                CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), bookingPrimaryButtonTextColor())
            }
        }
    }
}

@Composable
private fun StatusFlightCard(
    language: AppLanguage,
    flight: BookingGeneratorFlightSnapshot,
    label: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(bookingRaisedColor().copy(alpha = .72f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(19.dp), MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Text(flight.flightNumbers, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(flight.airline, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 1)
            }
            Text(
                if ((flight.stops ?: 0) == 0) t(language, "Прямой", "Direct", "To‘g‘ridan", "Тўғридан") else t(language, "С пересадкой", "Connection", "Ulanish", "Уланиш"),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            )
        }

        val segments = flight.segments.orEmpty()
        if (segments.isNotEmpty()) {
            segments.forEachIndexed { index, segment ->
                if (index > 0) DividerLine(0.dp)
                StatusFlightSegment(segment)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(flight.origin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
                Spacer(Modifier.weight(1f))
                Text(flight.destination, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusFlightSegment(segment: BookingGeneratorFlightSegmentSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(segment.flightNumber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            segment.aircraft?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(segment.origin, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(isoTime(segment.departureAt), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(segment.destination, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(isoTime(segment.arrivalAt), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            segment.originTerminal?.trim()?.takeIf { it.isNotEmpty() }?.let { FlightMetaPill("Dep T$it") }
            segment.destinationTerminal?.trim()?.takeIf { it.isNotEmpty() }?.let { FlightMetaPill("Arr T$it") }
            segment.cabin?.trim()?.takeIf { it.isNotEmpty() }?.let { FlightMetaPill(it) }
        }
    }
}

@Composable
private fun LegacyFlightCard(
    title: String,
    route: String,
    date: String,
    value: String,
    language: AppLanguage,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(bookingRaisedColor().copy(alpha = .72f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        Text(route, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(L10n.date(date, language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
    }
}

@Composable
private fun FlightMetaPill(text: String) {
    Box(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .045f))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
    }
}

@Composable
private fun TripPlanPreview(
    language: AppLanguage,
    session: StoredBookingSession,
    remoteItems: List<BookingItineraryItem>,
    onOpenSchedule: () -> Unit,
) {
    val items = previewItineraryItems(session, remoteItems)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(t(language, "План поездки", "Trip plan", "Safar rejasi", "Сафар режаси"), null)
        val shape = RoundedCornerShape(25.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bookingCardColor())
                .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape),
        ) {
            if (items.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .padding(horizontal = 17.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        t(language, "События поездки появятся после подтверждения деталей.", "Trip events will appear after the details are confirmed.", "Tafsilotlar tasdiqlangach safar voqealari paydo bo‘ladi.", "Тафсилотлар тасдиқлангач сафар воқеалари пайдо бўлади."),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                    )
                }
            } else {
                items.forEachIndexed { index, item ->
                    TripPlanRow(language, item)
                    if (index < items.lastIndex) DividerLine(56.dp)
                }
            }
            DividerLine(17.dp)
            IumrahPressable(
                onClick = onOpenSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                cornerRadius = 0.dp,
                background = Color.Transparent,
                shadowElevation = 0.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        t(language, "Открыть полное расписание", "Open full schedule", "To‘liq jadvalni ochish", "Тўлиқ жадвални очиш"),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
                }
            }
        }
    }
}

@Composable
private fun TripPlanRow(language: AppLanguage, item: BookingItineraryItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(39.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bookingRaisedColor()),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(safeItineraryIcon(item.icon), null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(compactDate(item.dateLocal, language), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.subtitle.trim().takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            item.location.trim().takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .34f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TripManagement(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(t(language, "Управление поездкой", "Trip management", "Safarni boshqarish", "Сафарни бошқариш"), null)
        val shape = RoundedCornerShape(25.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bookingCardColor())
                .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape),
        ) {
            ManagementRow(
                CupertinoSymbol.Sliders,
                t(language, "Управлять бронированием", "Manage booking", "Bronni boshqarish", "Бронни бошқариш"),
                t(language, "Отели, данные, услуги и документы", "Hotels, details, services and documents", "Mehmonxona, ma’lumotlar, xizmatlar va hujjatlar", "Меҳмонхона, маълумотлар, хизматлар ва ҳужжатлар"),
            ) { chrome.openBookingDetail(session.id) }
            DividerLine(65.dp)
            ManagementRow(
                CupertinoSymbol.PlusPerson,
                t(language, "Добавить паломника", "Add pilgrim", "Ziyoratchi qo‘shish", "Зиёратчи қўшиш"),
                t(language, "Запрос через iumrah Care", "Request via iumrah Care", "iumrah Care orqali so‘rov", "iumrah Care орқали сўров"),
            ) { chrome.openBookingChat(session.id) }
            DividerLine(65.dp)
            ManagementRow(
                CupertinoSymbol.Route,
                t(language, "Зияраты", "Ziyarats", "Ziyoratlar", "Зиёратлар"),
                t(language, "Фото, подробности и места Вашей программы", "Photos, details and places in your program", "Dasturingizdagi fotosuratlar, tafsilotlar va joylar", "Дастурингиздаги фотосуратлар, тафсилотлар ва жойлар"),
            ) { chrome.openBookingZiyarats() }
            DividerLine(65.dp)
            ManagementRow(
                CupertinoSymbol.Plus,
                t(language, "Новая Umrah", "New Umrah", "Yangi Umra", "Янги Умра"),
                t(language, "Собрать новый пакет", "Build a new package", "Yangi paket tuzish", "Янги пакет тузиш"),
            ) { chrome.startNewTrip() }
        }
    }
}

@Composable
private fun ManagementRow(
    icon: CupertinoSymbol,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    IumrahPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        cornerRadius = 0.dp,
        background = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bookingRaisedColor()),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(icon, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground)
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
        }
    }
}

@Composable
private fun CompactBookingCard(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
    onDelete: suspend (String) -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    IumrahPressable(
        onClick = { chrome.openBookingDetail(session.id) },
        modifier = Modifier
            .fillMaxWidth()
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .055f), shape),
        cornerRadius = 22.dp,
        background = bookingCardColor(),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor(session.effectiveStatus)))
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${session.booking.route.originCode} → ${session.booking.route.outboundDestination}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(session.displayBookingNumber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .34f))
                }
                Spacer(Modifier.height(4.dp))
                Text(L10n.status(session.effectiveStatus, language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f), maxLines = 1)
                session.travelerName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .34f), maxLines = 1)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatPrice(session.booking.perPilgrimUsd), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(L10n.date(session.booking.input.startDate, language), fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
        }
    }
}

@Composable
private fun EmptyBookingHome(
    language: AppLanguage,
    unreadCount: Int,
    chrome: AppChromeStore,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bookingPageColor()),
        contentPadding = PaddingValues(
            start = IOS_PAGE_PADDING.dp,
            end = IOS_PAGE_PADDING.dp,
            top = 10.dp,
            bottom = 112.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BookingRootHeader(
                language = language,
                title = L10n.text("tab_booking", language),
                usesBrandLogo = false,
                showsMakkahTime = true,
                unreadCount = unreadCount,
                chrome = chrome,
            )
        }
        item { EmptyStatusCard(language) }
        item { ExplorePackagesButton(language, chrome) }
        item {
            EmptyShowcaseCard(
                imageRes = R.drawable.iumrah_configurator_hero,
                imageBackground = Color.Black,
                eyebrow = "Iumrah Configurator",
                badge = t(language, "5 минут", "5 minutes", "5 daqiqa", "5 дақиқа"),
                title = L10n.text("booking_hero_title", language),
                body = L10n.text("booking_hero_body", language),
                cta = L10n.text("booking_hero_cta", language),
                dark = true,
                icon = CupertinoSymbol.Sliders,
                onClick = chrome::startNewTrip,
            )
        }
        item {
            EmptyShowcaseCard(
                imageRes = R.drawable.iumrah_care_showcase,
                imageBackground = Color.White,
                eyebrow = "Iumrah Care",
                badge = t(language, "За вас", "For you", "Siz uchun", "Сиз учун"),
                title = t(language, "Соберите Umrah за меня", "Build my Umrah for me", "Umramni men uchun yig‘ing", "Умрамни мен учун йиғинг"),
                body = t(language, "Расскажите даты, бюджет и пожелания. Iumrah Care соберёт для вас персональный вариант поездки.", "Tell us your dates, budget and preferences. Iumrah Care will prepare a personal Umrah option for you.", "Sanalar, budjet va istaklaringizni ayting. Iumrah Care siz uchun shaxsiy Umra variantini tayyorlaydi.", "Саналар, бюджет ва истакларингизни айтинг. Iumrah Care сиз учун шахсий Умра вариантини тайёрлайди."),
                cta = t(language, "Собрать мою Umrah", "Build my Umrah", "Mening Umramni yig‘ish", "Менинг Умрамни йиғиш"),
                dark = false,
                icon = CupertinoSymbol.HeartFill,
                onClick = { chrome.navigate(AppTab.CARE) },
            )
        }
    }
}

@Composable
private fun EmptyStatusCard(language: AppLanguage) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bookingCardColor())
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), shape)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .055f)),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(CupertinoSymbol.TrayFill, null, Modifier.size(23.dp), MaterialTheme.colorScheme.onBackground)
        }
        Column(Modifier.weight(1f)) {
            Text(
                t(language, "Пока бронирований нет", "No bookings yet", "Hozircha bron yo‘q", "Ҳозирча брон йўқ"),
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                t(language, "Пока здесь нет активных бронирований. Начните с Конфигуратора или передайте сборку iumrah Care.", "There are no active bookings here yet. Start with the Configurator or let iumrah Care prepare the trip for you.", "Hozircha bu yerda faol bronlar yo‘q. Konfiguratorni oching yoki safarni iumrah Care’ga topshiring.", "Ҳозирча бу ерда фаол бронлар йўқ. Конфигураторни очинг ёки сафарни iumrah Care’га топширинг."),
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            )
        }
    }
}

@Composable
private fun ExplorePackagesButton(language: AppLanguage, chrome: AppChromeStore) {
    IumrahPressable(
        onClick = { chrome.navigate(AppTab.HOTELS) },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        cornerRadius = 20.dp,
        background = Color.Black,
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CupertinoIcon(CupertinoSymbol.SuitcaseFill, null, Modifier.size(16.dp), Color.White)
            Text(
                t(language, "Смотреть готовые пакеты", "Explore Packages", "Tayyor paketlarni ko‘rish", "Тайёр пакетларни кўриш"),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp), Color.White)
        }
    }
}

@Composable
private fun EmptyShowcaseCard(
    imageRes: Int,
    imageBackground: Color,
    eyebrow: String,
    badge: String,
    title: String,
    body: String,
    cta: String,
    dark: Boolean,
    icon: CupertinoSymbol,
    onClick: () -> Unit,
) {
    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    val buttonBg = if (dark) Color.White else Color.Black
    val buttonFg = if (dark) Color.Black else Color.White
    val shape = RoundedCornerShape(34.dp)

    IumrahPressable(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(.8.dp, (if (dark) Color.White else Color.Black).copy(alpha = if (dark) .06f else .055f), shape),
        cornerRadius = 34.dp,
        background = bg,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(236.dp)
                    .background(imageBackground),
            ) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 1.08f; scaleY = 1.08f },
                    contentScale = ContentScale.Crop,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 222.dp)
                    .padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val eyebrowColor = if (dark) Color.White.copy(alpha = .78f) else Color.Black.copy(alpha = .58f)
                    CupertinoIcon(icon, null, Modifier.size(15.dp), eyebrowColor)
                    Spacer(Modifier.width(8.dp))
                    Text(eyebrow, color = eyebrowColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .45.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .height(29.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(fg.copy(alpha = if (dark) .10f else .055f))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(badge, color = if (dark) Color.White.copy(alpha = .82f) else Color.Black.copy(alpha = .62f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text(title, color = fg, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.75).sp)
                Spacer(Modifier.height(8.dp))
                Text(body, color = if (dark) Color.White.copy(alpha = .68f) else Color.Black.copy(alpha = .62f), fontSize = 15.sp, lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(buttonBg)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cta, color = buttonFg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(15.dp), buttonFg)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.35).sp,
            modifier = Modifier.weight(1f),
        )
        trailing?.let {
            Text(
                it,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            )
        }
    }
}

@Composable
private fun DividerLine(start: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(.7.dp)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = .07f)),
    )
}

private data class BookingHomeLifecyclePhase(val kind: LifecycleKind, val deadlineMs: Long, val symbol: CupertinoSymbol)
private enum class LifecycleKind { AVAILABILITY, PRICE_LOCK, PAYMENT_CONFIRMATION, DOCUMENTS }

private fun lifecyclePhase(session: StoredBookingSession): BookingHomeLifecyclePhase? {
    return when (session.effectiveStatus.uppercase()) {
        "NEW", "AVAILABILITY_CHECK" -> lifecycleDeadline(
            explicit = session.availabilityDeadlineAt,
            start = session.availabilityStartedAt
                ?: transitionDate("availability_check", session)
                ?: transitionDate("new", session)
                ?: session.booking.createdAt,
            durationMs = 6L * 60 * 60 * 1000,
        )?.let { BookingHomeLifecyclePhase(LifecycleKind.AVAILABILITY, it, CupertinoSymbol.CalendarClock) }

        "PAYMENT_PENDING" -> {
            if (!session.paymentReceivedAt.isNullOrBlank()) {
                lifecycleDeadline(
                    explicit = session.paymentConfirmationDeadlineAt,
                    start = session.paymentReceivedAt,
                    durationMs = 10L * 60 * 1000,
                )?.let { BookingHomeLifecyclePhase(LifecycleKind.PAYMENT_CONFIRMATION, it, CupertinoSymbol.CheckCircle) }
            } else {
                lifecycleDeadline(
                    explicit = session.priceLockExpiresAt,
                    start = session.priceLockStartedAt
                        ?: transitionDate("payment_pending", session)
                        ?: session.booking.updatedAt,
                    durationMs = 30L * 60 * 1000,
                )?.let { BookingHomeLifecyclePhase(LifecycleKind.PRICE_LOCK, it, CupertinoSymbol.CreditCard) }
            }
        }

        "PAID", "BOOKING_CONFIRMED" -> lifecycleDeadline(
            explicit = session.documentsDeadlineAt,
            start = session.documentsStartedAt
                ?: transitionDate("booking_confirmed", session)
                ?: transitionDate("paid", session)
                ?: session.booking.updatedAt,
            durationMs = 24L * 60 * 60 * 1000,
        )?.let { BookingHomeLifecyclePhase(LifecycleKind.DOCUMENTS, it, CupertinoSymbol.Document) }

        else -> null
    }
}

private fun lifecycleDeadline(explicit: String?, start: String?, durationMs: Long): Long? {
    parseInstantMs(explicit)?.let { return it }
    return parseInstantMs(start)?.plus(durationMs)
}

private fun transitionDate(status: String, session: StoredBookingSession): String? =
    session.orderedStatusHistory.lastOrNull {
        it.newStatus.trim().equals(status.trim(), ignoreCase = true)
    }?.createdAt

private fun lifecycleTimerTitle(language: AppLanguage, kind: LifecycleKind, expired: Boolean): String = when (kind) {
    LifecycleKind.AVAILABILITY -> if (expired) {
        t(language, "Проверка занимает дольше обычного", "The check is taking longer than usual", "Tekshiruv odatdagidan uzoqroq davom etmoqda", "Текширув одатдагидан узоқроқ давом этмоқда")
    } else t(language, "До максимального срока проверки", "Until the maximum check time", "Tekshiruvning maksimal muddatigacha", "Текширувнинг максимал муддатигача")

    LifecycleKind.PRICE_LOCK -> if (expired) {
        t(language, "Срок фиксации цены завершён", "Price hold has ended", "Narxni saqlash muddati tugadi", "Нархни сақлаш муддати тугади")
    } else t(language, "Цена зафиксирована ещё", "Price held for", "Narx yana shuncha vaqtga saqlanadi", "Нарх яна шунча вақтга сақланади")

    LifecycleKind.PAYMENT_CONFIRMATION -> if (expired) {
        t(language, "Подтверждение занимает дольше обычного", "Confirmation is taking longer than usual", "Tasdiqlash odatdagidan uzoqroq davom etmoqda", "Тасдиқлаш одатдагидан узоқроқ давом этмоқда")
    } else t(language, "Подтверждаем оплату", "Confirming payment", "To‘lov tasdiqlanmoqda", "Тўлов тасдиқланмоқда")

    LifecycleKind.DOCUMENTS -> if (expired) {
        t(language, "Подготовка занимает дольше обычного", "Preparation is taking longer than usual", "Tayyorlash odatdagidan uzoqroq davom etmoqda", "Тайёрлаш одатдагидан узоқроқ давом этмоқда")
    } else t(language, "Плановый срок подготовки", "Planned preparation time", "Rejalashtirilgan tayyorlash muddati", "Режалаштирилган тайёрлаш муддати")
}

private fun lifecycleTimerFootnote(language: AppLanguage, kind: LifecycleKind, expired: Boolean): String = when (kind) {
    LifecycleKind.AVAILABILITY -> if (expired) {
        t(language, "Мы продолжаем проверку. Статус обновится автоматически, как только все компоненты будут подтверждены.", "We are continuing the check. The status will update automatically once all components are confirmed.", "Tekshiruv davom etmoqda. Barcha qismlar tasdiqlangach holat avtomatik yangilanadi.", "Текширув давом этмоқда. Барча қисмлар тасдиқлангач ҳолат автоматик янгиланади.")
    } else {
        t(language, "Обычно подтверждение занимает 1–2 часа. Можно закрыть приложение — статус обновится автоматически.", "Confirmation usually takes 1–2 hours. You can close the app — the status will update automatically.", "Tasdiqlash odatda 1–2 soat davom etadi. Ilovani yopishingiz mumkin — holat avtomatik yangilanadi.", "Тасдиқлаш одатда 1–2 соат давом этади. Иловани ёпишингиз мумкин — ҳолат автоматик янгиланади.")
    }

    LifecycleKind.PRICE_LOCK -> if (expired) {
        t(language, "Перед подтверждением оплаты iumrah повторно проверит актуальную итоговую стоимость.", "Before confirming payment, iumrah will recheck the current total price.", "To‘lovni tasdiqlashdan oldin iumrah yakuniy narxning dolzarbligini qayta tekshiradi.", "Тўловни тасдиқлашдан олдин iumrah якуний нархнинг долзарблигини қайта текширади.")
    } else {
        t(language, "Авиабилеты и некоторые другие компоненты имеют динамическую стоимость и после окончания периода могут потребовать повторной проверки.", "Flights and some other components have dynamic pricing and may require a fresh check after this period.", "Aviachiptalar va ayrim boshqa qismlar dinamik narxga ega, muddat tugagach qayta tekshiruv talab qilinishi mumkin.", "Авиачипталар ва айрим бошқа қисмлар динамик нархга эга, муддат тугагач қайта текширув талаб қилиниши мумкин.")
    }

    LifecycleKind.PAYMENT_CONFIRMATION -> t(language,
        "Оплата получена. Обычно проверка и окончательная фиксация бронирования занимают до 10 минут.",
        "Payment received. Verification and final booking confirmation usually take up to 10 minutes.",
        "To‘lov qabul qilindi. Tekshiruv va bronni yakuniy tasdiqlash odatda 10 daqiqagacha davom etadi.",
        "Тўлов қабул қилинди. Текширув ва бронни якуний тасдиқлаш одатда 10 дақиқагача давом этади.",
    )

    LifecycleKind.DOCUMENTS -> t(language,
        "Обычно доступные документы готовятся в течение 24 часов. Срок визы может зависеть от доступности официальных визовых систем Саудовской Аравии и внешних ограничений.",
        "Available travel documents are usually prepared within 24 hours. Visa timing can depend on the availability of Saudi Arabia’s official visa systems and external restrictions.",
        "Mavjud safar hujjatlari odatda 24 soat ichida tayyorlanadi. Viza muddati Saudiya Arabistonining rasmiy viza tizimlari mavjudligi va tashqi cheklovlarga bog‘liq bo‘lishi mumkin.",
        "Мавжуд сафар ҳужжатлари одатда 24 соат ичида тайёрланади. Виза муддати Саудия Арабистонининг расмий виза тизимлари мавжудлиги ва ташқи чекловларга боғлиқ бўлиши мумкин.",
    )
}

private fun lifecycleCountdown(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun activeStageSubtitle(language: AppLanguage, session: StoredBookingSession, stage: ProgressStage): String =
    when (lifecyclePhase(session)?.kind) {
        LifecycleKind.PAYMENT_CONFIRMATION -> t(language, "Оплата получена · подтверждаем бронирование", "Payment received · confirming booking", "To‘lov qabul qilindi · bron tasdiqlanmoqda", "Тўлов қабул қилинди · брон тасдиқланмоқда")
        LifecycleKind.DOCUMENTS -> t(language, "Бронирование подтверждено · готовим документы", "Booking confirmed · preparing documents", "Bron tasdiqlandi · hujjatlar tayyorlanmoqda", "Брон тасдиқланди · ҳужжатлар тайёрланмоқда")
        else -> stageSubtitle(language, stage)
    }

private fun activeCardTitle(language: AppLanguage, session: StoredBookingSession, stage: ProgressStage): String =
    when (lifecyclePhase(session)?.kind) {
        LifecycleKind.PRICE_LOCK -> t(language, "Цена зафиксирована", "Price held", "Narx saqlandi", "Нарх сақланди")
        LifecycleKind.PAYMENT_CONFIRMATION -> t(language, "Оплата получена", "Payment received", "To‘lov qabul qilindi", "Тўлов қабул қилинди")
        LifecycleKind.DOCUMENTS -> t(language, "Подготавливаем документы", "Preparing documents", "Hujjatlar tayyorlanmoqda", "Ҳужжатлар тайёрланмоқда")
        else -> stageCardTitle(language, stage)
    }

private fun activeCardBody(language: AppLanguage, session: StoredBookingSession, stage: ProgressStage): String =
    when (lifecyclePhase(session)?.kind) {
        LifecycleKind.AVAILABILITY -> t(language, "iumrah подтверждает перелёт, отель и выбранные услуги. Обычно это занимает 1–2 часа, максимальный срок — до 6 часов.", "iumrah is confirming your flight, hotel and selected services. This usually takes 1–2 hours, with a maximum target of 6 hours.", "iumrah parvoz, mehmonxona va tanlangan xizmatlarni tasdiqlamoqda. Odatda 1–2 soat, maksimal muddat 6 soatgacha.", "iumrah парвоз, меҳмонхона ва танланган хизматларни тасдиқламоқда. Одатда 1–2 соат, максимал муддат 6 соатгача.")
        LifecycleKind.PRICE_LOCK -> t(language, "Итоговая цена пакета зафиксирована на время оплаты.", "Your package total is held during the payment window.", "Paketning yakuniy narxi to‘lov oynasi davomida saqlanadi.", "Пакетнинг якуний нархи тўлов ойнаси давомида сақланади.")
        LifecycleKind.PAYMENT_CONFIRMATION -> t(language, "Проверяем полученную оплату и окончательно фиксируем бронирование.", "We are verifying the payment and finalizing your booking.", "Qabul qilingan to‘lov tekshirilmoqda va bron yakuniy tasdiqlanmoqda.", "Қабул қилинган тўлов текширилмоқда ва брон якуний тасдиқланмоқда.")
        LifecycleKind.DOCUMENTS -> t(language, "Бронирование подтверждено. Теперь готовим доступные документы поездки.", "Your booking is confirmed. We are now preparing the available travel documents.", "Bron tasdiqlandi. Endi mavjud safar hujjatlari tayyorlanmoqda.", "Брон тасдиқланди. Энди мавжуд сафар ҳужжатлари тайёрланмоқда.")
        null -> stageCardBody(language, stage)
    }

private fun progressIndex(status: String): Int = when (status.uppercase()) {
    "NEW", "AVAILABILITY_CHECK" -> 1
    "PAYMENT_PENDING" -> 2
    "PAID", "BOOKING_CONFIRMED" -> 3
    "DOCUMENTS_READY", "READY_TO_TRAVEL" -> 4
    "IN_TRIP" -> 5
    "COMPLETED" -> 6
    "CANCELLED" -> 1
    else -> 1
}

private fun statusColor(status: String): Color = IumrahBookingStatusVisual.color(status)

private fun statusIcon(status: String): CupertinoSymbol = when (status.uppercase()) {
    "NEW", "AVAILABILITY_CHECK" -> CupertinoSymbol.Hourglass
    "PAYMENT_PENDING" -> CupertinoSymbol.CreditCard
    "PAID", "BOOKING_CONFIRMED" -> CupertinoSymbol.CheckCircle
    "DOCUMENTS_READY", "READY_TO_TRAVEL" -> CupertinoSymbol.ShieldCheck
    "IN_TRIP" -> CupertinoSymbol.Location
    "COMPLETED" -> CupertinoSymbol.CheckCircle
    "CANCELLED" -> CupertinoSymbol.ExclamationCircle
    else -> CupertinoSymbol.Suitcase
}

private fun activeNodeForeground(status: String): Color = when (status.uppercase()) {
    "NEW", "AVAILABILITY_CHECK" -> Color.Black.copy(alpha = .78f)
    else -> Color.White
}

private fun stageTitle(language: AppLanguage, stage: ProgressStage): String =
    t(language, stage.titleRu, stage.titleEn, stage.titleUz, stage.titleCy)

private fun stageSubtitle(language: AppLanguage, stage: ProgressStage): String =
    t(language, stage.subtitleRu, stage.subtitleEn, stage.subtitleUz, stage.subtitleCy)

private fun stageCardTitle(language: AppLanguage, stage: ProgressStage): String =
    t(language, stage.cardTitleRu, stage.cardTitleEn, stage.cardTitleUz, stage.cardTitleCy)

private fun stageCardBody(language: AppLanguage, stage: ProgressStage): String =
    t(language, stage.cardBodyRu, stage.cardBodyEn, stage.cardBodyUz, stage.cardBodyCy)

private fun cancelledStage(language: AppLanguage): ProgressStage = ProgressStage(
    "CANCELLED",
    t(language, "Бронирование отменено", "Booking cancelled", "Bron bekor qilindi", "Брон бекор қилинди"),
    t(language, "Booking cancelled", "Booking cancelled", "Booking cancelled", "Booking cancelled"),
    t(language, "Bron bekor qilindi", "Bron bekor qilindi", "Bron bekor qilindi", "Bron bekor qilindi"),
    t(language, "Брон бекор қилинди", "Брон бекор қилинди", "Брон бекор қилинди", "Брон бекор қилинди"),
    t(language, "Поездка остановлена", "The trip has been stopped", "Safar to‘xtatildi", "Сафар тўхтатилди"),
    t(language, "The trip has been stopped", "The trip has been stopped", "The trip has been stopped", "The trip has been stopped"),
    t(language, "Safar to‘xtatildi", "Safar to‘xtatildi", "Safar to‘xtatildi", "Safar to‘xtatildi"),
    t(language, "Сафар тўхтатилди", "Сафар тўхтатилди", "Сафар тўхтатилди", "Сафар тўхтатилди"),
    cardBodyRu = "Откройте бронирование, чтобы посмотреть сохранённые детали поездки и доступные действия.",
    cardBodyEn = "Open the booking to review the saved trip details and available actions.",
    cardBodyUz = "Saqlangan safar tafsilotlari va mavjud amallarni ko‘rish uchun bronni oching.",
    cardBodyCy = "Сақланган сафар тафсилотлари ва мавжуд амалларни кўриш учун бронни очинг.",
)

private fun progressCounter(language: AppLanguage, current: Int, total: Int): String =
    when (language) {
        AppLanguage.RUSSIAN -> "${current + 1} из $total"
        AppLanguage.ENGLISH -> "${current + 1} of $total"
        else -> "${current + 1} / $total"
    }

private fun stageTimestamp(index: Int, current: Int, session: StoredBookingSession, cancelled: Boolean): String? = when (index) {
    0 -> session.booking.createdAt
    1 -> if (cancelled) {
        session.latestStatusTimestamp(setOf("cancelled"))
            ?: transitionDate("cancelled", session)
            ?: session.booking.updatedAt
    } else {
        session.availabilityStartedAt
            ?: session.latestStatusTimestamp(setOf("availability_check", "new"))
            ?: session.booking.createdAt
    }
    2 -> session.priceLockStartedAt
        ?: session.latestStatusTimestamp(setOf("payment_pending"))
        ?: session.booking.updatedAt
    3 -> session.documentsStartedAt
        ?: session.latestStatusTimestamp(setOf("booking_confirmed", "paid"))
        ?: session.paymentReceivedAt
        ?: session.booking.updatedAt
    4 -> session.latestStatusTimestamp(setOf("ready_to_travel", "documents_ready"))
        ?: transitionDate("ready_to_travel", session)
        ?: session.booking.updatedAt
    5 -> session.latestStatusTimestamp(setOf("in_trip"))
        ?: transitionDate("in_trip", session)
        ?: session.booking.updatedAt
    6 -> session.latestStatusTimestamp(setOf("completed"))
        ?: transitionDate("completed", session)
        ?: session.booking.updatedAt
    else -> null
}

private fun compactTimestamp(raw: String, language: AppLanguage): String {
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw.take(10)
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.UZBEK -> Locale("uz")
        AppLanguage.UZBEK_CYRILLIC -> Locale("uz", "Cyrl")
    }
    return DateTimeFormatter.ofPattern("d MMM · HH:mm", locale)
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

private fun previewItineraryItems(
    session: StoredBookingSession,
    remote: List<BookingItineraryItem>,
): List<BookingItineraryItem> {
    val distinctDays = remote.map { it.dateLocal }.toSet().size
    val source = if (distinctDays >= 2) {
        remote.sortedWith(compareBy<BookingItineraryItem> { it.dateLocal }.thenBy { it.sortOrder })
    } else {
        buildList {
        add(
            BookingItineraryItem(
                id = "generated-outbound",
                bookingID = session.id,
                dateLocal = session.booking.input.startDate,
                sortOrder = 0,
                title = "${session.booking.route.originCode} → ${session.booking.route.outboundDestination}",
                subtitle = session.booking.flight,
                icon = "airplane",
                location = session.booking.route.outboundDestination,
                notes = "",
                createdAt = session.booking.createdAt,
                updatedAt = session.booking.updatedAt,
            ),
        )
        add(
            BookingItineraryItem(
                id = "generated-makkah",
                bookingID = session.id,
                dateLocal = session.booking.stay.makkahCheckIn,
                sortOrder = 10,
                title = session.booking.hotelNames.makkah,
                subtitle = "Makkah · ${session.booking.stay.makkahNights} nights",
                icon = "building.2",
                location = "Makkah",
                notes = "",
                createdAt = session.booking.createdAt,
                updatedAt = session.booking.updatedAt,
            ),
        )
        if (session.booking.input.includeMadinah && session.booking.hotelNames.madinah.isNotBlank()) {
            add(
                BookingItineraryItem(
                    id = "generated-madinah",
                    bookingID = session.id,
                    dateLocal = session.booking.stay.madinahCheckIn ?: session.booking.input.endDate,
                    sortOrder = 20,
                    title = session.booking.hotelNames.madinah,
                    subtitle = "Madinah · ${session.booking.stay.madinahNights ?: 0} nights",
                    icon = "building.2",
                    location = "Madinah",
                    notes = "",
                    createdAt = session.booking.createdAt,
                    updatedAt = session.booking.updatedAt,
                ),
            )
        }
        add(
            BookingItineraryItem(
                id = "generated-return",
                bookingID = session.id,
                dateLocal = session.booking.input.endDate,
                sortOrder = 30,
                title = "${session.booking.route.returnOrigin} → ${session.booking.route.originCode}",
                subtitle = session.booking.flight,
                icon = "airplane",
                location = session.booking.route.originCode,
                notes = "",
                createdAt = session.booking.createdAt,
                updatedAt = session.booking.updatedAt,
            ),
        )
        }
    }
    if (source.isEmpty()) return emptyList()
    val today = LocalDate.now(ZoneId.of("Asia/Riyadh")).toString()
    val upcoming = source.filter { it.dateLocal >= today }
    return if (upcoming.isNotEmpty()) upcoming.take(3) else source.takeLast(3)
}

private fun compactDate(raw: String, language: AppLanguage): String {
    val date = runCatching { LocalDate.parse(raw) }.getOrNull() ?: return raw
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.UZBEK -> Locale("uz")
        AppLanguage.UZBEK_CYRILLIC -> Locale("uz", "Cyrl")
    }
    return DateTimeFormatter.ofPattern("d MMM", locale).format(date)
}

private fun safeItineraryIcon(raw: String): CupertinoSymbol {
    val value = raw.lowercase()
    return when {
        "airplane" in value -> CupertinoSymbol.Airplane
        "hotel" in value || "building" in value || "bed" in value -> CupertinoSymbol.Hotel
        "car" in value || "bus" in value -> CupertinoSymbol.Car
        "map" in value || "location" in value -> CupertinoSymbol.Route
        "meal" in value || "fork" in value -> CupertinoSymbol.ForkKnife
        "doc" in value || "ticket" in value -> CupertinoSymbol.Document
        else -> CupertinoSymbol.CalendarClock
    }
}

private fun shortBookingNumber(language: AppLanguage, number: String): String = when (language) {
    AppLanguage.RUSSIAN -> "Бронь $number"
    AppLanguage.ENGLISH -> "Booking $number"
    AppLanguage.UZBEK -> "Bron $number"
    AppLanguage.UZBEK_CYRILLIC -> "Брон $number"
}

private fun formatPrice(amount: Double): String = NumberFormat.getCurrencyInstance(Locale.US).apply {
    currency = Currency.getInstance("USD")
    maximumFractionDigits = 0
}.format(amount)

private fun pilgrimCount(language: AppLanguage, count: Int): String = when (language) {
    AppLanguage.RUSSIAN -> "$count паломн."
    AppLanguage.ENGLISH -> if (count == 1) "1 pilgrim" else "$count pilgrims"
    AppLanguage.UZBEK -> "$count ziyoratchi"
    AppLanguage.UZBEK_CYRILLIC -> "$count зиёратчи"
}

private fun parseInstantMs(raw: String?): Long? {
    if (raw.isNullOrBlank()) return null
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun isoTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(raw))
}.getOrElse { raw.takeLast(5) }

private fun makkahTime(nowMs: Long): String = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.of("Asia/Riyadh"))
    .format(Instant.ofEpochMilli(nowMs))

private fun t(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

@Composable
private fun bookingDarkMode(): Boolean = MaterialTheme.colorScheme.background.iosLuminance() < .45f

/** iOS systemBackground used by the SwiftUI Booking tab. */
@Composable
private fun bookingPageColor(): Color = if (bookingDarkMode()) Color.Black else Color(0xFFF2F2F7)

/** iOS secondarySystemGroupedBackground used by the SwiftUI booking cards. */
@Composable
private fun bookingCardColor(): Color = if (bookingDarkMode()) Color(0xFF1C1C1E) else Color.White

/** iOS tertiarySystemGroupedBackground used by raised controls and icon wells. */
@Composable
private fun bookingRaisedColor(): Color = if (bookingDarkMode()) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

/** Mirrors Color.iumrahPrimaryButtonBackground / Text from the iOS design system. */
@Composable
private fun bookingPrimaryButtonColor(): Color = if (bookingDarkMode()) Color(0xFFF5F5F7) else Color.Black

@Composable
private fun bookingPrimaryButtonTextColor(): Color = if (bookingDarkMode()) Color(0xFF121316) else Color.White

private fun Color.iosLuminance(): Float = .2126f * red + .7152f * green + .0722f * blue
