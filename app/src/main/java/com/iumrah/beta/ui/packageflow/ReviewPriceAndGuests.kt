package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.journey.JourneyStore

/** Shared price acknowledgement and party editing for every Trip Summary entry point. */
@Composable internal fun ReviewPriceAndGuests(language:AppLanguage, journey:JourneyStore, chrome:AppChromeStore, enabled:Boolean) {
    val state by journey.state.collectAsState();val quote=state.quote
    var edit by remember { mutableStateOf(false) }
    fun t(r:String,e:String,u:String,c:String)=reviewText(language,r,e,u,c)
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(quote!=null && quote.hasVerifiedBreakdown && !state.priceChangeAccepted) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(t("Стоимость изменилась","Price changed","Narx o‘zgardi","Нарх ўзгарди"),style=MaterialTheme.typography.titleMedium)
                Text(t("Прежняя стоимость","Previous total","Avvalgi narx","Аввалги нарх")+": ${state.acceptedReviewTotal} USD")
                Text(t("Новая стоимость","New total","Yangi narx","Янги нарх")+": ${quote.totalPackagePrice} USD")
                Button(onClick={journey.acceptReviewPrice(quote)},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text(t("Подтвердить новую цену","Accept new price","Yangi narxni tasdiqlash","Янги нархни тасдиқлаш"))}
            }
        }
        OutlinedButton(onClick={edit=true},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text(t("Изменить состав поездки","Edit travel party","Sayohatchilarni o‘zgartirish","Саёҳатчиларни ўзгартириш"))}
    }
    if(edit) {
        var adults by remember { mutableIntStateOf(state.trip.adults) };var rooms by remember { mutableIntStateOf(state.trip.rooms.coerceAtLeast((state.trip.adults+4)/5)) }
        var ages by remember { mutableStateOf(journey.resolvedGuestAges(state)) }
        val minRooms=(adults+4)/5
        fun adjustAdults(n:Int){adults=n;rooms=rooms.coerceIn((n+4)/5,n)}
        AlertDialog(onDismissRequest={edit=false},title={Text(t("Состав поездки","Travel party","Sayohatchilar","Саёҳатчилар"))},text={
            Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Row {Text(t("Взрослые","Adults","Kattalar","Катталар"),Modifier.weight(1f));TextButton(onClick={adjustAdults(adults-1)},enabled=adults>1){Text("−")};Text("$adults");TextButton(onClick={adjustAdults(adults+1)},enabled=adults+ages.size<9){Text("+")}}
                Row {Text(t("Номера","Rooms","Xonalar","Хоналар"),Modifier.weight(1f));TextButton(onClick={rooms--},enabled=rooms>minRooms){Text("−")};Text("$rooms");TextButton(onClick={rooms++},enabled=rooms<adults){Text("+")}}
                ages.forEachIndexed { i,age ->
                    var expanded by remember(i){mutableStateOf(false)}
                    Row {Text(t("Возраст ребёнка","Child age","Bola yoshi","Бола ёши")+" ${i+1}",Modifier.weight(1f));Box {TextButton(onClick={expanded=true}){Text(if(age<0) "—" else "$age")};DropdownMenu(expanded,{expanded=false}){(0..17).forEach{n->DropdownMenuItem(text={Text("$n")},onClick={ages=ages.toMutableList().also{it[i]=n};expanded=false})}}};TextButton(onClick={ages=ages.filterIndexed{index,_->index!=i}}){Text("×")}}
                }
                OutlinedButton(onClick={ages=ages+(-1)},enabled=adults+ages.size<9){Text(t("Добавить ребёнка","Add child","Bola qo‘shish","Бола қўшиш"))}
            }
        },confirmButton={TextButton(enabled=ages.all{it in 0..17},onClick={
            if(journey.state.value==state){journey.applyReviewGuests(adults,rooms,ages);edit=false;if(state.nuitee!=null)chrome.openNuiteeJourneyHotels() else chrome.openHotelSelection()} else edit=false
        }){Text(t("Применить и пересчитать","Apply and reprice","Qo‘llash va hisoblash","Қўллаш ва ҳисоблаш"))}},dismissButton={TextButton(onClick={edit=false}){Text(t("Отмена","Cancel","Bekor qilish","Бекор қилиш"))}})
    }
}
