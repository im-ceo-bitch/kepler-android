@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.flights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.AviasalesFlightDiscoveryService
import com.iumrah.beta.data.flight.FlightDiscoveryCalendarDay
import com.iumrah.beta.ui.components.IumrahPressable
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun text(language:AppLanguage,ru:String,en:String,uz:String,cy:String)=when(language){
    AppLanguage.RUSSIAN->ru; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY ->en; AppLanguage.UZBEK->uz; AppLanguage.UZBEK_CYRILLIC->cy
}

@Composable
internal fun FlightPriceGraphV46(
    language: AppLanguage, origin: String, destination: String, selectedDeparture: LocalDate,
    selectedReturn: LocalDate, roundTrip: Boolean, service: AviasalesFlightDiscoveryService,
    onDismiss: () -> Unit, onSelect: (LocalDate, LocalDate) -> Unit,
) {
    var selectedLeg by remember { mutableStateOf(0) }
    var onlyDirect by remember { mutableStateOf(false) }
    var outbound by remember { mutableStateOf<List<FlightDiscoveryCalendarDay>>(emptyList()) }
    var inbound by remember { mutableStateOf<List<FlightDiscoveryCalendarDay>>(emptyList()) }
    var departure by remember(selectedDeparture) { mutableStateOf(selectedDeparture) }
    var returnDate by remember(selectedReturn) { mutableStateOf(selectedReturn) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(origin,destination,onlyDirect,selectedDeparture,selectedReturn,roundTrip) {
        loading=true;error=false
        try {
            outbound=service.calendar(origin,destination,departure.toString().take(7),onlyDirect).days
            if(roundTrip) inbound=service.calendar(destination,origin,returnDate.toString().take(7),onlyDirect).days
        } catch(cancel:CancellationException) { throw cancel }
        catch(_:Exception) { error=true;outbound=emptyList();inbound=emptyList() }
        finally { loading=false }
    }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,
        dragHandle=null,shape=RoundedCornerShape(topStart=30.dp,topEnd=30.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom=25.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(text(language,"График цен","Price chart","Narx grafigi","Нарх графиги"),fontSize=25.sp,fontWeight=FontWeight.Bold)
            Text("$origin → $destination",fontSize=15.sp,fontWeight=FontWeight.Bold)
            Text(text(language,"1 пассажир · эконом","1 passenger · economy","1 yo‘lovchi · ekonom","1 йўловчи · эконом"),
                fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.55f))
            if (roundTrip) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha=.065f)).padding(3.dp)) {
                    listOf("$origin → $destination","$destination → $origin").forEachIndexed { i, label ->
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                            .background(if(i==selectedLeg)MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { selectedLeg=i }.padding(12.dp),contentAlignment=Alignment.Center) {
                            Text(label,fontSize=12.sp,fontWeight=FontWeight.Bold)
                        }
                    }
                }
            }
            val days=if(selectedLeg==0)outbound else inbound
            val selected=if(selectedLeg==0)departure else returnDate
            Text(if(selectedLeg==0) text(language,"Туда","Outbound","Borish","Бориш")
                else text(language,"Обратно","Return","Qaytish","Қайтиш"),fontSize=17.sp,fontWeight=FontWeight.Bold)
            if (loading && days.isEmpty()) CircularProgressIndicator()
            else if(days.isEmpty()) Text(if(error) text(language,"Ошибка загрузки","Could not load fares","Narxlar yuklanmadi","Нархлар юкланмади")
                else text(language,"Нет данных по этим датам","No fares for these dates","Bu sanalar uchun narx yo‘q","Бу саналар учун нарх йўқ"),
                fontSize=13.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.65f))
            else {
                val prices=days.map { it.price }.filter { it>0 }
                val min=prices.minOrNull()?:1.0
                val max=prices.maxOrNull()?:min
                val spread=(max-min).coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().height(178.dp).horizontalScroll(rememberScrollState()),
                    verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    days.sortedBy { it.date }.forEach { day ->
                        val isSelected=day.date==selected.toString()
                        val cheapest=kotlin.math.abs(day.price-min)<.5
                        val color=if(isSelected)Color(0xFF007AFF) else if(cheapest)Color(0xFF34C759)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha=.24f)
                        val height=(40+((day.price-min)/spread*70)).dp
                        Column(Modifier.width(44.dp).clickable {
                            val parsed=runCatching { LocalDate.parse(day.date) }.getOrNull()
                            if(parsed!=null) {
                                if(selectedLeg==0) { departure=parsed;if(!returnDate.isAfter(departure))returnDate=departure.plusDays(7) }
                                else if(parsed.isAfter(departure))returnDate=parsed
                            }
                        },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(if(isSelected||cheapest)"$${day.price.toInt()}" else " ",fontSize=10.sp,fontWeight=FontWeight.Bold,color=color)
                            Box(Modifier.width(37.dp).height(height).clip(RoundedCornerShape(topStart=5.dp,topEnd=5.dp)).background(color))
                            Text(day.date.takeLast(2),fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                        }
                    }
                }
            }
            Text(text(language,"Показаны недавно найденные цены в одну сторону, а не стоимость всего билета туда-обратно.",
                "These are recently cached one-way fares, not a complete round-trip fare.",
                "Bular yaqinda topilgan bir tomonlik narxlar, borib-kelish narxi emas.",
                "Бу яқинда топилган бир томонлама нархлар, бориб-келиш нархи эмас."),
                fontSize=12.sp,lineHeight=17.sp,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.6f))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(text(language,"Только прямые рейсы","Non-stop flights only","Faqat to‘g‘ri reyslar","Фақат тўғри рейслар"),
                    Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
                Switch(checked=onlyDirect,onCheckedChange={onlyDirect=it})
            }
            IumrahPressable(onClick={onSelect(departure,returnDate);onDismiss()},modifier=Modifier.fillMaxWidth().height(55.dp),
                cornerRadius=17.dp,background=Color(0xFF007AFF),shadowElevation=0.dp) {
                Box(Modifier.fillMaxWidth().height(55.dp),contentAlignment=Alignment.Center) {
                    Text(text(language,"Готово","Done","Tayyor","Тайёр"),fontWeight=FontWeight.Bold,color=Color.White)
                }
            }
        }
    }
}
