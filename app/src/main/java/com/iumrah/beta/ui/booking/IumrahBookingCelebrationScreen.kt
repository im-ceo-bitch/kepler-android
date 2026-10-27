package com.iumrah.beta.ui.booking

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/** iOS IumrahBookingCelebrationView parity shown immediately after booking creation. */
@Composable
fun IumrahBookingCelebrationScreen(
    bookingID: String,
    language: AppLanguage,
    bookingStore: BookingStore,
    chrome: AppChromeStore,
) {
    val state by bookingStore.state.collectAsState()
    var loading by remember(bookingID) { mutableStateOf(true) }
    val session = state.sessions.firstOrNull { it.id == bookingID }

    LaunchedEffect(bookingID) {
        bookingStore.refresh(bookingID)
        loading = false
    }

    if (session == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(color = Color.White)
            else Text(celebrationText(language, "Бронирование не найдено", "Booking not found", "Bron topilmadi", "Брон топилмади"), color = Color.White)
        }
        return
    }

    val view = LocalView.current
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black, Color(0xFF05060A), Color.Black),
                ),
            ),
    ) {
        CelebrationParticleField(Modifier.fillMaxSize())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(66.dp))

            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.iumrah_icon_fur_preview),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .border(.8.dp, Color.White.copy(alpha = .20f), RoundedCornerShape(25.dp))
                    .clickable {
                        IumrahHaptics.soft(view)
                    },
            )

            Text(
                celebrationText(language, "Ваша Umrah создана", "Your Umrah is created", "Umrangiz yaratildi", "Умрангиз яратилди"),
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 39.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.9).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 28.dp, start = 4.dp, end = 4.dp),
            )
            Text(
                celebrationText(
                    language,
                    "Бронирование создано и передано на проверку наличия. Вам ничего не нужно делать прямо сейчас.",
                    "Your booking has been created and sent for availability confirmation. Nothing else is required from you right now.",
                    "Bron yaratildi va mavjudlikni tekshirishga yuborildi. Hozir sizdan boshqa amal talab qilinmaydi.",
                    "Брон яратилди ва мавжудликни текширишга юборилди. Ҳозир сиздан бошқа амал талаб қилинмайди.",
                ),
                color = Color.White.copy(alpha = .62f),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp, start = 10.dp, end = 10.dp),
            )

            AvailabilityTimer(session, language, Modifier.padding(top = 24.dp))
            CelebrationTelegramCard(language, session, chrome, Modifier.padding(top = 16.dp))

            IumrahPressable(
                onClick = {
                    IumrahHaptics.soft(view)
                    chrome.openBookingDetail(bookingID)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(58.dp),
                cornerRadius = 20.dp,
                background = Color.White,
                pressedScale = .985f,
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        celebrationText(language, "Открыть бронирование", "Open booking", "Bronni ochish", "Бронни очиш"),
                        modifier = Modifier.weight(1f),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    CupertinoIcon(CupertinoSymbol.ArrowRight, null, Modifier.size(17.dp), Color.Black)
                }
            }

            IumrahPressable(
                onClick = {
                    IumrahHaptics.selection(view)
                    chrome.navigate(AppTab.HOME)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                cornerRadius = 18.dp,
                background = Color.Transparent,
                shadowElevation = 0.dp,
            ) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    CupertinoIcon(CupertinoSymbol.Home, null, Modifier.size(16.dp), Color.White.copy(alpha = .78f))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        celebrationText(language, "На главную", "Home", "Asosiy sahifa", "Асосий саҳифа"),
                        color = Color.White.copy(alpha = .78f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AvailabilityTimer(session: StoredBookingSession, language: AppLanguage, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(session.id) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val deadline = bookingDeadlineMillis(session)
    val seconds = ((deadline - now).coerceAtLeast(0L) / 1000L).toInt()
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = .055f))
            .border(.8.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(26.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            celebrationText(language, "ПРОВЕРКА НАЛИЧИЯ", "AVAILABILITY CHECK", "MAVJUDLIK TEKSHIRUVI", "МАВЖУДЛИК ТЕКШИРУВИ"),
            color = Color.White.copy(alpha = .46f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.15.sp,
        )
        Text(
            "%02d:%02d:%02d".format(h, m, s),
            color = Color.White,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-.8).sp,
        )
        Text(
            celebrationText(
                language,
                "После проверки наличия статус изменится автоматически. Можно закрыть приложение — iumrah обновит бронь и уведомит Вас.",
                "After availability is checked, the status will update automatically. You can close the app — iumrah will update the booking and notify you.",
                "Mavjudlik tekshirilgach holat avtomatik yangilanadi. Ilovani yopishingiz mumkin — iumrah bronni yangilab, sizga xabar beradi.",
                "Мавжудлик текширилгач ҳолат автоматик янгиланади. Иловадан чиқишингиз мумкин — iumrah бронни янгилаб, сизга хабар беради.",
            ),
            color = Color.White.copy(alpha = .54f),
            fontSize = 13.5.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CelebrationTelegramCard(
    language: AppLanguage,
    session: StoredBookingSession,
    chrome: AppChromeStore,
    modifier: Modifier = Modifier,
) {
    IumrahPressable(
        onClick = chrome::openBookingTelegramIntegration,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        background = Color.White.copy(alpha = .055f),
        shadowElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().border(.8.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(26.dp)).padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(Color(0xFF229ED9).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                CupertinoIcon(CupertinoSymbol.Send, null, Modifier.size(19.dp), Color(0xFF48B8EE))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Telegram", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    celebrationText(language, "Подключить бронь ${session.displayBookingNumber}", "Connect booking ${session.displayBookingNumber}", "${session.displayBookingNumber} bronini ulang", "${session.displayBookingNumber} бронини уланг"),
                    color = Color.White.copy(alpha = .55f),
                    fontSize = 12.5.sp,
                    lineHeight = 16.sp,
                )
            }
            CupertinoIcon(CupertinoSymbol.ChevronRight, null, Modifier.size(14.dp), Color.White.copy(alpha = .34f))
        }
    }
}

@Composable
private fun CelebrationParticleField(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "booking-celebration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6200, easing = LinearEasing), RepeatMode.Restart),
        label = "particles",
    )
    Canvas(modifier.graphicsLayer { alpha = .95f }) {
        val cx = size.width * .5f
        val cy = size.height * .235f
        val maxR = minOf(size.width, size.height) * .48f
        repeat(34) { i ->
            val seed = ((i * 73) % 101) / 101f
            val p = (phase + seed) % 1f
            val angle = (i * 137.508 + p * 42.0) * PI / 180.0
            val radius = maxR * (.08f + .88f * p)
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius * .78f + p * size.height * .12f
            val alpha = ((1f - p) * .62f).coerceAtLeast(0f)
            val dot = if (i % 7 == 0) 3.2f else if (i % 3 == 0) 2.25f else 1.55f
            drawCircle(Color.White.copy(alpha = alpha), radius = dot, center = androidx.compose.ui.geometry.Offset(x, y))
        }
    }
}

private fun bookingDeadlineMillis(session: StoredBookingSession): Long {
    parseIsoMillis(session.availabilityDeadlineAt)?.let { return it }
    val start = parseIsoMillis(session.availabilityStartedAt ?: session.booking.createdAt) ?: System.currentTimeMillis()
    return start + 6L * 60L * 60L * 1000L
}

private fun parseIsoMillis(raw: String?): Long? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}

private fun celebrationText(language: AppLanguage, ru: String, en: String, uz: String, cy: String): String = when (language) {
    AppLanguage.RUSSIAN -> ru
    AppLanguage.ENGLISH -> en; AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> com.iumrah.beta.core.localization.L10n.phrase(en, language)
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}
