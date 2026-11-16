package com.iumrah.beta.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalUriHandler
import com.iumrah.beta.core.design.IumrahHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
    GUIDE("guide", R.drawable.gift_card_journey, CupertinoSymbol.Persons),
    ZIYARAT("ziyarat", R.drawable.ziyarat_quba_1, CupertinoSymbol.Map),
    HARAMAIN("haramain", R.drawable.haramain_hero, CupertinoSymbol.Route),
    SUPPORT("support", R.drawable.iumrah_care_team_hero, CupertinoSymbol.Heart),
    DIGITAL("digital", R.drawable.iumrah_esim_home_card, CupertinoSymbol.SimCard),
}

private data class ServiceCopy(val title: String, val category: String, val short: String, val detail: String)

// Same information architecture and cautious visa conditions as the current SwiftUI page.
private fun Service.copy(language: AppLanguage): ServiceCopy = when (this) {
    Service.VISA -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Виза в Саудовскую Аравию", "Документы", "Туристическая виза для совершения умры.", "Помогаем подготовить документы и оформить туристическую визу для поездки в Саудовскую Аравию. Такая виза разрешает совершать умру в установленные властями периоды, но не хадж. Тип визы, даты и условия выдачи определяются Саудовской Аравией.")
        AppLanguage.UZBEK -> ServiceCopy("Saudiya Arabistoni vizasi", "Hujjatlar", "Umra qilishga ruxsat beruvchi turistik viza.", "Saudiya Arabistoniga sayohat uchun hujjatlarni tayyorlash va turistik viza rasmiylashtirishda yordam beramiz. Ushbu viza ruxsat etilgan davrlarda umra qilishga imkon beradi, haj uchun esa amal qilmaydi. Viza turi va shartlarini Saudiya Arabistoni belgilaydi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Саудия Арабистони визаси", "Ҳужжатлар", "Умра қилишга рухсат берувчи туристик виза.", "Саудия Арабистонига саёҳат учун ҳужжатларни тайёрлаш ва туристик виза расмийлаштиришда ёрдам берамиз. Ушбу виза рухсат этилган даврларда умра қилишга имкон беради, ҳаж учун эса амал қилмайди. Виза тури ва шартларини Саудия Арабистони белгилайди.")
        else -> ServiceCopy("Saudi Arabia visa", "Documents", "A tourist visa that permits Umrah.", "We assist with preparing documents and applying for a tourist visa to Saudi Arabia. Umrah is permitted during authorized periods, but Hajj is not. Visa type, dates and issuance terms are determined by Saudi authorities.")
    }
    Service.FLIGHTS -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Авиабилеты", "Перелёт", "Прежде всего прямые рейсы туда и обратно.", "Подбираем билеты в Саудовскую Аравию и обратно с учётом города вылета, дат и состава семьи. В первую очередь предлагаем доступные прямые рейсы, чтобы сократить пересадки и время в пути. Если прямых рейсов нет, показываем подходящие альтернативы.")
        AppLanguage.UZBEK -> ServiceCopy("Aviachiptalar", "Parvoz", "Avvalo to‘g‘ridan-to‘g‘ri borish va qaytish reyslari.", "Jo‘nash shahri, sanalar va oila tarkibiga qarab borish hamda qaytish chiptalarini tanlaymiz. Vaqt va tranzitni kamaytirish uchun avvalo mavjud to‘g‘ridan-to‘g‘ri reyslarni tavsiya qilamiz.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Авиачипталар", "Парвоз", "Аввало тўғридан-тўғри бориш ва қайтиш рейслари.", "Жўнаш шаҳри, саналар ва оила таркибига қараб бориш ҳамда қайтиш чипталарини танлаймиз. Вақт ва транзитни камайтириш учун аввало мавжуд тўғридан-тўғри рейсларни тавсия қиламиз.")
        else -> ServiceCopy("Flights", "Air travel", "Nonstop round-trip flights are our priority.", "We choose outbound and return flights based on your city, dates and family size. Available nonstop flights are prioritized to reduce connections and travel time. When nonstop service is unavailable, we present suitable alternatives.")
    }
    Service.HOTELS -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Отели", "Проживание", "Проживание в Мекке и Медине для вашей семьи.", "Подбираем отели в Мекке и Медине с учётом класса пакета, количества гостей, удобства размещения и расстояния до мечети. Даты, тип номера и питание показываются в вашей конфигурации поездки.")
        AppLanguage.UZBEK -> ServiceCopy("Mehmonxonalar", "Turar joy", "Makka va Madinada oilangiz uchun qulay yashash.", "Makka va Madinada paket darajasi, mehmonlar soni, xona qulayligi va masjidgacha masofani hisobga olib mehmonxona tanlaymiz. Sanalar va xona turi safar rejangizda ko‘rsatiladi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Меҳмонхоналар", "Турар жой", "Макка ва Мадинада оилангиз учун қулай яшаш.", "Макка ва Мадинада пакет даражаси, меҳмонлар сони, хона қулайлиги ва масжидгача масофани ҳисобга олиб меҳмонхона танлаймиз. Саналар ва хона тури сафар режангизда кўрсатилади.")
        else -> ServiceCopy("Hotels", "Accommodation", "Stays in Makkah and Madinah for your family.", "We select hotels in Makkah and Madinah according to package tier, family size, room comfort and location. Your chosen itinerary shows dates, room type and meal terms.")
    }
    Service.TRANSFER -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Приватный трансфер", "Личный транспорт", "Отдельный автомобиль для вас и вашей семьи.", "Организуем приватные поездки из аэропорта, между отелями и важными точками маршрута — без общей группы. Подбираем автомобиль под количество пассажиров и багажа. Конкретный транспорт отражается в подтверждённой поездке.")
        AppLanguage.UZBEK -> ServiceCopy("Shaxsiy transfer", "Shaxsiy transport", "Siz va oilangiz uchun alohida avtomobil.", "Aeroport, mehmonxona va muhim manzillar orasida guruhsiz shaxsiy safarlarni tashkil qilamiz. Avtomobil yo‘lovchilar va yuk soniga mos tanlanadi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Шахсий трансфер", "Шахсий транспорт", "Сиз ва оилангиз учун алоҳида автомобиль.", "Аэропорт, меҳмонхона ва муҳим манзиллар орасида гуруҳсиз шахсий сафарларни ташкил қиламиз. Автомобиль йўловчилар ва юк сонига мос танланади.")
        else -> ServiceCopy("Private transfers", "Private transport", "A dedicated car for you and your family.", "We arrange private rides between airports, hotels and key stops without a shared tour group. The vehicle is chosen for passenger and luggage capacity, with details shown in the confirmed booking.")
    }
    Service.GUIDE -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Личный гид", "Сопровождение", "Помощь вашей семье на протяжении поездки.", "Ваша семья путешествует своим составом, без обязательной большой туристической группы. Гид встречает по договорённости, помогает ориентироваться в Саудовской Аравии, сопровождает предусмотренные программой посещения и объясняет этапы умры.")
        AppLanguage.UZBEK -> ServiceCopy("Shaxsiy gid", "Hamrohlik", "Safar davomida oilangizga shaxsiy yordam.", "Oilangiz katta majburiy turistik guruhsiz sayohat qiladi. Gid kutib olish, manzillarni topish, dasturdagi ziyoratlar va umra bosqichlarida yordam beradi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Шахсий гид", "Ҳамроҳлик", "Сафар давомида оилангизга шахсий ёрдам.", "Оилангиз катта мажбурий туристик гуруҳсиз саёҳат қилади. Гид кутиб олиш, манзилларни топиш, дастурдаги зиёратлар ва умра босқичларида ёрдам беради.")
        else -> ServiceCopy("Personal guide", "Assistance", "Personal support for your family during the trip.", "Your family travels privately rather than with a mandatory large tour group. A guide assists with arrival, orientation, scheduled visits and understanding the steps of Umrah.")
    }
    Service.ZIYARAT -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Зияраты", "Маршруты", "Священные и исторические места Мекки и Медины.", "Посещайте значимые места Мекки и Медины в понятной последовательности. В рамках запланированных зияратов вы узнаете об истории этих мест и получите помощь гида с организацией и маршрутом.")
        AppLanguage.UZBEK -> ServiceCopy("Ziyoratlar", "Yo‘nalishlar", "Makka va Madinadagi muqaddas va tarixiy joylar.", "Muhim joylarni qulay tartibda ziyorat qiling. Dasturga kiritilgan ziyoratlarda gid joylar tarixi va yo‘nalish bo‘yicha yordam beradi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Зиёратлар", "Йўналишлар", "Макка ва Мадинадаги муқаддас ва тарихий жойлар.", "Муҳим жойларни қулай тартибда зиёрат қилинг. Дастурга киритилган зиёратларда гид жойлар тарихи ва йўналиш бўйича ёрдам беради.")
        else -> ServiceCopy("Ziyarats", "Routes", "Important sites in Makkah and Madinah.", "Visit historic and sacred places in a clear, organized order. When ziyarats are part of your itinerary, the guide helps you navigate the route and understand each location.")
    }
    Service.HARAMAIN -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Haramain Train", "Поезд", "Скоростной поезд, если он выбран для маршрута.", "Скоростная железная дорога Haramain соединяет ключевые города и транспортные узлы региона. Поезд можно использовать для переезда между Меккой, Джиддой и Мединой, когда это подходит вашей программе. Билет включён только при выборе этого варианта.")
        AppLanguage.UZBEK -> ServiceCopy("Haramain poyezdi", "Poyezd", "Yo‘nalishingizda tanlangan bo‘lsa, tezyurar poyezd.", "Haramain tezyurar temiryo‘li Makka, Jidda va Madinani bog‘laydi. Poyezd chiptasi faqat ushbu variant paketga qo‘shilgan bo‘lsa kiritiladi.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("Haramain поезди", "Поезд", "Йўналишингизда танланган бўлса, тезюрар поезд.", "Haramain тезюрар темирйўли Макка, Жидда ва Мадинани боғлайди. Поезд чиптаси фақат ушбу вариант пакетга қўшилган бўлса киритилади.")
        else -> ServiceCopy("Haramain Train", "Rail", "High-speed rail when selected for your itinerary.", "Haramain high-speed rail links major cities and transport hubs in the region. It may be used for journeys among Makkah, Jeddah and Madinah. A train ticket is included only when selected in your package.")
    }
    Service.SUPPORT -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("Поддержка iumrah", "Помощь", "Организационные вопросы — с нами.", "Помогаем разобраться с деталями поездки до вылета и во время пребывания в Саудовской Аравии: вопросы по бронированию, документам, времени встреч и предоставленным услугам. Каналы связи доступны внутри приложения.")
        AppLanguage.UZBEK -> ServiceCopy("iumrah yordami", "Yordam", "Safarga oid tashkiliy savollarda ko‘mak.", "Jo‘nashdan oldin va Saudiya Arabistonida bo‘lgan vaqtingizda bron, hujjatlar, uchrashuv va xizmatlar bo‘yicha savollaringizga yordam beramiz.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("iumrah ёрдами", "Ёрдам", "Сафарга оид ташкилий саволларда кўмак.", "Жўнашдан олдин ва Саудия Арабистонида бўлган вақтингизда брон, ҳужжатлар, учрашув ва хизматлар бўйича саволларингизга ёрдам берамиз.")
        else -> ServiceCopy("iumrah Support", "Assistance", "Help with practical questions throughout your journey.", "We help with booking details, documents, meeting times and arranged services before departure and during your stay in Saudi Arabia. Contact channels are available in the app.")
    }
    Service.DIGITAL -> when (language) {
        AppLanguage.RUSSIAN -> ServiceCopy("eSIM — интернет и связь", "Саудовская Аравия", "Мобильный интернет в Саудовской Аравии без физической SIM-карты.", "Подключите eSIM на совместимом телефоне для мобильного интернета в Саудовской Аравии. Пользуйтесь картами, мессенджерами и интернет-звонками. Доступность, объём трафика и условия зависят от выбранного тарифа.")
        AppLanguage.UZBEK -> ServiceCopy("eSIM — internet va aloqa", "Saudiya Arabistoni", "Saudiya Arabistonida jismoniy SIM-kartasiz mobil internet.", "Mos telefonda eSIM’ni faollashtiring va Saudiya Arabistonida mobil internet, xaritalar, messenjerlar hamda internet qo‘ng‘iroqlaridan foydalaning. Trafik va shartlar tarifga bog‘liq.")
        AppLanguage.UZBEK_CYRILLIC -> ServiceCopy("eSIM — интернет ва алоқа", "Саудия Арабистони", "Саудия Арабистонида жисмоний SIM-картасиз мобил интернет.", "Мос телефонда eSIM’ни фаоллаштиринг ва Саудия Арабистонида мобил интернет, хариталар, мессенжерлар ҳамда интернет қўнғироқларидан фойдаланинг. Трафик ва шартлар тарифга боғлиқ.")
        else -> ServiceCopy("eSIM — internet & connectivity", "Saudi Arabia", "Mobile internet in Saudi Arabia without a physical SIM.", "Activate an eSIM on a compatible phone for mobile internet in Saudi Arabia. Use maps, messaging and internet calls. Availability, data allowance and terms depend on the selected plan.")
    }
}

private fun heading(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Сервисы iumrah"
    AppLanguage.UZBEK -> "iumrah servislar"
    AppLanguage.UZBEK_CYRILLIC -> "iumrah сервислари"
    else -> "iumrah Services"
}
private fun subtitle(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Всё необходимое для вашей умры собрано в одном пакете: виза, перелёт, отели, трансфер и сопровождение."
    AppLanguage.UZBEK -> "Umra uchun kerakli barcha xizmatlar bitta paketda: viza, parvoz, mehmonxona, transfer va hamrohlik."
    AppLanguage.UZBEK_CYRILLIC -> "Умра учун керакли барча хизматлар битта пакетда: виза, парвоз, меҳмонхона, трансфер ва ҳамроҳлик."
    else -> "Everything you need for Umrah in one package: visa, flights, hotels, transfers and personal assistance."
}

/** Home carousel: 9 iOS service IDs, same order and card envelope 282 × 322. */
@Composable
fun IumrahServicesCarousel(language: AppLanguage, chrome: AppChromeStore) {
    val rowState = rememberLazyListState()
    val selected = rowState.layoutInfo.visibleItemsInfo.minByOrNull { item ->
        kotlin.math.abs(item.offset + item.size / 2 - (rowState.layoutInfo.viewportStartOffset + rowState.layoutInfo.viewportEndOffset) / 2)
    }?.index ?: 0
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Text(heading(language), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle(language), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        LazyRow(state = rowState, flingBehavior = rememberSnapFlingBehavior(lazyListState = rowState), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(Service.entries) { service ->
                val copy = service.copy(language)
                Column(Modifier.width(282.dp).height(322.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color.White)
                    .border(.75.dp, Color.Black.copy(alpha = .055f), RoundedCornerShape(26.dp))
                    .clickable { chrome.openIumrahServices(service.id) }) {
                    Image(painterResource(service.photo), copy.title, Modifier.fillMaxWidth().height(164.dp), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CupertinoIcon(service.icon, null, Modifier.size(12.dp), Color.Black.copy(alpha = .50f))
                            Text(copy.category, fontSize = 12.sp, color = Color.Black.copy(alpha = .50f), modifier = Modifier.weight(1f))
                            CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(12.dp), Color.Black.copy(alpha = .50f))
                        }
                        Text(copy.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.45).sp, color = Color.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(copy.short, fontSize = 13.sp, lineHeight = 18.sp, color = Color.Black.copy(alpha = .60f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        HomeCarouselDots(Service.entries.size, selected)
    }
}

/** Same selected-card deep link and expandable reference sections as iOS IumrahServicesView. */
@Composable
fun IumrahServicesScreen(language: AppLanguage, initialService: String, chrome: AppChromeStore) {
    val hapticView = LocalView.current
    val selectedStart = Service.entries.indexOfFirst { it.id == initialService }.coerceAtLeast(0)
    var expanded by remember(initialService) { mutableStateOf<String?>(Service.entries[selectedStart].id) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedStart + 1)
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        // The deep link scrolls directly to a service, so back navigation must live outside the list.
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable { chrome.back() }, contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.ChevronLeft, when (language) {
                    AppLanguage.RUSSIAN -> "Назад"
                    AppLanguage.UZBEK -> "Orqaga"
                    AppLanguage.UZBEK_CYRILLIC -> "Орқага"
                    else -> "Back"
                }, Modifier.size(20.dp))
            }
        }
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(heading(language), fontSize = 31.sp, letterSpacing = (-.7).sp, fontWeight = FontWeight.Bold)
                Text(when (language) {
                    AppLanguage.RUSSIAN -> "Всё, что мы организуем для вашей поездки. Откройте сервис, чтобы узнать подробности."
                    AppLanguage.UZBEK -> "Safaringiz uchun tashkil qiladigan xizmatlarimiz. Batafsil ma’lumot uchun servisni oching."
                    AppLanguage.UZBEK_CYRILLIC -> "Сафарингиз учун ташкил қиладиган хизматларимиз. Батафсил маълумот учун сервисни очинг."
                    else -> "Everything we organize for your journey. Tap a service to learn more."
                }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f))
            }
        }
        items(Service.entries) { service ->
            val copy = service.copy(language)
            val open = expanded == service.id
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
                .border(.75.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))) {
                Row(Modifier.fillMaxWidth().clickable { IumrahHaptics.selection(hapticView); expanded = if (open) null else service.id }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(37.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CupertinoIcon(service.icon, null, Modifier.size(19.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(copy.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(copy.category, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                    CupertinoIcon(if (open) CupertinoSymbol.ChevronUp else CupertinoSymbol.ChevronDown, null, Modifier.size(16.dp))
                }
                AnimatedVisibility(open, enter = expandVertically(tween(320)) + fadeIn(tween(320)), exit = shrinkVertically(tween(320)) + fadeOut(tween(220))) {
                    Column(Modifier.fillMaxWidth().padding(start = 15.dp, end = 15.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Image(painterResource(service.photo), copy.title, Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
                        Text(copy.detail, fontSize = 14.sp, lineHeight = 20.sp)
                        if (service == Service.VISA) VisaInformation(language)
                        Text(when (language) {
                            AppLanguage.RUSSIAN -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Зависит от пакета" else "Компонент поездки"
                            AppLanguage.UZBEK -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Paketga bog‘liq" else "Safar xizmati"
                            AppLanguage.UZBEK_CYRILLIC -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Пакетга боғлиқ" else "Сафар хизмати"
                            else -> if (service == Service.HARAMAIN || service == Service.DIGITAL) "Depends on package" else "Journey component"
                        }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .53f))
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun VisaInformation(language: AppLanguage) {
    fun tr(ru: String, en: String, uz: String, cy: String) = when (language) {
        AppLanguage.RUSSIAN -> ru
        AppLanguage.UZBEK -> uz
        AppLanguage.UZBEK_CYRILLIC -> cy
        else -> en
    }
    val uriHandler = LocalUriHandler.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            tr("Однократная", "Single entry", "Bir martalik", "Бир марталик") to tr("90 дней", "90 days", "90 kun", "90 кун"),
            tr("Многократная", "Multiple entry", "Ko‘p martalik", "Кўп марталик") to tr("1 год", "1 year", "1 yil", "1 йил"),
        ).forEach { (title, validity) ->
            Column(Modifier.weight(1f).heightIn(min = 92.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.background).padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(validity, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(tr("Срок действия", "Visa validity", "Amal qilish muddati", "Амал қилиш муддати"), fontSize = 11.sp)
            }
        }
    }
    Text(tr("Это сроки действия визы. Разрешённое пребывание — до 90 дней согласно условиям выданной визы. Однократная виза допускает один въезд, многократная — несколько въездов в течение срока её действия.", "These are visa validity periods. Permitted stay is up to 90 days according to the issued visa. A single-entry visa permits one entry, while a multiple-entry visa permits repeated entries during its validity.", "Bu vizalarning amal qilish muddati. Ruxsat etilgan qolish muddati berilgan viza shartlariga ko‘ra 90 kungacha. Bir martalik viza bitta, ko‘p martalik viza esa bir necha kirishga ruxsat beradi.", "Бу визаларнинг амал қилиш муддати. Рухсат этилган қолиш муддати берилган виза шартларига кўра 90 кунгача. Бир марталик виза битта, кўп марталик виза эса бир неча киришга рухсат беради."), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
    Text(tr("Выбор типа визы не всегда доступен. Выдачу, условия въезда и сезонные ограничения (включая период хаджа) устанавливают власти Саудовской Аравии. iumrah не гарантирует выдачу визы.", "Visa type may not be selectable. Saudi authorities determine approval, entry conditions and seasonal restrictions, including Hajj. iumrah cannot guarantee visa issuance.", "Viza turini har doim tanlab bo‘lmaydi. Berish, kirish shartlari va haj davridagi cheklovlarni Saudiya Arabistoni belgilaydi. iumrah viza berilishini kafolatlamaydi.", "Виза турини ҳар доим танлаб бўлмайди. Бериш, кириш шартлари ва ҳаж давридаги чекловларни Саудия Арабистони белгилайди. iumrah виза берилишини кафолатламайди."), fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .68f))
    Row(Modifier.clickable { runCatching { uriHandler.openUri("https://ksavisa.sa/visa/tourism/details") } }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Официальные условия KSA Visa", "Official KSA Visa terms", "KSA Visa rasmiy shartlari", "KSA Visa расмий шартлари"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(12.dp))
    }
}
