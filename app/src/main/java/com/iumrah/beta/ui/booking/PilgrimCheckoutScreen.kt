package com.iumrah.beta.ui.booking

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.iumrah.beta.core.media.AndroidImageCodec
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountService
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.account.IumrahCheckoutResponse
import com.iumrah.beta.models.account.IumrahTravelerForm
import com.iumrah.beta.models.account.IumrahTravelDocument
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahPaymentMethodsMarquee
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.media.LoopingRawVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * iOS `PilgrimCheckoutView(presentation: .screen)` is only a compatibility entry
 * point: it opens BookingDetail with the Status page preselected. Android mirrors
 * that structure instead of keeping a second unrelated Material checkout screen.
 */
@Composable
fun PilgrimCheckoutScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    accountStore: IumrahAccountStore,
    accountService: IumrahAccountService,
    chrome: AppChromeStore,
) {
    BookingDetailScreen(
        bookingID = bookingID,
        language = language,
        bookingStore = bookingStore,
        accountStore = accountStore,
        accountService = accountService,
        chrome = chrome,
        initialPage = BookingPrimaryPageAndroid.STATUS,
    )
}

private enum class CheckoutActivationMethod { IUMRAH_ID, EMAIL, SMS }

@Composable
internal fun PilgrimCheckoutEmbedded(
    bookingID: String,
    language: AppLanguage,
    session: StoredBookingSession,
    bookingStore: BookingStore,
    accountStore: IumrahAccountStore,
    accountService: IumrahAccountService,
    chrome: AppChromeStore,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountState by accountStore.state.collectAsState()
    var checkout by remember(bookingID) { mutableStateOf<IumrahCheckoutResponse?>(null) }
    var loading by remember(bookingID) { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var activationMethod by remember { mutableStateOf(CheckoutActivationMethod.IUMRAH_ID) }
    var activationContact by remember(bookingID) { mutableStateOf("") }
    var activationChallenge by remember(bookingID) { mutableStateOf<String?>(null) }
    var activationCode by remember(bookingID) { mutableStateOf("") }
    var travelerEditor by remember { mutableStateOf<IumrahTravelerForm?>(null) }

    suspend fun load(showLoader: Boolean = true) {
        if (showLoader) loading = true
        error = null
        runCatching { accountService.checkoutWithFallback(bookingID, session.accessToken, accountStore.bearerToken) }
            .onSuccess { checkout = it }
            .onFailure { error = it.message }
        loading = false
    }

    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || accountStore.bearerToken.isNullOrBlank()) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    AndroidImageCodec.jpeg(context.contentResolver, uri, 2048, 88)
                }
                accountService.uploadReceipt(bookingID, "manual_card", bytes, "image/jpeg", accountStore.bearerToken!!)
            }.onSuccess { load(showLoader = false) }
                .onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(bookingID, accountStore.bearerToken) { load() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (loading) {
            BookingCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        bookingText(language, "Загружаем защищённое оформление…", "Loading secure checkout…", "Himoyalangan sahifa yuklanmoqda…", "Ҳимояланган саҳифа юкланмоқда…"),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        checkout?.let { value ->
            val accountID = accountState.iumrahID?.filter(Char::isDigit).orEmpty()
            val checkoutID = value.iumrahID.filter(Char::isDigit)
            val accountMatchesTrip = accountID.isNotEmpty() && accountID == checkoutID
            val availability = value.status.lowercase() == "availability_check"
            val paymentPending = value.status.lowercase() == "payment_pending"
            val postPayment = value.status.lowercase() in setOf("paid", "booking_confirmed", "documents_ready", "ready_to_travel", "in_trip", "completed")

            if (!accountMatchesTrip) {
                if (value.accountActive) {
                    CheckoutLoginCard(
                        language = language,
                        value = value,
                        password = loginPassword,
                        onPassword = { loginPassword = it },
                        busy = busy,
                        onRecover = { chrome.openPasswordRecovery() },
                        onLogin = {
                            if (loginPassword.isBlank()) return@CheckoutLoginCard
                            busy = true
                            scope.launch {
                                runCatching { accountStore.login(value.iumrahID, loginPassword) }
                                    .onSuccess { load(showLoader = false) }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                    )
                } else {
                    CheckoutActivationCard(
                        language = language,
                        value = value,
                        method = activationMethod,
                        onMethod = { activationMethod = it; activationChallenge = null; activationContact = ""; activationCode = ""; error = null },
                        contact = activationContact,
                        onContact = { activationContact = it },
                        challenge = activationChallenge,
                        code = activationCode,
                        onCode = { activationCode = it.filter(Char::isDigit).take(6) },
                        password = password,
                        onPassword = { password = it },
                        confirm = confirmPassword,
                        onConfirm = { confirmPassword = it },
                        busy = busy,
                        onActivate = {
                            if (busy) return@CheckoutActivationCard
                            busy = true
                            scope.launch {
                                runCatching {
                                    when (activationMethod) {
                                        CheckoutActivationMethod.IUMRAH_ID -> {
                                            require(password.length >= 8 && password == confirmPassword)
                                            accountStore.activate(bookingID, session.accessToken, password, language.localeTag)
                                        }
                                        CheckoutActivationMethod.EMAIL -> {
                                            if (activationChallenge == null) {
                                                activationChallenge = accountStore.startActivationEmail(bookingID, session.accessToken, activationContact.trim().lowercase(), language.localeTag).challengeID
                                                null
                                            } else accountStore.confirmActivationEmail(bookingID, session.accessToken, activationChallenge!!, activationCode, language.localeTag)
                                        }
                                        CheckoutActivationMethod.SMS -> {
                                            if (activationChallenge == null) {
                                                activationChallenge = accountStore.startActivationSMS(bookingID, session.accessToken, activationContact.trim(), language.localeTag).challengeID
                                                null
                                            } else accountStore.confirmActivationSMS(bookingID, session.accessToken, activationChallenge!!, activationCode, language.localeTag)
                                        }
                                    }
                                }.onSuccess { activated -> if (activated != null) load(showLoader = false) }
                                    .onFailure { error = it.message }
                                busy = false
                            }
                        },
                    )
                }
            } else {
                TravelersCard(language, value, onEdit = { travelerEditor = it })

                if (availability) {
                    AvailabilityPaymentLockedCard(language)
                    TravelDocumentsLockedCard(language)
                    GuideTransferCheckoutCard(language, enabled = false, onClick = {})
                } else if (paymentPending) {
                    PaymentCard(
                        language = language,
                        value = value,
                        busy = busy,
                        canUpload = !accountStore.bearerToken.isNullOrBlank(),
                        onUpload = { receiptLauncher.launch("image/*") },
                        chrome = chrome,
                        session = session,
                    )
                    TravelDocumentsLockedCard(language)
                    GuideTransferCheckoutCard(language, enabled = false, onClick = {})
                } else if (postPayment) {
                    if (value.receipts.isNotEmpty()) PaidReceiptCard(language, value)
                    DocumentsCard(language, value)
                    val guideReady = session.guide != null || value.status.lowercase() in setOf("booking_confirmed", "documents_ready", "ready_to_travel", "in_trip", "completed")
                    GuideTransferCheckoutCard(language, enabled = guideReady, onClick = { chrome.openBookingGuideTransfer(bookingID) })
                } else {
                    PaymentCard(
                        language = language,
                        value = value,
                        busy = busy,
                        canUpload = !accountStore.bearerToken.isNullOrBlank(),
                        onUpload = { receiptLauncher.launch("image/*") },
                        chrome = chrome,
                        session = session,
                    )
                    TravelDocumentsLockedCard(language)
                    GuideTransferCheckoutCard(language, enabled = false, onClick = {})
                }
            }
        }

        error?.takeIf { it.isNotBlank() }?.let {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 17.sp)
            }
        }
    }

    travelerEditor?.let { traveler ->
        val token = accountStore.bearerToken
        if (!token.isNullOrBlank()) {
            TravelerEditorSheet(
                bookingID = bookingID,
                traveler = traveler,
                language = language,
                accountService = accountService,
                token = token,
                onDismiss = { travelerEditor = null },
                onSaved = {
                    scope.launch { load(showLoader = false) }
                },
            )
        } else {
            travelerEditor = null
        }
    }
}

@Composable
private fun CheckoutActivationCard(
    language: AppLanguage,
    value: IumrahCheckoutResponse,
    method: CheckoutActivationMethod,
    onMethod: (CheckoutActivationMethod) -> Unit,
    contact: String,
    onContact: (String) -> Unit,
    challenge: String?,
    code: String,
    onCode: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    confirm: String,
    onConfirm: (String) -> Unit,
    busy: Boolean,
    onActivate: () -> Unit,
) {
    BookingCard {
        StageHeader("01", CupertinoSymbol.Key, bookingText(language, "Активируйте аккаунт iumrah", "Activate your iumrah account", "iumrah akkauntingizni faollashtiring", "iumrah аккаунтингизни фаоллаштиринг"))
        Spacer(Modifier.height(14.dp))
        Text(
            bookingText(language, "Ваш iumrah ID уже создан для этой поездки. Создайте пароль, чтобы защищённо продолжить оформление.", "Your iumrah ID is already created for this trip. Create a password to continue securely.", "Bu safar uchun iumrah ID allaqachon yaratilgan. Davom etish uchun parol yarating.", "Бу сафар учун iumrah ID аллақачон яратилган. Давом этиш учун парол яратинг."),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(14.dp))
        BookingSegmentedControl(
            items = listOf(
                CheckoutActivationMethod.IUMRAH_ID to "iumrah ID",
                CheckoutActivationMethod.EMAIL to "Email",
                CheckoutActivationMethod.SMS to "SMS",
            ),
            selected = method,
            onSelect = onMethod,
        )
        Spacer(Modifier.height(14.dp))

        if (method == CheckoutActivationMethod.IUMRAH_ID) {
            CheckoutIdentityRow("iumrah ID", value.iumrahID)
            Spacer(Modifier.height(10.dp))
            BookingPasswordField(
                bookingText(language, "Создайте пароль", "Create password", "Parol yarating", "Парол яратинг"),
                password,
                onPassword,
            )
            Spacer(Modifier.height(10.dp))
            BookingPasswordField(
                bookingText(language, "Подтвердите пароль", "Confirm password", "Parolni tasdiqlang", "Паролни тасдиқланг"),
                confirm,
                onConfirm,
            )
            Spacer(Modifier.height(10.dp))
            RequirementRow(bookingText(language, "Минимум 8 символов", "At least 8 characters", "Kamida 8 belgi", "Камида 8 белги"), password.length >= 8)
            Spacer(Modifier.height(5.dp))
            RequirementRow(bookingText(language, "Пароли совпадают", "Passwords match", "Parollar mos", "Пароллар мос"), confirm.isNotEmpty() && confirm == password)
            Spacer(Modifier.height(15.dp))
            BookingPrimaryAction(
                bookingText(language, if (busy) "Активируем…" else "Активировать аккаунт", if (busy) "Activating…" else "Activate account", if (busy) "Faollashtirilmoqda…" else "Akkauntni faollashtirish", if (busy) "Фаоллаштирилмоқда…" else "Аккаунтни фаоллаштириш"),
                onClick = onActivate,
                trailing = CupertinoSymbol.ArrowRight,
            )
        } else {
            BookingRaisedCard {
                Text(
                    bookingText(language, "Подтвердите контакт, чтобы активировать iumrah ID для этой поездки.", "Verify your contact to activate this trip's iumrah ID.", "Safar iumrah ID sini faollashtirish uchun kontaktingizni tasdiqlang.", "Сафар iumrah ID сини фаоллаштириш учун контактингизни тасдиқланг."),
                    fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f),
                )
                Spacer(Modifier.height(12.dp))
                if (challenge == null) {
                    OutlinedTextField(
                        value = contact,
                        onValueChange = onContact,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (method == CheckoutActivationMethod.EMAIL) "Email" else "+998") },
                        keyboardOptions = KeyboardOptions(keyboardType = if (method == CheckoutActivationMethod.EMAIL) KeyboardType.Email else KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                } else {
                    Text(contact, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = code,
                        onValueChange = onCode,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(bookingText(language, "Шестизначный код", "Six-digit code", "Olti xonali kod", "Олти хонали код")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onActivate,
                    enabled = !busy && if (challenge == null) {
                        if (method == CheckoutActivationMethod.EMAIL) contact.contains('@') else contact.trim().startsWith("+998")
                    } else code.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    Text(if (challenge == null) bookingText(language, "Отправить код", "Send code", "Kod yuborish", "Код юбориш") else bookingText(language, "Подтвердить и активировать", "Verify & activate", "Tasdiqlash va faollashtirish", "Тасдиқлаш ва фаоллаштириш"))
                }
            }
        }
    }
}

@Composable
private fun CheckoutLoginCard(
    language: AppLanguage,
    value: IumrahCheckoutResponse,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    onRecover: () -> Unit,
    onLogin: () -> Unit,
) {
    BookingCard {
        StageHeader("01", CupertinoSymbol.LockShield, bookingText(language, "Войдите в iumrah", "Sign in to iumrah", "iumrah'ga kiring", "iumrah'га киринг"))
        Spacer(Modifier.height(14.dp))
        Text(
            bookingText(language, "Этот iumrah ID уже активирован. Войдите, чтобы открыть данные паломников, оплату и документы.", "This iumrah ID is already active. Sign in to open pilgrim details, payment and documents.", "Bu iumrah ID allaqachon faol. Ma’lumotlar, to‘lov va hujjatlarni ochish uchun kiring.", "Бу iumrah ID аллақачон фаол. Маълумотлар, тўлов ва ҳужжатларни очиш учун киринг."),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(14.dp))
        CheckoutIdentityRow("iumrah ID", value.iumrahID)
        Spacer(Modifier.height(10.dp))
        BookingPasswordField(bookingText(language, "Пароль", "Password", "Parol", "Парол"), password, onPassword)
        Spacer(Modifier.height(15.dp))
        BookingPrimaryAction(
            bookingText(language, if (busy) "Входим…" else "Войти", if (busy) "Signing in…" else "Sign in", if (busy) "Kirilmoqda…" else "Kirish", if (busy) "Кирилмоқда…" else "Кириш"),
            onClick = onLogin,
            trailing = CupertinoSymbol.ArrowRight,
        )
        TextButton(onClick = onRecover, modifier = Modifier.fillMaxWidth()) {
            Text(bookingText(language, "Забыли пароль?", "Forgot password?", "Parolni unutdingizmi?", "Паролни унутдингизми?"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CheckoutIdentityRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).background(bookingIosRaised(), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        CupertinoIcon(CupertinoSymbol.IdentityCard, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .58f))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
            Text(value, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BookingPasswordField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        placeholder = { Text(label, fontSize = 14.sp) },
        leadingIcon = { CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
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
private fun RequirementRow(title: String, ready: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CupertinoIcon(if (ready) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.CheckCircle, null, Modifier.size(14.dp), if (ready) BookingCareDark else MaterialTheme.colorScheme.onBackground.copy(alpha = .44f))
        Text(title, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, color = if (ready) BookingCareDark else MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
    }
}

@Composable
private fun TravelersCard(language: AppLanguage, value: IumrahCheckoutResponse, onEdit: (IumrahTravelerForm) -> Unit) {
    val attached = value.travelers.count { it.hasPassport }
    val total = value.travelers.size
    val ready = total > 0 && attached == total
    BookingCard {
        StageHeader(
            "01",
            if (ready) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Passport,
            bookingText(language, "Прикрепить паспорта", "Attach passports", "Pasportlarni biriktirish", "Паспортларни бириктириш"),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (ready)
                bookingText(language, "Паспорта всех паломников прикреплены.", "Every pilgrim passport is attached.", "Barcha ziyoratchilar pasporti biriktirilgan.", "Барча зиёратчилар паспорти бириктирилган.")
            else bookingText(language, "Достаточно чёткой фотографии страницы паспорта с данными. Ручное заполнение — по желанию.", "A clear photo of the passport information page is enough. Manual details are optional.", "Pasport ma’lumotlar sahifasining aniq rasmi yetarli. Qo‘lda to‘ldirish ixtiyoriy.", "Паспорт маълумотлар саҳифасининг аниқ расми етарли. Қўлда тўлдириш ихтиёрий."),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(15.dp))
        value.travelers.sortedBy { it.position }.forEachIndexed { index, traveler ->
            TravelerRow(language, traveler, onEdit)
            if (index != value.travelers.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TravelerRow(language: AppLanguage, traveler: IumrahTravelerForm, onEdit: (IumrahTravelerForm) -> Unit) {
    IumrahPressable(
        onClick = { onEdit(traveler) },
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        background = Color.Transparent,
        pressedScale = .985f,
    ) {
        Row(
            Modifier.fillMaxWidth().background(bookingIosRaised().copy(alpha = .72f), RoundedCornerShape(20.dp)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            BookingIconBadge(
                symbol = if (traveler.hasPassport) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Passport,
                tint = if (traveler.hasPassport) Color(0xFF34C759) else Color(0xFF007AFF),
                size = 48.dp,
                symbolSize = 19.dp,
                radius = 16.dp,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(travelerDisplayName(language, traveler), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (traveler.hasPassport) bookingText(language, "Паспорт прикреплён", "Passport attached", "Pasport biriktirilgan", "Паспорт бириктирилган")
                    else bookingText(language, "Прикрепите фото паспорта", "Attach passport photo", "Pasport rasmini biriktiring", "Паспорт расмини бириктиринг"),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    color = if (traveler.hasPassport) Color(0xFF34C759) else MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
                )
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
        }
    }
}

private fun travelerDisplayName(language: AppLanguage, traveler: IumrahTravelerForm): String =
    listOf(traveler.firstName, traveler.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
        bookingText(language, "Паломник ${traveler.position}", "Pilgrim ${traveler.position}", "Ziyoratchi ${traveler.position}", "Зиёратчи ${traveler.position}")
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelerEditorSheet(
    bookingID: String,
    traveler: IumrahTravelerForm,
    language: AppLanguage,
    accountService: IumrahAccountService,
    token: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember(traveler) { mutableStateOf(traveler) }
    var busyUpload by remember { mutableStateOf(false) }
    var busyManual by remember { mutableStateOf(false) }
    var showManual by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var passportUri by remember { mutableStateOf<Uri?>(null) }
    var passportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var uploadedInSession by remember { mutableStateOf(false) }
    val passportReady = traveler.hasPassport || uploadedInSession

    val passportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        passportUri = uri
        passportBytes = null
        error = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { AndroidImageCodec.jpeg(context.contentResolver, uri, 2048, 90) } }
                .onSuccess { passportBytes = it }
                .onFailure { error = it.message }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bookingIosPage(), dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = BOOKING_PAGE_PADDING.dp).padding(top = 2.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CupertinoIcon(CupertinoSymbol.Passport, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                        Text(bookingText(language, "Паспорт", "Passport", "Pasport", "Паспорт"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                        if (passportReady) {
                            Spacer(Modifier.weight(1f))
                            CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(14.dp), Color(0xFF34C759))
                            Text(bookingText(language, "Прикреплён", "Attached", "Biriktirilgan", "Бириктирилган"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF34C759))
                        }
                    }
                    Text(bookingText(language, "Прикрепите паспорт", "Attach passport", "Pasportni biriktiring", "Паспортни бириктиринг"), fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
                    Text(
                        bookingText(language, "Достаточно чёткой фотографии страницы с данными. Заполнять данные вручную ниже не обязательно.", "A clear photo of the information page is enough. Manual entry below is optional.", "Ma’lumotlar sahifasining aniq rasmi yetarli. Quyidagi ma’lumotlarni qo‘lda kiritish shart emas.", "Маълумотлар саҳифасининг аниқ расми етарли. Қуйидаги маълумотларни қўлда киритиш шарт эмас."),
                        fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                IumrahPressable(onClick = onDismiss, modifier = Modifier.size(38.dp), cornerRadius = 19.dp, background = bookingIosRaised(), pressedScale = .94f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LoopingRawVideo(
                    resourceName = "iumrah_security_identity",
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)),
                    muted = true,
                )
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    CupertinoIcon(CupertinoSymbol.ShieldCheck, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
                    Text(
                        bookingText(language, "Паспортные данные защищены и используются только для оформления поездки.", "Passport data is handled securely for your booking.", "Pasport ma’lumotlari himoyalangan va faqat safarni rasmiylashtirish uchun ishlatiladi.", "Паспорт маълумотлари ҳимояланган ва фақат сафарни расмийлаштириш учун ишлатилади."),
                        Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                    )
                }
            }

            BookingCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BookingIconBadge(if (passportReady) CupertinoSymbol.CheckCircleFill else CupertinoSymbol.Passport, if (passportReady) Color(0xFF34C759) else Color(0xFF007AFF), 50.dp, 20.dp, 17.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bookingText(language, "Страница паспорта с данными", "Passport information page", "Pasport ma’lumotlar sahifasi", "Паспорт маълумотлар саҳифаси"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            bookingText(language, "В кадре должны полностью быть видны фотография владельца, номер паспорта, имя и даты.", "The holder photo, passport number, name and dates must all fit in the frame.", "Kadrda egasining rasmi, pasport raqami, ism va sanalar to‘liq ko‘rinsin.", "Кадрда эгасининг расми, паспорт рақами, исм ва саналар тўлиқ кўринсин."),
                            fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                        )
                    }
                }

                if (passportUri != null) {
                    Spacer(Modifier.height(15.dp))
                    Box {
                        AsyncImage(
                            model = passportUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp, max = 300.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .035f)),
                            contentScale = ContentScale.Fit,
                        )
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black.copy(alpha = .62f)).padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            CupertinoIcon(CupertinoSymbol.Eye, null, Modifier.size(14.dp), Color.White)
                            Text(bookingText(language, "Если фото нечёткое — выберите другое", "If the photo is blurry, choose another", "Rasm xira bo‘lsa, boshqasini tanlang", "Расм хира бўлса, бошқасини танланг"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                } else if (passportReady) {
                    Spacer(Modifier.height(15.dp))
                    Row(Modifier.fillMaxWidth().background(Color(0xFF34C759).copy(alpha = .08f), RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CupertinoIcon(CupertinoSymbol.CheckCircleFill, null, Modifier.size(24.dp), Color(0xFF34C759))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(bookingText(language, "Паспорт получен", "Passport received", "Pasport qabul qilindi", "Паспорт қабул қилинди"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(bookingText(language, "При необходимости можно заменить его более чёткой фотографией.", "You can replace it if you want to send a clearer photo.", "Kerak bo‘lsa, aniqroq rasm bilan almashtirishingiz mumkin.", "Керак бўлса, аниқроқ расм билан алмаштиришингиз мумкин."), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                        }
                    }
                }

                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth().background(Color(0xFFFF9500).copy(alpha = .10f), RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(17.dp), Color(0xFFFF9500))
                    Text(
                        bookingText(language, "Обратите внимание, чтобы все данные паспорта были видны и читались. Они используются для покупки авиабилета, бронирования отеля и оформления документов бронирования.", "Make sure every passport field is visible and readable. These details are used to issue airline tickets, book hotels and prepare the booking documents.", "Pasportdagi barcha ma’lumotlar ko‘rinsin va o‘qilsin. Ular aviachipta, mehmonxona va bron hujjatlarini rasmiylashtirish uchun ishlatiladi.", "Паспортдаги барча маълумотлар кўринсин ва ўқилсин. Улар авиачипта, меҳмонхона ва брон ҳужжатларини расмийлаштириш учун ишлатилади."),
                        Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF9500),
                    )
                }

                Spacer(Modifier.height(15.dp))
                BookingSecondaryAction(
                    title = when {
                        passportUri != null -> bookingText(language, "Выбрать другое фото", "Choose another photo", "Boshqa rasm tanlash", "Бошқа расм танлаш")
                        passportReady -> bookingText(language, "Заменить фото паспорта", "Replace passport photo", "Pasport rasmini almashtirish", "Паспорт расмини алмаштириш")
                        else -> bookingText(language, "Выбрать фото паспорта", "Choose passport photo", "Pasport rasmini tanlash", "Паспорт расмини танлаш")
                    },
                    onClick = { passportLauncher.launch("image/*") },
                    trailing = CupertinoSymbol.ChevronRight,
                )

                if (passportBytes != null) {
                    Spacer(Modifier.height(10.dp))
                    BookingPrimaryAction(
                        title = if (busyUpload) bookingText(language, "Прикрепляем…", "Attaching…", "Biriktirilmoqda…", "Бириктирилмоқда…") else bookingText(language, "Прикрепить паспорт", "Attach passport", "Pasportni biriktirish", "Паспортни бириктириш"),
                        onClick = {
                            val bytes = passportBytes ?: return@BookingPrimaryAction
                            if (busyUpload) return@BookingPrimaryAction
                            busyUpload = true; error = null
                            scope.launch {
                                runCatching { accountService.uploadPassport(bookingID, traveler.position, bytes, "image/jpeg", token) }
                                    .onSuccess {
                                        uploadedInSession = true
                                        passportBytes = null
                                        passportUri = null
                                        onSaved()
                                    }
                                    .onFailure { error = it.message }
                                busyUpload = false
                            }
                        },
                        leading = CupertinoSymbol.Document,
                    )
                }
            }

            BookingCard {
                IumrahPressable(
                    onClick = { showManual = !showManual },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    background = Color.Transparent,
                    pressedScale = .985f,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BookingIconBadge(CupertinoSymbol.Pencil, Color(0xFF5856D6), 46.dp, 18.dp, 15.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(bookingText(language, "Заполнить вручную", "Fill in manually", "Qo‘lda to‘ldirish", "Қўлда тўлдириш"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(bookingText(language, "Не обязательно", "Optional", "Ixtiyoriy", "Ихтиёрий"), Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = .08f), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                            }
                            Text(bookingText(language, "Заполнять вручную не обязательно, но эти данные ускорят оформление бронирования.", "Manual details are not required, but they help our team process the booking faster.", "Qo‘lda to‘ldirish shart emas, lekin bu ma’lumotlar bronni tezroq rasmiylashtirishga yordam beradi.", "Қўлда тўлдириш шарт эмас, лекин бу маълумотлар бронни тезроқ расмийлаштиришга ёрдам беради."), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                        }
                        CupertinoIcon(if (showManual) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
                    }
                }

                if (showManual) {
                    Spacer(Modifier.height(15.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = .08f))
                    Spacer(Modifier.height(15.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        TravelerField(bookingText(language, "Имя как в паспорте", "First name as in passport", "Pasportdagi ism", "Паспортдаги исм"), form.firstName) { form = form.copy(firstName = it) }
                        TravelerField(bookingText(language, "Фамилия как в паспорте", "Last name as in passport", "Pasportdagi familiya", "Паспортдаги фамилия"), form.lastName) { form = form.copy(lastName = it) }
                        TravelerField(bookingText(language, "Номер паспорта", "Passport number", "Pasport raqami", "Паспорт рақами"), form.passportNumber) { form = form.copy(passportNumber = it.uppercase()) }
                        TravelerField(bookingText(language, "Дата рождения · YYYY-MM-DD", "Date of birth · YYYY-MM-DD", "Tug‘ilgan sana · YYYY-MM-DD", "Туғилган сана · YYYY-MM-DD"), form.dateOfBirth) { form = form.copy(dateOfBirth = it) }
                        TravelerField(bookingText(language, "Срок действия паспорта · YYYY-MM-DD", "Passport expiry · YYYY-MM-DD", "Pasport muddati · YYYY-MM-DD", "Паспорт муддати · YYYY-MM-DD"), form.passportExpiryDate) { form = form.copy(passportExpiryDate = it) }
                        TravelerField(bookingText(language, "Гражданство", "Citizenship", "Fuqarolik", "Фуқаролик"), form.nationality) { form = form.copy(nationality = it) }
                        BookingSegmentedControl(
                            items = listOf("male" to bookingText(language, "Мужской", "Male", "Erkak", "Эркак"), "female" to bookingText(language, "Женский", "Female", "Ayol", "Аёл")),
                            selected = form.gender,
                            onSelect = { form = form.copy(gender = it) },
                        )
                        BookingPrimaryAction(
                            title = if (busyManual) bookingText(language, "Сохраняем…", "Saving…", "Saqlanmoqda…", "Сақланмоқда…") else bookingText(language, "Сохранить ручные данные", "Save manual details", "Qo‘lda kiritilganlarni saqlash", "Қўлда киритилганларни сақлаш"),
                            onClick = {
                                if (busyManual) return@BookingPrimaryAction
                                busyManual = true; error = null
                                scope.launch {
                                    val payload = form.copy(passportIssuingCountry = form.passportIssuingCountry.ifBlank { form.nationality })
                                    runCatching { accountService.saveTraveler(bookingID, traveler.position, payload, token) }
                                        .onSuccess { onSaved() }
                                        .onFailure { error = it.message }
                                    busyManual = false
                                }
                            },
                            leading = CupertinoSymbol.Checkmark,
                        )
                    }
                }
            }

            error?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                    Text(it, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, fontSize = 13.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun TravelerRelationshipSelector(language: AppLanguage, selected: String, onSelect: (String) -> Unit) {
    val options = listOf(
        "self" to bookingText(language, "Я", "Me", "Men", "Мен"),
        "spouse" to bookingText(language, "Муж / жена", "Husband / wife", "Turmush o‘rtog‘i", "Турмуш ўртоғи"),
        "mother" to bookingText(language, "Мама", "Mother", "Ona", "Она"),
        "father" to bookingText(language, "Папа", "Father", "Ota", "Ота"),
        "brother" to bookingText(language, "Брат", "Brother", "Aka / uka", "Ака / ука"),
        "sister" to bookingText(language, "Сестра", "Sister", "Opa / singil", "Опа / сингил"),
        "child" to bookingText(language, "Ребёнок", "Child", "Farzand", "Фарзанд"),
        "relative" to bookingText(language, "Родственник", "Relative", "Qarindosh", "Қариндош"),
        "friend" to bookingText(language, "Друг / подруга", "Friend", "Do‘st", "Дўст"),
        "other" to bookingText(language, "Попутчик", "Travel companion", "Hamroh", "Ҳамроҳ"),
    )
    var expanded by remember { mutableStateOf(false) }
    Box {
        IumrahPressable(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            cornerRadius = 19.dp,
            background = bookingIosRaised().copy(alpha = .72f),
            pressedScale = .985f,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CupertinoIcon(if (selected == "spouse") CupertinoSymbol.HeartFill else if (selected == "self") CupertinoSymbol.PersonCircle else CupertinoSymbol.Persons, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(bookingText(language, "Кто едет?", "Who is traveling?", "Kim bormoqda?", "Ким бормоқда?"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
                    Text(options.firstOrNull { it.first == selected }?.second ?: options.last().second, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                CupertinoIcon(CupertinoSymbol.ChevronDown, null, Modifier.size(12.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .32f))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = { onSelect(value); expanded = false },
                    leadingIcon = { if (value == selected) CupertinoIcon(CupertinoSymbol.Checkmark, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onBackground) },
                )
            }
        }
    }
}

@Composable
private fun TravelerFormSection(symbol: CupertinoSymbol, title: String, content: @Composable ColumnScope.() -> Unit) {
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BookingIconBadge(symbol, Color(0xFF5856D6), 38.dp, 16.dp, 13.dp)
            Text(title, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun TravelerField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        placeholder = { Text(label, fontSize=14.sp, color=MaterialTheme.colorScheme.onBackground.copy(alpha=.46f)) },
        singleLine = true,
        shape = RoundedCornerShape(19.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = bookingIosRaised().copy(alpha=.72f), unfocusedContainerColor = bookingIosRaised().copy(alpha=.72f),
            focusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.10f), unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha=.055f),
        ),
    )
}

@Composable
private fun AvailabilityPaymentLockedCard(language: AppLanguage) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(bookingIosCard(), RoundedCornerShape(22.dp))
            .border(.7.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = .06f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            BookingIconBadge(CupertinoSymbol.CalendarClock, Color(0xFFFF9500), 44.dp, 17.dp, 15.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bookingText(language, "Оплата откроется после подтверждения", "Payment opens after confirmation", "To‘lov tasdiqdan keyin ochiladi", "Тўлов тасдиқдан кейин очилади"), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    bookingText(language, "Сейчас прикрепите страницу паспорта с данными для каждого паломника. Инвойс и реквизиты оплаты появятся после подтверждения наличия.", "Attach the passport information page for every pilgrim now. Invoice and payment details stay hidden until availability is confirmed.", "Hozir har bir ziyoratchining pasport ma’lumotlar sahifasini biriktiring. Invoice va to‘lov rekvizitlari mavjudlik tasdiqlangach ochiladi.", "Ҳозир ҳар бир зиёратчининг паспорт маълумотлар саҳифасини бириктиринг. Invoice ва тўлов реквизитлари мавжудлик тасдиқлангач очилади."),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }
        }
    }
}

@Composable
private fun TravelDocumentsLockedCard(language: AppLanguage) {
    Row(
        Modifier.fillMaxWidth().background(bookingIosCard(), RoundedCornerShape(22.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BookingIconBadge(CupertinoSymbol.Lock, Color(0xFF007AFF), 44.dp, 17.dp, 15.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(bookingText(language, "Документы поездки", "Travel documents", "Safar hujjatlari", "Сафар ҳужжатлари"), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                bookingText(language, "Авиабилеты, виза и номера бронирований будут доступны после оплаты и подтверждения бронирования.", "Airline tickets, visa and booking references become available after payment and booking confirmation.", "Aviachiptalar, viza va bron raqamlari to‘lov hamda bron tasdiqlangach ochiladi.", "Авиачипталар, виза ва брон рақамлари тўлов ҳамда брон тасдиқлангач очилади."),
                fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
            )
        }
    }
}

@Composable
private fun GuideTransferCheckoutCard(language: AppLanguage, enabled: Boolean, onClick: () -> Unit) {
    IumrahPressable(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        background = bookingIosCard(),
        pressedScale = if (enabled) .985f else 1f,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            BookingIconBadge(if (enabled) CupertinoSymbol.ShieldCheck else CupertinoSymbol.Lock, Color(0xFF5856D6), 46.dp, 18.dp, 15.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bookingText(language, "Гид и трансфер", "Guide & transfer", "Gid va transfer", "Гид ва трансфер"), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (enabled)
                        bookingText(language, "Контакты, данные трансфера и фото для встречи в аэропорту.", "Contacts, transfer details and airport meeting photo.", "Kontaktlar, transfer va aeroportdagi uchrashuv rasmi.", "Контактлар, трансфер ва аэропортдаги учрашув расми.")
                    else bookingText(language, "Откроется после подтверждения бронирования и назначения команды.", "Opens after booking confirmation and team assignment.", "Bron tasdiqlanib, jamoa tayinlangach ochiladi.", "Брон тасдиқланиб, жамоа тайинлангач очилади."),
                    fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }
            if (enabled) CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .30f))
        }
    }
}

private fun travelerRelationshipTitle(language: AppLanguage, value: String?, position: Int): String = when (value?.lowercase()) {
    "self" -> bookingText(language, "Вы", "You", "Siz", "Сиз")
    "spouse" -> bookingText(language, "Муж или жена", "Spouse", "Turmush o‘rtog‘i", "Турмуш ўртоғи")
    "mother" -> bookingText(language, "Мама", "Mother", "Ona", "Она")
    "father" -> bookingText(language, "Папа", "Father", "Ota", "Ота")
    "brother" -> bookingText(language, "Брат", "Brother", "Aka yoki uka", "Ака ёки ука")
    "sister" -> bookingText(language, "Сестра", "Sister", "Opa yoki singil", "Опа ёки сингил")
    "child" -> bookingText(language, "Ребёнок", "Child", "Farzand", "Фарзанд")
    "relative" -> bookingText(language, "Родственник", "Relative", "Qarindosh", "Қариндош")
    "friend" -> bookingText(language, "Друг или подруга", "Friend", "Do‘st", "Дўст")
    else -> if (position == 1) bookingText(language, "Вы", "You", "Siz", "Сиз") else bookingText(language, "Участник поездки", "Traveler", "Sayohatchi", "Саёҳатчи")
}

@Composable
private fun FriendsBenefitUnavailableCard(language: AppLanguage) {
    Column(
        Modifier.fillMaxWidth().background(bookingIosRaised().copy(alpha = .45f), RoundedCornerShape(22.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BookingIconBadge(CupertinoSymbol.Wallet, Color(0xFFFF2D55), 42.dp, 17.dp, 15.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Iumrah Gift Cards", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(bookingText(language, "Gift Card и Iumrah Balance", "Gift Card & Iumrah Balance", "Gift Card va Iumrah Balance", "Gift Card ва Iumrah Balance"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
            CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .45f))
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFF9500).copy(alpha = .08f), RoundedCornerShape(17.dp)).border(.8.dp, Color(0xFFFF9500).copy(alpha = .18f), RoundedCornerShape(17.dp)).padding(13.dp),
            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            CupertinoIcon(CupertinoSymbol.ExclamationCircle, null, Modifier.size(17.dp), Color(0xFFFF9500))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bookingText(language, "Gift Card ещё недоступна", "Gift Cards are not available yet", "Gift Card hali mavjud emas", "Gift Card ҳали мавжуд эмас"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    bookingText(language, "Раздел уже виден в бронировании, но применение Gift Card и Iumrah Balance временно отключено. Мы включим его после готовности платёжного сценария.", "The section is already visible in the booking, but applying Gift Cards and Iumrah Balance is temporarily disabled. We will enable it after the payment flow is ready.", "Bo‘lim bron ichida ko‘rinadi, ammo Gift Card va Iumrah Balance qo‘llash vaqtincha o‘chirilgan. To‘lov jarayoni tayyor bo‘lgach yoqiladi.", "Бўлим брон ичида кўринади, аммо Gift Card ва Iumrah Balance қўллаш вақтинча ўчирилган. Тўлов жараёни тайёр бўлгач ёқилади."),
                    fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                )
            }
        }
    }
}

@Composable
private fun PaymentCard(
    language: AppLanguage,
    value: IumrahCheckoutResponse,
    busy: Boolean,
    canUpload: Boolean,
    onUpload: () -> Unit,
    chrome: AppChromeStore,
    session: StoredBookingSession,
) {
    BookingCard {
        StageHeader("02", CupertinoSymbol.CreditCard, bookingText(language, "Оплата", "Payment", "To‘lov", "Тўлов"))
        Spacer(Modifier.height(14.dp))
        BookingManualPaymentNotice(language)
        Spacer(Modifier.height(10.dp))
        BookingRefundPolicyCompact(language)
        Spacer(Modifier.height(10.dp))
        BookingInvoiceCompact(session, language)
        Spacer(Modifier.height(10.dp))
        FriendsBenefitUnavailableCard(language)
        Spacer(Modifier.height(10.dp))
        IumrahPaymentMethodsMarquee(modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        BookingRaisedCard(padding = 15.dp, radius = 20.dp) {
            Text("Visa", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
            Spacer(Modifier.height(5.dp))
            Text(groupedCard(value.payment.visaCardNumber), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (value.payment.visaHolder.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(value.payment.visaHolder, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
        if (value.payment.humoCardNumber.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            BookingRaisedCard(padding = 15.dp, radius = 20.dp) {
                Text("Humo", fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
                Spacer(Modifier.height(5.dp))
                Text(groupedCard(value.payment.humoCardNumber), fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value.payment.instructions.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(value.payment.instructions, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f))
        }
        Spacer(Modifier.height(14.dp))
        BookingPrimaryAction(
            bookingText(language, if (busy) "Загружаем…" else "Прикрепить чек оплаты", if (busy) "Uploading…" else "Attach payment receipt", if (busy) "Yuklanmoqda…" else "To‘lov chekini biriktirish", if (busy) "Юкланмоқда…" else "Тўлов чекини бириктириш"),
            onClick = onUpload,
            leading = CupertinoSymbol.Paperclip,
        )
        if (!canUpload) {
            Spacer(Modifier.height(9.dp))
            Text(
                bookingText(language, "Войдите в аккаунт iumrah, чтобы загрузить чек.", "Sign in to your iumrah account to upload the receipt.", "Chekni yuklash uchun iumrah akkauntiga kiring.", "Чекни юклаш учун iumrah аккаунтига киринг."),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            )
        }
    }
}

@Composable
private fun PaidReceiptCard(language: AppLanguage, value: IumrahCheckoutResponse) {
    val receipt = value.receipts.firstOrNull()
    BookingCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            BookingIconBadge(CupertinoSymbol.CheckCircleFill, Color(0xFF34C759), 48.dp, 18.dp, 16.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bookingText(language, "Оплата получена", "Payment received", "To‘lov qabul qilindi", "Тўлов қабул қилинди"), fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(receipt?.reviewStatus ?: value.status, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f))
            }
        }
    }
}

@Composable
private fun DocumentsCard(language: AppLanguage, value: IumrahCheckoutResponse) {
    BookingCard {
        StageHeader("03", CupertinoSymbol.Document, bookingText(language, "Документы поездки", "Travel documents", "Safar hujjatlari", "Сафар ҳужжатлари"))
        Spacer(Modifier.height(12.dp))
        Text(
            bookingText(language, "Каждый документ появится здесь отдельно сразу после готовности.", "Each document appears here as soon as it is ready.", "Har bir hujjat tayyor bo‘lishi bilan shu yerda alohida paydo bo‘ladi.", "Ҳар бир ҳужжат тайёр бўлиши билан шу ерда алоҳида пайдо бўлади."),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(14.dp))
        val rows = listOf(
            Triple("ticket", bookingText(language, "Авиабилет", "Airline ticket", "Aviachipta", "Авиачипта"), CupertinoSymbol.Airplane),
            Triple("voucher", bookingText(language, "Подтверждение отеля", "Hotel confirmation", "Mehmonxona tasdig‘i", "Меҳмонхона тасдиғи"), CupertinoSymbol.Hotel),
            Triple("visa", bookingText(language, "Виза", "Visa", "Viza", "Виза"), CupertinoSymbol.CheckCircleFill),
            Triple("insurance", bookingText(language, "Страховка", "Insurance", "Sug‘urta", "Суғурта"), CupertinoSymbol.ShieldCheck),
        )
        rows.forEachIndexed { index, (kind, title, symbol) ->
            DocumentStatusRow(language, title, symbol, value.documents.firstOrNull { it.documentKind.lowercase() == kind })
            if (index != rows.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun DocumentStatusRow(language: AppLanguage, title: String, symbol: CupertinoSymbol, document: IumrahTravelDocument?) {
    Row(
        Modifier.fillMaxWidth().background(bookingIosRaised(), RoundedCornerShape(20.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        BookingIconBadge(symbol, if (document != null) Color(0xFF34C759) else Color(0xFF007AFF), 48.dp, 18.dp, 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (document != null) bookingText(language, "Готов · можно открыть", "Ready to open", "Tayyor · ochish mumkin", "Тайёр · очиш мумкин")
                else bookingText(language, "Готовится", "Being prepared", "Tayyorlanmoqda", "Тайёрланмоқда"),
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .52f),
            )
        }
        if (document != null) CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onBackground.copy(alpha = .32f))
        else CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
    }
}

@Composable
private fun StageHeader(number: String, icon: CupertinoSymbol, title: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
        BookingIconBadge(icon, Color(0xFF5856D6), 38.dp, 15.dp, 12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(number, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .50f))
            Text(title, fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun groupedCard(raw: String): String {
    val value = raw.filterNot(Char::isWhitespace)
    return if (value.isBlank()) "—" else value.chunked(4).joinToString(" ")
}
