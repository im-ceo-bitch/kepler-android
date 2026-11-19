package com.iumrah.beta.ui.booking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Verified booking contact is the only credential used for recovery. */
@Composable internal fun GuestBookingRecoveryDialog(language:AppLanguage,store:BookingStore,onDismiss:()->Unit,onRecovered:(String)->Unit) {
    fun t(r:String,e:String,u:String,c:String)=bookingText(language,r,e,u,c)
    val scope=rememberCoroutineScope()
    var number by remember { mutableStateOf("") }; var channel by remember { mutableStateOf("email") }; var contact by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<String?>(null) }; var code by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val normalized=if(channel=="email")contact.trim().lowercase() else contact.trim().replace(" ","")
    val contactValid=if(channel=="email") android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches() else normalized.matches(Regex("\\+[0-9]{8,15}"))
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text(t("Восстановить бронирование","Recover booking","Bronni tiklash","Бронни тиклаш"))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(t("Введите номер бронирования и подтверждённый контакт.","Enter the booking number and its verified contact.","Bron raqami va tasdiqlangan kontaktni kiriting.","Брон рақами ва тасдиқланган контактни киритинг."))
        OutlinedTextField(number,{number=it.take(40)},label={Text(t("Номер бронирования","Booking number","Bron raqami","Брон рақами"))},enabled=!busy&&challenge==null,singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("email","sms").forEach{kind->FilterChip(selected=channel==kind,onClick={channel=kind;contact=""},enabled=!busy&&challenge==null,label={Text(if(kind=="email")"Email" else "SMS")})}}
        OutlinedTextField(contact,{contact=it.take(254)},label={Text(if(channel=="email")"Email" else "+998…")},enabled=!busy&&challenge==null,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(channel=="email")KeyboardType.Email else KeyboardType.Phone))
        if(challenge!=null){OutlinedTextField(code,{code=it.filter(Char::isDigit).take(8)},label={Text(t("Код подтверждения","Verification code","Tasdiqlash kodi","Тасдиқлаш коди"))},enabled=!busy,singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number));TextButton(enabled=!busy,onClick={challenge=null;code="";error=null}){Text(t("Изменить контакт / новый код","Change contact / new code","Kontaktni o‘zgartirish / yangi kod","Контактни ўзгартириш / янги код"))}}
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth());error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(enabled=!busy&&number.isNotBlank()&&contactValid&&(challenge==null||code.length>=4),onClick={scope.launch{
        busy=true;error=null
        try { val id=challenge;if(id==null)challenge=store.service.startGuestContact(channel,normalized,language.code).challengeID else {val session=store.recoverGuestBooking(number.trim(),channel,id,code);onRecovered(session.id)} }
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "BOOKING_RECOVERY_FAILED"}finally{busy=false}
    }}){Text(if(challenge==null)t("Получить код","Get code","Kod olish","Код олиш") else t("Восстановить","Recover","Tiklash","Тиклаш"))}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(t("Отмена","Cancel","Bekor qilish","Бекор қилиш"))}})
}
