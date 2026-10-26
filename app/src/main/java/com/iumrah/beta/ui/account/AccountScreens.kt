package com.iumrah.beta.ui.account
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.activity.compose.LocalActivity

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.ui.components.IumrahRootPageHeader
import com.iumrah.beta.IumrahApplication
import com.iumrah.beta.core.auth.GoogleSignInSupport
import com.iumrah.beta.core.design.IumrahBookingStatusVisual
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppAppearance
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.core.settings.AppSettingsStore
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.notification.ClientNotificationStore
import com.iumrah.beta.models.account.IumrahAccountProfile
import com.iumrah.beta.models.account.IumrahTravelerForm
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.models.notification.ClientSystemNotification
import com.iumrah.beta.ui.booking.IumrahSecurityConfirmationPanel
import com.iumrah.beta.ui.components.IumrahPaymentMethodsMarquee
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.IumrahIconBadge
import kotlinx.coroutines.launch

private val Care = Color(0xFF30B0C7)
private val Signal = Color(0xFF1677FF)
private val Danger = Color(0xFFFF3B30)
private val Success = Color(0xFF34C759)
private val IosGray = Color(0xFF8E8E93)
private val CardRadius = 28.dp
private val PagePad = 18.dp

private fun tr(l: AppLanguage, en: String, ru: String, uz: String, cy: String) = when (l) {
    AppLanguage.ENGLISH -> en
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

private fun normalizedID(raw: String): String = raw.trim().uppercase().removePrefix("UMR-").let { if (it.isBlank()) "UMR —" else "UMR $it" }

@Composable
fun AccountRootScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    bookingStore: BookingStore,
    settingsStore: AppSettingsStore,
    notifications: ClientNotificationStore,
    chrome: AppChromeStore,
) {
    val accountState by accountStore.state.collectAsState()
    val bookings by bookingStore.state.collectAsState()
    val settings by settingsStore.state.collectAsState()
    val signalState by notifications.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as IumrahApplication
    val pushState by app.container.pushManager.state.collectAsState()
    val pushPermissionGranted = app.container.pushManager.hasNotificationPermission()
    val notificationDeliveryStatus = when {
        !pushState.isConfigured -> tr(language, "FCM is not configured", "FCM не настроен", "FCM sozlanmagan", "FCM созланмаган")
        !pushPermissionGranted -> tr(language, "Disabled in Android Settings", "Выключены в настройках Android", "Android sozlamalarida o‘chirilgan", "Android созламаларида ўчирилган")
        pushState.deviceToken.isNullOrBlank() -> tr(language, "Registering with FCM…", "Регистрация FCM…", "FCM ro‘yxatdan o‘tmoqda…", "FCM рўйхатдан ўтмоқда…")
        bookings.pushRegistrationError != null || signalState.lastError != null -> tr(language, "Delivery connection error", "Ошибка подключения доставки", "Yetkazish ulanishida xato", "Етказиш уланишида хато")
        bookings.pushRegistrationReady == false || signalState.pushProviderReady == false -> tr(language, "Push service is not ready", "Push-сервис не готов", "Push xizmati tayyor emas", "Push хизмати тайёр эмас")
        else -> tr(language, "Enabled · delivery connected", "Включены · доставка подключена", "Yoqilgan · yetkazish ulangan", "Ёқилган · етказиш уланган")
    }
    val profile = accountState.account
    var signOutConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = PagePad, end = PagePad, top = 12.dp, bottom = 130.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { AccountHeader(language, profile != null, signalState.unreadCount, chrome) }
        if (profile != null) {
            item { IumrahIdentityHeroAndroid(profile, language) }
            item { IumrahUserDataEntryCardAndroid(language, profile.displayName) { chrome.openAccountUserData() } }
            item { TravelCompanionsParityCard(language) { chrome.openAccountTravelers() } }
            item { WalletSection(profile, language) }
            bookings.sessions.firstOrNull { !it.isPastAccountTrip() }?.let { active ->
                item { IumrahTripWalletEntryAndroid(active, profile, language) }
            }
            item { TripsSection(bookings.sessions, language, chrome) }
            item { TelegramIntegrationCard(language) { chrome.openAccountTelegramIntegration() } }
            item { PaymentSecuritySection(language, bookings.sessions.firstOrNull(), true, chrome) }
            item { ServicesSection(language, chrome) }
            item { SettingsSection(language, settings.appearance, signalState.unreadCount, notificationDeliveryStatus, chrome, context) }
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Danger.copy(alpha = .075f)).clickable { signOutConfirm = true }.padding(horizontal = 18.dp).height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CupertinoIcon(CupertinoSymbol.SignOut, null, Modifier.size(20.dp), Danger)
                    Text(tr(language, "Sign out", "Выйти из аккаунта", "Akkauntdan chiqish", "Аккаунтдан чиқиш"), color = Danger, fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            item { IumrahLockedIdentityCardAndroid(language) }
            item { GuestLoginCard(language, accountStore, bookingStore, chrome) }
            item { TelegramIntegrationCard(language) { chrome.openAccountTelegramIntegration() } }
            item { PaymentSecuritySection(language, bookings.sessions.firstOrNull(), false, chrome) }
            item { GuestSettingsSection(language, settings.appearance, signalState.unreadCount, notificationDeliveryStatus, chrome, context) }
        }
    }

    if (signOutConfirm) {
        AlertDialog(
            onDismissRequest = { signOutConfirm = false },
            title = { Text(tr(language, "Sign out of iumrah?", "Выйти из iumrah?", "iumrah akkauntidan chiqasizmi?", "iumrah аккаунтидан чиқасизми?")) },
            text = { Text(tr(language, "Your trips stay safely linked to your iumrah account. You will need to sign in again on this device.", "Ваши поездки останутся безопасно привязаны к аккаунту iumrah. На этом устройстве потребуется войти снова.", "Safarlaringiz iumrah akkauntingizga xavfsiz bog‘langan holda qoladi. Bu qurilmada qayta kirishingiz kerak bo‘ladi.", "Сафарларингиз iumrah аккаунтингизга хавфсиз боғланган ҳолда қолади. Бу қурилмада қайта киришингиз керак бўлади.")) },
            confirmButton = { TextButton(onClick = { signOutConfirm = false; scope.launch { accountStore.logout() } }) { Text(tr(language, "Sign out", "Выйти", "Chiqish", "Чиқиш"), color = Danger) } },
            dismissButton = { TextButton(onClick = { signOutConfirm = false }) { Text(tr(language, "Cancel", "Отмена", "Bekor qilish", "Бекор қилиш")) } },
        )
    }
}

@Composable
private fun AccountHeader(language: AppLanguage, authenticated: Boolean, unread: Int, chrome: AppChromeStore) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        IumrahRootPageHeader(
            title = when (language) {
                AppLanguage.RUSSIAN, AppLanguage.UZBEK_CYRILLIC -> "Аккаунт"
                AppLanguage.UZBEK -> "Akkaunt"
                AppLanguage.ENGLISH -> "Account"
            },
            chrome = chrome,
            unreadCount = unread,
        )
        Text(
            if (authenticated) tr(language, "Your iumrah profile and trips", "Ваш профиль и поездки iumrah", "iumrah profilingiz va safarlaringiz", "iumrah профилингиз ва сафарларингиз")
            else tr(language, "Sign in or create your permanent iumrah account", "Войдите или создайте постоянный аккаунт iumrah", "Doimiy iumrah akkauntingizga kiring yoki uni yarating", "Доимий iumrah аккаунтингизга киринг ёки уни яратинг"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WalletSection(profile: IumrahAccountProfile, language: AppLanguage) {
    CardBlock {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Wallet, null, Modifier.size(19.dp), Color.White)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    tr(language, "iumrah ID in Google Wallet", "iumrah ID в Google Wallet", "iumrah ID Google Wallet’da", "iumrah ID Google Wallet’да"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Text(
                    tr(
                        language,
                        "Google Wallet support for your digital pilgrim ID and QR code is coming soon on Android.",
                        "Поддержка цифровой карты паломника iumrah ID и QR-кода в Google Wallet скоро появится на Android.",
                        "Raqamli ziyoratchi iumrah ID va QR-kodni Google Wallet’ga qo‘shish Android’da tez orada paydo bo‘ladi.",
                        "Рақамли зиёратчи iumrah ID ва QR-кодни Google Wallet’га қўшиш Android’да тез орада пайдо бўлади.",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CupertinoIcon(CupertinoSymbol.Wallet, null, Modifier.size(19.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            Spacer(Modifier.width(9.dp))
            Text(
                tr(language, "Google Wallet · Coming soon", "Google Wallet · Скоро", "Google Wallet · Tez orada", "Google Wallet · Тез орада"),
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActiveTripCard(session: StoredBookingSession, language: AppLanguage, onOpen: () -> Unit) {
    val inTrip = session.effectiveStatus.uppercase() == "IN_TRIP"
    val tint = IumrahBookingStatusVisual.color(session.effectiveStatus)
    val shape = RoundedCornerShape(CardRadius)
    Surface(
        onClick = onOpen,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if (inTrip) CupertinoSymbol.Location else CupertinoSymbol.AirplaneTakeoff, tint, 48.dp, 19.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(language,"Active trip","Активная поездка","Faol safar","Фаол сафар"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = IosGray)
                    Text("${session.booking.route.originCode} → ${session.booking.route.outboundDestination}", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("Бронь ${session.displayBookingNumber}", fontSize = 12.sp, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, fontWeight = FontWeight.SemiBold, color = IosGray)
                }
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.padding(top = 16.dp).size(15.dp), IosGray.copy(alpha=.7f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountStatusChip(session.effectiveStatus, language)
                AccountDateChip(session)
            }
            session.booking.hotelNames.makkah.takeIf { it.isNotBlank() }?.let { hotel ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    CupertinoIcon(CupertinoSymbol.Building, null, Modifier.size(14.dp), IosGray)
                    Text(hotel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = IosGray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun TripsSection(sessions: List<StoredBookingSession>, language: AppLanguage, chrome: AppChromeStore) {
    var past by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val scoped = remember(sessions, past) { sessions.filter { it.isPastAccountTrip() == past } }
    CardBlock {
        Row(Modifier.fillMaxWidth().clickable { chrome.openAccountTripsHistory(past) }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(tr(language,"My trips","Мои поездки","Safarlarim","Сафарларим"), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(tr(language,"Current and previous journeys","Активные и прошлые поездки","Faol va oldingi safarlar","Фаол ва олдинги сафарлар"), fontSize = 12.sp, color = IosGray)
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), IosGray)
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(listOf(tr(language,"Active","Активные","Faol","Фаол"), tr(language,"Past","Прошлые","Oldingi","Олдинги")), if(past) 1 else 0) { past = it == 1; expanded = false }
        Spacer(Modifier.height(12.dp))
        if (scoped.isEmpty()) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.045f)).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(if(past) CupertinoSymbol.Refresh else CupertinoSymbol.Suitcase, IosGray, 42.dp, 18.dp)
                Text(if(past) tr(language,"Previous trips will appear here.","Здесь появится история прошлых поездок.","Oldingi safarlar tarixi shu yerda ko‘rinadi.","Олдинги сафарлар тарихи шу ерда кўринади.") else tr(language,"No active trips yet.","Активных поездок пока нет.","Hozircha faol safar yo‘q.","Ҳозирча фаол сафар йўқ."), color = IosGray, fontSize = 14.sp)
            }
        } else {
            val shown = if(expanded) scoped else scoped.take(3)
            shown.forEachIndexed { index, session ->
                if(index > 0) HorizontalDivider(Modifier.padding(start=54.dp))
                Row(Modifier.fillMaxWidth().clickable { chrome.openBookingDetail(session.id) }.padding(vertical=10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconBadge(if(session.effectiveStatus.uppercase()=="IN_TRIP") CupertinoSymbol.Location else CupertinoSymbol.Airplane, statusTint(session.effectiveStatus), 42.dp, 17.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${session.booking.route.originCode} → ${session.booking.route.outboundDestination}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("${session.displayBookingNumber} · ${session.booking.input.startDate}", fontSize = 11.sp, color = IosGray)
                    }
                    CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), IosGray.copy(alpha=.7f))
                }
            }
            if(scoped.size > 3) {
                Spacer(Modifier.height(10.dp))
                Surface(onClick={chrome.openAccountTripsHistory(past)}, shape=RoundedCornerShape(16.dp), color=MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)) {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal=14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Text(tr(language,"Open all trips","Открыть все поездки","Barcha safarlarni ochish","Барча сафарларни очиш"), fontSize=14.sp, fontWeight=FontWeight.SemiBold, modifier=Modifier.weight(1f))
                        CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelegramIntegrationCard(language: AppLanguage, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        onClick = onOpen,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painter = painterResource(R.drawable.telegram_integration_hero),
                contentDescription = null,
                modifier = Modifier.width(84.dp).height(70.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tr(language, "iumrah in Telegram", "iumrah в Telegram", "iumrah Telegram’da", "iumrah Telegram’да"), fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
                Text(tr(language, "Status and booking notifications", "Статус и уведомления по брони", "Bron statusi va bildirishnomalar", "Брон статуси ва билдиришномалар"), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .30f))
        }
    }
}

@Composable
private fun TravelCompanionsParityCard(language: AppLanguage, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(CardRadius)
    Surface(onClick = onOpen, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .075f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Image(
                painterResource(R.drawable.travel_companions_cover),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(94.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                IconBadge(CupertinoSymbol.Persons, Care, 50.dp, 20.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr(language,"Who is traveling with you","Кто едет с Вами","Siz bilan kim bormoqda","Сиз билан ким бормоқда"), fontSize=20.sp, fontWeight=FontWeight.Bold)
                    Text(tr(language,"Your family and loved ones","Ваша семья и близкие","Oilangiz va yaqinlaringiz","Оилангиз ва яқинларингиз"), fontSize=14.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.padding(top=17.dp).size(14.dp),MaterialTheme.colorScheme.onSurface.copy(alpha=.30f))
            }
            Text(tr(language,"Keep each traveler’s passport details in a separate, clear card and reuse them for the booking.","Храните данные каждого участника в отдельной понятной карточке и используйте их в бронировании.","Har bir sayohatchi ma’lumotini alohida kartada saqlang va bronda ishlating.","Ҳар бир саёҳатчи маълумотини алоҳида картада сақланг ва бронда ишлатинг."), fontSize=12.sp, lineHeight=16.sp, color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AccountStatusChip(status:String, language:AppLanguage) {
    val label=when(status.uppercase()) {
        "AVAILABILITY_CHECK"->tr(language,"Availability check","Проверка наличия","Mavjudlik tekshiruvi","Мавжудлик текшируви")
        "PAYMENT_PENDING"->tr(language,"Waiting payment","Ожидает оплаты","To‘lov kutilmoqda","Тўлов кутилмоқда")
        "BOOKING_CONFIRMED"->tr(language,"Confirmed","Подтверждено","Tasdiqlandi","Тасдиқланди")
        "READY_TO_TRAVEL"->tr(language,"Ready to travel","Готово к поездке","Safarga tayyor","Сафарга тайёр")
        "IN_TRIP"->tr(language,"In trip","В поездке","Safarda","Сафарда")
        "COMPLETED"->tr(language,"Completed","Завершено","Yakunlandi","Якунланди")
        else->status.replace('_',' ').lowercase().replaceFirstChar{it.uppercase()}
    }
    val tint=statusTint(status)
    Row(Modifier.clip(CircleShape).background(tint.copy(alpha=.11f)).padding(horizontal=10.dp,vertical=6.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint)); Text(label,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=tint)
    }
}

@Composable
private fun AccountDateChip(session:StoredBookingSession) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)).padding(horizontal=10.dp,vertical=6.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        CupertinoIcon(CupertinoSymbol.CalendarClock,null,Modifier.size(12.dp),IosGray); Text(session.booking.input.startDate,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=IosGray)
    }
}

private fun StoredBookingSession.isPastAccountTrip():Boolean = effectiveStatus.uppercase() in setOf("COMPLETED","CANCELLED")
private fun statusTint(status:String):Color = IumrahBookingStatusVisual.color(status)

@Composable
private fun ProfileSection(profile: IumrahAccountProfile, language: AppLanguage, onEdit: () -> Unit) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { SectionHeader(CupertinoSymbol.IdentityCard, tr(language,"Account details","Данные аккаунта","Akkaunt ma’lumotlari","Аккаунт маълумотлари"), tr(language,"Used for your profile and future trips","Используются в профиле и новых поездках","Profil va yangi safarlarda ishlatiladi","Профил ва янги сафарларда ишлатилади")) }
            Surface(onClick=onEdit, shape=RoundedCornerShape(18.dp), color=MaterialTheme.colorScheme.onSurface.copy(alpha=.06f)) { Text(tr(language,"Edit","Изменить","Tahrirlash","Таҳрирлаш"), Modifier.padding(horizontal=12.dp,vertical=10.dp), fontSize=12.sp,fontWeight=FontWeight.Bold) }
        }
        Spacer(Modifier.height(12.dp))
        SummaryRow(CupertinoSymbol.PersonCircle,tr(language,"Name","Имя","Ism","Исм"),profile.displayName.ifBlank { listOf(profile.firstName,profile.lastName).filter{it.isNotBlank()}.joinToString(" ") })
        HorizontalDivider(Modifier.padding(start=52.dp)); SummaryRow(CupertinoSymbol.Phone,tr(language,"Phone","Телефон","Telefon","Телефон"),profile.phone)
        HorizontalDivider(Modifier.padding(start=52.dp)); SummaryRow(CupertinoSymbol.Mail,"Email",profile.email)
        HorizontalDivider(Modifier.padding(start=52.dp)); SummaryRow(CupertinoSymbol.Send,"Telegram",profile.telegram)
        HorizontalDivider(Modifier.padding(start=52.dp)); SummaryRow(CupertinoSymbol.Message,"WhatsApp",profile.whatsapp)
    }
}

@Composable private fun SummaryRow(icon: CupertinoSymbol,title:String,value:String){ Row(Modifier.fillMaxWidth().padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){IconBadge(icon,Care,38.dp,14.dp); Column{Text(title,fontSize=11.sp,color=IosGray);Text(value.ifBlank{"—"},fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(value.isBlank()) IosGray else MaterialTheme.colorScheme.onSurface,maxLines=2)}} }

@Composable
private fun PaymentSecuritySection(language: AppLanguage, trip: StoredBookingSession?, authenticated: Boolean, chrome: AppChromeStore) {
    SectionCard(
        tr(language,"Payment & privacy","Оплата и правила","To‘lov va qoidalar","Тўлов ва қоидалар"),
        tr(language,"Payment methods, refunds and privacy","Способы оплаты, возвраты и конфиденциальность","To‘lov usullari, qaytarish va maxfiylik","Тўлов усуллари, қайтариш ва махфийлик"),
        CupertinoSymbol.CreditCard,
    ) {
        IumrahPaymentMethodsMarquee(compact = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SettingsRow(CupertinoSymbol.CreditCard,tr(language,"Payment","Оплата","To‘lov","Тўлов"),tr(language,"Method, confirmation and payment security","Способ, подтверждение и безопасность платежа","Usul, tasdiqlash va to‘lov xavfsizligi","Усул, тасдиқлаш ва тўлов хавфсизлиги")){chrome.openAccountPolicy("payment")}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.UndoCircle,tr(language,"Refund policy","Правила возврата","Qaytarish qoidalari","Қайтариш қоидалари"),tr(language,"Flights, hotels, transfer and services","Авиабилеты, отели, трансфер и сервисы","Aviachipta, mehmonxona, transfer va xizmatlar","Авиачипта, меҳмонхона, трансфер ва хизматлар")){chrome.openAccountPolicy("refund")}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.HandRaised,tr(language,"Privacy","Конфиденциальность","Maxfiylik","Махфийлик"),tr(language,"Personal data and privacy","Персональные данные и конфиденциальность","Shaxsiy ma’lumotlar va maxfiylik","Шахсий маълумотлар ва махфийлик")){chrome.openAccountPolicy("privacy")}
    }
}
@Composable
private fun ServicesSection(language: AppLanguage, chrome: AppChromeStore) {
    SectionCard(
        tr(language, "Services", "Сервисы", "Xizmatlar", "Хизматлар"),
        tr(language, "All key iumrah tools in one place", "Все основные функции iumrah в одном месте", "iumrah asosiy funksiyalari bir joyda", "iumrah асосий функциялари бир жойда"),
        CupertinoSymbol.Grid,
    ) {
        SettingsRow(
            CupertinoSymbol.Airplane,
            "Airmora Flights Status",
            tr(language, "Live flight status, route and travel updates", "Статус рейса, маршрут и обновления поездки", "Reys holati, yo‘nalish va safar yangiliklari", "Рейс ҳолати, йўналиш ва сафар янгиликлари"),
        ) { chrome.openLiveFlights() }
        HorizontalDivider(Modifier.padding(start = 54.dp))
        SettingsRow(
            CupertinoSymbol.Map,
            "iumrah Ziyarats",
            tr(language, "Makkah and Madinah places, route and navigation", "Места Мекки и Медины, маршрут и навигация", "Makka va Madina joylari, yo‘nalish va navigatsiya", "Макка ва Мадина жойлари, йўналиш ва навигация"),
        ) { chrome.openZiyarats() }
        HorizontalDivider(Modifier.padding(start = 54.dp))
        SettingsRow(
            CupertinoSymbol.SimCard,
            "iumrah eSIM",
            tr(language, "Saudi connectivity and eSIM status", "Связь в Саудии и статус eSIM", "Saudiya aloqasi va eSIM holati", "Саудия алоқаси ва eSIM ҳолати"),
        ) { chrome.openESIM() }
        HorizontalDivider(Modifier.padding(start = 54.dp))
        SettingsRow(
            CupertinoSymbol.HeartFill,
            "iumrah Care",
            tr(language, "Booking help and trip support", "Помощь по бронированию и сопровождению поездки", "Bron va safar bo‘yicha yordam", "Брон ва сафар бўйича ёрдам"),
        ) { chrome.navigate(AppTab.CARE) }
        HorizontalDivider(Modifier.padding(start = 54.dp))
        SettingsRow(
            CupertinoSymbol.Gift,
            tr(language, "Gift Cards & Balance", "Gift Cards и баланс", "Gift Cards va balans", "Gift Cards ва баланс"),
            tr(language, "Gifts, referrals and iumrah Balance", "Подарки, приглашения и iumrah Balance", "Sovg‘alar, takliflar va iumrah Balance", "Совғалар, таклифлар ва iumrah Balance"),
        ) { chrome.openGiftCards() }
    }
}

@Composable
private fun SettingsSection(language: AppLanguage, appearance: AppAppearance, unread: Int, notificationStatus: String, chrome: AppChromeStore, context: android.content.Context) {
    SectionCard(tr(language,"Settings","Настройки","Sozlamalar","Созламалар"),tr(language,"Language, appearance and notifications","Язык, оформление и уведомления","Til, ko‘rinish va bildirishnomalar","Тил, кўриниш ва билдиришномалар"),CupertinoSymbol.Gear) {
        SettingsRow(CupertinoSymbol.LockShield,tr(language,"Account security","Безопасность аккаунта","Akkaunt xavfsizligi","Аккаунт хавфсизлиги"),tr(language,"Google and active sessions","Google и активные сеансы","Google va faol seanslar","Google ва фаол сеанслар")){chrome.openAccountSecurity()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.Globe,tr(language,"Language","Язык","Til","Тил"),languageTitle(language)){chrome.openAccountLanguage()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.HalfCircle,tr(language,"Appearance","Оформление","Ko‘rinish","Кўриниш"),appearanceTitle(appearance,language)){chrome.openAccountAppearance()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.BellSignal,tr(language,"Umrah status signal","Umra статус сигнал","Umra holat signali","Умра ҳолат сигнали"),if(unread>0) tr(language,"$unread new","$unread новых","$unread yangi","$unread янги") else tr(language,"All caught up","Новых нет","Yangi yo‘q","Янги йўқ")){chrome.openAccountSignals()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.BellBadge,tr(language,"Notifications","Уведомления","Bildirishnomalar","Билдиришномалар"),notificationStatus){
            val intent=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching{context.startActivity(intent)}
        }
    }
}

@Composable
private fun GuestSettingsSection(language: AppLanguage, appearance: AppAppearance, unread: Int, notificationStatus: String, chrome: AppChromeStore, context: android.content.Context) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(CupertinoSymbol.Sliders, Care, 42.dp, 17.dp)
            Text(tr(language,"App settings","Настройки приложения","Ilova sozlamalari","Илова созламалари"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        SettingsRow(CupertinoSymbol.Globe,tr(language,"Language","Язык","Til","Тил"),languageTitle(language)){chrome.openAccountLanguage()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.HalfCircle,tr(language,"Appearance","Оформление","Ko‘rinish","Кўриниш"),appearanceTitle(appearance,language)){chrome.openAccountAppearance()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.BellSignal,tr(language,"Umrah status signal","Umra статус сигнал","Umra holat signali","Умра ҳолат сигнали"),if(unread>0) tr(language,"$unread new","$unread новых","$unread yangi","$unread янги") else tr(language,"All caught up","Новых нет","Yangi yo‘q","Янги йўқ")){chrome.openAccountSignals()}
        HorizontalDivider(Modifier.padding(start=54.dp))
        SettingsRow(CupertinoSymbol.BellBadge,tr(language,"Notifications","Уведомления","Bildirishnomalar","Билдиришномалар"),notificationStatus){
            val intent=Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); runCatching{context.startActivity(intent)}
        }
    }
}

@Composable
private fun GuestLoginCard(language:AppLanguage,accountStore:IumrahAccountStore,bookingStore:BookingStore,chrome:AppChromeStore){
    // Observe the booking store from composition. Reading StateFlow.value here would not
    // trigger recomposition when an active booking is added or removed.
    val bookings by bookingStore.state.collectAsState()
    val scope=rememberCoroutineScope(); val activity = LocalActivity.current; var register by remember{mutableStateOf(false)}; var method by remember{mutableStateOf("sms")}
    var identifier by remember{mutableStateOf("")};var password by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var challenge by remember{mutableStateOf<String?>(null)}
    var first by remember{mutableStateOf("")};var last by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    CardBlock{
        SectionHeader(
            if(register) CupertinoSymbol.PlusPerson else CupertinoSymbol.Key,
            if(register) tr(language,"Create account","Регистрация","Ro‘yxatdan o‘tish","Рўйхатдан ўтиш") else tr(language,"Sign in","Вход","Kirish","Кириш"),
            if(register) tr(language,"Create one permanent account for all future trips","Создайте один постоянный аккаунт для всех будущих поездок","Barcha keyingi safarlar uchun bitta doimiy akkaunt yarating","Барча кейинги сафарлар учун битта доимий аккаунт яратинг") else tr(language,"Use the method linked to your permanent iumrah account","Используйте способ, привязанный к Вашему постоянному аккаунту iumrah","Doimiy iumrah akkauntingizga ulangan usuldan foydalaning","Доимий iumrah аккаунтингизга уланган усулдан фойдаланинг")
        )
        Spacer(Modifier.height(14.dp));Segmented(listOf(tr(language,"Register","Регистрация","Ro‘yxatdan o‘tish","Рўйхатдан ўтиш"),tr(language,"Sign in","Вход","Kirish","Кириш")),if(register)0 else 1){register=it==0;challenge=null;error=null}
        Spacer(Modifier.height(12.dp));Segmented(listOf("SMS","Email","iumrah ID"),when(method){"sms"->0;"email"->1;else->2}){method=listOf("sms","email","id")[it];challenge=null;error=null}
        Spacer(Modifier.height(14.dp))
        if(register && (method=="email" || method=="sms")){
            AccountField(first,{first=it},tr(language,"First name","Имя","Ism","Исм"));Spacer(Modifier.height(9.dp));AccountField(last,{last=it},tr(language,"Last name","Фамилия","Familiya","Фамилия"));Spacer(Modifier.height(9.dp))
        }
        AccountField(identifier,{identifier=it},when(method){"sms"->"+998";"email"->"Email";else->"UMR ID"},if(method=="sms") KeyboardType.Phone else KeyboardType.Email)
        if(method=="id" || (!register && method=="email") || (register && (method=="email" || method=="sms") && challenge!=null)){Spacer(Modifier.height(9.dp));AccountField(password,{password=it},tr(language,"Password","Пароль","Parol","Пароль"),password=true)}
        if(challenge!=null){Spacer(Modifier.height(9.dp));AccountField(code,{code=it},tr(language,"Verification code","Код подтверждения","Tasdiqlash kodi","Тасдиқлаш коди"),KeyboardType.Number)}
        error?.let{Spacer(Modifier.height(10.dp));Text(it,color=Danger,fontSize=12.sp)}
        Spacer(Modifier.height(14.dp))
        Button(onClick={busy=true;error=null;scope.launch{runCatching{
            when{
                register && method=="sms" && challenge==null -> challenge=accountStore.startPhoneRegistration(identifier.trim(),first.trim(),last.trim(),language.localeTag).challengeID
                register && method=="sms" -> accountStore.confirmPhoneRegistration(challenge!!,code.trim(),password,language.localeTag)
                register && method=="email" && challenge==null -> challenge=accountStore.startEmailRegistration(identifier.trim(),language.localeTag).challengeID
                register && method=="email" -> accountStore.confirmEmailRegistration(challenge!!,code.trim(),password,first.trim(),last.trim(),language.localeTag)
                !register && method=="sms" && challenge==null -> challenge=accountStore.startPhoneLogin(identifier.trim(),language.localeTag).challengeID
                !register && method=="sms" -> accountStore.confirmPhoneLogin(challenge!!,code.trim(),language.localeTag)
                !register && (method=="email" || method=="id") -> accountStore.login(identifier.trim(),password,language.localeTag)
                else -> throw IllegalStateException(tr(language,"iumrah ID registration is completed from an active booking.","Регистрация по iumrah ID завершается из активного бронирования.","iumrah ID orqali ro‘yxatdan o‘tish faol bron ichida yakunlanadi.","iumrah ID орқали рўйхатдан ўтиш фаол брон ичида якунланади."))
            }
        }.onFailure{error=it.message ?: "Error"};busy=false}},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(19.dp),enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Color.Black)){if(busy) CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp,color=Color.White) else Text(if(challenge==null) tr(language,"Continue","Продолжить","Davom etish","Давом этиш") else tr(language,"Confirm","Подтвердить","Tasdiqlash","Тасдиқлаш"),fontWeight=FontWeight.Bold)}
        if(!register && (method=="email" || method=="id")){Spacer(Modifier.height(8.dp));TextButton(onClick={chrome.openAccountPasswordRecovery()},modifier=Modifier.fillMaxWidth()){Text(tr(language,"Forgot password?","Забыли пароль?","Parolni unutdingizmi?","Паролни унутдингизми?"),fontSize=14.sp,fontWeight=FontWeight.SemiBold)}}
        if(register && method=="id" && bookings.sessions.isNotEmpty()){Spacer(Modifier.height(10.dp));Text(tr(language,"Use the active booking to finish creating this account.","Используйте активную бронь, чтобы завершить создание аккаунта.","Akkaunt yaratishni yakunlash uchun faol brondan foydalaning.","Аккаунт яратишни якунлаш учун фаол брондан фойдаланинг."),fontSize=12.sp,color=IosGray)}
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .20f))
            Text(tr(language,"or","или","yoki","ёки"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .20f))
        }
        Spacer(Modifier.height(16.dp))
        ProviderButton(CupertinoSymbol.Apple,if(register)tr(language,"Continue with Apple","Продолжить с Apple","Apple bilan davom etish","Apple билан давом этиш") else tr(language,"Sign in with Apple","Войти с Apple","Apple orqali kirish","Apple орқали кириш"), enabled = !busy) {
            error = tr(language, "Apple Sign-In on Android requires the Apple Web Services redirect configuration.", "Для Apple Sign-In на Android требуется Web Services redirect Apple.", "Android’da Apple Sign-In uchun Apple Web Services redirect sozlamasi kerak.", "Android’да Apple Sign-In учун Apple Web Services redirect созламаси керак.")
        }
        Spacer(Modifier.height(10.dp))
        ProviderButton(CupertinoSymbol.Globe,if(register)tr(language,"Continue with Google","Продолжить с Google","Google bilan davom etish","Google билан давом этиш") else tr(language,"Sign in with Google","Войти с Google","Google orqali kirish","Google орқали кириш"), enabled = !busy) {
            val host = activity ?: return@ProviderButton
            busy = true; error = null
            scope.launch {
                runCatching { GoogleSignInSupport.signIn(host) }
                    .mapCatching { accountStore.signInWithGoogle(it, language.localeTag) }
                    .onFailure { error = it.message ?: "Google Sign-In failed" }
                busy = false
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            tr(language,
                "Apple and Google use the same permanent iumrah account; they do not create a separate trip profile.",
                "Apple и Google используют тот же постоянный аккаунт iumrah и не создают отдельный профиль поездки.",
                "Apple va Google aynan shu doimiy iumrah akkauntidan foydalanadi va alohida safar profili yaratmaydi.",
                "Apple ва Google айнан шу доимий iumrah аккаунтидан фойдаланади ва алоҳида сафар профили яратмайди."),
            fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProviderButton(icon: CupertinoSymbol, title: String, enabled: Boolean = true, onClick: () -> Unit) {
    val isApple = icon == CupertinoSymbol.Apple
    val shape = RoundedCornerShape(19.dp)
    Surface(
        shape = shape,
        color = if (isApple) Color.Black else Color.White,
        border = if (isApple) null else androidx.compose.foundation.BorderStroke(.8.dp, Color.Black.copy(alpha = .20f)),
        shadowElevation = if (isApple) 0.dp else 2.dp,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (isApple) CupertinoIcon(CupertinoSymbol.Apple, null, Modifier.size(21.dp), Color.White)
            else Text("G", color = Color(0xFF4285F4), fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(11.dp))
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = if (isApple) Color.White else Color.Black.copy(alpha = .90f), maxLines = 1)
        }
    }
}

@Composable
fun AccountProfileEditorScreen(language:AppLanguage,accountStore:IumrahAccountStore,chrome:AppChromeStore){
    val state by accountStore.state.collectAsState();val p=state.account;val scope=rememberCoroutineScope()
    var first by remember(p){mutableStateOf(p?.firstName.orEmpty())};var last by remember(p){mutableStateOf(p?.lastName.orEmpty())};var phone by remember(p){mutableStateOf(p?.phone.orEmpty())};var email by remember(p){mutableStateOf(p?.email.orEmpty())};var telegram by remember(p){mutableStateOf(p?.telegram.orEmpty())};var whatsapp by remember(p){mutableStateOf(p?.whatsapp.orEmpty())};var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    AccountPage(tr(language,"Account details","Данные аккаунта","Akkaunt ma’lumotlari","Аккаунт маълумотлари"),chrome){
        CardBlock{AccountField(first,{first=it},tr(language,"First name","Имя","Ism","Исм"));Spacer(Modifier.height(10.dp));AccountField(last,{last=it},tr(language,"Last name","Фамилия","Familiya","Фамилия"));Spacer(Modifier.height(10.dp));AccountField(phone,{phone=it},tr(language,"Phone","Телефон","Telefon","Телефон"),KeyboardType.Phone);Spacer(Modifier.height(10.dp));AccountField(email,{email=it},"Email",KeyboardType.Email);Spacer(Modifier.height(10.dp));AccountField(telegram,{telegram=it},"Telegram");Spacer(Modifier.height(10.dp));AccountField(whatsapp,{whatsapp=it},"WhatsApp")}
        error?.let{Text(it,color=Danger,fontSize=12.sp)}
        Button(onClick={busy=true;scope.launch{runCatching{accountStore.updateProfile(first,last,phone,email,telegram,whatsapp)}.onSuccess{chrome.back()}.onFailure{error=it.message};busy=false}},modifier=Modifier.fillMaxWidth().height(56.dp),shape=RoundedCornerShape(19.dp),enabled=!busy,colors=ButtonDefaults.buttonColors(containerColor=Color.Black)){Text(tr(language,"Save","Сохранить","Saqlash","Сақлаш"),fontWeight=FontWeight.Bold)}
    }
}

@Composable
fun AccountTravelersScreen(language:AppLanguage,accountStore:IumrahAccountStore,bookingStore:BookingStore,accountService:IumrahAccountService,chrome:AppChromeStore){
    val sessions by bookingStore.state.collectAsState();var travelers by remember{mutableStateOf<List<Pair<StoredBookingSession,IumrahTravelerForm>>>(emptyList())};var loading by remember{mutableStateOf(true)}
    LaunchedEffect(sessions.sessions,accountStore.bearerToken){loading=true;val rows=mutableListOf<Pair<StoredBookingSession,IumrahTravelerForm>>();sessions.sessions.forEach{s->runCatching{accountService.checkout(s.id,bookingStore.headersFor(s))}.getOrNull()?.travelers?.forEach{rows+=s to it}};travelers=rows;loading=false}
    AccountPage(tr(language,"Travel companions","Спутники","Hamrohlar","Ҳамроҳлар"),chrome){
        CardBlock{SectionHeader(CupertinoSymbol.Persons,tr(language,"Your pilgrims","Ваши паломники","Ziyoratchilaringiz","Зиёратчиларингиз"),tr(language,"Traveler details are attached to bookings and stay ready for the trip.","Данные паломников привязаны к бронированиям и готовы к поездке.","Sayohatchi ma’lumotlari bronlarga bog‘langan va safar uchun tayyor turadi.","Саёҳатчи маълумотлари бронларга боғланган ва сафар учун тайёр туради."))}
        when{loading->Box(Modifier.fillMaxWidth().padding(32.dp),contentAlignment=Alignment.Center){CircularProgressIndicator()};travelers.isEmpty()->CardBlock{EmptyRow(CupertinoSymbol.Persons,tr(language,"No companions yet","Пока нет спутников","Hali hamrohlar yo‘q","Ҳали ҳамроҳлар йўқ"))};else->travelers.forEach{(session,t)->CardBlock{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){IconBadge(CupertinoSymbol.PersonCircle,Care,44.dp,18.dp);Column(Modifier.weight(1f)){Text(listOf(t.firstName,t.lastName).filter{it.isNotBlank()}.joinToString(" ").ifBlank{tr(language,"Pilgrim ${t.position}","Паломник ${t.position}","Ziyoratchi ${t.position}","Зиёратчи ${t.position}")},fontWeight=FontWeight.Bold);Text("${session.displayBookingNumber} · ${if(t.completed) tr(language,"Ready","Готово","Tayyor","Тайёр") else tr(language,"Needs details","Нужны данные","Ma’lumot kerak","Маълумот керак")}",fontSize=12.sp,color=IosGray)}};Spacer(Modifier.height(12.dp));Button(onClick={chrome.openPilgrimCheckout(session.id)},shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text(tr(language,"Open traveler data","Открыть данные паломников","Ziyoratchi ma’lumotlarini ochish","Зиёратчи маълумотларини очиш"))}}}
        }
    }
}

@Composable
fun AccountPolicyScreen(kind:String,language:AppLanguage,chrome:AppChromeStore){
    val title=when(kind){"refund"->tr(language,"Refund policy","Правила возврата","Qaytarish qoidalari","Қайтариш қоидалари");"privacy"->tr(language,"Privacy","Конфиденциальность","Maxfiylik","Махфийлик");else->tr(language,"Payment & security","Оплата и безопасность","To‘lov va xavfsizlik","Тўлов ва хавфсизлик")}
    val sections=when(kind){
        "refund"->listOf(
            tr(language,"Flights","Авиабилеты","Aviachiptalar","Авиачипталар") to tr(language,"Flights are non-refundable by default unless the selected fare explicitly allows a refund.","Авиабилеты по умолчанию невозвратные, если выбранный тариф прямо не предусматривает возврат.","Tanlangan tarifda qaytarish aniq ko‘rsatilmagan bo‘lsa, aviachiptalar odatda qaytarilmaydi.","Танланган тарифда қайтариш аниқ кўрсатилмаган бўлса, авиачипталар одатда қайтарилмайди."),
            tr(language,"Hotels","Отели","Mehmonxonalar","Меҳмонхоналар") to tr(language,"Hotel cancellation follows the booked room rate and provider conditions.","Возврат по отелю зависит от тарифа номера и условий поставщика.","Mehmonxona bekor qilinishi xona tarifi va provayder shartlariga bog‘liq.","Меҳмонхона бекор қилиниши хона тарифи ва провайдер шартларига боғлиқ."),
            tr(language,"Transfer","Трансфер","Transfer","Трансфер") to tr(language,"Transfer is fully refundable until 120 hours before service. Later cancellation may include a \$100 fee.","Трансфер полностью возвратный до 120 часов до услуги. При более поздней отмене может удерживаться комиссия \$100.","Transfer xizmatdan 120 soat oldingacha to‘liq qaytariladi. Keyinroq bekor qilishda \$100 komissiya bo‘lishi mumkin.","Трансфер хизматдан 120 соат олдингача тўлиқ қайтарилади. Кейинроқ бекор қилишда \$100 комиссия бўлиши мумкин."),
            tr(language,"iumrah services","Сервисы iumrah","iumrah xizmatlari","iumrah хизматлари") to tr(language,"Unused iumrah services can be refunded before the service begins. The terms fixed at booking remain authoritative.","Неиспользованные сервисы iumrah можно вернуть до начала оказания услуги. Действуют условия, зафиксированные при бронировании.","Foydalanilmagan iumrah xizmatlari boshlanishidan oldin qaytarilishi mumkin. Bron paytidagi shartlar amal qiladi.","Фойдаланилмаган iumrah хизматлари бошланишидан олдин қайтарилиши мумкин. Брон пайтидаги шартлар амал қилади.")
        )
        "privacy"->listOf(
            tr(language,"Data we use","Какие данные мы используем","Qaysi ma’lumotlardan foydalanamiz","Қайси маълумотлардан фойдаланамиз") to tr(language,"Identity, contact, passport and trip information required to create and service your booking.","Идентификационные, контактные, паспортные данные и сведения о поездке, необходимые для оформления и обслуживания брони.","Bronni yaratish va xizmat ko‘rsatish uchun zarur shaxsiy, aloqa, pasport va safar ma’lumotlari.","Бронни яратиш ва хизмат кўрсатиш учун зарур шахсий, алоқа, паспорт ва сафар маълумотлари."),
            tr(language,"Why we use it","Зачем мы их используем","Nega foydalanamiz","Нега фойдаланамиз") to tr(language,"To provide booked travel services, verify identity, support payments and communicate important trip updates.","Чтобы оказывать забронированные услуги, подтверждать личность, сопровождать оплату и сообщать важные обновления поездки.","Bron qilingan xizmatlarni ko‘rsatish, shaxsni tasdiqlash, to‘lovni qo‘llab-quvvatlash va muhim safar yangiliklarini yuborish uchun.","Брон қилинган хизматларни кўрсатиш, шахсни тасдиқлаш, тўловни қўллаб-қувватлаш ва муҳим сафар янгиликларини юбориш учун."),
            tr(language,"Sharing with providers","Передача поставщикам","Provayderlarga uzatish","Провайдерларга узатиш") to tr(language,"Only the data required to fulfill a booked flight, hotel, transfer or other service is shared with the relevant provider.","Поставщику передаются только данные, необходимые для исполнения конкретного авиабилета, отеля, трансфера или другой услуги.","Faqat aniq xizmatni bajarish uchun zarur ma’lumotlar tegishli provayderga uzatiladi.","Фақат аниқ хизматни бажариш учун зарур маълумотлар тегишли провайдерга узатилади."),
            tr(language,"Retention and deletion","Хранение и удаление","Saqlash va o‘chirish","Сақлаш ва ўчириш") to tr(language,"Data is retained only as needed for service, security, accounting and legal obligations, then deleted or anonymized.","Данные хранятся столько, сколько необходимо для сервиса, безопасности, учёта и юридических обязательств, после чего удаляются или обезличиваются.","Ma’lumotlar xizmat, xavfsizlik, hisob va qonuniy talablar uchun zarur muddat saqlanadi, so‘ng o‘chiriladi yoki anonimlashtiriladi.","Маълумотлар хизмат, хавфсизлик, ҳисоб ва қонуний талаблар учун зарур муддат сақланади, сўнг ўчирилади ёки анонимлаштирилади.")
        )
        else->listOf(
            tr(language,"Manual payments","Ручная оплата","Qo‘lda to‘lov","Қўлда тўлов") to tr(language,"During the first release, payment details are provided only inside your booking. Always verify the booking amount and recipient before paying.","На первом этапе реквизиты для оплаты показываются только внутри бронирования. Перед оплатой всегда сверяйте сумму и получателя.","Dastlab to‘lov rekvizitlari faqat bron ichida ko‘rsatiladi. To‘lashdan oldin summa va qabul qiluvchini tekshiring.","Дастлаб тўлов реквизитлари фақат брон ичида кўрсатилади. Тўлашдан олдин сумма ва қабул қилувчини текширинг."),
            tr(language,"Payment security","Безопасность платежа","To‘lov xavfsizligi","Тўлов хавфсизлиги") to tr(language,"iumrah never asks for your card PIN, CVV or banking password in chat or by phone.","iumrah никогда не запрашивает PIN-код карты, CVV или банковский пароль в чате или по телефону.","iumrah chat yoki telefon orqali karta PIN, CVV yoki bank parolini so‘ramaydi.","iumrah чат ёки телефон орқали карта PIN, CVV ёки банк паролини сўрамайди."),
            tr(language,"Invoice and receipt","Счёт и чек","Hisob va chek","Ҳисоб ва чек") to tr(language,"Your booking contains the payable amount and lets you upload a payment receipt for verification.","В бронировании указана сумма к оплате и можно загрузить чек для проверки.","Bron ichida to‘lov summasi ko‘rsatiladi va tekshiruv uchun chek yuklash mumkin.","Брон ичида тўлов суммаси кўрсатилади ва текширув учун чек юклаш мумкин."),
            tr(language,"Cancellation","Отмена поездки","Safarni bekor qilish","Сафарни бекор қилиш") to tr(language,"If a trip is cancelled, each component is refunded according to its own fare and cancellation terms.","При отмене поездки каждый компонент возвращается по своим тарифным и отменным условиям.","Safar bekor qilinsa, har bir komponent o‘z tarifi va bekor qilish shartlari bo‘yicha qaytariladi.","Сафар бекор қилинса, ҳар бир компонент ўз тарифи ва бекор қилиш шартлари бўйича қайтарилади.")
        )
    }
    AccountPage(title,chrome){
        CardBlock {
            IumrahPaymentMethodsMarquee(compact = true, modifier = Modifier.fillMaxWidth())
        }
        sections.forEach{(h,b)->CardBlock{Text(h,fontSize=17.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(7.dp));Text(b,color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=14.sp,lineHeight=20.sp)}}
    }
}

@Composable
fun AccountKycScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    chrome: AppChromeStore,
) {
    val session = bookingStore.booking(bookingID)
    AccountPage(
        tr(language, "Iumrah Security", "Iumrah Security", "Iumrah Security", "Iumrah Security"),
        chrome,
    ) {
        if (session == null) {
            CardBlock {
                EmptyRow(
                    CupertinoSymbol.IdentityCard,
                    tr(language, "Booking is unavailable", "Бронирование недоступно", "Bron mavjud emas", "Брон мавжуд эмас"),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    tr(language, "Security Confirmation · KYC", "Security Confirmation · KYC", "Security Confirmation · KYC", "Security Confirmation · KYC"),
                    fontSize = 12.sp,
                    color = IosGray,
                    fontWeight = FontWeight.SemiBold,
                )
                IumrahSecurityConfirmationPanel(session, bookingStore.service)
            }
        }
    }
}

@Composable
fun AccountSecurityScreen(
    language: AppLanguage,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
) {
    AccountSecurityParityContent(language, accountStore, chrome)
}

@Composable
fun AccountAppearanceScreen(language:AppLanguage,settingsStore:AppSettingsStore,chrome:AppChromeStore){
    val settings by settingsStore.state.collectAsState()
    val icons=listOf(
        Triple("blue","Blue",R.drawable.iumrah_icon_blue_preview),
        Triple("cyan","Cyan",R.drawable.iumrah_icon_cyan_preview),
        Triple("deep","Deep",R.drawable.iumrah_icon_deep_preview),
        Triple("world","World",R.drawable.iumrah_icon_world_preview),
        Triple("makkah","Makkah",R.drawable.iumrah_icon_makkah_preview),
    )
    AccountPage(tr(language,"Appearance","Оформление","Ko‘rinish","Кўриниш"),chrome){
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(tr(language,"Theme","Тема","Mavzu","Мавзу"),fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=4.dp))
            Text(tr(language,"Choose how iumrah looks on this device.","Выберите, как iumrah выглядит на этом устройстве.","iumrah ushbu qurilmada qanday ko‘rinishini tanlang.","iumrah ушбу қурилмада қандай кўринишини танланг."),fontSize=13.sp,color=IosGray,modifier=Modifier.padding(start=4.dp))
            CardBlock{
                AppearanceRow(CupertinoSymbol.Device,tr(language,"System","Системная","Tizim","Тизим"),settings.appearance==AppAppearance.SYSTEM){settingsStore.setAppearance(AppAppearance.SYSTEM)}
                HorizontalDivider(Modifier.padding(start=70.dp))
                AppearanceRow(CupertinoSymbol.Sun,tr(language,"Light","Светлая","Yorug‘","Ёруғ"),settings.appearance==AppAppearance.LIGHT){settingsStore.setAppearance(AppAppearance.LIGHT)}
                HorizontalDivider(Modifier.padding(start=70.dp))
                AppearanceRow(CupertinoSymbol.Moon,tr(language,"Dark","Тёмная","Qorong‘i","Қоронғи"),settings.appearance==AppAppearance.DARK){settingsStore.setAppearance(AppAppearance.DARK)}
            }
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
            Text(tr(language,"App icon","Иконка приложения","Ilova ikonkasi","Илова иконкаси"),fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=4.dp))
            Text(tr(language,"Choose how iumrah appears on your Home Screen.","Выберите, как iumrah выглядит на главном экране.","iumrah bosh ekranda qanday ko‘rinishini tanlang.","iumrah бош экранда қандай кўринишини танланг."),fontSize=13.sp,color=IosGray,modifier=Modifier.padding(start=4.dp))
            CardBlock{
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    icons.forEach{(key,name,res)->
                        Column(Modifier.width(92.dp).clickable{settingsStore.setLauncherIcon(key)},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Box{
                                Image(painterResource(res),name,Modifier.size(92.dp).clip(RoundedCornerShape(22.dp)).border(if(settings.launcherIcon==key)1.5.dp else 1.dp,if(settings.launcherIcon==key)MaterialTheme.colorScheme.onSurface.copy(alpha=.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha=.07f),RoundedCornerShape(22.dp)),contentScale=ContentScale.Crop)
                                if(settings.launcherIcon==key) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha=.82f)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.Checkmark,null,Modifier.size(13.dp),Color.White)}
                            }
                            Text(name,fontSize=11.sp,fontWeight=if(settings.launcherIcon==key)FontWeight.Bold else FontWeight.SemiBold,color=if(settings.launcherIcon==key)MaterialTheme.colorScheme.onSurface else IosGray)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp));HorizontalDivider();Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().clickable(enabled=settings.launcherIcon!="standard"){settingsStore.setLauncherIcon("standard")}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    IconBadge(CupertinoSymbol.Refresh,Care,38.dp,15.dp)
                    Column(Modifier.weight(1f)){Text(tr(language,"Standard icon","Стандартная иконка","Standart ikonka","Стандарт иконка"),fontWeight=FontWeight.SemiBold);Text(tr(language,"Restore the original iumrah icon","Вернуть исходную иконку iumrah","Asl iumrah ikonkasini qaytarish","Асл iumrah иконкасини қайтариш"),fontSize=11.sp,color=IosGray)}
                    if(settings.launcherIcon=="standard") CupertinoIcon(CupertinoSymbol.CheckCircle,null,Modifier.size(20.dp),Care) else CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(14.dp),IosGray)
                }
            }
        }
    }
}

@Composable private fun AppearanceRow(icon:CupertinoSymbol,title:String,selected:Boolean,onClick:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){IconBadge(icon,Care,42.dp,17.dp);Text(title,Modifier.weight(1f),fontWeight=FontWeight.SemiBold);if(selected) CupertinoIcon(CupertinoSymbol.CheckCircle,null,Modifier.size(21.dp),Care)}}

@Composable
fun AccountLanguageScreen(language: AppLanguage, settingsStore: AppSettingsStore, chrome: AppChromeStore) {
    val current by settingsStore.state.collectAsState()
    AccountPage(tr(language, "Language", "Язык", "Til", "Тил"), chrome) {
        CardBlock {
            AppLanguage.entries.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = 54.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { settingsStore.setLanguage(item) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(CupertinoSymbol.Globe, Care, 40.dp, 16.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(languageTitle(item), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (current.language == item) CupertinoIcon(CupertinoSymbol.CheckCircle, null, Modifier.size(21.dp), Care)
                }
            }
        }
    }
}

@Composable
fun AccountSignalsScreen(language:AppLanguage,notifications:ClientNotificationStore,accountStore:IumrahAccountStore,chrome:AppChromeStore){
    val state by notifications.state.collectAsState()
    val scope=rememberCoroutineScope()
    val unread=remember(state.inbox){state.inbox.filter{!it.isRead}}
    val earlier=remember(state.inbox){state.inbox.filter{it.isRead}}
    LaunchedEffect(Unit){notifications.refresh(accountStore.bearerToken)}
    AccountPage("iumrah Signal",chrome){
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors=listOf(Color(0xFF12358F),Color(0xFF2B63E0),Color(0xFF5E4FCA)),
                    start=Offset.Zero,
                    end=Offset(900f,700f),
                )
            ).border(.8.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(30.dp)).padding(22.dp)
        ){
            Column(verticalArrangement=Arrangement.spacedBy(17.dp)){
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(14.dp)){
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                        Text("iumrah Signal",fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=.7.sp,color=Color.White.copy(alpha=.72f))
                        Text(tr(language,"Everything important about your journey","Всё важное по вашей поездке","Safaringiz bo‘yicha barcha muhim xabarlar","Сафарингиз бўйича барча муҳим хабарлар"),fontSize=29.sp,lineHeight=31.sp,fontWeight=FontWeight.Bold,color=Color.White)
                        Text(tr(language,"Booking changes, trip updates and reminders stay here even after a push notification disappears.","Изменения бронирования, новости поездки и напоминания остаются здесь, даже когда push уже исчез.","Bron o‘zgarishlari, safar yangiliklari va eslatmalar push yo‘qolgandan keyin ham shu yerda qoladi.","Брон ўзгаришлари, сафар янгиликлари ва эслатмалар push йўқолгандан кейин ҳам шу ерда қолади."),fontSize=13.sp,lineHeight=18.sp,color=Color.White.copy(alpha=.78f))
                    }
                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(17.dp)).background(Color.White.copy(alpha=.10f)),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.BellBadge,null,Modifier.size(20.dp),Color.White)}
                }
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    SignalChip("${state.unreadCount}",tr(language,"new","новых","yangi","янги"),state.unreadCount>0)
                    SignalChip("${state.inbox.size}",tr(language,"total","всего","jami","жами"),false)
                }
            }
        }
        if(state.inbox.isEmpty()){
            CardBlock{
                IconBadge(CupertinoSymbol.BellSignal,Signal,48.dp,19.dp)
                Spacer(Modifier.height(12.dp))
                Text(tr(language,"No notifications yet","Пока нет уведомлений","Hali bildirishnomalar yo‘q","Ҳали билдиришномалар йўқ"),fontSize=17.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text(tr(language,"When iumrah sends a Signal, it will appear here and on the Home screen.","Когда iumrah отправит Signal, он появится здесь и на главной странице.","iumrah Signal yuborganda, u shu yerda va Asosiy sahifada ko‘rinadi.","iumrah Signal юборганда, у шу ерда ва Асосий саҳифада кўринади."),fontSize=13.sp,lineHeight=18.sp,color=IosGray)
            }
        } else {
            if(unread.isNotEmpty()) SignalNotificationSection(language,tr(language,"New","Новые","Yangi","Янги"),tr(language,"Needs your attention","То, что стоит посмотреть","E’tibor berish kerak","Эътибор бериш керак"),unread,true,notifications,accountStore,chrome,scope,state.dismissedHomeIDs)
            if(earlier.isNotEmpty()) SignalNotificationSection(language,tr(language,"Earlier","Ранее","Avvalgi","Аввалги"),tr(language,"Already opened","Уже просмотрено","Ko‘rib chiqilgan","Кўриб чиқилган"),earlier,false,notifications,accountStore,chrome,scope,state.dismissedHomeIDs)
        }
    }
}

@Composable
private fun SignalNotificationSection(
    language:AppLanguage,
    title:String,
    subtitle:String,
    rows:List<ClientSystemNotification>,
    unreadSection:Boolean,
    notifications:ClientNotificationStore,
    accountStore:IumrahAccountStore,
    chrome:AppChromeStore,
    scope:kotlinx.coroutines.CoroutineScope,
    dismissed:Set<String>,
){
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)){
                Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold)
                Text(subtitle,fontSize=12.sp,color=IosGray)
            }
            Text("${rows.size}",Modifier.clip(CircleShape).background((if(unreadSection)Signal else IosGray).copy(alpha=.10f)).padding(horizontal=10.dp,vertical=7.dp),fontSize=12.sp,fontWeight=FontWeight.Bold,fontFamily=com.iumrah.beta.core.design.IumrahFontFamily,color=if(unreadSection)Signal else IosGray)
        }
        rows.forEach{n->SignalNotificationCard(language,n,dismissed.contains(n.id),notifications,accountStore,chrome,scope)}
    }
}

@Composable
private fun SignalNotificationCard(
    language:AppLanguage,
    n:ClientSystemNotification,
    hidden:Boolean,
    notifications:ClientNotificationStore,
    accountStore:IumrahAccountStore,
    chrome:AppChromeStore,
    scope:kotlinx.coroutines.CoroutineScope,
){
    val unread=!n.isRead
    val icon=when(n.destination){"hotels"->CupertinoSymbol.Building;"bookings","booking"->CupertinoSymbol.Suitcase;"care"->CupertinoSymbol.HeartFill;"account"->CupertinoSymbol.PersonCircle;else->CupertinoSymbol.Home}
    val destination=when(n.destination){"hotels"->tr(language,"Hotels","Отели","Mehmonxonalar","Меҳмонхоналар");"bookings"->tr(language,"Trips","Поездки","Safarlar","Сафарлар");"booking"->tr(language,"Trip details","Детали поездки","Safar tafsilotlari","Сафар тафсилотлари");"care"->"iumrah Care";"account"->"Account";else->tr(language,"Home","Главная","Asosiy","Асосий")}
    val shape=RoundedCornerShape(26.dp)
    Surface(shape=shape,color=if(unread)Signal.copy(alpha=.065f) else MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=if(unread)2.dp else 1.dp,border=androidx.compose.foundation.BorderStroke(1.dp,if(unread)Signal.copy(alpha=.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha=.055f))){
        Row{
            if(unread) Box(Modifier.width(4.dp).height(190.dp).padding(vertical=22.dp).clip(CircleShape).background(Signal))
            Column(Modifier.weight(1f).padding(18.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    IconBadge(icon,if(unread)Signal else IosGray,44.dp,16.dp)
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Row(verticalAlignment=Alignment.Top){Text(n.title,Modifier.weight(1f),fontSize=17.sp,fontWeight=if(unread)FontWeight.Bold else FontWeight.SemiBold);if(unread){Spacer(Modifier.width(8.dp));Box(Modifier.padding(top=6.dp).size(8.dp).clip(CircleShape).background(Signal))}}
                        Text(n.sentAt?:n.createdAt,fontSize=11.sp,color=IosGray)
                    }
                }
                Text(n.body,fontSize=14.sp,lineHeight=20.sp,color=if(unread)MaterialTheme.colorScheme.onSurface.copy(alpha=.78f) else IosGray)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    CupertinoIcon(CupertinoSymbol.ArrowDown,null,Modifier.size(13.dp),IosGray)
                    Text(destination,fontSize=11.sp,fontWeight=FontWeight.SemiBold,color=IosGray)
                    if(hidden) Text(tr(language,"Hidden on Home","Скрыто на главной","Asosiyda yashirilgan","Асосийда яширилган"),Modifier.clip(CircleShape).background(IosGray.copy(alpha=.10f)).padding(horizontal=8.dp,vertical=5.dp),fontSize=9.sp,fontWeight=FontWeight.Bold,color=IosGray)
                    Spacer(Modifier.weight(1f))
                    Surface(onClick={if(hidden)notifications.restoreToHome(n.id) else notifications.dismissFromHome(n.id)},shape=CircleShape,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.055f)){Box(Modifier.size(36.dp),contentAlignment=Alignment.Center){CupertinoIcon(if(hidden)CupertinoSymbol.Eye else CupertinoSymbol.EyeSlash,null,Modifier.size(14.dp),IosGray)}}
                }
                Button(onClick={
                    scope.launch{notifications.markOpened(n,accountStore.bearerToken)}
                    when(n.destination){
                        "hotels"->chrome.navigate(AppTab.HOTELS)
                        "bookings"->chrome.navigate(AppTab.BOOKING)
                        "care"->chrome.navigate(AppTab.CARE)
                        "account"->chrome.navigate(AppTab.ACCOUNT)
                        "booking"->n.destinationBookingID?.let(chrome::openBookingDetail)?:chrome.navigate(AppTab.BOOKING)
                        else->chrome.navigate(AppTab.HOME)
                    }
                },modifier=Modifier.fillMaxWidth().height(46.dp),shape=RoundedCornerShape(16.dp),colors=ButtonDefaults.buttonColors(containerColor=if(unread)Signal else MaterialTheme.colorScheme.onSurface.copy(alpha=.07f),contentColor=if(unread)Color.White else MaterialTheme.colorScheme.onSurface)){
                    Text(tr(language,"Open","Открыть","Ochish","Очиш"),Modifier.weight(1f),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    CupertinoIcon(CupertinoSymbol.ArrowUpRight,null,Modifier.size(15.dp),if(unread)Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable private fun SignalChip(value:String,title:String,emphasized:Boolean){Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha=if(emphasized).16f else .09f)).padding(horizontal=13.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically){Text(value,color=Color.White,fontWeight=FontWeight.Bold,fontFamily=com.iumrah.beta.core.design.IumrahFontFamily);Text(title,color=Color.White.copy(alpha=if(emphasized)1f else .78f),fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}

@Composable
private fun AccountPage(title:String,chrome:AppChromeStore,content:@Composable ColumnScope.()->Unit){
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(),contentPadding=PaddingValues(start=PagePad,end=PagePad,top=14.dp,bottom=48.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){Surface(onClick={chrome.back()},shape=CircleShape,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.06f)){Box(Modifier.size(38.dp),contentAlignment=Alignment.Center){CupertinoIcon(CupertinoSymbol.ChevronLeft,null,Modifier.size(18.dp),MaterialTheme.colorScheme.onSurface)}};Text(title,Modifier.weight(1f).padding(horizontal=12.dp),fontSize=17.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.width(38.dp))}}
        item{Column(verticalArrangement=Arrangement.spacedBy(18.dp),content=content)}
    }
}

@Composable private fun SectionCard(title:String,subtitle:String,icon:CupertinoSymbol,content:@Composable ColumnScope.()->Unit){CardBlock{SectionHeader(icon,title,subtitle);Spacer(Modifier.height(10.dp));content()}}
@Composable private fun SectionHeader(icon:CupertinoSymbol,title:String,subtitle:String){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){IconBadge(icon,Care,42.dp,17.dp);Column(Modifier.weight(1f)){Text(title,fontSize=17.sp,fontWeight=FontWeight.Bold);Text(subtitle,fontSize=12.sp,color=IosGray)}}}
@Composable private fun CardBlock(content:@Composable ColumnScope.()->Unit){val shape=RoundedCornerShape(CardRadius);Surface(shape=shape,color=MaterialTheme.colorScheme.surface,tonalElevation=0.dp,shadowElevation=3.dp,border=androidx.compose.foundation.BorderStroke(.7.dp,MaterialTheme.colorScheme.onSurface.copy(alpha=.075f))){Column(Modifier.fillMaxWidth().padding(18.dp),content=content)}}

@Composable
private fun SettingsRow(icon:CupertinoSymbol,title:String,value:String,enabled:Boolean=true,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().then(if(enabled)Modifier.clickable(onClick=onClick) else Modifier).padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        IconBadge(icon,Care,42.dp,17.dp);Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.SemiBold,color=if(enabled)MaterialTheme.colorScheme.onSurface else IosGray);Text(value,fontSize=12.sp,color=IosGray,maxLines=2)};if(enabled)CupertinoIcon(CupertinoSymbol.ChevronRight,null,Modifier.size(15.dp),IosGray.copy(alpha=.7f))
    }
}
@Composable private fun EmptyRow(icon:CupertinoSymbol,text:String){Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){IconBadge(icon,IosGray,42.dp,17.dp);Text(text,color=IosGray,fontSize=13.sp)}}
/** All Account icon badges share the exact iOS solid icon-badge primitive. */
@Composable private fun IconBadge(
    icon: CupertinoSymbol,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    symbolSize: androidx.compose.ui.unit.Dp,
    darkFill: Boolean = false,
) {
    val radius = when {
        size >= 52.dp -> 17.dp
        size >= 48.dp -> 16.dp
        size >= 44.dp -> 15.dp
        size >= 42.dp -> 14.dp
        size >= 40.dp -> 13.dp
        else -> 12.dp
    }
    if (darkFill) {
        Box(Modifier.size(size).clip(RoundedCornerShape(radius)).background(Color.White.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(icon, null, Modifier.size(symbolSize), Color.White)
        }
    } else {
        IumrahIconBadge(icon, tint, size = size, symbolSize = symbolSize, cornerRadius = radius)
    }
}

@Composable private fun AccountField(value:String,onChange:(String)->Unit,label:String,keyboard:KeyboardType=KeyboardType.Text,password:Boolean=false){OutlinedTextField(value,onChange,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(16.dp),keyboardOptions=KeyboardOptions(keyboardType=keyboard),visualTransformation=if(password)PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None)}
@Composable
private fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f)).padding(3.dp)) {
        labels.forEachIndexed { index, title ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .background(if (index == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, fontSize = 12.sp, fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Medium, color = if (index == selected) MaterialTheme.colorScheme.onSurface else IosGray)
            }
        }
    }
}
private fun languageTitle(l:AppLanguage)=when(l){AppLanguage.ENGLISH->"English";AppLanguage.RUSSIAN->"Русский";AppLanguage.UZBEK->"O‘zbekcha";AppLanguage.UZBEK_CYRILLIC->"Ўзбекча"}
private fun appearanceTitle(a:AppAppearance,l:AppLanguage)=when(a){AppAppearance.SYSTEM->tr(l,"System","Системная","Tizim","Тизим");AppAppearance.LIGHT->tr(l,"Light","Светлая","Yorug‘","Ёруғ");AppAppearance.DARK->tr(l,"Dark","Тёмная","Qorong‘i","Қоронғи")}

