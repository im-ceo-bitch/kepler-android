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
import kotlinx.coroutines.CancellationException
import com.iumrah.beta.domain.booking.BookingRewardsPolicy
import com.iumrah.beta.domain.booking.BookingSessionPolicy
import com.iumrah.beta.core.network.APIException
import com.iumrah.beta.models.account.IumrahAccountAuthResponse
import com.iumrah.beta.models.account.IumrahFriendsBookingSummary
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
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
    val ownerToken = accountStore.bearerToken
    val bookingService = (context.applicationContext as com.iumrah.beta.IumrahApplication).container.bookingService
    var checkout by remember(bookingID, ownerToken) { mutableStateOf<IumrahCheckoutResponse?>(null) }
    var rewardSummary by remember(bookingID, ownerToken) { mutableStateOf<IumrahFriendsBookingSummary?>(null) }
    var rewardUncertain by remember(bookingID, ownerToken) { mutableStateOf(false) }
    var creditConfirmation by remember(bookingID, ownerToken) { mutableStateOf<Int?>(null) }
    var loading by remember(bookingID, ownerToken) { mutableStateOf(true) }
    val rewardMatch = !loading && BookingRewardsPolicy.confirmed(checkout?.rewards, rewardSummary) && !rewardUncertain
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var travelerEditor by remember(bookingID, ownerToken) { mutableStateOf<IumrahTravelerForm?>(null) }

    suspend fun load(showLoader: Boolean = true) {
        loading = true
        error = null
        val token = accountStore.bearerToken
        try {
            val matchingToken = token.takeIf { BookingSessionPolicy.sameIdentity(session.pilgrimID ?: session.accountOwnerID, accountStore.state.value.iumrahID) }
            val response = accountService.checkoutWithFallback(bookingID, session.accessToken, matchingToken)
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (token != accountStore.bearerToken) return
            // Do not keep a previous payment quote when the reward lookup fails.
            rewardSummary = null
            rewardUncertain = false
            checkout = response
            val idMatches = BookingSessionPolicy.sameIdentity(accountStore.state.value.iumrahID, response.iumrahID)
            if (token != null && idMatches && response.status.equals("payment_pending", true)) {
                val summary = try { bookingService.friendsSummary(bookingID, accountStore.authorizationHeaders(session.accessToken)) }
                    catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null }
                if (token != accountStore.bearerToken) return
                if (summary?.ok == true && summary.bookingID == bookingID) {
                    rewardSummary = summary
                    rewardUncertain = !BookingRewardsPolicy.confirmed(response.rewards, summary)
                }
            } else rewardSummary = null
        } catch (cancel: CancellationException) { throw cancel }
        catch (ex: Exception) { if (token == accountStore.bearerToken) error = ex.message }
        finally { if (token == accountStore.bearerToken) loading = false }
    }

    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val token = accountStore.bearerToken ?: return@rememberLauncherForActivityResult
        if (uri == null || busy || loading || !rewardMatch || checkout?.status?.equals("payment_pending", true) != true || !BookingSessionPolicy.sameIdentity(accountStore.state.value.iumrahID, checkout?.iumrahID)) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    AndroidImageCodec.jpeg(context.contentResolver, uri, 2048, 88)
                }
                check(accountStore.bearerToken == token) { "Account changed" }
                val fresh = accountService.checkoutWithFallback(bookingID, session.accessToken, token)
                check(BookingSessionPolicy.sameIdentity(accountStore.state.value.iumrahID, fresh.iumrahID) && fresh.status.equals("payment_pending", true) && fresh.rewards == checkout?.rewards && BookingRewardsPolicy.confirmed(fresh.rewards, rewardSummary)) { "Refresh payment amounts" }
                check(accountStore.bearerToken == token) { "Account changed" }
                accountService.uploadReceipt(bookingID, "manual_card", bytes, "image/jpeg", token)
            }.onSuccess { load(showLoader = false) }
                .onFailure { error = it.message }
            busy = false
        }
    }

    LaunchedEffect(bookingID, ownerToken, session.effectiveStatus) { load() }
    creditConfirmation?.let { amount ->
        AlertDialog(onDismissRequest = { creditConfirmation = null },
            title = { Text(bookingText(language,"Списать бонусы в счёт этой поездки?","Apply rewards to this booking?","Bonuslarni ushbu bronga qo‘llaysizmi?","Бонусларни ушбу бронга қўллайсизми?")) },
            text = { Text("\$$amount") },
            confirmButton = { TextButton(onClick = {
                creditConfirmation = null
                val token = accountStore.bearerToken
                val current = checkout
                if (!busy && !loading && !rewardUncertain && token != null && current != null && amount in BookingRewardsPolicy.amounts(current.status,current.receipts.isEmpty(),current.rewards,rewardSummary,bookingID)) {
                    busy = true
                    scope.launch {
                        try {
                            val fresh = accountService.checkoutWithFallback(bookingID,session.accessToken,token)
                            check(accountStore.bearerToken == token) { "Account changed" }
                            check(accountStore.state.value.iumrahID?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() } == fresh.iumrahID.filter(Char::isDigit)) { "Account identity changed" }
                            val headers = accountStore.authorizationHeaders(session.accessToken)
                            val summary = bookingService.friendsSummary(bookingID,headers)
                            check(accountStore.bearerToken == token && amount in BookingRewardsPolicy.amounts(fresh.status,fresh.receipts.isEmpty(),fresh.rewards,summary,bookingID)) { "Refresh payment amounts" }
                            // An ambiguous POST failure must never be replayed automatically.
                            rewardUncertain = true
                            val updated = bookingService.applyFriendCredit(bookingID,headers,amount)
                            check(updated.ok && updated.bookingID == bookingID) { "Invalid rewards response" }
                            if (accountStore.bearerToken == token) { rewardSummary = updated; load(showLoader = false) }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (ex: Exception) { if (accountStore.bearerToken == token) { load(showLoader = false); error = ex.message } }
                        finally { busy = false }
                    }
                }
            }) { Text(bookingText(language,"Применить","Apply","Qo‘llash","Қўллаш") + " \$$amount") } },
            dismissButton = { TextButton(onClick = { creditConfirmation = null }) { Text(bookingText(language,"Отмена","Cancel","Bekor qilish","Бекор қилиш")) } })
    }

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

        if (!loading && checkout == null) {
            BookingPasswordlessAccessCard(language, session, accountStore, accountService, bookingStore) { scope.launch { load(showLoader = false) } }
        }
        checkout?.let { value ->
            val accountMatchesTrip = ownerToken != null && BookingSessionPolicy.sameIdentity(accountState.iumrahID, value.iumrahID)
            val availability = value.status.lowercase() == "availability_check"
            val paymentPending = value.status.lowercase() == "payment_pending"
            val postPayment = value.status.lowercase() in setOf("paid", "booking_confirmed", "documents_ready", "ready_to_travel", "in_trip", "completed")

            if (!accountMatchesTrip) {
                if (availability) {
                    AvailabilityPaymentLockedCard(language)
                    TravelDocumentsLockedCard(language)
                }
                BookingPasswordlessAccessCard(language, session, accountStore, accountService, bookingStore) {
                    scope.launch { load(showLoader = false) }
                }
            } else {
                TravelersCard(language, value, session.travelerName, onEdit = { travelerEditor = it })

                if (availability) {
                    AvailabilityPaymentLockedCard(language)
                    TravelDocumentsLockedCard(language)
                    GuideTransferCheckoutCard(language, enabled = false, onClick = {})
                } else if (paymentPending) {
                    PaymentCard(
                        language = language,
                        value = value,
                        busy = busy,
                        canUpload = !accountStore.bearerToken.isNullOrBlank() && rewardMatch && paymentPending && !busy,
                        onUpload = { if (rewardMatch && paymentPending && !busy) receiptLauncher.launch("image/*") },
                        rewardSummary = rewardSummary,
                        pricingConfirmed = rewardMatch && paymentPending,
                        rewardAmounts = if (rewardMatch && !busy) BookingRewardsPolicy.amounts(value.status, value.receipts.isEmpty(), value.rewards, rewardSummary, bookingID) else emptyList(),
                        onApplyCredit = { creditConfirmation = it },
                        onRefreshRewards = { if (!busy && !loading) scope.launch { load(showLoader = false) } },
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
                        canUpload = !accountStore.bearerToken.isNullOrBlank() && rewardMatch && paymentPending && !busy,
                        onUpload = { if (rewardMatch && paymentPending && !busy) receiptLauncher.launch("image/*") },
                        rewardSummary = rewardSummary,
                        pricingConfirmed = rewardMatch && paymentPending,
                        rewardAmounts = if (rewardMatch && !busy) BookingRewardsPolicy.amounts(value.status, value.receipts.isEmpty(), value.rewards, rewardSummary, bookingID) else emptyList(),
                        onApplyCredit = { creditConfirmation = it },
                        onRefreshRewards = { if (!busy && !loading) scope.launch { load(showLoader = false) } },
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
        if (!token.isNullOrBlank() && BookingSessionPolicy.sameIdentity(accountStore.state.value.iumrahID, checkout?.iumrahID)) {
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
internal fun BookingPasswordlessAccessCard(
    language: AppLanguage,
    session: StoredBookingSession?,
    accountStore: IumrahAccountStore,
    accountService: IumrahAccountService,
    bookingStore: BookingStore,
    onRestored: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val prefill = session ?: bookingStore.state.value.sessions.firstOrNull { it.verifiedBookingEmail != null || it.verifiedBookingPhone != null }
    val initialChannel = if (prefill?.verifiedBookingPhone != null && prefill.verifiedBookingEmail == null) "sms" else "email"
    var channel by remember(session?.id) { mutableStateOf(initialChannel) }
    var contact by remember(session?.id) { mutableStateOf(if (initialChannel == "sms") prefill?.verifiedBookingPhone.orEmpty() else prefill?.verifiedBookingEmail.orEmpty()) }
    var challenge by remember(session?.id) { mutableStateOf<String?>(null) }
    var code by remember(session?.id) { mutableStateOf("") }
    var target by remember(session?.id) { mutableStateOf<StoredBookingSession?>(null) }
    var expanded by remember(session?.id) { mutableStateOf(false) }
    var bookingMode by remember(session?.id) { mutableStateOf(false) }
    var verifiedSession by remember(session?.id) { mutableStateOf<IumrahAccountAuthResponse?>(null) }
    var initialToken by remember(session?.id) { mutableStateOf<String?>(null) }
    var busy by remember(session?.id) { mutableStateOf(false) }
    var error by remember(session?.id) { mutableStateOf<String?>(null) }
    val normalized = if (channel == "sms") BookingSessionPolicy.normalizedPhone(contact) else BookingSessionPolicy.normalizedEmail(contact)
    val valid = if (channel == "sms") normalized.length in 9..16 else android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()
    BookingCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IumrahPressable(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, background = Color.Transparent) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CupertinoIcon(CupertinoSymbol.Key, null, Modifier.size(23.dp))
                    Text(if (session == null) bookingText(language,"Найти бронирование","Find booking","Bronni topish","Бронни топиш") else bookingText(language,"Подтвердить доступ к поездке","Confirm access to your trip","Safarga kirishni tasdiqlash","Сафарга киришни тасдиқлаш"), modifier=Modifier.weight(1f), fontSize=18.sp,fontWeight=FontWeight.Bold)
                    CupertinoIcon(if (expanded) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(14.dp))
                }
            }
            Text(bookingText(language,"Подтвердите контакт бронирования, чтобы открыть оплату и документы. Пароль не нужен.","Verify your booking contact to access payment and documents. No password required.","To‘lov va hujjatlar uchun bron kontaktini tasdiqlang. Parol kerak emas.","Тўлов ва ҳужжатлар учун брон контактини тасдиқланг. Парол керак эмас."),fontSize=13.sp,lineHeight=18.sp)
            if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = channel == "email", enabled = !busy && challenge == null, onClick = { channel = "email"; contact = prefill?.verifiedBookingEmail.orEmpty() }, label = { Text("Email") })
                FilterChip(selected = channel == "sms", enabled = !busy && challenge == null, onClick = { channel = "sms"; contact = prefill?.verifiedBookingPhone.orEmpty() }, label = { Text("SMS") })
            }
            OutlinedTextField(contact, { contact = it }, label = { Text(if(channel == "email") "Email" else bookingText(language,"Телефон","Phone","Telefon","Телефон")) }, enabled = !busy && challenge == null, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType=if(channel == "email") KeyboardType.Email else KeyboardType.Phone))
            if (challenge != null && verifiedSession == null) {
                OutlinedTextField(code, { code = it.filter { c -> c in '0'..'9' }.take(6) }, label = { Text(bookingText(language,"Код подтверждения","Verification code","Tasdiqlash kodi","Тасдиқлаш коди")) }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType=KeyboardType.Number))
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp) }
            Button(enabled = !busy && valid && (challenge == null || code.length == 6 || verifiedSession != null), modifier = Modifier.fillMaxWidth(), onClick = {
                if (!busy) {
                    busy = true; error = null
                    scope.launch {
                        try {
                            if (challenge == null) {
                                initialToken = accountStore.bearerToken
                                val explicit = session?.let { bookingStore.booking(it.id) ?: kotlin.error("Booking no longer available") }
                                if (session != null) check(explicit?.accessToken == session.accessToken) { "Booking access changed" }
                                val verified = if(channel == "email") explicit?.verifiedBookingEmail else explicit?.verifiedBookingPhone
                                if (explicit?.verifiedBookingEmail != null || explicit?.verifiedBookingPhone != null) {
                                    check(BookingSessionPolicy.matchesContact(channel, normalized, verified)) { bookingText(language,"Укажите подтверждённый контакт поездки.","Use the verified booking contact.","Tasdiqlangan bron kontaktini kiriting.","Тасдиқланган брон контактини киритинг.") }
                                }
                                target = explicit ?: bookingStore.state.value.sessions.filter { candidate -> candidate.accessToken.isNotBlank() && BookingSessionPolicy.matchesContact(channel, normalized, if (channel == "email") candidate.verifiedBookingEmail else candidate.verifiedBookingPhone) }.singleOrNull()
                                try {
                                    challenge = if (channel == "email") accountService.startEmailLogin(normalized, language.code).challengeID else accountService.startPhoneLogin(normalized, language.code).challengeID
                                    bookingMode = false
                                } catch (e: APIException.Server) {
                                    val existing = target ?: throw e
                                    if (e.code != 404 || e.serverMessage.uppercase() !in setOf("EMAIL_ACCOUNT_NOT_FOUND","PHONE_ACCOUNT_NOT_FOUND") || existing.accessToken.isBlank()) throw e
                                    challenge = if(channel == "email") accountService.startActivationEmail(existing.id, existing.accessToken, normalized, language.code).challengeID else accountService.startActivationSMS(existing.id, existing.accessToken, normalized, language.code).challengeID
                                    bookingMode = true
                                }
                                return@launch
                            }
                            check(accountStore.bearerToken == initialToken) { "Account changed" }
                            val id = requireNotNull(challenge)
                            val existing = target?.let { saved ->
                                val latest = bookingStore.booking(saved.id) ?: kotlin.error("Booking no longer available")
                                check(latest.accessToken == saved.accessToken) { "Booking access changed" }
                                latest
                            }
                            val auth = verifiedSession ?: when {
                                bookingMode && channel == "email" -> requireNotNull(existing).let { accountService.confirmActivationEmail(it.id,it.accessToken,id,code,language.code) }
                                bookingMode -> requireNotNull(existing).let { accountService.confirmActivationSMS(it.id,it.accessToken,id,code,language.code) }
                                channel == "email" -> accountService.confirmEmailLogin(id,code,language.code)
                                else -> accountService.confirmPhoneLogin(id,code,language.code)
                            }.also { check(it.ok && it.session.token.isNotBlank()); verifiedSession = it }
                            check(accountStore.bearerToken == initialToken) { "Account changed" }
                            val link = if(existing != null && existing.accessToken.isNotBlank()) {
                                accountService.linkBooking(existing.id,existing.accessToken,auth.session.token).also {
                                    check(it.ok && BookingSessionPolicy.sameIdentity(it.pilgrimID,auth.account.iumrahID)) { "Booking identity mismatch" }
                                }
                            } else if (existing != null) {
                                val detail = accountService.tripDetail(existing.id,auth.session.token)
                                check(detail.booking.id == existing.id && detail.trip.bookingID == existing.id && BookingSessionPolicy.sameIdentity(detail.trip.pilgrimID,auth.account.iumrahID)) { "Booking identity mismatch" }
                                null
                            } else null
                            accountStore.acceptVerifiedSession(auth, initialToken)
                            if(link != null && existing != null) bookingStore.adoptVerifiedBooking(existing.id,existing.accessToken,link)
                            bookingStore.restoreAccountTrips()
                            verifiedSession = null; code = ""; challenge = null
                            onRestored()
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (e: Exception) { error = e.message ?: "Access failed" }
                        finally { busy = false }
                    }
                }
            }) { Text(if(challenge == null) bookingText(language,"Получить код","Get code","Kod olish","Код олиш") else bookingText(language,"Подтвердить доступ","Verify access","Kirishni tasdiqlash","Киришни тасдиқлаш")) }
            if(challenge != null && !busy && verifiedSession == null) TextButton(onClick={ challenge=null; code=""; error=null; target=null }) { Text(bookingText(language,"Изменить контакт / новый код","Change contact / new code","Kontaktni o‘zgartirish / yangi kod","Контактни ўзгартириш / янги код")) }
            }
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
            val app = LocalContext.current.applicationContext as com.iumrah.beta.IumrahApplication
            if (!app.container.accountStore.state.collectAsState().value.isAuthenticated) {
                com.iumrah.beta.ui.account.ReferralConsentForm(language, app.container.referralStore, enabled = !busy)
                Spacer(Modifier.height(12.dp))
            }
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
private fun TravelersCard(language: AppLanguage, value: IumrahCheckoutResponse, holderName: String?, onEdit: (IumrahTravelerForm) -> Unit) {
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
            else bookingText(language, "Достаточно чёткой фотографии страницы паспорта с данными.", "A clear photo of the passport information page is enough.", "Pasport ma’lumotlar sahifasining aniq rasmi yetarli.", "Паспорт маълумотлар саҳифасининг аниқ расми етарли."),
            fontSize = 14.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
        )
        Spacer(Modifier.height(15.dp))
        value.travelers.sortedBy { it.position }.forEachIndexed { index, traveler ->
            TravelerRow(language, traveler, holderName.takeIf { traveler.relationship == "self" || (index == 0 && traveler.firstName.isBlank() && traveler.lastName.isBlank()) }, onEdit)
            if (index != value.travelers.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TravelerRow(language: AppLanguage, traveler: IumrahTravelerForm, holderName: String?, onEdit: (IumrahTravelerForm) -> Unit) {
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
                Text(holderName?.takeIf { it.isNotBlank() } ?: travelerDisplayName(language, traveler), fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val activeAccountStore = (context.applicationContext as com.iumrah.beta.IumrahApplication).container.accountStore
    val scope = rememberCoroutineScope()
    var busyUpload by remember { mutableStateOf(false) }
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
                        bookingText(language, "Достаточно чёткой фотографии страницы с данными.", "A clear photo of the information page is enough.", "Ma’lumotlar sahifasining aniq rasmi yetarli.", "Маълумотлар саҳифасининг аниқ расми етарли."),
                        fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .55f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                IumrahPressable(onClick = onDismiss, modifier = Modifier.size(38.dp), cornerRadius = 19.dp, background = bookingIosRaised(), pressedScale = .94f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onBackground) }
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
                                runCatching {
                                    check(activeAccountStore.bearerToken == token) { "Account changed" }
                                    accountService.uploadPassport(bookingID, traveler.position, bytes, "image/jpeg", token)
                                    check(activeAccountStore.bearerToken == token) { "Account changed" }
                                }
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

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LoopingRawVideo(
                    resourceName = "iumrah_security_identity",
                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(28.dp)),
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
private fun FriendsBenefitCard(language: AppLanguage, summary: IumrahFriendsBookingSummary?, amounts: List<Int>, onApply: (Int)->Unit, onRefresh: ()->Unit) {
    Column(Modifier.fillMaxWidth().background(bookingIosRaised().copy(alpha=.45f), RoundedCornerShape(22.dp)).padding(14.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){BookingIconBadge(CupertinoSymbol.Gift,Color(0xFFFF2D55),42.dp,17.dp,15.dp);Text("iumrah Balance",fontSize=17.sp,fontWeight=FontWeight.Bold)}
        if(summary==null){Text(bookingText(language,"Баланс недоступен. Обновите страницу.","Balance unavailable. Refresh to try again.","Balans mavjud emas. Yangilang.","Баланс мавжуд эмас. Янгиланг."),fontSize=12.sp);TextButton(onClick=onRefresh){Text(bookingText(language,"Обновить","Refresh","Yangilash","Янгилаш"))}}
        else {
            val values=listOf(bookingText(language,"Лимит Gift Card","Gift Card limit","Gift Card limiti","Gift Card лимити") to summary.maxDiscountUsd,bookingText(language,"Пакет","Package","Paket","Пакет") to summary.totalUsd,bookingText(language,"Скидка","Discount","Chegirma","Чегирма") to summary.totalDiscountUsd,bookingText(language,"К оплате","Amount to pay","To‘lov summasi","Тўлов суммаси") to summary.payableUsd,bookingText(language,"Доступный Iumrah Balance","Available Iumrah Balance","Mavjud Iumrah Balance","Мавжуд Iumrah Balance") to summary.availableCreditUsd)
            values.forEach { (label,amount)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(amount),fontWeight=FontWeight.SemiBold)} }
            if(!summary.identityConfirmed) Text(bookingText(language,"Подтвердите личность для использования бонусов.","Verify your identity before using rewards.","Bonuslar uchun shaxsingizni tasdiqlang.","Бонуслар учун шахсингизни тасдиқланг."),fontSize=12.sp)
            amounts.forEach{amount->BookingPrimaryAction(bookingText(language,"Применить","Apply","Qo‘llash","Қўллаш")+" \$$amount",onClick={onApply(amount)})}
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
    rewardSummary: IumrahFriendsBookingSummary?,
    pricingConfirmed: Boolean,
    rewardAmounts: List<Int>,
    onApplyCredit: (Int)->Unit,
    onRefreshRewards: ()->Unit,
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
        FriendsBenefitCard(language, rewardSummary.takeIf { pricingConfirmed }, rewardAmounts, onApplyCredit, onRefreshRewards)
        Spacer(Modifier.height(10.dp))
        if (pricingConfirmed) {
            value.rewards?.let { pricing ->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(bookingText(language,"К оплате","Amount to pay","To‘lov summasi","Тўлов суммаси"),Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(pricing.payableUsd),fontSize=24.sp,fontWeight=FontWeight.Bold)
                }
            }
        } else {
            Text(bookingText(language,"Сумма к оплате ещё не подтверждена. Обновите данные.","Payment amount is not confirmed. Refresh the data.","To‘lov summasi tasdiqlanmagan. Yangilang.","Тўлов суммаси тасдиқланмаган. Янгиланг."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=onRefreshRewards,enabled=!busy){Text(bookingText(language,"Обновить","Refresh","Yangilash","Янгилаш"))}
            return@BookingCard
        }
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
        if (canUpload) BookingPrimaryAction(
            bookingText(language, if (busy) "Загружаем…" else "Прикрепить чек оплаты", if (busy) "Uploading…" else "Attach payment receipt", if (busy) "Yuklanmoqda…" else "To‘lov chekini biriktirish", if (busy) "Юкланмоқда…" else "Тўлов чекини бириктириш"),
            onClick = onUpload,
            leading = CupertinoSymbol.Paperclip,
        )
        if (!canUpload) {
            Spacer(Modifier.height(9.dp))
            Text(
                bookingText(language, "Обновите подтверждённую сумму к оплате, чтобы загрузить чек.", "Refresh the confirmed amount before uploading the receipt.", "Chekni yuklashdan oldin tasdiqlangan to‘lovni yangilang.", "Чекни юклашдан олдин тасдиқланган тўловни янгиланг."),
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
