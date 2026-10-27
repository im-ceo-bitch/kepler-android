package com.iumrah.beta.ui.trip
import com.iumrah.beta.ui.cupertino.Icon

import com.iumrah.beta.ui.cupertino.CupertinoSymbol

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.design.IumrahFontFamily
import com.iumrah.beta.ui.cupertino.IumrahIconBadge
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.flight.FlightReferenceCatalog
import com.iumrah.beta.models.flight.FlightCabinClass
import com.iumrah.beta.models.flight.FlightInfantSeating
import com.iumrah.beta.models.flight.FlightSearchFilters
import com.iumrah.beta.models.flight.FlightStopsPreference
import com.iumrah.beta.models.flight.FlightTimeWindow
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.iumrahCard
import com.iumrah.beta.ui.generator.generatorCardColor
import com.iumrah.beta.ui.generator.generatorRaisedColor
import com.iumrah.beta.ui.generator.generatorPageColor

@Composable
fun FlightSearchFiltersCard(
    filters: FlightSearchFilters,
    infantCount: Int,
    language: AppLanguage,
    onChange: (FlightSearchFilters) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, IumrahMotion.selection, label = "filters-chevron")
    val copy = remember(language) { FilterCopy(language) }

    Column(
        modifier = Modifier.iumrahCard(background = generatorCardColor()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IumrahPressable(
            onClick = {
                expanded = !expanded
                IumrahHaptics.selection(view)
            },
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            background = Color.Transparent,
            pressedScale = .985f,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IumrahIconBadge(
                    symbol = CupertinoSymbol.Sliders,
                    tint = IumrahColors.SystemGray,
                    size = 38.dp,
                    symbolSize = 16.dp,
                    cornerRadius = 13.dp,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(copy.title, style = MaterialTheme.typography.titleMedium)
                    Text(summary(filters, copy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f), maxLines = 2)
                }
                Icon(CupertinoSymbol.ChevronDown, contentDescription = null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(IumrahMotion.fastFade) + expandVertically(),
            exit = fadeOut(IumrahMotion.fastFade) + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FilterSection(copy.cabin) {
                    EnumChips(FlightCabinClass.entries, filters.cabinClass, { cabinTitle(it, copy) }) {
                        onChange(filters.copy(cabinClass = it)); IumrahHaptics.selection(view)
                    }
                }
                FilterSection(copy.stops) {
                    EnumChips(FlightStopsPreference.entries, filters.stops, { stopsTitle(it, copy) }) {
                        onChange(filters.copy(stops = it)); IumrahHaptics.selection(view)
                    }
                }
                // iOS uses two 54pt menu tiles, not two full-width chip carousels.
                FilterSection(copy.time) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterMenuTile(
                            title = copy.departure,
                            selected = filters.departureWindow,
                            values = FlightTimeWindow.entries,
                            label = { timeTitle(it, copy) },
                            modifier = Modifier.weight(1f),
                        ) { onChange(filters.copy(departureWindow = it)); IumrahHaptics.selection(view) }
                        FilterMenuTile(
                            title = copy.arrival,
                            selected = filters.arrivalWindow,
                            values = FlightTimeWindow.entries,
                            label = { timeTitle(it, copy) },
                            modifier = Modifier.weight(1f),
                        ) { onChange(filters.copy(arrivalWindow = it)); IumrahHaptics.selection(view) }
                    }
                }
                FilterSection(copy.baggage) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterMenuTile(
                            title = copy.carryOn,
                            selected = filters.minCarryOnBags,
                            values = (0..2).toList(),
                            label = { if (it == 0) copy.any else "$it+" },
                            modifier = Modifier.weight(1f),
                            trailingSymbol = CupertinoSymbol.Suitcase,
                        ) { onChange(filters.copy(minCarryOnBags = it)); IumrahHaptics.selection(view) }
                        FilterMenuTile(
                            title = copy.checked,
                            selected = filters.minCheckedBags,
                            values = (0..2).toList(),
                            label = { if (it == 0) copy.any else "$it+" },
                            modifier = Modifier.weight(1f),
                            trailingSymbol = CupertinoSymbol.Suitcase,
                        ) { onChange(filters.copy(minCheckedBags = it)); IumrahHaptics.selection(view) }
                    }
                }
                FilterSection(copy.price) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(generatorRaisedColor())
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("$", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        val amount = filters.maxPriceUSD?.toString().orEmpty()
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (amount.isEmpty()) Text(
                                copy.noLimit,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
                            )
                            BasicTextField(
                                value = amount,
                                onValueChange = { raw ->
                                    val digits = raw.filter(Char::isDigit).take(6)
                                    onChange(filters.copy(maxPriceUSD = digits.toIntOrNull()))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    fontFamily = IumrahFontFamily,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                        if (filters.maxPriceUSD != null) {
                            IumrahPressable(
                                onClick = { onChange(filters.copy(maxPriceUSD = null)); IumrahHaptics.selection(view) },
                                modifier = Modifier.size(27.dp),
                                cornerRadius = 99.dp,
                                background = Color.Transparent,
                            ) {
                                Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
                                    Icon(CupertinoSymbol.Close, copy.reset, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                                }
                            }
                        }
                    }
                }
                AirlineFilters(filters, copy) { onChange(it); IumrahHaptics.selection(view) }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(copy.protectedConnections, style = MaterialTheme.typography.titleSmall)
                        Text(copy.protectedConnectionsBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.50f))
                    }
                    IumrahPressable(
                        onClick = {
                            onChange(filters.copy(allowSelfTransfer = !filters.allowSelfTransfer))
                            IumrahHaptics.selection(view)
                        },
                        modifier = Modifier.size(width = 51.dp, height = 31.dp),
                        cornerRadius = 99.dp,
                        background = if (!filters.allowSelfTransfer) Color(0xFF34C759) else MaterialTheme.colorScheme.onSurface.copy(alpha = .14f),
                    ) {
                        Box(Modifier.fillMaxWidth().height(31.dp).padding(2.dp)) {
                            Box(
                                Modifier.align(if (!filters.allowSelfTransfer) Alignment.CenterEnd else Alignment.CenterStart)
                                    .size(27.dp).background(Color.White, CircleShape)
                            )
                        }
                    }
                }

                if (infantCount > 0) {
                    FilterSection(copy.infant) {
                        EnumChips(FlightInfantSeating.entries, filters.infantSeating, { if (it == FlightInfantSeating.LAP) copy.lap else copy.seat }) {
                            onChange(filters.copy(infantSeating = it)); IumrahHaptics.selection(view)
                        }
                    }
                }

                // iOS reset is a plain secondary-label button, without a filled capsule.
                IumrahPressable(
                    onClick = { onChange(FlightSearchFilters()); IumrahHaptics.selection(view) },
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    background = Color.Transparent,
                ) {
                    Text(
                        copy.reset,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                    )
                }
            }
        }
    }
}

@Composable private fun FilterSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha=.52f))
        content()
    }
}

@Composable private fun <T> EnumChips(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values, key = { it.toString() }) { value ->
            val isSelected = value == selected
            IumrahPressable(
                onClick = { onSelect(value) },
                cornerRadius = 99.dp,
                background = if (isSelected) MaterialTheme.colorScheme.onSurface else generatorRaisedColor(),
                pressedScale = .965f,
            ) {
                Text(
                    label(value),
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    color = if (isSelected) generatorPageColor() else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** Matches iOS FlightSearchFiltersCard.menuTile/countMenu (54pt, 16pt corners).
 * Uses native Compose menus and genuine Cupertino glyphs, without new networking
 * or changes to FlightSearchFilters fields/serialization. */
@Composable
private fun <T> FilterMenuTile(
    title: String,
    selected: T,
    values: List<T>,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    trailingSymbol: CupertinoSymbol = CupertinoSymbol.ChevronDown,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IumrahPressable(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            cornerRadius = 16.dp,
            background = generatorRaisedColor(),
        ) {
            Column(
                Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 13.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label(selected), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Icon(trailingSymbol, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            values.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(label(candidate), style = MaterialTheme.typography.bodyMedium) },
                    onClick = { open = false; onSelect(candidate) },
                    trailingIcon = { if (candidate == selected) Icon(CupertinoSymbol.Checkmark, null, Modifier.size(15.dp)) },
                )
            }
        }
    }
}

private enum class AirlineMode { ALL, INCLUDE, EXCLUDE }

@Composable private fun AirlineFilters(filters: FlightSearchFilters, copy: FilterCopy, onChange: (FlightSearchFilters) -> Unit) {
    val mode = when {
        filters.normalizedAirlinesInclude.isNotEmpty() -> AirlineMode.INCLUDE
        filters.normalizedAirlinesExclude.isNotEmpty() -> AirlineMode.EXCLUDE
        else -> AirlineMode.ALL
    }
    val selected = if (mode == AirlineMode.EXCLUDE) filters.normalizedAirlinesExclude.toSet() else filters.normalizedAirlinesInclude.toSet()
    FilterSection(copy.airlines) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            AirlineMode.entries.forEach { candidate ->
                val active = candidate == mode
                IumrahPressable(
                    onClick = {
                        val existing = selected.toList()
                        onChange(when (candidate) {
                            AirlineMode.ALL -> filters.copy(airlinesInclude = emptyList(), airlinesExclude = emptyList())
                            AirlineMode.INCLUDE -> filters.copy(airlinesInclude = existing, airlinesExclude = emptyList())
                            AirlineMode.EXCLUDE -> filters.copy(airlinesInclude = emptyList(), airlinesExclude = existing)
                        })
                    },
                    modifier = Modifier.weight(1f),
                    cornerRadius = 99.dp,
                    background = if (active) MaterialTheme.colorScheme.onSurface else generatorRaisedColor(),
                    pressedScale = .96f,
                ) {
                    Text(
                        when(candidate) { AirlineMode.ALL -> copy.allAirlines; AirlineMode.INCLUDE -> copy.only; AirlineMode.EXCLUDE -> copy.exclude },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = if (active) generatorPageColor() else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        if (mode != AirlineMode.ALL) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FlightReferenceCatalog.filterAirlines, key = { it.iata }) { airline ->
                    val active = selected.contains(airline.iata)
                    IumrahPressable(
                        onClick = {
                            val values = selected.toMutableSet().apply { if (!add(airline.iata)) remove(airline.iata) }.sorted()
                            onChange(if (mode == AirlineMode.EXCLUDE) filters.copy(airlinesExclude = values, airlinesInclude = emptyList()) else filters.copy(airlinesInclude = values, airlinesExclude = emptyList()))
                        },
                        cornerRadius = 99.dp,
                        background = if (active) MaterialTheme.colorScheme.onSurface else generatorRaisedColor(),
                        pressedScale = .96f,
                    ) {
                        Text("${airline.iata} · ${airline.name}", modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp), color = if (active) generatorPageColor() else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun summary(f: FlightSearchFilters, c: FilterCopy): String = buildList {
    add(cabinTitle(f.cabinClass, c)); add(stopsTitle(f.stops, c))
    if (f.minCheckedBags > 0) add("${f.minCheckedBags}+ ${c.checked}")
    f.maxPriceUSD?.let { add("≤ $$it") }
}.joinToString(" · ")

private fun cabinTitle(value: FlightCabinClass, c: FilterCopy) = when(value) {
    FlightCabinClass.ECONOMY -> c.economy; FlightCabinClass.PREMIUM_ECONOMY -> c.premium; FlightCabinClass.BUSINESS -> c.business; FlightCabinClass.FIRST -> c.first
}
private fun stopsTitle(value: FlightStopsPreference, c: FilterCopy) = when(value) {
    FlightStopsPreference.ANY -> c.anyStops; FlightStopsPreference.NONSTOP -> c.direct; FlightStopsPreference.UP_TO_ONE -> c.oneStop; FlightStopsPreference.UP_TO_TWO -> c.twoStops
}
private fun timeTitle(value: FlightTimeWindow, c: FilterCopy) = when(value) {
    FlightTimeWindow.ANY -> c.any; FlightTimeWindow.NIGHT -> c.night; FlightTimeWindow.MORNING -> c.morning; FlightTimeWindow.AFTERNOON -> c.afternoon; FlightTimeWindow.EVENING -> c.evening
}

private class FilterCopy(language: AppLanguage) {
    private val v: List<String> = when(language) {
        AppLanguage.RUSSIAN -> listOf("Параметры перелёта","Класс","Пересадки","Вылет","Прилёт","Багаж включён","Ручная кладь","Багаж","Максимальная цена перелёта","Без лимита","Авиакомпании","Без отдельных билетов","Исключать self-transfer, когда источник может его определить","Младенцы","На руках","Отдельное место","Сбросить фильтры","Любое","Эконом","Премиум","Бизнес","Первый","Любые","Прямой","До 1","До 2","Ночь","Утро","День","Вечер","Все","Только","Исключить")
        AppLanguage.ENGLISH -> listOf("Flight preferences","Cabin","Stops","Departure","Arrival","Included bags","Carry-on","Checked","Maximum flight price","No limit","Airlines","No separate tickets","Exclude identifiable self-transfer itineraries","Infants","On lap","Own seat","Reset filters","Any","Economy","Premium","Business","First","Any","Nonstop","Up to 1","Up to 2","Night","Morning","Afternoon","Evening","All","Only","Exclude")
        AppLanguage.UZBEK -> listOf("Parvoz parametrlari","Klass","To‘xtashlar","Uchish","Yetib kelish","Bagaj","Qo‘l yuki","Bagaj","Maksimal narx","Cheklovsiz","Aviakompaniyalar","Alohida chiptalarsiz","Aniqlangan self-transfer variantlarini chiqarib tashlash","Chaqaloqlar","Qo‘lda","Alohida joy","Filtrlarni tozalash","Istalgan","Ekonom","Premium","Biznes","Birinchi","Istalgan","To‘g‘ridan","1 gacha","2 gacha","Tun","Ertalab","Kunduzi","Kechqurun","Barchasi","Faqat","Chiqarish")
        AppLanguage.UZBEK_CYRILLIC -> listOf("Парвоз параметрлари","Класс","Тўхташлар","Учиш","Етиб келиш","Багаж","Қўл юки","Багаж","Максимал нарх","Чекловсиз","Авиакомпаниялар","Алоҳида чипталарсиз","Аниқланган self-transfer вариантларини чиқариб ташлаш","Чақалоқлар","Қўлда","Алоҳида жой","Филтрларни тозалаш","Исталган","Эконом","Премиум","Бизнес","Биринчи","Исталган","Тўғридан","1 гача","2 гача","Тун","Эрталаб","Кундузи","Кечқурун","Барчаси","Фақат","Чиқариш")
    }
    val time = when(language) { AppLanguage.RUSSIAN -> "Время"; AppLanguage.ENGLISH -> "Time"; AppLanguage.UZBEK -> "Vaqt"; AppLanguage.UZBEK_CYRILLIC -> "Вақт" }
    val title=v[0]; val cabin=v[1]; val stops=v[2]; val departure=v[3]; val arrival=v[4]; val baggage=v[5]; val carryOn=v[6]; val checked=v[7]; val price=v[8]; val noLimit=v[9]; val airlines=v[10]; val protectedConnections=v[11]; val protectedConnectionsBody=v[12]; val infant=v[13]; val lap=v[14]; val seat=v[15]; val reset=v[16]; val any=v[17]; val economy=v[18]; val premium=v[19]; val business=v[20]; val first=v[21]; val anyStops=v[22]; val direct=v[23]; val oneStop=v[24]; val twoStops=v[25]; val night=v[26]; val morning=v[27]; val afternoon=v[28]; val evening=v[29]; val allAirlines=v[30]; val only=v[31]; val exclude=v[32]
}
