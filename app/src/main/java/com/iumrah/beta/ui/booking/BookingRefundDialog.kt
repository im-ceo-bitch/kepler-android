package com.iumrah.beta.ui.booking

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.booking.BookingRefundPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun BookingRefundDialog(language: AppLanguage, bookingID: String, store: BookingStore, onDismiss: () -> Unit) {
    fun t(r:String,e:String,u:String,c:String)=bookingText(language,r,e,u,c)
    fun money(cents:Int)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(cents/100.0)
    val scope=rememberCoroutineScope()
    var preview by remember(bookingID) { mutableStateOf<BookingRefundPreview?>(null) }
    var error by remember(bookingID) { mutableStateOf<String?>(null) }
    var busy by remember(bookingID) { mutableStateOf(false) }
    var sent by remember(bookingID) { mutableStateOf(false) }
    var confirm by remember(bookingID) { mutableStateOf(false) }
    val accountService=(androidx.compose.ui.platform.LocalContext.current.applicationContext as com.iumrah.beta.IumrahApplication).container.accountService
    suspend fun checkExisting(headers: Map<String,String>): Boolean {
        val result=accountService.checkout(bookingID,headers)
        check(result.ok && store.booking(bookingID)?.let(store::headersFor)==headers){"CHECKOUT_FAILED"}
        return !result.cancellationRequestedAt.isNullOrBlank()
    }
    suspend fun load() {
        busy=true; error=null; preview=null
        try { val session=requireNotNull(store.booking(bookingID)); val headers=store.headersFor(session); check(headers.isNotEmpty()); check(!session.canDeleteUnpaid && session.effectiveStatus !in setOf("COMPLETED","CANCELLED","DELETED")); sent=checkExisting(headers); if(!sent) preview=store.service.refundPreview(bookingID,headers) }
        catch(e:CancellationException){throw e} catch(e:Exception){error=e.message ?: "REFUND_PREVIEW_FAILED"} finally {busy=false}
    }
    LaunchedEffect(bookingID) { load() }
    AlertDialog(onDismissRequest={if(!busy) onDismiss()},title={Text(t("Отмена и возврат","Cancellation and refund","Bekor qilish va qaytarish","Бекор қилиш ва қайтариш"))},text={
        Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(busy && preview==null) CircularProgressIndicator()
            preview?.let { p ->
                if(p.available) {
                    p.items.forEach { item ->
                        Row { Column(Modifier.weight(1f)) { Text(item.label,fontWeight=FontWeight.Medium); Text(money(item.amountCents),fontSize=12.sp); if(item.reason.isNotBlank()) Text(item.reason,fontSize=12.sp) }; Text(item.refundCents?.let(::money) ?: t("Уточняется","Review required","Aniqlanadi","Аниқланади")) }
                        HorizontalDivider()
                    }
                    p.supportCents?.let { Row {Text(t("Комиссия 9%","9% fee · estimated","9% komissiya","9% комиссия"),Modifier.weight(1f));Text(money(it))} }
                    p.estimateCents?.let { Text(money(it),fontSize=30.sp,fontWeight=FontWeight.Bold) }
                    Text(t("Итог проверит менеджер. Комиссия оплаты и тарифы поставщиков уточняются отдельно.","A manager will verify the final amount, payment fee and supplier rules.","Yakuniy summani menejer tekshiradi.","Якуний суммани менежер текширади."),fontSize=12.sp)
                } else Text(t("Расчёт подготовит менеджер.","A manager will calculate the refund.","Menejer qaytarishni hisoblaydi.","Менежер қайтаришни ҳисоблайди."))
                Text(t("Запрос не отменяет поездку автоматически и не переводит деньги.","The request does not automatically cancel the trip or transfer money.","So‘rov safarni avtomatik bekor qilmaydi.","Сўров сафарни автоматик бекор қилмайди."),fontSize=12.sp)
            }
            if(sent) Text(t("Запрос отправлен","Request sent","So‘rov yuborildi","Сўров юборилди"))
            error?.let { Text(it,color=MaterialTheme.colorScheme.error); TextButton(enabled=!busy,onClick={scope.launch{load()}}){Text(t("Повторить","Retry","Qayta urinish","Қайта уриниш"))} }
        }
    },confirmButton={TextButton(enabled=!busy&&!sent&&preview!=null,onClick={confirm=true}){Text(t("Запросить отмену","Request cancellation","Bekor qilishni so‘rash","Бекор қилишни сўраш"),color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text(t("Закрыть","Close","Yopish","Ёпиш"))}})
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(t("Отправить запрос на отмену всей поездки?","Request cancellation of the entire trip?","Safarni bekor qilish so‘rovi?","Сафарни бекор қилиш сўрови?"))},confirmButton={TextButton(onClick={confirm=false;scope.launch{
        busy=true;error=null
        try { val session=requireNotNull(store.booking(bookingID)); val headers=store.headersFor(session);check(headers.isNotEmpty());check(!session.canDeleteUnpaid && session.effectiveStatus !in setOf("COMPLETED","CANCELLED","DELETED")); if(!checkExisting(headers)){ val result=store.service.requestCancellation(bookingID,headers);check(result.ok==true){"CANCELLATION_NOT_CONFIRMED"} };sent=true;store.refresh(bookingID) }
        catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "CANCELLATION_FAILED"}finally{busy=false}
    }}){Text(t("Отправить запрос","Send request","So‘rov yuborish","Сўров юбориш"))}},dismissButton={TextButton(onClick={confirm=false}){Text(t("Назад","Back","Orqaga","Орқага"))}})
}
