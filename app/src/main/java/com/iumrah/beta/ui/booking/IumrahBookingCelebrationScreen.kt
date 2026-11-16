package com.iumrah.beta.ui.booking

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.navigation.AppTab
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.domain.booking.BookingAvailabilityEstimate
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPrimaryButton
import com.iumrah.beta.ui.components.IumrahSecondaryButton
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Currency

/** iOS IumrahBookingCreatedContent: the receipt is not a payment confirmation. */
@Composable
fun IumrahBookingCelebrationScreen(bookingID: String, language: AppLanguage, bookingStore: BookingStore, chrome: AppChromeStore) {
    val state by bookingStore.state.collectAsState()
    val session = state.sessions.firstOrNull { it.id == bookingID }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var loading by remember(bookingID) { mutableStateOf(session == null) }
    LaunchedEffect(bookingID, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                if (bookingStore.booking(bookingID) == null) bookingStore.refresh(bookingID)
                while (bookingStore.booking(bookingID)?.effectiveStatus == "AVAILABILITY_CHECK") {
                    bookingStore.refreshCreatedBookingStatus(bookingID)
                    if (bookingStore.booking(bookingID)?.effectiveStatus != "AVAILABILITY_CHECK") break
                    delay(20_000)
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* Keep the server snapshot on transient failure. */ }
            finally { loading = false }
        }
    }
    if (session == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else IumrahSecondaryButton(com.iumrah.beta.core.localization.L10n.text("open_booking", language)) { chrome.navigate(AppTab.BOOKING) }
        }
        return
    }
    val checking = session.effectiveStatus == "AVAILABILITY_CHECK"
    val context = LocalContext.current
    val view = LocalView.current
    var didPlay by rememberSaveable(bookingID) { mutableStateOf(false) }
    val ring = remember(bookingID) { Animatable(if (didPlay) 1f else 0f) }
    val tick = remember(bookingID) { Animatable(if (didPlay) 1f else 0f) }
    val lift = remember(bookingID) { Animatable(0f) }
    LaunchedEffect(bookingID) {
        if (!didPlay) {
            ring.animateTo(1f, tween(360)); tick.animateTo(1f, tween(240))
            didPlay = true
            IumrahHaptics.success(view)
            // The bundled iOS sound is played once and never in silent/vibrate mode.
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val player = if (manager?.ringerMode == AudioManager.RINGER_MODE_NORMAL) runCatching {
                MediaPlayer.create(context, R.raw.iumrah_booking_created,
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build(), 0)
            }.getOrNull() else null
            try {
                runCatching { player?.start() }
                lift.animateTo(-3f, tween(120)); delay(180); lift.animateTo(0f, tween(350))
                if (player != null) delay(player.duration.toLong().coerceIn(0L, 5000L))
            } finally { player?.release() }
        }
    }
    fun t(key: CreatedCopy) = bookingCreatedCopy(language, key)
    val money = remember(language) { NumberFormat.getCurrencyInstance(language.locale).apply { currency = Currency.getInstance("USD") } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val white = Color.White
        Column(Modifier.fillMaxWidth().offset(y = lift.value.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF09090A))
            .border(1.dp, white.copy(alpha = .09f), RoundedCornerShape(24.dp))) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 17.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("iumrah", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp, color = white, modifier = Modifier.weight(1f))
                session.bookingNumber?.takeIf { it > 0 }?.let { Text("#%04d".format(it), color = white.copy(alpha = .85f), fontWeight = FontWeight.SemiBold) }
            }
            SpectralBookingDome(Modifier.fillMaxWidth().height(108.dp).padding(horizontal = 18.dp))
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t(CreatedCopy.Package), color = white.copy(alpha = .65f), fontSize = 12.sp)
                        Text("${session.booking.input.travelers.totalPeople} · ${t(CreatedCopy.Pilgrims)}", color = white, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t(CreatedCopy.Total), color = white.copy(alpha = .65f), fontSize = 12.sp)
                        Text(money.format(session.booking.totalUsd), color = white, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(session.displayPilgrimID?.let { "iumrah ID · $it" }.orEmpty(), color = white.copy(alpha = .7f), fontSize = 11.sp)
                    Text(t(CreatedCopy.PerPerson) + " " + money.format(session.booking.perPilgrimUsd), color = white.copy(alpha = .7f), fontSize = 11.sp, textAlign = TextAlign.End)
                }
            }
        }
        val ink = MaterialTheme.colorScheme.onSurface
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(64.dp)) {
                drawArc(ink, -90f, 360f * ring.value, false, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
                val path = Path().apply { moveTo(size.width * 17f / 64f, size.height * .5f); lineTo(size.width * 28f / 64f, size.height * 43f / 64f); lineTo(size.width * 47f / 64f, size.height * 22f / 64f) }
                val measure = PathMeasure().apply { setPath(path, false) }
                val segment = Path(); measure.getSegment(0f, measure.length * tick.value, segment, true)
                drawPath(segment, ink, style = Stroke(2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text(t(CreatedCopy.Created), fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(t(if (checking) CreatedCopy.NoPayment else CreatedCopy.UpdatedBody), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
        CreatedAvailability(session, language)
        CreatedPanel {
            Text(t(CreatedCopy.Notifications), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val account = (context.applicationContext as com.iumrah.beta.IumrahApplication).container.accountStore
            val accountState by account.state.collectAsState()
            val token = account.bearerToken.takeIf { accountState.isAuthenticated }
            key(session.id, session.accessToken, token) {
                com.iumrah.beta.ui.telegram.TelegramConnectCard(language, session, token, bookingStore)
            }
            Text(t(CreatedCopy.ChannelsBody), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        CreatedPanel {
            Text(t(CreatedCopy.Next), fontSize = 17.sp, fontWeight = FontWeight.Bold)
            listOf(CreatedCopy.Checking to CreatedCopy.CheckBody, CreatedCopy.Payment to CreatedCopy.PaymentBody, CreatedCopy.Documents to CreatedCopy.DocumentsBody).forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).background(ink.copy(alpha = .07f), CircleShape), contentAlignment = Alignment.Center) { Text("${i + 1}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(t(step.first), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(t(step.second), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        IumrahPrimaryButton(com.iumrah.beta.core.localization.L10n.text("open_booking", language)) { chrome.openBookingDetail(bookingID) }
        IumrahSecondaryButton(when (language) {
            AppLanguage.RUSSIAN -> "На главную"; AppLanguage.UZBEK -> "Bosh sahifaga"; AppLanguage.UZBEK_CYRILLIC -> "Бош саҳифага"
            AppLanguage.ARABIC -> "الصفحة الرئيسية"; AppLanguage.TURKISH -> "Ana sayfa"; AppLanguage.INDONESIAN -> "Beranda"; AppLanguage.MALAY -> "Laman utama"; AppLanguage.ENGLISH -> "Home"
        }) { chrome.navigate(AppTab.HOME) }
    }
}

@Composable private fun CreatedPanel(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable private fun CreatedAvailability(session: StoredBookingSession, language: AppLanguage) {
    fun t(key: CreatedCopy) = bookingCreatedCopy(language, key)
    val checking = session.effectiveStatus == "AVAILABILITY_CHECK"
    val deadline = BookingAvailabilityEstimate.deadline(session.availabilityStartedAt, session.booking.createdAt, session.availabilityDeadlineAt)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(session.id, checking, deadline, lifecycle) {
        if (checking) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    }
    val remaining = deadline?.let { BookingAvailabilityEstimate.remainingSeconds(it, now).toInt() }
    CreatedPanel {
        if (checking) {
            val delayed = remaining == 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t(if (delayed) CreatedCopy.Delayed else CreatedCopy.Checking), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (remaining == null) Text(t(CreatedCopy.Estimate), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    else if (remaining > 0) Text("%02d:%02d".format(remaining / 60, remaining % 60), fontSize = 32.sp, fontWeight = FontWeight.SemiBold)
                }
                CupertinoIcon(CupertinoSymbol.Clock, null, Modifier.size(23.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (remaining != null && remaining > 0) LinearProgressIndicator(progress = { (remaining / 1800f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(t(if (delayed) CreatedCopy.DelayedBody else CreatedCopy.EstimateBody), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } else {
            Text(t(CreatedCopy.Updated), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(t(CreatedCopy.UpdatedBody), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
