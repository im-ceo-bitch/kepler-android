package com.iumrah.beta.ui.booking

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.config.AppConfig
import com.iumrah.beta.core.media.AndroidImageCodec
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.chat.ChatService
import com.iumrah.beta.data.chat.IumrahPublicProfile
import com.iumrah.beta.domain.trip.TransferVehicleKind
import com.iumrah.beta.models.booking.BookingGuideSnapshot
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun IumrahGuideTransferScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    chatService: ChatService,
    chrome: AppChromeStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookingState by bookingStore.state.collectAsState()
    val session = bookingState.sessions.firstOrNull { it.id == bookingID }
    var guideProfile by remember(bookingID) { mutableStateOf<IumrahPublicProfile?>(null) }
    var ownerProfile by remember(bookingID) { mutableStateOf<IumrahPublicProfile?>(null) }
    var faceUri by remember { mutableStateOf<Uri?>(null) }
    var faceBytes by remember { mutableStateOf<ByteArray?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(bookingID, session?.guide?.id) {
        runCatching { chatService.loadTeamProfiles() }.onSuccess { profiles ->
            ownerProfile = profiles.firstOrNull { it.isOwner }
            val guideID = session?.guide?.id.orEmpty()
            guideProfile = profiles.firstOrNull { it.id == guideID }
        }
    }

    val facePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        faceUri = uri
        sent = false
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { AndroidImageCodec.jpeg(context.contentResolver, uri, 2048, 88) }
            }.onSuccess { faceBytes = it }
                .onFailure {
                    faceBytes = null
                    error = guideTr(language, "Не удалось подготовить фотографию.", "Could not prepare this photo.", "Rasmni tayyorlab bo‘lmadi.", "Расмни тайёрлаб бўлмади.")
                }
        }
    }

    Column(Modifier.fillMaxSize().background(bookingIosPage()).statusBarsPadding()) {
        BookingNavigationBar(
            title = guideTr(language, "Гид и трансфер", "Guide & transfer", "Gid va transfer", "Гид ва трансфер"),
            chrome = chrome,
        )

        if (session == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(guideTr(language, "Бронирование не найдено.", "Booking not found.", "Bron topilmadi.", "Брон топилмади."))
            }
        } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = BOOKING_PAGE_PADDING.dp).padding(top = 12.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BookingSectionHeader(
                title = guideTr(language, "Ваша команда в Саудии", "Your team in Saudi Arabia", "Saudiya jamoangiz", "Саудия жамоангиз"),
                subtitle = guideTr(
                    language,
                    "Главный гид, основатель iumrah и подтверждённые данные трансфера собраны в одном месте для встречи в аэропорту.",
                    "Your lead guide, iumrah founder and confirmed transfer details are kept together for the airport meeting.",
                    "Bosh gid, iumrah asoschisi va tasdiqlangan transfer ma’lumotlari aeroportdagi uchrashuv uchun bir joyda.",
                    "Бош гид, iumrah асосчиси ва тасдиқланган трансфер маълумотлари аэропортдаги учрашув учун бир жойда.",
                ),
            )

            TeamProfileCard(
                language = language,
                name = session.guide?.displayName?.takeIf { it.isNotBlank() }
                    ?: guideTr(language, "Ваш гид", "Your guide", "Sizning gidingiz", "Сизнинг гидингиз"),
                subtitle = guideTr(language, "Главный гид iumrah · стаж 4 года", "Lead iumrah guide · 4 years experience", "iumrah bosh gidi · 4 yil tajriba", "iumrah бош гиди · 4 йил тажриба"),
                body = guideTr(language, "Ваш главный гид координирует встречу в аэропорту, сопровождение Умры и ключевые переезды во время поездки.", "Your main guide coordinates the airport meeting, Umrah assistance and key movements during the trip.", "Bosh gidingiz aeroportdagi uchrashuv, Umra hamrohligi va safardagi asosiy ko‘chishlarni muvofiqlashtiradi.", "Бош гидингиз аэропортдаги учрашув, Умра ҳамроҳлиги ва сафардаги асосий кўчишларни мувофиқлаштиради."),
                profile = guideProfile,
                phone = preferredPhone(guideProfile?.phoneSA, guideProfile?.phoneUZ, session.guide),
                telegram = cleanHandle(guideProfile?.telegram ?: session.guide?.telegram.orEmpty()),
                contactsUnlocked = contactsUnlocked(session),
                onCall = { phone -> openPhone(context, phone) },
                onTelegram = { handle -> openTelegram(context, handle) },
            )

            FounderCard(
                language = language,
                profile = ownerProfile,
                onCall = { phone -> openPhone(context, phone) },
                onTelegram = { handle -> openTelegram(context, handle) },
                onCare = { chrome.openBookingChat(bookingID) },
            )

            GuideResponsibilitiesCard(language)
            TransferCard(session, language)

            BookingCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BookingIconBadge(CupertinoSymbol.PersonCircle, Color(0xFF5856D6), 48.dp, 18.dp, 16.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(guideTr(language, "Помогите гиду быстрее Вас найти", "Help us recognize you", "Sizni tezroq topishga yordam bering", "Сизни тезроқ топишга ёрдам беринг"), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                        Text(guideTr(language, "Не обязательно: прикрепите актуальную фотографию лица. Она будет отправлена в чат iumrah Care для команды встречи в аэропорту.", "Optional: attach a recent face photo. It will be sent to the iumrah Care conversation for the airport meeting team.", "Ixtiyoriy: yuzingiz ko‘rinadigan yangi rasmni biriktiring. U aeroportda kutib olish jamoasi uchun iumrah Care chatiga yuboriladi.", "Ихтиёрий: юзингиз кўринадиган янги расмни бириктиринг. У аэропортда кутиб олиш жамоаси учун iumrah Care чатига юборилади."), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                    }
                }

                faceUri?.let { uri ->
                    Spacer(Modifier.height(14.dp))
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                if (sent) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(17.dp), Color(0xFF34C759))
                        Text(guideTr(language, "Фото отправлено команде встречи", "Photo sent to the meeting team", "Rasm kutib olish jamoasiga yuborildi", "Расм кутиб олиш жамоасига юборилди"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF34C759))
                    }
                }

                Spacer(Modifier.height(14.dp))
                BookingSecondaryAction(
                    title = if (faceUri == null) guideTr(language, "Выбрать фотографию", "Choose face photo", "Yuz rasmini tanlash", "Юз расмини танлаш") else guideTr(language, "Выбрать другое фото", "Choose another photo", "Boshqa rasm tanlash", "Бошқа расм танлаш"),
                    onClick = { facePicker.launch("image/*") },
                    trailing = CupertinoSymbol.ChevronRight,
                )

                if (faceBytes != null && !sent) {
                    Spacer(Modifier.height(10.dp))
                    BookingPrimaryAction(
                        title = if (sending) guideTr(language, "Отправляем…", "Sending…", "Yuborilmoqda…", "Юборилмоқда…") else guideTr(language, "Отправить команде встречи", "Send to meeting team", "Kutib olish jamoasiga yuborish", "Кутиб олиш жамоасига юбориш"),
                        onClick = {
                            val bytes = faceBytes ?: return@BookingPrimaryAction
                            if (sending) return@BookingPrimaryAction
                            sending = true
                            error = null
                            scope.launch {
                                runCatching {
                                    val headers = bookingStore.headersFor(session)
                                    chatService.send(
                                        guideTr(language, "Фото для быстрой встречи и узнавания в аэропорту.", "Face photo for airport pickup recognition.", "Aeroportda tezroq tanish uchun yuz rasmi.", "Аэропортда тезроқ таниш учун юз расми."),
                                        bookingID,
                                        headers,
                                    )
                                    chatService.sendPhoto(bytes, bookingID, headers)
                                }.onSuccess { sent = true }
                                    .onFailure { error = it.message }
                                sending = false
                            }
                        },
                        leading = CupertinoSymbol.Send,
                    )
                }
            }

            error?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
        }
        }
    }
}

@Composable
private fun TeamProfileCard(
    language: AppLanguage,
    name: String,
    subtitle: String,
    body: String,
    profile: IumrahPublicProfile?,
    phone: String,
    telegram: String,
    contactsUnlocked: Boolean,
    onCall: (String) -> Unit,
    onTelegram: (String) -> Unit,
) {
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileAvatar(profile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VerifiedName(name)
                Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                instagramHandle(profile?.instagram)?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007AFF)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(body, fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        Spacer(Modifier.height(14.dp))
        if (contactsUnlocked) {
            ContactButtons(language, phone, telegram, onCall, onTelegram)
        } else {
            Row(
                Modifier.fillMaxWidth().background(bookingIosRaised(), RoundedCornerShape(15.dp)).padding(13.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                Text(guideTr(language, "Телефон и Telegram откроются после подтверждения оплаты.", "Phone and Telegram unlock after payment is confirmed.", "Telefon va Telegram to‘lov tasdiqlangandan keyin ochiladi.", "Телефон ва Telegram тўлов тасдиқлангандан кейин очилади."), Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
            }
        }
    }
}

@Composable
private fun FounderCard(
    language: AppLanguage,
    profile: IumrahPublicProfile?,
    onCall: (String) -> Unit,
    onTelegram: (String) -> Unit,
    onCare: () -> Unit,
) {
    val name = profile?.displayName?.takeIf { it.isNotBlank() } ?: "Abdulaziz"
    val phone = preferredPhone(profile?.phoneSA, profile?.phoneUZ, null).ifBlank { "+998 50 889 88 45" }
    val telegram = cleanHandle(profile?.telegram.orEmpty()).ifBlank { "saudiclub966" }
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileAvatar(profile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                VerifiedName(name)
                Text("Founder · iumrah", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                instagramHandle(profile?.instagram)?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF007AFF)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(guideTr(language, "Прямой контакт с основателем iumrah по Вашему бронированию и сопровождению поездки.", "Direct contact with the iumrah founder for your booking and trip support.", "Bron va safar yordami bo‘yicha iumrah asoschisi bilan bevosita aloqa.", "Брон ва сафар ёрдами бўйича iumrah асосчиси билан бевосита алоқа."), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        Spacer(Modifier.height(14.dp))
        ContactButtons(language, phone, telegram, onCall, onTelegram)
        Spacer(Modifier.height(10.dp))
        BookingSecondaryAction(guideTr(language, "Чат iumrah Care", "Care chat", "iumrah Care chat", "iumrah Care чат"), onCare, trailing = CupertinoSymbol.ChevronRight)
    }
}

@Composable
private fun ProfileAvatar(profile: IumrahPublicProfile?) {
    val url = AppConfig.absoluteUrl(profile?.photoURL)
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier.size(72.dp).clip(CircleShape).background(bookingIosRaised()),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.size(72.dp).clip(CircleShape).background(bookingIosRaised()), contentAlignment = Alignment.Center) {
            CupertinoIcon(CupertinoSymbol.PersonCircle, null, Modifier.size(34.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
        }
    }
}

@Composable
private fun VerifiedName(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, Modifier.weight(1f, fill = false), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(17.dp), Color(0xFF007AFF))
    }
}

@Composable
private fun ContactButtons(language: AppLanguage, phone: String, telegram: String, onCall: (String) -> Unit, onTelegram: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ContactButton(guideTr(language, "Позвонить", "Call", "Qo‘ng‘iroq", "Қўнғироқ"), CupertinoSymbol.Phone, Color(0xFF34C759), phone.isNotBlank(), Modifier.weight(1f)) { onCall(phone) }
        ContactButton("Telegram", CupertinoSymbol.Send, Color(0xFF007AFF), telegram.isNotBlank(), Modifier.weight(1f)) { onTelegram(telegram) }
    }
}

@Composable
private fun ContactButton(title: String, icon: CupertinoSymbol, tint: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    IumrahPressable(
        onClick = { if (enabled) onClick() },
        modifier = modifier.height(48.dp),
        cornerRadius = 16.dp,
        background = if (enabled) tint.copy(alpha = .11f) else bookingIosRaised(),
        pressedScale = if (enabled) .97f else 1f,
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CupertinoIcon(icon, null, Modifier.size(16.dp), if (enabled) tint else MaterialTheme.colorScheme.onBackground.copy(alpha = .38f))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) tint else MaterialTheme.colorScheme.onBackground.copy(alpha = .38f), maxLines = 1)
        }
    }
}

@Composable
private fun GuideResponsibilitiesCard(language: AppLanguage) {
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Checklist, Color(0xFF5856D6), 48.dp, 19.dp, 16.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(guideTr(language, "Сопровождение от прилёта до вылета", "Your support from arrival to departure", "Kelishdan qaytishgacha hamrohlik", "Келишдан қайтишгача ҳамроҳлик"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(guideTr(language, "Всё ниже уже входит в сопровождение Вашей поездки.", "Everything below is already part of your trip support.", "Quyidagilarning barchasi safar hamrohligiga kiradi.", "Қуйидагиларнинг барчаси сафар ҳамроҳлигига киради."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
            }
        }
        Spacer(Modifier.height(15.dp))
        val duties = listOf(
            Triple(CupertinoSymbol.AirplaneLand, guideTr(language,"Встреча в аэропорту","Airport meeting","Aeroportda kutib olish","Аэропортда кутиб олиш"), guideTr(language,"Гид координирует прилёт и помогает группе встретиться с водителем без лишнего ожидания.","The guide coordinates your arrival and helps the group meet the driver without unnecessary waiting.","Gid kelishni muvofiqlashtiradi va guruhning haydovchi bilan ortiqcha kutmasdan uchrashishiga yordam beradi.","Гид келишни мувофиқлаштиради ва гуруҳнинг ҳайдовчи билан ортиқча кутмасдан учрашишига ёрдам беради.")),
            Triple(CupertinoSymbol.Building, guideTr(language,"Сопровождение до отеля","Hotel check-in support","Mehmonxonagacha hamrohlik","Меҳмонхонагача ҳамроҳлик"), guideTr(language,"Вас сопровождают до подтверждённого отеля и помогают с первыми организационными вопросами после прилёта.","You are accompanied to the confirmed hotel and helped with the first practical steps after arrival.","Tasdiqlangan mehmonxonagacha hamrohlik qilinadi va kelgandan keyingi dastlabki tashkiliy masalalarda yordam beriladi.","Тасдиқланган меҳмонхонагача ҳамроҳлик қилинади ва келгандан кейинги дастлабки ташкилий масалаларда ёрдам берилади.")),
            Triple(CupertinoSymbol.Person, guideTr(language,"Сопровождение Умры","Umrah guidance","Umra hamrohligi","Умра ҳамроҳлиги"), guideTr(language,"Гид помогает группе ориентироваться по основным этапам Умры и координирует перемещения, когда это необходимо.","The guide keeps the group oriented through the main Umrah stages and coordinates movement when needed.","Gid Umraning asosiy bosqichlarida guruhga yo‘l-yo‘riq ko‘rsatadi va zarur paytda harakatni muvofiqlashtiradi.","Гид Умранинг асосий босқичларида гуруҳга йўл-йўриқ кўрсатади ва зарур пайтда ҳаракатни мувофиқлаштиради.")),
            Triple(CupertinoSymbol.Map, guideTr(language,"Зияраты","Ziyarats","Ziyoratlar","Зиёратлар"), guideTr(language,"Включённые посещения в Мекке и Медине координируются вместе с гидом и маршрутом трансфера.","Your included Makkah and Madinah visits are coordinated with the guide and transfer route.","Makka va Madinadagi kiritilgan ziyoratlar gid va transfer yo‘nalishi bilan muvofiqlashtiriladi.","Макка ва Мадинадаги киритилган зиёратлар гид ва трансфер йўналиши билан мувофиқлаштирилади.")),
            Triple(CupertinoSymbol.Route, guideTr(language,"Переезд между городами","Intercity coordination","Shaharlararo yo‘l","Шаҳарлараро йўл"), guideTr(language,"Команда координирует переезд Мекка–Медина, включая поезд, если он входит в Ваш маршрут.","The team coordinates the Makkah–Madinah movement, including the train segment when it is part of your itinerary.","Jamoa Makka–Madina harakatini, yo‘nalishga kirsa poyezd qismini ham muvofiqlashtiradi.","Жамоа Макка–Мадина ҳаракатини, йўналишга кирса поезд қисмини ҳам мувофиқлаштиради.")),
            Triple(CupertinoSymbol.AirplaneTakeoff, guideTr(language,"Сопровождение до аэропорта","Departure support","Aeroportgacha hamrohlik","Аэропортгача ҳамроҳлик"), guideTr(language,"В конце поездки трансфер и команда координируют Ваш выезд в аэропорт обратного рейса.","At the end of the trip, the transfer and team coordinate your return to the departure airport.","Safar oxirida transfer va jamoa qaytish reysi aeroportiga borishingizni muvofiqlashtiradi.","Сафар охирида трансфер ва жамоа қайтиш рейси аэропортига боришингизни мувофиқлаштиради.")),
        )
        duties.forEachIndexed { index, duty ->
            GuideDuty(duty.first, duty.second, duty.third)
            if (index != duties.lastIndex) Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GuideDuty(icon: CupertinoSymbol, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF007AFF).copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(icon, null, Modifier.size(15.dp), Color(0xFF007AFF))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(body, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        }
    }
}

@Composable
private fun TransferCard(session: StoredBookingSession, language: AppLanguage) {
    val vehicle = session.transferVehicle ?: TransferVehicleKind.CARNIVAL
    val drawable = when (vehicle) {
        TransferVehicleKind.MALIBU -> R.drawable.transfer_malibu
        TransferVehicleKind.CARNIVAL -> R.drawable.transfer_carnival
        TransferVehicleKind.YUKON -> R.drawable.transfer_yukon
    }
    BookingCard {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(bookingIosRaised()).border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha=.07f), RoundedCornerShape(22.dp)),
        ) {
            Image(painterResource(drawable), null, Modifier.fillMaxWidth().height(178.dp).padding(horizontal = 8.dp, vertical = 8.dp), contentScale = ContentScale.Fit)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(vehicle.modelName, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Text(guideTr(language,"Выбранный автомобиль трансфера","Your selected transfer vehicle","Tanlangan transfer avtomobili","Танланган трансфер автомобили"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha=.55f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    CupertinoIcon(CupertinoSymbol.Persons, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha=.50f))
                    Text(vehicle.passengerCapacity.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha=.55f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Car, Color(0xFF007AFF), 48.dp, 19.dp, 16.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(guideTr(language,"Трансфер по маршруту","Airport transfer","Yo‘nalish transferi","Йўналиш трансфери"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(guideTr(language,"Встреча привязана к подтверждённому рейсу и отелям.","Pickup follows your confirmed flight and hotels.","Kutib olish tasdiqlangan reys va mehmonxonalarga bog‘langan.","Кутиб олиш тасдиқланган рейс ва меҳмонхоналарга боғланган."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha=.55f))
            }
        }
        Spacer(Modifier.height(15.dp))
        RouteRow(CupertinoSymbol.AirplaneLand, guideTr(language,"Аэропорт прилёта: ${session.booking.input.arrivalAirportCode}","Arrival airport: ${session.booking.input.arrivalAirportCode}","Kelish aeroporti: ${session.booking.input.arrivalAirportCode}","Келиш аэропорти: ${session.booking.input.arrivalAirportCode}"))
        Spacer(Modifier.height(10.dp))
        RouteRow(CupertinoSymbol.Clock, guideTr(language,"Встреча: после фактического прилёта и получения багажа. Команда отслеживает Ваш подтверждённый рейс.","Meeting: after the actual landing and baggage collection. The team follows your confirmed flight.","Uchrashuv: haqiqiy qo‘nish va bagajni olgandan keyin. Jamoa tasdiqlangan reysingizni kuzatadi.","Учрашув: ҳақиқий қўниш ва багажни олгандан кейин. Жамоа тасдиқланган рейсингизни кузатади."))
        saudiArrivalText(session, language)?.let { Spacer(Modifier.height(10.dp)); RouteRow(CupertinoSymbol.Timer, it) }
        session.booking.hotelNames.makkah.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(10.dp)); RouteRow(CupertinoSymbol.Building, it) }
        session.booking.hotelNames.madinah.takeIf { it.isNotBlank() }?.let { Spacer(Modifier.height(10.dp)); RouteRow(CupertinoSymbol.MoonStarsFill, it) }
        Spacer(Modifier.height(10.dp))
        RouteRow(CupertinoSymbol.AirplaneTakeoff, guideTr(language,"Обратный вылет: ${session.booking.route.returnOrigin}","Return from: ${session.booking.route.returnOrigin}","Qaytish: ${session.booking.route.returnOrigin}","Қайтиш: ${session.booking.route.returnOrigin}"))
    }
}

@Composable
private fun RouteRow(icon: CupertinoSymbol, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CupertinoIcon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
        Text(text, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 19.sp)
    }
}

private fun contactsUnlocked(session: StoredBookingSession): Boolean =
    session.effectiveStatus.uppercase() in setOf("PAID", "BOOKING_CONFIRMED", "DOCUMENTS_READY", "READY_TO_TRAVEL", "IN_TRIP", "COMPLETED")

private fun preferredPhone(sa: String?, uz: String?, guide: BookingGuideSnapshot?): String =
    listOf(sa.orEmpty(), uz.orEmpty(), guide?.phoneSA.orEmpty(), guide?.phoneUZ.orEmpty()).map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

private fun cleanHandle(raw: String): String {
    var value = raw.trim()
    listOf("https://t.me/", "http://t.me/", "t.me/").firstOrNull { value.lowercase().startsWith(it) }?.let { value = value.drop(it.length) }
    return value.trim(' ', '@', '/')
}

private fun instagramHandle(raw: String?): String? = cleanHandle(raw.orEmpty()).takeIf { it.isNotBlank() }?.let { "@$it" }

private fun openPhone(context: android.content.Context, raw: String) {
    val value = raw.filter { it.isDigit() || it == '+' }
    if (value.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$value"))) }
}

private fun openTelegram(context: android.content.Context, raw: String) {
    val handle = cleanHandle(raw)
    if (handle.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$handle"))) }
}

private fun saudiArrivalText(session: StoredBookingSession, language: AppLanguage): String? {
    val raw = session.booking.generatorTrace?.outbound?.arrivalAt ?: return null
    val instant = runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(raw) }.getOrNull()
        ?: return null
    val locale = when (language) {
        AppLanguage.RUSSIAN -> Locale("ru")
        AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> language.locale
        AppLanguage.UZBEK, AppLanguage.UZBEK_CYRILLIC -> Locale("uz")
    }
    val time = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(ZoneId.of("Asia/Riyadh")).format(instant)
    return guideTr(language,"Плановое время прилёта: $time по времени Саудии","Scheduled arrival: $time Saudi time","Rejadagi kelish vaqti: $time, Saudiya vaqti","Режадаги келиш вақти: $time, Саудия вақти")
}

private fun guideTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
