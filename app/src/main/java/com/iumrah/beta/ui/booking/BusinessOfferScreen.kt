package com.iumrah.beta.ui.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.data.booking.BusinessOfferService
import com.iumrah.beta.domain.booking.BookingSessionPolicy
import com.iumrah.beta.models.booking.*
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.hotels.nuiteeText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable internal fun BusinessOfferScreen(token: String, language: AppLanguage, bookings: BookingStore, chrome: AppChromeStore) {
    fun t(ru:String,en:String,uz:String,cy:String) = nuiteeText(language,ru,en,uz,cy)
    val scope = rememberCoroutineScope()
    val service = remember { BusinessOfferService() }
    val state by bookings.state.collectAsState()
    var offer by remember(token) { mutableStateOf<BusinessOfferPayload?>(null) }
    var busy by remember(token) { mutableStateOf(true) }
    var error by remember(token) { mutableStateOf<String?>(null) }
    var refresh by remember(token) { mutableIntStateOf(0) }
    var form by remember(token) { mutableStateOf(false) }
    var first by remember(token) { mutableStateOf("") }; var last by remember(token) { mutableStateOf("") }
    var channel by remember(token) { mutableStateOf("email") }; var contact by remember(token) { mutableStateOf("") }
    var challenge by remember(token) { mutableStateOf<String?>(null) }; var code by remember(token) { mutableStateOf("") }
    var proof by remember(token) { mutableStateOf<String?>(null) }
    var submitted by remember(token) { mutableStateOf(false) }
    val requestSecret = rememberSaveable(token) { (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "") }
    val normalized = if (channel == "email") BookingSessionPolicy.normalizedEmail(contact) else BookingSessionPolicy.normalizedPhone(contact)
    val valid = first.isNotBlank() && last.isNotBlank() && if (channel == "email") android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches() else normalized.length in 9..16
    LaunchedEffect(token, refresh) {
        busy = true; error = null
        try { offer = service.load(token) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = t("Не удалось загрузить предложение", "Could not load offer", "Taklif yuklanmadi", "Таклиф юкланмади") }
        finally { busy = false }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = { if (!busy) chrome.back() }) { Text(t("Закрыть", "Close", "Yopish", "Ёпиш")) }
        Column(Modifier.fillMaxWidth().background(Color.Black,RoundedCornerShape(24.dp)).padding(24.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("iumrah", color=Color.White, fontSize=30.sp,fontWeight=FontWeight.Bold)
            Text(t("Ваше предложение", "Your offer", "Sizning taklifingiz", "Сизнинг таклифингиз"), color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Bold)
            offer?.let { Text("Package ID · ${it.packageID}",color=Color.White.copy(alpha=.7f),fontSize=13.sp) }
        }
        if (busy) CircularProgressIndicator()
        offer?.let { value ->
            val details = value.`package`
            Text(java.text.NumberFormat.getCurrencyInstance(language.locale).apply { currency = java.util.Currency.getInstance("USD") }.format(details.totalUsd), fontSize=32.sp,fontWeight=FontWeight.Bold)
            Text("${details.input.travelers.totalPeople} · " + t("паломников", "pilgrims", "ziyoratchi", "зиёратчи"))
            Text("${details.input.startDate} — ${details.input.endDate}")
            Text(details.flight); Text(details.hotelNames.makkah)
            if (details.hotelNames.madinah.isNotBlank()) Text(details.hotelNames.madinah)
            Text(t("Наличие и цена требуют подтверждения. Оплата пока не требуется.", "Availability and price require confirmation. No payment yet.", "Mavjudlik va narx tasdiqlanishi kerak. Hozircha to‘lov yo‘q.", "Мавжудлик ва нарх тасдиқланиши керак. Ҳозирча тўлов йўқ."), color=MaterialTheme.colorScheme.onSurfaceVariant)
            val existing = state.sessions.firstOrNull { it.booking.offerPackageID == value.packageID && bookings.booking(it.id) != null }
            if (existing != null) IumrahPrimaryButton(t("Открыть бронирование", "Open booking", "Bronni ochish", "Бронни очиш"), enabled = !busy) { chrome.openBookingDetail(existing.id) }
            else if (value.status != "available" && !submitted) Text(t("Предложение больше недоступно", "Offer no longer available", "Taklif endi mavjud emas", "Таклиф энди мавжуд эмас"))
            else if (!form) IumrahPrimaryButton(t("Забронировать умру", "Book Umrah", "Umrani bron qilish", "Умрани брон қилиш"), enabled = !busy && value.status == "available") { form = true }
            else {
                OutlinedTextField(first, { first=it }, label={Text(t("Имя", "First name", "Ism", "Исм"))}, enabled=!busy && challenge==null, singleLine=true, modifier=Modifier.fillMaxWidth())
                OutlinedTextField(last, { last=it }, label={Text(t("Фамилия", "Last name", "Familiya", "Фамилия"))}, enabled=!busy && challenge==null, singleLine=true, modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("email","sms").forEach { c -> FilterChip(selected=channel==c, enabled=!busy && challenge==null, onClick={channel=c;contact=""}, label={Text(c.uppercase())}) } }
                OutlinedTextField(contact,{contact=it}, label={Text(if(channel=="email") "Email" else t("Телефон","Phone","Telefon","Телефон"))},enabled=!busy && challenge==null,singleLine=true,modifier=Modifier.fillMaxWidth())
                if(challenge!=null && proof==null) OutlinedTextField(code,{code=it.filter { c->c in '0'..'9' }.take(6)},label={Text(t("Код", "Code", "Kod", "Код"))},enabled=!busy,singleLine=true,modifier=Modifier.fillMaxWidth())
                if(challenge!=null && !submitted) TextButton(enabled=!busy,onClick={challenge=null;proof=null;code=""}) { Text(t("Новый код / другой контакт", "New code / change contact", "Yangi kod / boshqa kontakt", "Янги код / бошқа контакт")) }
                IumrahPrimaryButton(if(challenge==null) t("Получить код","Get code","Kod olish","Код олиш") else t("Подтвердить и забронировать","Confirm and book","Tasdiqlash va bron qilish","Тасдиқлаш ва брон қилиш"),enabled=!busy && valid && (challenge==null || proof!=null || code.length==6)) {
                    if (!busy) {
                        busy=true;error=null
                        scope.launch {
                            try {
                                if(challenge==null) challenge=bookings.service.startGuestContact(channel,normalized,language.code).challengeID
                                else {
                                    val verified=proof ?: bookings.service.confirmGuestContact(channel,requireNotNull(challenge),code).let {
                                        check(BookingSessionPolicy.matchesContact(channel,normalized,if(channel=="email") it.email else it.phone))
                                        it.verificationToken.also { v->proof=v }
                                    }
                                    submitted=true
                                    val receipt=service.accept(token,BusinessOfferAccept(value.revision,first.trim(),last.trim(),channel,verified,requestSecret))
                                    val session=bookings.adoptOfferBooking(receipt,value.packageID,channel,normalized)
                                    // Conversion already created a booking. Contact-attachment or identity failure must not lose its proof.
                                    try { bookings.service.attachGuestContact(session.id, session.accessToken, channel, verified, language.code) }
                                    catch (cancel: CancellationException) { throw cancel }
                                    catch (_: Exception) { /* contact already verified by native offer endpoint */ }
                                    chrome.openBookingCelebration(session.id)
                                }
                            } catch(cancel:CancellationException){throw cancel}
                            catch(_:Exception){error=if(submitted) t("Не удалось подтвердить результат. Повторите с тем же кодом; не создавайте новую заявку.", "Result not confirmed. Retry with the same verification; do not create another booking.", "Natija tasdiqlanmadi. Shu tasdiq bilan qayta urining.", "Натижа тасдиқланмади. Шу тасдиқ билан қайта урининг.") else t("Не удалось подтвердить контакт", "Contact verification failed", "Kontakt tasdiqlanmadi", "Контакт тасдиқланмади")}
                            finally {busy=false}
                        }
                    }
                }
            }
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error); if(!submitted && !form) TextButton(enabled=!busy,onClick={refresh++}) { Text(t("Повторить","Retry","Qayta urinish","Қайта уриниш")) } }
    }
}
