package com.iumrah.beta.ui.booking

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.account.IumrahCheckoutResponse
import com.iumrah.beta.models.account.IumrahPaymentReceipt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun PaymentReviewDetails(language:AppLanguage,value:IumrahCheckoutResponse,onOpenReceipt:((IumrahPaymentReceipt)->Unit)?=null) {
    fun t(r:String,e:String,u:String,c:String)=bookingText(language,r,e,u,c)
    fun money(cents:Int)=java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(cents/100.0)
    if(value.paymentReview==null && value.receipts.isEmpty())return
    BookingCard { Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text(t("Проверка оплаты","Payment verification","To‘lovni tekshirish","Тўловни текшириш"),fontWeight=FontWeight.Bold)
        value.paymentReview?.let { review ->
            Text(t("Подтверждено","Verified","Tasdiqlangan","Тасдиқланган")+": "+money(review.paidCents))
            review.dueCents?.let{Text(t("К оплате","Amount due","To‘lash kerak","Тўлаш керак")+": "+money(it))}
            Text(if(review.satisfied)t("Оплата подтверждена","Payment confirmed","To‘lov tasdiqlandi","Тўлов тасдиқланди")else t("Ожидается подтверждение оплаты","Payment confirmation pending","To‘lov tasdig‘i kutilmoqda","Тўлов тасдиғи кутилмоқда"))
        }
        value.receipts.forEach{receipt->
            HorizontalDivider();Text(when(receipt.reviewStatus.lowercase()){"approved"->t("Чек проверен","Receipt verified","Chek tekshirildi","Чек текширилди");"rejected"->t("Нужно исправить","Needs attention","Qayta yuklang","Қайта юкланг");else->t("Отправлен на проверку","Sent for verification","Tekshiruvga yuborildi","Текширувга юборилди")},fontWeight=FontWeight.SemiBold)
            receipt.amountUsd?.let{Text(java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(it))};receipt.reviewReason?.takeIf{it.isNotBlank()}?.let{Text(it,fontSize=12.sp)}
            if (!receipt.url.isNullOrBlank() && onOpenReceipt != null) TextButton(onClick={onOpenReceipt(receipt)}) { Text(t("Открыть чек","View receipt","Chekni ochish","Чекни очиш")) }
        }
    }}
}

@Composable internal fun TravelCompletionCard(language:AppLanguage,value:IumrahCheckoutResponse,id:String,store:BookingStore,onSaved:()->Unit){
    fun t(r:String,e:String,u:String,c:String)=bookingText(language,r,e,u,c)
    val scope=rememberCoroutineScope();var rating by remember(id,value.feedback){mutableIntStateOf(value.feedback?.rating?:0)};var comment by remember(id,value.feedback){mutableStateOf(value.feedback?.comment.orEmpty())};var busy by remember(id){mutableStateOf(false)};var sent by remember(id,value.feedback){mutableStateOf(value.feedback!=null)};var error by remember(id){mutableStateOf<String?>(null)}
    BookingCard { Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text(t("Поездка завершена","Trip completed","Safar yakunlandi","Сафар якунланди"),fontWeight=FontWeight.Bold,fontSize=19.sp)
        value.certificates.orEmpty().forEach{cert->Text(t("Сертификат умры","Umrah certificate","Umra sertifikati","Умра сертификати")+" · "+cert.name,fontWeight=FontWeight.SemiBold);Text(cert.id+" · "+cert.issuedAt,fontSize=12.sp)}
        Text(t("Оцените поездку","Rate your trip","Safarni baholang","Сафарни баҳоланг"))
        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)){(1..5).forEach{n->FilterChip(selected=n<=rating,onClick={rating=n},enabled=!busy&&!sent,label={Text("★")})}}
        OutlinedTextField(comment,{comment=it.take(1000)},enabled=!busy&&!sent,label={Text(t("Отзыв","Feedback","Fikr","Фикр"))},modifier=Modifier.fillMaxWidth())
        Button(enabled=rating in 1..5&&!busy&&!sent,onClick={scope.launch{busy=true;error=null;try{val session=requireNotNull(store.booking(id));check(session.effectiveStatus=="COMPLETED");val headers=store.headersFor(session);check(headers.isNotEmpty());val response=store.service.submitTravelFeedback(id,rating,comment,headers);check(response.ok);sent=true;onSaved()}catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"FEEDBACK_FAILED"}finally{busy=false}}}){Text(if(sent)t("Отзыв отправлен","Feedback sent","Fikr yuborildi","Фикр юборилди")else t("Отправить","Send","Yuborish","Юбориш"))}
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    }}
}
