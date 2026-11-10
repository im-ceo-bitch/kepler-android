package com.iumrah.beta.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/** Mirror of iOS IumrahServiceKind. Shared IDs link the home card to its expanded detail. */
private enum class Service(val id: String, val photo: Int, val icon: CupertinoSymbol) {
    VISA("visa", R.drawable.iumrah_visa_services_hero, CupertinoSymbol.Document),
    FLIGHTS("flights", R.drawable.iumrah_services_flight_hero, CupertinoSymbol.Airplane),
    HOTELS("hotels", R.drawable.iumrah_makkah_background, CupertinoSymbol.Bed),
    TRANSFER("transfer", R.drawable.transfer_carnival, CupertinoSymbol.Car),
    GUIDE("guide", R.drawable.iumrah_care_showcase, CupertinoSymbol.Persons),
    ZIYARAT("ziyarat", R.drawable.ziyarat_quba_1, CupertinoSymbol.Map),
    HARAMAIN("haramain", R.drawable.haramain_hero, CupertinoSymbol.Route),
    SUPPORT("support", R.drawable.ziyarat_quba_3, CupertinoSymbol.HeartFill),
    DIGITAL("digital", R.drawable.iumrah_esim_home_card, CupertinoSymbol.SignalWave),
}

private data class ServiceCopy(val title: String, val category: String, val short: String, val detail: String)

// Same information architecture and cautious visa conditions as the current SwiftUI page.
private fun Service.copy(language: AppLanguage): ServiceCopy {
    val ru = when (this) {
        Service.VISA -> ServiceCopy("Виза в Саудовскую Аравию", "Документы", "Поможем подготовить туристическую визу для умры.", "Помогаем подготовить документы и оформить туристическую визу. Она разрешает совершать умру в установленные властями периоды, но не хадж. Условия и выдачу визы определяет Саудовская Аравия.")
        Service.FLIGHTS -> ServiceCopy("Авиабилеты", "Перелёт", "Прямые рейсы туда и обратно — в приоритете.", "Подбираем перелёты по городу вылета, датам и числу путешественников. Сначала предлагаем доступные прямые рейсы, а при их отсутствии — подходящие альтернативы.")
        Service.HOTELS -> ServiceCopy("Отели", "Проживание", "Отели в Мекке и Медине для вашей семьи.", "Подбираем отели по классу пакета, числу гостей, комфорту номера и близости к мечети. Даты, питание и тип номера указаны в предложении.")
        Service.TRANSFER -> ServiceCopy("Трансфер", "Транспорт", "Встреча в аэропорту и переезды по маршруту.", "Организуем включённые в пакет переезды между аэропортом, отелями и точками маршрута. Автомобиль подбирается по числу пассажиров и багажа.")
        Service.GUIDE -> ServiceCopy("Личный гид", "Сопровождение", "Персональная поддержка вашей семьи в поездке.", "Ваша семья путешествует своим составом, без обязательной большой группы. Гид помогает при встрече, заселении и посещениях, объясняет этапы умры.")
        Service.ZIYARAT -> ServiceCopy("Зияраты", "Посещения", "Исторические места Мекки и Медины.", "Помогаем посетить предусмотренные программой исторические и религиозные места, с понятным порядком, объяснением и маршрутом.")
        Service.HARAMAIN -> ServiceCopy("Поезд Haramain", "Железная дорога", "Скоростное сообщение между городами.", "При необходимости поможем подобрать скоростной поезд между Меккой и Мединой. Наличие билетов и включение в пакет уточняются отдельно.")
        Service.SUPPORT -> ServiceCopy("Поддержка iumrah", "Забота", "Помощь до поездки, во время неё и после.", "Отвечаем на вопросы по программе, документам и организационным вопросам. Связь с командой доступна в приложении.")
        Service.DIGITAL -> ServiceCopy("eSIM и цифровые сервисы", "Связь", "Удобная связь и информация в поездке.", "Цифровые инструменты помогают следить за поездкой, статусом рейса и подключать связь. Доступность конкретных услуг зависит от вашего пакета.")
    }
    val en = when (this) {
        Service.VISA -> ServiceCopy("Saudi Arabia visa", "Documents", "We help arrange a tourist visa for Umrah.", "We help prepare documents and apply for a tourist visa. Umrah is permitted during authorized periods but Hajj is not. Saudi authorities determine approval, validity and seasonal restrictions.")
        Service.FLIGHTS -> ServiceCopy("Flights", "Air travel", "Nonstop round-trip flights are our priority.", "Flights are selected for your departure city, dates and travelers. Available direct services come first; suitable alternatives follow when needed.")
        Service.HOTELS -> ServiceCopy("Hotels", "Accommodation", "Stays in Makkah and Madinah for your family.", "We select stays according to package tier, guest count, comfort and mosque proximity. Dates, room and meal conditions appear in your quote.")
        Service.TRANSFER -> ServiceCopy("Transfers", "Transport", "Airport pickup and planned rides.", "We organize the transfers included in your itinerary between airports, hotels and key stops, with a vehicle matching your group and luggage.")
        Service.GUIDE -> ServiceCopy("Personal guide", "Assistance", "Individual support throughout your family's trip.", "Your family travels privately, rather than joining a required large group. The guide assists with arrival, orientation, scheduled visits and Umrah steps.")
        Service.ZIYARAT -> ServiceCopy("Ziyarat visits", "Excursions", "Meaningful visits in Makkah and Madinah.", "We help arrange visits to historical and religious places included in your selected itinerary, with route information and context.")
        Service.HARAMAIN -> ServiceCopy("Haramain train", "Rail travel", "High-speed intercity journeys.", "We can help find train journeys between Makkah and Madinah. Availability and package inclusion depend on the selected itinerary.")
        Service.SUPPORT -> ServiceCopy("iumrah support", "Care", "Assistance before, during and after your journey.", "Our team helps with itinerary, documents and travel questions. You can contact support from the app.")
        Service.DIGITAL -> ServiceCopy("eSIM & digital services", "Connectivity", "Useful digital tools for your journey.", "Follow your flight and journey status and arrange connectivity. Individual features may depend on your package.")
    }
    val uz = when (this) {
        Service.VISA -> ServiceCopy("Saudiya Arabistoni vizasi", "Hujjatlar", "Umra uchun turistik viza rasmiylashtirishda yordam.", "Hujjatlarni tayyorlash va turistik vizaga ariza berishda yordam beramiz. Viza umra uchun belgilangan muddatlarda amal qiladi, haj uchun emas. Shartlarni Saudiya Arabistoni belgilaydi.")
        Service.FLIGHTS -> ServiceCopy("Aviachiptalar", "Parvoz", "Avvalo to‘g‘ridan-to‘g‘ri borish va qaytish reyslari.", "Shahar, sana va yo‘lovchilar soniga mos parvozlarni tanlaymiz. Avvalo mavjud to‘g‘ridan-to‘g‘ri reyslar tavsiya etiladi.")
        Service.HOTELS -> ServiceCopy("Mehmonxonalar", "Turar joy", "Makka va Madinada oilangiz uchun turar joy.", "Paket darajasi, mehmonlar soni, qulaylik va masjidgacha masofa bo‘yicha mehmonxonalar tanlaymiz.")
        Service.TRANSFER -> ServiceCopy("Transfer", "Transport", "Aeroportda kutib olish va rejalashtirilgan safarlar.", "Paketga kiruvchi aeroport va mehmonxonalar orasidagi transferlarni yo‘lovchilar va yuk soniga mos tashkil qilamiz.")
        Service.GUIDE -> ServiceCopy("Shaxsiy gid", "Hamrohlik", "Safar davomida oilangizga yordam.", "Oilangiz katta guruhsiz sayohat qiladi. Gid kutib olish, joylashish va umra bosqichlarida yordam beradi.")
        Service.ZIYARAT -> ServiceCopy("Ziyoratlar", "Tashriflar", "Makka va Madinadagi tarixiy joylar.", "Tanlangan dasturdagi tarixiy va diniy joylarga tashrifni tashkil qilishga yordam beramiz.")
        Service.HARAMAIN -> ServiceCopy("Haramain poyezdi", "Temir yo‘l", "Shaharlararo tezkor poyezd.", "Makka va Madina orasida poyezd variantlarini topishga yordam beramiz. Mavjudlik paketga bog‘liq.")
        Service.SUPPORT -> ServiceCopy("iumrah yordami", "Yordam", "Safardan oldin va safar davomida qo‘llab-quvvatlash.", "Jamoamiz safar, hujjatlar va tashkiliy savollarga javob beradi.")
        Service.DIGITAL -> ServiceCopy("eSIM va servislar", "Aloqa", "Safar uchun raqamli imkoniyatlar.", "Parvoz holati va internet ulanishi kabi xizmatlar paketingizga bog‘liq bo‘lishi mumkin.")
    }
    return when (language) {
        AppLanguage.RUSSIAN -> ru
        AppLanguage.UZBEK -> uz
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy(uzCyr(uz.title), uzCyr(uz.category), uzCyr(uz.short), uzCyr(uz.detail))
        else -> en
    }
}
/** Uzbek Latin to Cyrillic fallback for service copy until editorial review. */
private fun uzCyr(input: String): String {
    val multi = linkedMapOf(
        "O‘" to "Ў", "o‘" to "ў", "O'" to "Ў", "o'" to "ў",
        "G‘" to "Ғ", "g‘" to "ғ", "G'" to "Ғ", "g'" to "ғ",
        "Sh" to "Ш", "sh" to "ш", "Ch" to "Ч", "ch" to "ч",
        "Yo" to "Ё", "yo" to "ё", "Yu" to "Ю", "yu" to "ю", "Ya" to "Я", "ya" to "я",
        "Ng" to "Нг", "ng" to "нг",
    )
    val single = mapOf('a' to 'а', 'b' to 'б', 'd' to 'д', 'e' to 'е', 'f' to 'ф', 'g' to 'г',
        'h' to 'ҳ', 'i' to 'и', 'j' to 'ж', 'k' to 'к', 'l' to 'л', 'm' to 'м', 'n' to 'н',
        'o' to 'о', 'p' to 'п', 'q' to 'қ', 'r' to 'р', 's' to 'с', 't' to 'т', 'u' to 'у',
        'v' to 'в', 'x' to 'х', 'y' to 'й', 'z' to 'з')
    val buffer = StringBuilder()
    var index = 0
    while (index < input.length) {
        val digraph = multi.entries.firstOrNull { input.startsWith(it.key, index) }
        if (digraph != null) {
            buffer.append(digraph.value)
            index += digraph.key.length
        } else {
            val c = input[index]
            val mapped = single[c.lowercaseChar()]
            buffer.append(if (mapped == null) c else if (c.isUpperCase()) mapped.uppercaseChar() else mapped)
            index++
        }
    }
    return buffer.toString()
}

private fun heading(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Сервисы iumrah"
    AppLanguage.UZBEK -> "iumrah servislar"
    AppLanguage.UZBEK_CYRILLIC -> "iumrah сервислари"
    else -> "iumrah Services"
}
private fun subtitle(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Виза, перелёт, отели, трансфер и сопровождение — всё необходимое для умры в одном пакете."
    AppLanguage.UZBEK -> "Umra uchun viza, parvoz, mehmonxona, transfer va hamrohlik — bitta paketda."
    AppLanguage.UZBEK_CYRILLIC -> "Умра учун виза, парвоз, меҳмонхона, трансфер ва ҳамроҳлик — битта пакетда."
    else -> "Visa, flights, hotels, transfers and personal assistance — one journey, one package."
}

/** Home carousel: 9 iOS service IDs, same order and card envelope 282 × 322. */
@Composable
fun IumrahServicesCarousel(language: AppLanguage, chrome: AppChromeStore) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Text(heading(language), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle(language), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Service.entries) { service ->
                val copy = service.copy(language)
                Column(Modifier.width(282.dp).height(322.dp).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(.75.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .clickable { chrome.openIumrahServices(service.id) }) {
                    Image(painterResource(service.photo), copy.title, Modifier.fillMaxWidth().height(174.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CupertinoIcon(service.icon, null, Modifier.size(17.dp))
                            Text(copy.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
                        }
                        Text(copy.title, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(copy.short, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Text(when (language) {
            AppLanguage.RUSSIAN -> "Все сервисы"
            AppLanguage.UZBEK -> "Barcha servislar"
            AppLanguage.UZBEK_CYRILLIC -> "Барча сервислар"
            else -> "All services"
        }, modifier = Modifier.align(Alignment.End).clickable { chrome.openIumrahServices("visa") }.padding(vertical = 4.dp), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Same selected-card deep link and expandable reference sections as iOS IumrahServicesView. */
@Composable
fun IumrahServicesScreen(language: AppLanguage, initialService: String, chrome: AppChromeStore) {
    val selectedStart = Service.entries.indexOfFirst { it.id == initialService }.coerceAtLeast(0)
    var expanded by remember(initialService) { mutableStateOf<String?>(Service.entries[selectedStart].id) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedStart + 1)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 50.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(43.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable { chrome.back() }, contentAlignment = Alignment.Center) {
                        CupertinoIcon(CupertinoSymbol.ChevronLeft, "Back", Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(heading(language), fontSize = 29.sp, fontWeight = FontWeight.Bold)
                }
                Text(when (language) {
                    AppLanguage.RUSSIAN -> "Всё, что мы организуем для поездки. Откройте сервис, чтобы узнать подробности."
                    AppLanguage.UZBEK -> "Safaringiz uchun xizmatlar. Batafsil ma’lumot uchun servisni oching."
                    AppLanguage.UZBEK_CYRILLIC -> "Сафарингиз учун хизматлар. Батафсил маълумот учун сервисни очинг."
                    else -> "Everything we organize for your journey. Tap a service to learn more."
                }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f))
            }
        }
        items(Service.entries) { service ->
            val copy = service.copy(language)
            val open = expanded == service.id
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                .border(.75.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp)).animateContentSize()) {
                Row(Modifier.fillMaxWidth().clickable { expanded = if (open) null else service.id }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(37.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CupertinoIcon(service.icon, null, Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(copy.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(copy.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                    CupertinoIcon(if (open) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(16.dp))
                }
                AnimatedVisibility(open) {
                    Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Image(painterResource(service.photo), copy.title, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                        Text(copy.detail, fontSize = 14.sp, lineHeight = 20.sp)
                        if (service == Service.VISA) VisaInformation(language)
                        Text(when (language) {
                            AppLanguage.RUSSIAN -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Зависит от пакета" else "Компонент поездки"
                            AppLanguage.UZBEK -> "Paket shartlariga bog‘liq"
                            AppLanguage.UZBEK_CYRILLIC -> "Пакет шартларига боғлиқ"
                            else -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Depends on package" else "Journey component"
                        }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .53f))
                    }
                }
            }
        }
    }
}

@Composable
private fun VisaInformation(language: AppLanguage) {
    val ru = language == AppLanguage.RUSSIAN
    val uz = language == AppLanguage.UZBEK || language == AppLanguage.UZBEK_CYRILLIC
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        listOf(
            Pair(if (ru) "Однократная" else if (uz) "Bir martalik" else "Single entry", if (ru) "90 дней" else if (uz) "90 kun" else "90 days"),
            Pair(if (ru) "Многократная" else if (uz) "Ko‘p martalik" else "Multiple entry", if (ru) "1 год" else if (uz) "1 yil" else "1 year"),
        ).forEach { (title, validity) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(validity, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(if (ru) "Срок действия" else if (uz) "Amal qilish muddati" else "Visa validity", fontSize = 11.sp)
            }
        }
    }
    Text(if (ru) "Срок действия визы не равен разрешённому пребыванию. Пребывание — до 90 дней по условиям визы. Тип визы, одобрение и сезонные ограничения устанавливают власти Саудовской Аравии; выдача не гарантирована."
         else if (uz) "Viza amal qilish muddati va qolish muddati turlicha. Qolish muddati berilgan vizaga ko‘ra 90 kungacha. Ruxsatni Saudiya Arabistoni belgilaydi."
         else "Visa validity differs from permitted stay. Stays can be up to 90 days subject to the visa's terms. Saudi authorities control issuance, visa type and seasonal restrictions; approval is not guaranteed.",
        fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
}
