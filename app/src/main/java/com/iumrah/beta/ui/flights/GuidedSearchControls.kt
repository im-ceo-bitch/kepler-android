package com.iumrah.beta.ui.flights

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun GuidedSearchControls(language: AppLanguage, filters: GuidedFilters, onFilters: (GuidedFilters)->Unit,
    airlines: List<String>, observations: List<Pair<LocalDate, Double?>>, selected: LocalDate, minimum: LocalDate,
    onCalendar: ()->Unit, onSelectDay: (LocalDate)->Unit) {
    fun t(r:String,e:String,u:String,c:String) = when(language) { AppLanguage.RUSSIAN -> r; AppLanguage.UZBEK -> u; AppLanguage.UZBEK_CYRILLIC -> c; else -> e }
    val sortLabels = listOf(t("По дате","Date","Sana","Сана"),t("Дешевле","Cheapest","Arzonroq","Арзонроқ"),t("Быстрее","Fastest","Tezroq","Тезроқ"))
    var airlineMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val days = remember(observations,selected,minimum) { GuidedFarePolicy.window(observations,selected,minimum) }
    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=6.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FarePill(t("Прямой","Direct","To‘g‘ri","Тўғри"), filters.direct) { onFilters(filters.copy(direct=!filters.direct)) }
            Box {
                FarePill(filters.airline.ifEmpty { t("Авиакомпания","Airline","Aviakompaniya","Авиакомпания") },filters.airline.isNotEmpty()) { airlineMenu=true }
                DropdownMenu(airlineMenu,{airlineMenu=false}) {
                    DropdownMenuItem(text={Text(t("Все","All","Barchasi","Барчаси"))},onClick={onFilters(filters.copy(airline=""));airlineMenu=false})
                    airlines.forEach { code -> DropdownMenuItem(text={Text(FlightReferenceCatalog.airlineName(code,code))},onClick={onFilters(filters.copy(airline=code));airlineMenu=false}) }
                }
            }
            Box {
                FarePill(sortLabels[filters.sort.ordinal],false) { sortMenu=true }
                DropdownMenu(sortMenu,{sortMenu=false}) { GuidedSort.entries.forEach { sort -> DropdownMenuItem(text={Text(sortLabels[sort.ordinal])},onClick={onFilters(filters.copy(sort=sort));sortMenu=false}) } }
            }
            FarePill(t("Календарь","Calendar","Taqvim","Тақвим"),false,onCalendar)
        }
        Text(t("Цены по датам","Fares by date","Sanalar bo‘yicha narx","Саналар бўйича нарх"),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val inset=((maxWidth-64.dp)/2).coerceAtLeast(0.dp)
            val state=rememberLazyListState(days.indexOfFirst { it.date==selected }.coerceAtLeast(0))
            val dragging by state.interactionSource.collectIsDraggedAsState()
            var userScroll by remember { mutableStateOf(false) }
            val currentSelection by rememberUpdatedState(onSelectDay)
            LaunchedEffect(dragging) { if(dragging) userScroll=true }
            LaunchedEffect(state.isScrollInProgress,dragging) {
                if(!state.isScrollInProgress && !dragging && userScroll) {
                    userScroll=false
                    val info=state.layoutInfo
                    val center=(info.viewportStartOffset+info.viewportEndOffset)/2
                    val index=info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset+it.size/2-center) }?.index
                    index?.let{days.getOrNull(it)?.date}?.takeIf{it!=selected}?.let(currentSelection)
                }
            }
            LaunchedEffect(selected,days.map { it.date }) { if(!dragging && !userScroll) state.scrollToItem(days.indexOfFirst { it.date==selected }.coerceAtLeast(0)) }
            val maxPrice=days.mapNotNull { it.price }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
            LazyRow(state=state,flingBehavior=rememberSnapFlingBehavior(lazyListState=state,snapPosition=SnapPosition.Center),contentPadding=PaddingValues(horizontal=inset),horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.height(112.dp)) {
                items(days,key={it.date.toString()}) { day ->
                    val active=day.date==selected
                    val ink=MaterialTheme.colorScheme.onSurface
                    Column(Modifier.width(64.dp).fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(if(active)ink.copy(alpha=.06f) else androidx.compose.ui.graphics.Color.Transparent).clickable { onSelectDay(day.date) }.padding(vertical=5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Spacer(Modifier.weight(1f))
                        Text(day.price?.let { "$"+"%,.0f".format(Locale.US,it) } ?: "—",fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1)
                        Box(Modifier.width(40.dp).height((day.price?.let { (55*it/maxPrice).coerceAtLeast(10.0) } ?: 3.0).dp).clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha=if(active)1f else if(day.price==null).12f else .24f)))
                        Text(day.date.format(DateTimeFormatter.ofPattern("d MMM",language.locale)),fontSize=11.sp,maxLines=1)
                    }
                }
            }
        }
        if(days.none { it.price!=null }) Text(t("Цены появятся после поиска","Fares appear after search","Narxlar qidiruvdan keyin chiqadi","Нархлар қидирувдан кейин чиқади"),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun FarePill(title:String,active:Boolean,onClick:()->Unit) {
    val ink=MaterialTheme.colorScheme.onSurface
    Box(Modifier.clip(RoundedCornerShape(50)).background(if(active)ink else MaterialTheme.colorScheme.surface).clickable(onClick=onClick).padding(horizontal=14.dp,vertical=10.dp)) {
        Text(title,fontSize=14.sp,fontWeight=FontWeight.Medium,color=if(active)MaterialTheme.colorScheme.surface else ink)
    }
}
