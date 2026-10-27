@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.iumrah.beta.ui.packageflow

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol

/** Shared parity sheet for iOS UmrahCarePackageExplanationView. */
@Composable
fun UmrahCarePackageExplanationSheet(
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp),
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IumrahPressable(
                    onClick = onDismiss,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 22.dp,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 0.dp,
                ) {
                    Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                        CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Image(
                painter = painterResource(R.drawable.care_price_support),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.Black),
                contentScale = ContentScale.FillWidth,
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    careTr(language, "Как iumrah собирает вашу поездку", "How iumrah builds your journey", "iumrah safaringizni qanday yig‘adi", "iumrah сафарингизни қандай йиғади"),
                    fontSize = 31.sp,
                    lineHeight = 35.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-.7).sp,
                )
                Text(
                    careTr(
                        language,
                        "iumrah — не просто сервис бронирования. Мы собираем перелёты, проживание, трансферы, сопровождение и ключевые этапы Умры как одну связанную поездку, чтобы паломнику не приходилось самому сводить десятки отдельных деталей.",
                        "iumrah is more than a booking service. Flights, stays, transfers, guidance and the key stages of Umrah are assembled as one connected journey so the pilgrim does not have to reconcile dozens of separate details.",
                        "iumrah oddiy bronlash xizmati emas. Parvozlar, turar joy, transferlar, yo‘riqnoma va Umraning asosiy bosqichlari yagona bog‘langan safar sifatida yig‘iladi.",
                        "iumrah оддий бронлаш хизмати эмас. Парвозлар, турар жой, трансферлар, йўриқнома ва Умранинг асосий босқичлари ягона боғланган сафар сифатида йиғилади.",
                    ),
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                )
            }

            CareExplanationRow(
                CupertinoSymbol.Grid,
                careTr(language, "Поездка просчитывается как единая система", "The trip is calculated as one system", "Safar yagona tizim sifatida hisoblanadi", "Сафар ягона тизим сифатида ҳисобланади"),
                careTr(language, "Даты, число паломников, ночи в Мекке и Медине и выбранные услуги связываются между собой заранее, чтобы не возникали лишние ночи, разрывы между городами или дублирование услуг.", "Dates, traveler count, nights in Makkah and Madinah and selected services are connected in advance to avoid extra nights, broken city transitions or duplicated services.", "Sanalar, ziyoratchilar soni, Makka va Madinadagi tunlar hamda xizmatlar oldindan bog‘lanadi.", "Саналар, зиёратчилар сони, Макка ва Мадинадаги тунлар ҳамда хизматлар олдиндан боғланади."),
            )
            CareExplanationRow(
                CupertinoSymbol.Airplane,
                careTr(language, "Рейс оценивается внутри всей поездки", "Flights are evaluated inside the whole journey", "Reys butun safar ichida baholanadi", "Рейс бутун сафар ичида баҳоланади"),
                careTr(language, "Система сравнивает варианты внутри общей стоимости пакета. Если даты гибкие или выбранный рейс заметно повышает цену, iumrah Care помогает найти более сбалансированный маршрут без изменения логики вашей поездки.", "The system compares options inside the total package price. If your dates are flexible or a selected flight raises the package price significantly, iumrah Care can help find a better-balanced itinerary without changing the journey logic.", "Tizim variantlarni umumiy paket narxi ichida taqqoslaydi. Sanalar moslashuvchan bo‘lsa yoki tanlangan reys narxni sezilarli oshirsa, iumrah Care yanada muvozanatli yo‘nalishga yordam beradi.", "Тизим вариантларни умумий пакет нархи ичида таққослайди. Саналар мослашувчан бўлса ёки танланган рейс нархни сезиларли оширса, iumrah Care янада мувозанатли йўналишга ёрдам беради."),
            )
            CareExplanationRow(
                CupertinoSymbol.Hotel,
                careTr(language, "Отели закрепляются под реальные ночи", "Hotels are matched to the actual nights", "Mehmonxonalar haqiqiy tunlarga moslanadi", "Меҳмонхоналар ҳақиқий тунларга мосланади"),
                careTr(language, "Отель связан с точными ночами поездки ещё во время расчёта. После подтверждения и оплаты система запускает оформление выбранного проживания на ваши даты как часть одного Umrah-пакета.", "The hotel is tied to the exact trip nights during calculation. After confirmation and payment, the system starts arranging the selected stay for your dates as part of the same Umrah package.", "Mehmonxona hisoblash vaqtidayoq safarning aniq tunlariga bog‘lanadi. Tasdiq va to‘lovdan so‘ng tizim tanlangan turar joyni shu Umra paketining bir qismi sifatida rasmiylashtirishni boshlaydi.", "Меҳмонхона ҳисоблаш вақтидаёқ сафарнинг аниқ тунларига боғланади. Тасдиқ ва тўловдан сўнг тизим танланган турар жойни шу Умра пакетининг бир қисми сифатида расмийлаштиришни бошлайди."),
            )
            CareExplanationRow(
                CupertinoSymbol.Persons,
                careTr(language, "Сопровождение привязано к вашей Умре", "Guidance is attached to your Umrah", "Yo‘riqnoma Umrangizga biriktiriladi", "Йўриқнома Умрангизга бириктирилади"),
                careTr(language, "Трансферы и гид координируются вокруг вашей фактической поездки — прилёта, переезда между городами, Умры и вылета домой.", "Transfers and guidance are coordinated around your actual journey: arrival, city transfer, Umrah and the flight home.", "Transfer va gid haqiqiy safaringiz — kelish, shaharlararo o‘tish, Umra va qaytish atrofida muvofiqlashtiriladi.", "Трансфер ва гид ҳақиқий сафарингиз — келиш, шаҳарлараро ўтиш, Умра ва қайтиш атрофида мувофиқлаштирилади."),
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFF375F).copy(alpha = .10f))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    careTr(language, "Наша задача — забота, а не просто бронь", "Our role is care, not just booking", "Vazifamiz — faqat bron emas, g‘amxo‘rlik", "Вазифамиз — фақат брон эмас, ғамхўрлик"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    careTr(language, "Основная сборка поездки выполняется системой автоматически. iumrah Care подключается как дополнительный уровень заботы — если вам нужно сбалансировать цену, рейс, расположение отеля или маршрут, не отвлекаясь от главного: вашей Умры и поклонения.", "The core journey is assembled automatically by the system. iumrah Care adds an extra layer of care when you want help balancing price, flights, hotel location or itinerary, so your focus can stay on Umrah and worship.", "Safarning asosiy yig‘ilishi tizim tomonidan avtomatik bajariladi. iumrah Care narx, reys, mehmonxona joylashuvi yoki yo‘nalishni muvozanatlash kerak bo‘lsa qo‘shimcha g‘amxo‘rlik sifatida yordam beradi.", "Сафарнинг асосий йиғилиши тизим томонидан автоматик бажарилади. iumrah Care нарх, рейс, меҳмонхона жойлашуви ёки йўналишни мувозанатлаш керак бўлса қўшимча ғамхўрлик сифатида ёрдам беради."),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                )
            }
        }
    }
}

@Composable
private fun CareExplanationRow(icon: CupertinoSymbol, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .055f), RoundedCornerShape(24.dp))
            .padding(17.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            CupertinoIcon(icon, null, Modifier.size(19.dp), MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(body, fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
        }
    }
}

private fun careTr(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
