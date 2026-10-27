package com.iumrah.beta.ui.care
import com.iumrah.beta.ui.cupertino.Icon

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import kotlinx.coroutines.launch
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahRootPageHeader
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/**
 * Android parity port of iOS `CareHomeView.swift`.
 *
 * Geometry, content hierarchy, hero image and interaction states intentionally
 * follow the iOS source instead of the older Android Care implementation.
 */
@Composable
fun CareHomeScreen(
    language: AppLanguage,
    bookingStore: BookingStore,
    chatService: ChatService,
    notifications: ClientNotificationStore,
    chrome: AppChromeStore,
) {
    val state by bookingStore.state.collectAsState()
    val notificationState by notifications.state.collectAsState()
    val context = LocalContext.current
    val palette = carePalette()
    var telegram by remember { mutableStateOf("@saudiclub966") }

    val activeSession = state.sessions.firstOrNull {
        it.effectiveStatus.uppercase() !in setOf("COMPLETED", "CANCELLED")
    }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    // Use the same actions in both the quick contacts and the large Care card.
    val callCare: () -> Unit = {
        runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+998508898845"))) }
        Unit
    }
    val openTelegram: () -> Unit = {
        val raw = telegram.trim()
        if (raw.isNotEmpty()) {
            val url = if (raw.startsWith("https://", ignoreCase = true)) raw
                else "https://t.me/${raw.removePrefix("@").trim('/')}"
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
        Unit
    }
    val openChat: () -> Unit = { activeSession?.let { chrome.openBookingChat(it.id) }; Unit }

    LaunchedEffect(bookingStore, chatService) {
        // Match the iOS screen's initial refresh behavior without introducing a
        // new store architecture: refresh the sessions already known locally.
        bookingStore.refreshAll()
        runCatching { chatService.loadCareProfile() }.getOrNull()?.telegram
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { telegram = it }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            if (!isRefreshing) scope.launch {
                isRefreshing = true
                try { bookingStore.refreshAll() } finally { isRefreshing = false }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.page)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 112.dp),
        ) {
            IumrahRootPageHeader(
                title = "iumrah Care",
                chrome = chrome,
                unreadCount = notificationState.unreadCount,
            )
            Spacer(Modifier.height(14.dp))
            CareQuickContacts(
                language = language,
                palette = palette,
                chatEnabled = activeSession != null,
                telegramEnabled = telegram.isNotBlank(),
                onChat = openChat,
                onPhone = callCare,
                onTelegram = openTelegram,
            )
            Spacer(Modifier.height(24.dp))
            CareIntro(language = language, palette = palette)
            Spacer(Modifier.height(24.dp))

            // Mirrors adaptiveLayout.isWide from SwiftUI without altering the
            // single-column geometry on phones.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 700.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.TopStart) {
                            Box(Modifier.widthIn(max = 470.dp)) {
                                CareHero(
                                language, activeSession, telegram, palette,
                                    onChat = openChat, onCall = callCare, onTelegram = openTelegram,
                                )
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            CareHelpTopics(language = language, palette = palette)
                            Spacer(Modifier.height(30.dp))
                            CareQuickAnswers(language = language, palette = palette)
                        }
                    }
                } else {
                    Column {
                        CareHero(
                            language, activeSession, telegram, palette,
                            onChat = openChat, onCall = callCare, onTelegram = openTelegram,
                        )
                        Spacer(Modifier.height(28.dp))
                        CareHelpTopics(language = language, palette = palette)
                        Spacer(Modifier.height(30.dp))
                        CareQuickAnswers(language = language, palette = palette)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

}

/** Top-row Care shortcuts, matching CareHomeView.careQuickContacts (105 x 25). */
@Composable
private fun CareQuickContacts(
    language: AppLanguage,
    palette: CarePalette,
    chatEnabled: Boolean,
    telegramEnabled: Boolean,
    onChat: () -> Unit,
    onPhone: () -> Unit,
    onTelegram: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        CareQuickContactTile(
            modifier = Modifier.weight(1f), icon = CupertinoSymbol.Phone,
            label = tr(language, "Call", "Позвонить", "Qo‘ng‘iroq", "Қўнғироқ"),
            color = Color(0xFF34C759), enabled = true, palette = palette, onClick = onPhone,
        )
        CareQuickContactTile(
            modifier = Modifier.weight(1f), icon = CupertinoSymbol.Send,
            label = "Telegram", color = Color(0xFF007AFF),
            enabled = telegramEnabled, palette = palette, onClick = onTelegram,
        )
        CareQuickContactTile(
            modifier = Modifier.weight(1f), icon = CupertinoSymbol.ChatBubble,
            label = tr(language, "Chat", "Чат", "Chat", "Чат"),
            color = palette.primary, enabled = chatEnabled, palette = palette, onClick = onChat,
        )
    }
}

@Composable
private fun CareQuickContactTile(
    modifier: Modifier,
    icon: CupertinoSymbol,
    label: String,
    color: Color,
    enabled: Boolean,
    palette: CarePalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier = modifier
            .height(105.dp)
            .clip(shape)
            .background(palette.card)
            .border(1.dp, color.copy(alpha = if (enabled) .22f else .06f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon == CupertinoSymbol.Send) {
            Image(
                painter = painterResource(R.drawable.iumrah_telegram_mark),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        } else {
            CupertinoIcon(icon, null, Modifier.size(24.dp), if (enabled) color else palette.secondary)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = palette.primary.copy(alpha = if (enabled) 1f else .48f),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CareRootHeader(
    title: String,
    onMenu: () -> Unit,
    palette: CarePalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            color = palette.primary,
            fontSize = 38.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.width(8.dp))

        val source = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(3.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(palette.glass)
                .border(0.7.dp, palette.border, CircleShape)
                .clickable(interactionSource = source, indication = null, onClick = onMenu),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(CupertinoSymbol.Menu, "Menu", Modifier.size(20.dp), palette.primary)
        }
    }
}

@Composable
private fun CareIntro(language: AppLanguage, palette: CarePalette) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = tr(
                language,
                en = "We’ll help you build and arrange your Umrah",
                ru = "Поможем собрать и оформить вашу Умру",
                uz = "Umra safaringizni yig‘ish va rasmiylashtirishga yordam beramiz",
                cyrl = "Умра сафарингизни тузиш ва расмийлаштиришга ёрдам берамиз",
            ),
            color = palette.primary,
            fontSize = 27.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        )
        Text(
            text = tr(
                language,
                en = "If you do not want to handle every detail yourself, iumrah Care can help with the route, hotel and services, review the details and guide the booking through to a ready trip.",
                ru = "Если не хочется разбираться во всём самостоятельно, iumrah Care поможет подобрать маршрут, отель и услуги, проверить детали и довести бронирование до готовой поездки.",
                uz = "Agar barcha tafsilotlarni o‘zingiz hal qilishni istamasangiz, iumrah Care yo‘nalish, mehmonxona va xizmatlarni tanlashga, tafsilotlarni tekshirishga va bronni tayyor safargacha olib borishga yordam beradi.",
                cyrl = "Агар барча тафсилотларни ўзингиз ҳал қилишни истамасангиз, iumrah Care йўналиш, меҳмонхона ва хизматларни танлашга, тафсилотларни текширишга ва бронни тайёр сафаргача олиб боришга ёрдам беради.",
            ),
            color = palette.secondary,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun CareHero(
    language: AppLanguage,
    activeSession: StoredBookingSession?,
    telegram: String,
    palette: CarePalette,
    onChat: () -> Unit,
    onCall: () -> Unit,
    onTelegram: () -> Unit,
) {
    val shape = RoundedCornerShape(34.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.07f),
                spotColor = Color.Black.copy(alpha = 0.07f),
            )
            .clip(shape)
            .background(palette.card)
            .border(0.7.dp, palette.border, shape),
    ) {
        Image(
            painter = painterResource(R.drawable.iumrah_care_showcase),
            contentDescription = "iumrah Care",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(236.dp),
        )

        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "iumrah Care",
                    color = palette.primary,
                    fontSize = 30.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.65).sp,
                )
                Text(
                    text = tr(
                        language,
                        en = "Help before, during and after your journey",
                        ru = "Помощь до, во время и после поездки",
                        uz = "Safardan oldin, davomida va undan keyin yordam",
                        cyrl = "Сафардан олдин, давомида ва ундан кейин ёрдам",
                    ),
                    color = palette.secondary,
                    fontSize = 14.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                CareActionTile(
                    modifier = Modifier.weight(1f),
                    icon = if (activeSession != null) CupertinoSymbol.ChatBubble else CupertinoSymbol.Lock,
                    title = tr(language, "Chat", "Чат", "Chat", "Чат"),
                    subtitle = if (activeSession != null) {
                        tr(language, "Available", "Доступен", "Mavjud", "Мавжуд")
                    } else {
                        tr(language, "After booking", "После брони", "Brondan keyin", "Брондан кейин")
                    },
                    enabled = activeSession != null,
                    palette = palette,
                    onClick = onChat,
                )
                CareActionTile(
                    modifier = Modifier.weight(1f),
                    icon = CupertinoSymbol.Phone,
                    title = tr(language, "Call", "Позвонить", "Qo‘ng‘iroq", "Қўнғироқ"),
                    subtitle = tr(language, "Contact", "Связаться", "Bog‘lanish", "Боғланиш"),
                    enabled = true,
                    palette = palette,
                    onClick = onCall,
                )
                CareActionTile(
                    modifier = Modifier.weight(1f),
                    icon = CupertinoSymbol.Send,
                    title = "Telegram",
                    subtitle = tr(language, "Contact", "Связаться", "Bog‘lanish", "Боғланиш"),
                    enabled = telegram.isNotBlank(),
                    palette = palette,
                    onClick = onTelegram,
                )
            }

            if (activeSession != null) {
                ActiveBookingContext(activeSession, language, palette)
            } else {
                LockedChatNote(language, palette)
            }
        }
    }
}

@Composable
private fun CareActionTile(
    modifier: Modifier,
    icon: CupertinoSymbol,
    title: String,
    subtitle: String,
    enabled: Boolean,
    palette: CarePalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(21.dp)
    val source = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 120.dp)
            .clip(shape)
            .background(palette.tile)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 7.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(palette.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            if (icon == CupertinoSymbol.Send) {
                Image(
                    painter = painterResource(R.drawable.iumrah_telegram_mark),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
            } else {
                CupertinoIcon(icon, null, Modifier.size(20.dp), if (enabled) palette.primary else palette.secondary.copy(alpha = 0.56f))
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = title,
            color = if (enabled) palette.primary else palette.secondary,
            fontSize = 13.5.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            color = palette.secondary,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ActiveBookingContext(
    session: StoredBookingSession,
    language: AppLanguage,
    palette: CarePalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(palette.tile)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(palette.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.primary),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = tr(
                    language,
                    en = "Care is linked to your active trip",
                    ru = "Care привязан к вашей активной поездке",
                    uz = "Care faol safaringizga bog‘langan",
                    cyrl = "Care фаол сафарингизга боғланган",
                ),
                color = palette.primary,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    append(session.displayBookingNumber)
                    append(" · ")
                    append(session.booking.route.originCode)
                    append(" → ")
                    append(session.booking.route.outboundDestination)
                    append(" · ")
                    append(L10n.status(session.effectiveStatus, language))
                },
                color = palette.secondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LockedChatNote(language: AppLanguage, palette: CarePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(palette.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(
                symbol = CupertinoSymbol.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.secondary,
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = tr(
                language,
                en = "One-to-one Care chat opens automatically when you have an active booking. Until then, you can call us or write in Telegram.",
                ru = "Личный чат с iumrah Care откроется автоматически, когда появится активное бронирование. До этого можно позвонить или написать в Telegram.",
                uz = "iumrah Care bilan shaxsiy chat faol bron paydo bo‘lganda avtomatik ochiladi. Ungacha qo‘ng‘iroq qilishingiz yoki Telegram’da yozishingiz mumkin.",
                cyrl = "iumrah Care билан шахсий чат фаол брон пайдо бўлганда автоматик очилади. Унгача қўнғироқ қилишингиз ёки Telegram’da ёзишингиз мумкин.",
            ),
            color = palette.secondary,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CareHelpTopics(language: AppLanguage, palette: CarePalette) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        CareSectionTitle(
            title = tr(language, "How we can help", "Чем мы можем помочь", "Nimada yordam bera olamiz", "Нимада ёрдам бера оламиз"),
            subtitle = tr(
                language,
                "One place for the practical parts of your journey.",
                "Один контакт для практических вопросов по вашей поездке.",
                "Safaringizdagi amaliy savollar uchun bitta aloqa nuqtasi.",
                "Сафарингиздаги амалий саволлар учун битта алоқа нуқтаси.",
            ),
            palette = palette,
        )

        val shape = RoundedCornerShape(27.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.card)
                .border(0.7.dp, palette.border, shape),
        ) {
            HelpTopicRow(CupertinoSymbol.Airplane, tr(language, "Flights and route", "Перелёт и маршрут", "Parvoz va yo‘nalish", "Парвоз ва йўналиш"), palette)
            CareDivider(palette)
            HelpTopicRow(CupertinoSymbol.Hotel, tr(language, "Hotel and accommodation", "Отель и размещение", "Mehmonxona va joylashish", "Меҳмонхона ва жойлашиш"), palette)
            CareDivider(palette)
            HelpTopicRow(CupertinoSymbol.Car, tr(language, "Transfer and services", "Трансфер и услуги", "Transfer va xizmatlar", "Трансфер ва хизматлар"), palette)
            CareDivider(palette)
            HelpTopicRow(CupertinoSymbol.Route, tr(language, "Booking changes", "Изменения бронирования", "Bronni o‘zgartirish", "Бронни ўзгартириш"), palette)
        }
    }
}

@Composable
private fun HelpTopicRow(icon: CupertinoSymbol, title: String, palette: CarePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(icon, null, modifier = Modifier.size(18.dp), tint = palette.primary)
        }
        Spacer(Modifier.width(13.dp))
        Text(
            text = title,
            color = palette.primary,
            fontSize = 15.5.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CareQuickAnswers(language: AppLanguage, palette: CarePalette) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        CareSectionTitle(
            title = tr(language, "Quick answers", "Быстрые ответы", "Tezkor javoblar", "Тезкор жавоблар"),
            subtitle = tr(
                language,
                "The essentials before you contact Care.",
                "Самое важное до обращения в Care.",
                "Care’ga murojaat qilishdan oldingi asosiy ma’lumotlar.",
                "Care’га мурожаат қилишдан олдинги асосий маълумотлар.",
            ),
            palette = palette,
        )

        val shape = RoundedCornerShape(27.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.card)
                .border(0.7.dp, palette.border, shape),
        ) {
            AnswerRow(
                icon = CupertinoSymbol.ChatBubble,
                title = tr(language, "When does personal chat open?", "Когда откроется личный чат?", "Shaxsiy chat qachon ochiladi?", "Шахсий чат қачон очилади?"),
                body = tr(
                    language,
                    "It opens automatically for an active booking, so the conversation stays linked to the correct trip.",
                    "Он открывается автоматически для активного бронирования, чтобы переписка всегда была привязана к конкретной поездке.",
                    "U faol bron uchun avtomatik ochiladi, shunda yozishmalar aynan shu safarga bog‘langan bo‘ladi.",
                    "У фаол брон учун автоматик очилади, шунда ёзишмалар айнан шу сафарга боғланган бўлади.",
                ),
                palette = palette,
            )
            CareDivider(palette)
            AnswerRow(
                icon = CupertinoSymbol.Phone,
                title = tr(language, "Can I ask before booking?", "Можно обратиться до бронирования?", "Brondan oldin murojaat qilsa bo‘ladimi?", "Брондан олдин мурожаат қилса бўладими?"),
                body = tr(
                    language,
                    "Yes. Call us or write in Telegram and we will help you understand the options before you create a booking.",
                    "Да. Позвоните или напишите в Telegram — поможем разобраться с вариантами ещё до создания бронирования.",
                    "Ha. Qo‘ng‘iroq qiling yoki Telegram’da yozing — bron yaratishdan oldin variantlarni tushunishga yordam beramiz.",
                    "Ҳа. Қўнғироқ қилинг ёки Telegram’da ёзинг — брон яратишдан олдин вариантларни тушунишга ёрдам берамиз.",
                ),
                palette = palette,
            )
            CareDivider(palette)
            AnswerRow(
                icon = CupertinoSymbol.LockShield,
                title = tr(language, "What can Care handle?", "С чем поможет Care?", "Care nimalarda yordam beradi?", "Care нималарда ёрдам беради?"),
                body = tr(
                    language,
                    "Route, hotel, transfer, services, booking questions and practical changes connected to your journey.",
                    "Маршрут, отель, трансфер, услуги, вопросы по бронированию и практические изменения, связанные с поездкой.",
                    "Yo‘nalish, mehmonxona, transfer, xizmatlar, bron savollari va safarga bog‘liq amaliy o‘zgarishlar.",
                    "Йўналиш, меҳмонхона, трансфер, хизматлар, брон саволлари ва сафарга боғлиқ амалий ўзгаришлар.",
                ),
                palette = palette,
            )
        }
    }
}

@Composable
private fun AnswerRow(icon: CupertinoSymbol, title: String, body: String, palette: CarePalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(icon, null, modifier = Modifier.size(17.dp), tint = palette.primary)
        }
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = title,
                color = palette.primary,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = palette.secondary,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun CareSectionTitle(title: String, subtitle: String, palette: CarePalette) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = title,
            color = palette.primary,
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.35).sp,
        )
        Text(
            text = subtitle,
            color = palette.secondary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun CareDivider(palette: CarePalette) {
    HorizontalDivider(
        modifier = Modifier.padding(start = 65.dp),
        thickness = 0.7.dp,
        color = palette.divider,
    )
}

private data class CarePalette(
    val page: Color,
    val card: Color,
    val tile: Color,
    val iconBackground: Color,
    val glass: Color,
    val primary: Color,
    val secondary: Color,
    val border: Color,
    val divider: Color,
)

@Composable
private fun carePalette(): CarePalette {
    val dark = MaterialTheme.colorScheme.background.red +
        MaterialTheme.colorScheme.background.green +
        MaterialTheme.colorScheme.background.blue < 1.5f

    return if (dark) {
        CarePalette(
            page = Color.Black,
            card = Color(0xFF1C1C1E),
            tile = Color(0xFF1C1C1E),
            iconBackground = Color(0xFF1C1C1E),
            glass = Color(0xFF2C2C2E).copy(alpha = 0.88f),
            primary = Color.White,
            secondary = Color(0xFF8E8E93),
            border = Color.White.copy(alpha = 0.065f),
            divider = Color.White.copy(alpha = 0.10f),
        )
    } else {
        CarePalette(
            page = Color.White,
            card = Color.White,
            tile = Color.White,
            iconBackground = Color(0xFFF2F2F7),
            glass = Color.White.copy(alpha = 0.92f),
            primary = Color.Black,
            secondary = Color(0xFF8E8E93),
            border = Color.Black.copy(alpha = 0.065f),
            divider = Color.Black.copy(alpha = 0.10f),
        )
    }
}

private fun tr(
    language: AppLanguage,
    en: String,
    ru: String,
    uz: String,
    cyrl: String,
): String = when (language) {
    AppLanguage.ENGLISH -> en
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cyrl
}
