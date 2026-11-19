package com.iumrah.beta.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.iumrah.beta.domain.booking.BookingSessionPolicy
import com.iumrah.beta.models.booking.BookingCreationNotificationIntent
import com.iumrah.beta.models.booking.StoredBookingSession
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.analytics.IumrahIntelligence
import androidx.compose.runtime.DisposableEffect
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.account.IumrahAccountStore
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.flight.CuratedFlightRecommendationService
import com.iumrah.beta.data.hotel.RemotePackageEngineClient
import com.iumrah.beta.domain.journey.JourneyStore
import com.iumrah.beta.models.booking.BookingPilgrimProfile
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import com.iumrah.beta.ui.generator.GeneratorCard
import com.iumrah.beta.ui.generator.GeneratorGeometry
import com.iumrah.beta.ui.generator.GeneratorHeader
import com.iumrah.beta.ui.generator.GeneratorPrimaryButton
import com.iumrah.beta.ui.generator.GeneratorStage
import com.iumrah.beta.ui.generator.generatorPageColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import java.math.RoundingMode
import kotlinx.coroutines.launch

/** iOS BookingCheckoutView parity: summary → payment/refund → next steps → profile sheet → create booking. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingCheckoutScreen(
    language: AppLanguage,
    journey: JourneyStore,
    bookingStore: BookingStore,
    accountStore: IumrahAccountStore,
    chrome: AppChromeStore,
    curated: CuratedFlightRecommendationService,
    packageEngine: RemotePackageEngineClient,
) {
    val state by journey.state.collectAsState()
    val account by accountStore.state.collectAsState()
    val quote = state.quote
    val canBook = state.priceChangeAccepted && state.hasFinalGeneratorQuote && state.transferSelectionConfirmed && quote != null && quote.hasVerifiedBreakdown && quote.allowsOccupancy(state.trip.adults,state.trip.rooms) && quote.bookingAllowed != false &&
        (state.nuitee == null || state.hasFinalGeneratorQuote && quote.bookingAllowed == true)
    val scope = rememberCoroutineScope()
    var firstName by remember(account.account?.iumrahID) { mutableStateOf(account.account?.firstName.orEmpty()) }
    var lastName by remember(account.account?.iumrahID) { mutableStateOf(account.account?.lastName.orEmpty()) }
    val context = LocalContext.current
    val container = (context.applicationContext as com.iumrah.beta.IumrahApplication).container
    var channel by remember { mutableStateOf("email") }
    var contact by remember(account.account?.iumrahID) { mutableStateOf(account.account?.email.orEmpty()) }
    var challenge by remember { mutableStateOf<String?>(null) }
    var code by remember { mutableStateOf("") }
    var proof by remember { mutableStateOf<String?>(null) }
    var committed by remember { mutableStateOf<StoredBookingSession?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val requestID = remember { java.util.UUID.randomUUID().toString() }
    var showProfile by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        IumrahIntelligence.detailAppeared("checkout")
        IumrahIntelligence.record("checkout_started", "checkout")
        if (quote != null) IumrahIntelligence.record("price_viewed", "checkout", "package_total")
        onDispose { IumrahIntelligence.detailDisappeared("checkout") }
    }
    val normalizedContact = if (channel == "sms") BookingSessionPolicy.normalizedPhone(contact) else BookingSessionPolicy.normalizedEmail(contact)
    val contactValid = if (channel == "sms") normalizedContact.length in 9..16 else android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedContact).matches()
    val validIdentity = firstName.isNotBlank() && lastName.isNotBlank() && contactValid

    Column(
        Modifier.fillMaxSize().background(generatorPageColor()).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = GeneratorGeometry.pagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        GeneratorHeader(GeneratorStage.READY, language, chrome, currentPriceText = quote?.let { "${it.totalPackagePrice.setScale(0, RoundingMode.HALF_UP)} ${it.currency}" })
        CheckoutHero(language, state, quote)
        ManualPaymentNotice(language)
        CheckoutRefundCard(language)
        NextStepsCard(language)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
        GeneratorPrimaryButton(
            title = if (busy) checkoutTr(language,"Создаём бронирование…","Creating booking…","Bron yaratilmoqda…","Брон яратилмоқда…") else checkoutTr(language,"Создать бронирование","Create booking","Bron yaratish","Брон яратиш"),
            enabled = canBook && !busy,
        ) { IumrahIntelligence.record("tap", "checkout", "continue_booking"); showProfile = true }
        Spacer(Modifier.height(34.dp))
    }

    if (showProfile) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { if (!busy) showProfile = false },
            sheetState = sheet,
            containerColor = generatorPageColor(),
            dragHandle = { Box(Modifier.padding(top=10.dp,bottom=10.dp).size(width=38.dp,height=5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.18f))) },
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=GeneratorGeometry.pagePadding).padding(bottom=34.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
                Text(checkoutTr(language,"Данные паломника","Pilgrim details","Ziyoratchi ma’lumotlari","Зиёратчи маълумотлари"),fontSize=27.sp,fontWeight=FontWeight.Bold)
                Text(checkoutTr(language,"Подтвердите имя и контакт перед созданием бронирования. Эти данные будут привязаны к поездке.","Confirm your name and contact before creating the booking. These details will be linked to the trip.","Bron yaratishdan oldin ism va kontaktni tasdiqlang. Bu ma’lumotlar safarga biriktiriladi.","Брон яратишдан олдин исм ва контактни тасдиқланг. Бу маълумотлар сафарга бириктирилади."),fontSize=13.sp,lineHeight=18.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.56f))
                // Once verified, lock contact and identity until the request has completed.
                OutlinedTextField(firstName, { firstName = it }, label = { Text(checkoutTr(language,"Имя","First name","Ism","Исм")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy && challenge == null)
                OutlinedTextField(lastName, { lastName = it }, label = { Text(checkoutTr(language,"Фамилия","Last name","Familiya","Фамилия")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy && challenge == null)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.material3.FilterChip(selected = channel == "email", enabled = !busy && challenge == null, onClick = { channel = "email"; contact = account.account?.email.orEmpty() }, label = { Text("Email") })
                    androidx.compose.material3.FilterChip(selected = channel == "sms", enabled = !busy && challenge == null, onClick = { channel = "sms"; contact = account.account?.phone.orEmpty() }, label = { Text("SMS") })
                }
                OutlinedTextField(contact, { contact = it }, label = { Text(if (channel == "email") "Email" else checkoutTr(language,"Телефон","Phone","Telefon","Телефон")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy && challenge == null, keyboardOptions = KeyboardOptions(keyboardType = if (channel == "email") KeyboardType.Email else KeyboardType.Phone))
                if (challenge != null && proof == null) {
                    OutlinedTextField(code, { code = it.filter { c -> c in '0'..'9' }.take(6) }, label = { Text(checkoutTr(language,"Код подтверждения","Verification code","Tasdiqlash kodi","Тасдиқлаш коди")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (challenge != null && !submitted && committed == null && !busy) {
                    androidx.compose.material3.TextButton(onClick = { challenge = null; code = ""; proof = null; error = null }) { Text(checkoutTr(language,"Изменить контакт / новый код","Change contact / new code","Kontaktni o‘zgartirish / yangi kod","Контактни ўзгартириш / янги код")) }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                GeneratorPrimaryButton(
                    if (challenge == null) checkoutTr(language,"Получить код","Get code","Kod olish","Код олиш")
                    else if (committed != null) checkoutTr(language,"Подтвердить контакт","Confirm contact","Kontaktni tasdiqlash","Контактни тасдиқлаш")
                    else checkoutTr(language,"Подтвердить и забронировать","Confirm and book","Tasdiqlash va bron qilish","Тасдиқлаш ва брон қилиш"),
                    enabled = !busy && validIdentity && (committed != null || (!submitted && canBook)) && (challenge == null || code.length == 6 || proof != null),
                ) {
                    if (busy) return@GeneratorPrimaryButton
                    val currentQuote = quote
                    busy = true; error = null
                    scope.launch {
                        try {
                            if (challenge == null) {
                                challenge = bookingStore.service.startGuestContact(channel, normalizedContact, language.code).challengeID
                                return@launch
                            }
                            val verified = proof ?: bookingStore.service.confirmGuestContact(channel, requireNotNull(challenge), code).let { response ->
                                check(BookingSessionPolicy.matchesContact(channel, normalizedContact, if (channel == "email") response.email else response.phone)) { "Contact verification mismatch" }
                                response.verificationToken.also { proof = it }
                            }
                            val session = committed ?: run {
                                check(!submitted && currentQuote != null) { "Booking already submitted" }
                                val finalQuote = if (state.nuitee != null) {
                                    check(journey.state.value == state) { "PACKAGE_SELECTION_CHANGED" }
                                    val fresh = journey.prepareNuiteePackage(curated, packageEngine).getOrThrow()
                                    check(fresh.hasVerifiedBreakdown && fresh.allowsOccupancy(state.trip.adults,state.trip.rooms) && fresh.totalPackagePrice.compareTo(currentQuote.totalPackagePrice) == 0 && fresh.pricePerPerson.compareTo(currentQuote.pricePerPerson) == 0 && fresh.currency == currentQuote.currency) {
                                        checkoutTr(language,"Цена изменилась. Проверьте новый итог перед бронированием.","Price changed. Review the new total before booking.","Narx o‘zgardi. Yangi summani tekshiring.","Нарх ўзгарди. Янги суммани текширинг.")
                                    }
                                    check(journey.state.value.quote == fresh && journey.state.value.nuitee == state.nuitee) { "PACKAGE_SELECTION_CHANGED" }
                                    fresh
                                } else { check(journey.state.value == state) { "PACKAGE_SELECTION_CHANGED" }; currentQuote }
                                val pricedState = journey.state.value
                                check(pricedState.priceChangeAccepted && pricedState.quote == finalQuote && pricedState.savedPackageID == state.savedPackageID) { "PACKAGE_SELECTION_CHANGED" }
                                val savedEntry = pricedState.savedPackageID?.let {
                                    container.savedPackages.save(pricedState,language)
                                }
                                check(journey.state.value == pricedState) { "PACKAGE_SELECTION_CHANGED" }
                                val push = container.pushManager
                                val intent = BookingCreationNotificationIntent(requestID = requestID, contactChannel = channel, contact = normalizedContact,
                                    verificationToken = verified, locale = language.code, pushDeviceToken = push.state.value.deviceToken.takeIf { push.hasNotificationPermission() },
                                    appBundleID = com.iumrah.beta.BuildConfig.APPLICATION_ID, installationID = container.notificationStore.installationID)
                                submitted = true // never replay a create POST after an ambiguous response
                                bookingStore.create(if (state.nuitee != null) journey.state.value else state, finalQuote, language,
                                    BookingPilgrimProfile(firstName.trim(), lastName.trim(), "", ""), intent).also { created ->
                                        committed = created
                                        savedEntry?.receipt?.packageID?.let { id ->
                                            try { container.savedPackages.link(id,created) }
                                            catch (cancel: CancellationException) { throw cancel }
                                            catch (_: Exception) { /* pending link is retried from Saved journeys */ }
                                        }
                                    }
                            }
                            bookingStore.service.attachGuestContact(session.id, session.accessToken, channel, verified, language.code)
                            bookingStore.recordVerifiedContact(session.id, session.accessToken, channel, normalizedContact)
                            // A missing newer exchange route must not undo a valid guest booking.
                            if (accountStore.bearerToken == null) {
                                try {
                                    val auth = container.accountService.exchangeVerifiedBookingProfile(session.id, session.accessToken, verified, channel, language.code)
                                    check(auth.ok && auth.session.token.isNotBlank())
                                    val link = container.accountService.linkBooking(session.id, session.accessToken, auth.session.token)
                                    check(link.ok && BookingSessionPolicy.sameIdentity(link.pilgrimID, auth.account.iumrahID))
                                    accountStore.acceptVerifiedSession(auth, null)
                                    bookingStore.adoptVerifiedBooking(session.id, session.accessToken, link)
                                } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { /* guest proof remains authoritative */ }
                            }
                            IumrahIntelligence.record("booking_submitted", "checkout")
                            showProfile = false
                            chrome.openBookingCelebration(session.id)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (cause: Exception) {
                            IumrahIntelligence.record("error", "checkout", "booking_submit")
                            error = if (submitted && committed == null) checkoutTr(language,"Результат создания не подтверждён. Проверьте бронирования или обратитесь в поддержку. Не отправляйте заявку повторно.","Booking result is unconfirmed. Check trips or contact support; do not submit again.","Bron natijasi tasdiqlanmadi. Safarlarni tekshiring yoki yordamga murojaat qiling.","Брон натижаси тасдиқланмади. Сафарларни текширинг ёки ёрдамга мурожаат қилинг.") else cause.message ?: "Booking failed"
                        } finally { busy = false }
                    }
                }
                committed?.let { saved ->
                    androidx.compose.material3.TextButton(onClick = { showProfile = false; chrome.openBookingCelebration(saved.id) }, enabled = !busy) {
                        Text(checkoutTr(language,"Открыть созданное бронирование","Open created booking","Yaratilgan bronni ochish","Яратилган бронни очиш"))
                    }
                }
            }
        }
    }
}

@Composable private fun CheckoutHero(language:AppLanguage,state:com.iumrah.beta.domain.journey.JourneyState,quote:com.iumrah.beta.domain.pricing.PackageQuote?) {
    GeneratorCard {
        Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(checkoutTr(language,"Оформление бронирования","Booking checkout","Bronni rasmiylashtirish","Бронни расмийлаштириш"),fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.Bold)
            Text(checkoutTr(language,"Проверьте итог и отправьте бронирование в iumrah. Оплата выполняется после подтверждения доступности.","Review the final package and send the booking to iumrah. Payment happens after availability is confirmed.","Yakuniy paketni tekshiring va bronni iumrah ga yuboring. To‘lov mavjudlik tasdiqlangandan keyin qilinadi.","Якуний пакетни текширинг ва бронни iumrah га юборинг. Тўлов мавжудлик тасдиқлангандан кейин қилинади."),fontSize=14.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.56f))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(generatorRaisedColor()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(state.makkahHotel?.name.orEmpty(),fontSize=16.sp,fontWeight=FontWeight.Bold)
                state.makkahRoomCategory?.displayName?.takeIf{it.isNotBlank()}?.let{CheckoutLine(CupertinoSymbol.Bed,it)}
                CheckoutLine(CupertinoSymbol.Airplane,"${state.trip.originCode} → ${state.trip.outboundDestinationCode}")
                quote?.let { Text("${it.totalPackagePrice.setScale(0,RoundingMode.HALF_UP)} ${it.currency}",fontSize=28.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=4.dp)) }
            }
        }
    }
}
@Composable private fun CheckoutLine(icon:CupertinoSymbol,text:String){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(icon,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f));Text(text,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.58f))}}

@Composable private fun ManualPaymentNotice(language:AppLanguage){GeneratorCard{Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF007AFF).copy(alpha=.1f)),contentAlignment=Alignment.Center){Icon(CupertinoSymbol.CreditCard,null,Modifier.size(21.dp),tint=Color(0xFF007AFF))};Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text(checkoutTr(language,"Оплата после подтверждения","Payment after confirmation","Tasdiqdan keyin to‘lov","Тасдиқдан кейин тўлов"),fontSize=16.sp,fontWeight=FontWeight.Bold);Text(checkoutTr(language,"Сначала команда iumrah проверит наличие перелётов и отелей. После подтверждения вы получите инструкции по оплате.","The iumrah team first confirms flight and hotel availability. Payment instructions are provided after confirmation.","Avval iumrah jamoasi reys va mehmonxona mavjudligini tasdiqlaydi. Shundan keyin to‘lov ko‘rsatmalari beriladi.","Аввал iumrah жамоаси рейс ва меҳмонхона мавжудлигини тасдиқлайди. Шундан кейин тўлов кўрсатмалари берилади."),fontSize=12.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.56f))}}}}

@Composable private fun CheckoutRefundCard(language:AppLanguage){GeneratorCard{Column(verticalArrangement=Arrangement.spacedBy(10.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){Icon(CupertinoSymbol.Document,null,Modifier.size(20.dp));Text(checkoutTr(language,"Условия возврата","Refund policy","Qaytarish shartlari","Қайтариш шартлари"),fontSize=17.sp,fontWeight=FontWeight.Bold)};Text(checkoutTr(language,"Условия зависят от каждого компонента. Авиабилеты и некоторые тарифы отелей могут быть невозвратными; остальные услуги отображаются отдельно перед оплатой.","Refund terms depend on each component. Flights and some hotel rates may be non-refundable; other services are shown separately before payment.","Qaytarish shartlari har bir komponentga bog‘liq. Aviabiletlar va ayrim mehmonxona tariflari qaytarilmasligi mumkin; boshqa xizmatlar to‘lovdan oldin alohida ko‘rsatiladi.","Қайтариш шартлари ҳар бир компонентга боғлиқ. Авиабилетлар ва айрим меҳмонхона тарифлари қайтарилмаслиги мумкин; бошқа хизматлар тўловдан олдин алоҳида кўрсатилади."),fontSize=12.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.56f))}}}

@Composable private fun NextStepsCard(language:AppLanguage){GeneratorCard{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(checkoutTr(language,"Что будет дальше","What happens next","Keyin nima bo‘ladi","Кейин нима бўлади"),fontSize=18.sp,fontWeight=FontWeight.Bold);CheckoutStep("01",checkoutTr(language,"Мы создадим бронирование и начнём проверку доступности.","We create the booking and start the availability check.","Bron yaratiladi va mavjudlik tekshiruvi boshlanadi.","Брон яратилади ва мавжудлик текшируви бошланади."));CheckoutStep("02",checkoutTr(language,"После подтверждения вы добавите паспортные данные.","After confirmation, you add passport details.","Tasdiqdan keyin pasport ma’lumotlarini qo‘shasiz.","Тасдиқдан кейин паспорт маълумотларини қўшасиз."));CheckoutStep("03",checkoutTr(language,"Вы оплачиваете подтверждённый пакет.","You pay for the confirmed package.","Tasdiqlangan paket uchun to‘lov qilasiz.","Тасдиқланган пакет учун тўлов қиласиз."));CheckoutStep("04",checkoutTr(language,"Документы поездки появятся в бронировании.","Trip documents appear in your booking.","Safar hujjatlari bron ichida paydo bo‘ladi.","Сафар ҳужжатлари брон ичида пайдо бўлади."))}}}
@Composable private fun CheckoutStep(number:String,text:String){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Box(Modifier.size(30.dp).clip(CircleShape).background(generatorRaisedColor()),contentAlignment=Alignment.Center){Text(number,fontSize=10.sp,fontWeight=FontWeight.Bold)};Text(text,fontSize=13.sp,modifier=Modifier.weight(1f))}}

@Composable private fun BookingField(label:String,value:String,onChange:(String)->Unit){OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(20.dp))}
private fun checkoutTr(l:AppLanguage,ru:String,en:String,uz:String,uzCy:String)=when(l){AppLanguage.RUSSIAN->ru;AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY ->en;AppLanguage.UZBEK->uz;AppLanguage.UZBEK_CYRILLIC->uzCy}
