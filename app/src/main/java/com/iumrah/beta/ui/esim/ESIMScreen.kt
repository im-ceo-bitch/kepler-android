package com.iumrah.beta.ui.esim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.euicc.EuiccManager
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahColors
import com.iumrah.beta.core.design.IumrahDesign
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.data.booking.BookingStore
import com.iumrah.beta.models.booking.ClientESIMProfile
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.components.IumrahBackButton
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ESIMScreen(
    language: AppLanguage,
    bookingStore: BookingStore,
    chrome: AppChromeStore,
) {
    val state by bookingStore.state.collectAsState()
    val activeSession = state.sessions.firstOrNull { session ->
        val status = (session.operationStatus ?: session.booking.status).lowercase()
        status != "completed" && status != "cancelled"
    } ?: state.sessions.firstOrNull()
    val profiles = activeSession?.let { state.esimProfilesByBooking[it.id].orEmpty() }.orEmpty()
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val session = activeSession ?: return
        if (refreshing) return
        refreshing = true
        runCatching { bookingStore.loadESIMs(session.id) }
            .onSuccess { error = null }
            .onFailure { if (bookingStore.esimProfiles(session.id).isEmpty()) error = esimText(language, "refresh_unavailable") }
        refreshing = false
    }

    LaunchedEffect(activeSession?.id) {
        if (activeSession != null) {
            while (true) {
                refresh()
                delay(60_000)
            }
        }
    }

    // iOS refreshes when returning to the foreground (e.g. after opening system eSIM settings).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (activeSession != null) scope.launch { refresh() }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refresh() } },
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = IumrahDesign.PagePadding, end = IumrahDesign.PagePadding, top = 10.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) { IumrahBackButton(chrome::back) } }
        item { ESIMHeader(language) }
        item { ESIMIntroCard(language) }
        if (profiles.isEmpty()) {
            item {
                ESIMEmptyState(
                    language = language,
                    session = activeSession,
                    includesESIM = activeSession?.includesESIM() == true,
                    refreshing = refreshing,
                    error = error,
                    onRefresh = { scope.launch { refresh() } },
                    onBuild = chrome::startNewTrip,
                )
            }
            item { ESIMPlanPreview(language) }
        } else {
            items(profiles, key = { it.id }) { ESIMProfileCard(it, language) }
        }
        item { ESIMPrivacyCard(language) }
    }
    }
}

private fun StoredBookingSession.includesESIM(): Boolean =
    esimOverride ?: (booking.customization?.esim == true || booking.includedServices?.any { it.equals("esim", ignoreCase = true) } == true)

@Composable
private fun ESIMHeader(language: AppLanguage) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("iumrah eSIM", fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        Text(esimText(language, "header_subtitle"), fontSize = 15.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
    }
}

@Composable
private fun ESIMIntroCard(language: AppLanguage) {
    val shape = RoundedCornerShape(IumrahDesign.HeroRadius)
    Column(Modifier.fillMaxWidth().shadow(18.dp, shape).clip(shape).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .07f), shape)) {
        Image(painterResource(R.drawable.iumrah_esim_home_card), contentDescription = null, modifier = Modifier.fillMaxWidth().height(228.dp).background(Color(0xFF040917)), contentScale = ContentScale.Crop)
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(esimText(language, "intro_title"), fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.45).sp)
            Text(esimText(language, "package_badge"), modifier = Modifier.height(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 11.dp, vertical = 7.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .45.sp)
            Text(esimText(language, "intro_body"), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.SignalWave, null, modifier = Modifier.size(18.dp), tint = IumrahColors.SystemTeal)
                Text(esimText(language, "tariffs_title"), modifier = Modifier.padding(start = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ESIMEmptyState(
    language: AppLanguage,
    session: StoredBookingSession?,
    includesESIM: Boolean,
    refreshing: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onBuild: () -> Unit,
) {
    ESIMCard {
        if (session != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (includesESIM) CupertinoSymbol.Hourglass else CupertinoSymbol.SignalWave, null, modifier = Modifier.size(20.dp))
                Text(if (includesESIM) esimText(language, "waiting_title") else esimText(language, "not_included_title"), Modifier.padding(start = 8.dp).weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(if (includesESIM) esimText(language, "waiting_body") else esimText(language, "not_included_body"), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            Row(Modifier.height(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.SuitcaseFill, null, modifier = Modifier.size(15.dp))
                Text(session.displayBookingNumber, modifier = Modifier.padding(start = 7.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            if (includesESIM) {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else SecondaryButton(esimText(language, "refresh"), onRefresh)
            }
            if (error != null) Text(error, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.Suitcase, null, modifier = Modifier.size(20.dp))
                Text(esimText(language, "no_trip_title"), Modifier.padding(start = 8.dp).weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(esimText(language, "no_trip_body"), fontSize = 14.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
            PrimaryButton(esimText(language, "build_package"), onBuild)
        }
    }
}

@Composable
private fun ESIMPlanPreview(language: AppLanguage) {
    ESIMCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(esimText(language, "tariffs_title"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(esimText(language, "tariffs_subtitle"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            }
            Icon(CupertinoSymbol.SignalWave, null, modifier = Modifier.size(19.dp), tint = IumrahColors.SystemTeal)
        }
        PlanRow(language, "5 GB", false)
        PlanRow(language, "10 GB", true)
        PlanRow(language, "20 GB", false)
        Text(esimText(language, "preview_disclaimer"), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
    }
}

@Composable
private fun PlanRow(language: AppLanguage, data: String, recommended: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(data, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text("Saudi Arabia · 30 ${esimText(language, "days")}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
        if (recommended) Text(esimText(language, "recommended"), modifier = Modifier.height(28.dp).clip(CircleShape).background(IumrahColors.CareLight.copy(alpha = .24f)).padding(horizontal = 9.dp, vertical = 7.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        else Text(esimText(language, "in_package"), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
    }
}

@Composable
private fun ESIMPrivacyCard(language: AppLanguage) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(IumrahColors.SystemGreen.copy(alpha = .13f)), contentAlignment = Alignment.Center) {
            Icon(CupertinoSymbol.LockShield, null, modifier = Modifier.size(18.dp), tint = IumrahColors.SystemGreen)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(esimText(language, "privacy_title"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(esimText(language, "privacy_body"), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        }
    }
}

@Composable
private fun ESIMProfileCard(profile: ClientESIMProfile, language: AppLanguage) {
    val context = LocalContext.current
    var showManual by remember(profile.id) { mutableStateOf(false) }
    var activationError by remember(profile.id) { mutableStateOf<String?>(null) }
    val payload = profile.qrPayload()
    ESIMCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ESIMUsageRing(profile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(profile.label.ifBlank { "Saudi Arabia eSIM" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile.planName.ifBlank { esimText(language, "your_plan") }, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                Text(profile.statusText(language), modifier = Modifier.height(29.dp).clip(CircleShape).background(IumrahColors.SystemGreen.copy(alpha = .14f)).padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IumrahColors.SystemGreen)
                profile.validityText(language)?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f)) }
            }
        }
        if (profile.usageAvailable) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ESIMMetric(esimText(language, "remaining"), dataText(profile.remainingMB), Modifier.weight(1f))
                ESIMMetric(esimText(language, "used"), dataText(profile.usedMB), Modifier.weight(1f))
            }
        } else {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(esimText(language, "usage_pending"), Modifier.padding(start = 10.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (profile.hasActivationData && payload.isNotBlank()) {
            PrimaryButton(esimText(language, "open_esim_settings")) {
                val euicc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.getSystemService(Context.EUICC_SERVICE) as? EuiccManager
                } else null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && euicc?.isEnabled == true) {
                    runCatching {
                        context.startActivity(Intent(EuiccManager.ACTION_MANAGE_EMBEDDED_SUBSCRIPTIONS))
                    }.onSuccess { activationError = null }
                        .onFailure { activationError = esimText(language, "settings_unavailable") }
                } else {
                    activationError = esimText(language, "esim_unsupported")
                }
            }
            Text(esimText(language, "settings_guidance"), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            activationError?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
        if (payload.isNotBlank()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ESIMQRCode(payload)
                Text(esimText(language, "qr_help"), fontSize = 11.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), textAlign = TextAlign.Center)
            }
        }
        if (profile.smdpAddress.isNotBlank() || profile.activationCode.isNotBlank() || profile.iccid.isNotBlank()) {
            Row(Modifier.fillMaxWidth().clickable { showManual = !showManual }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(CupertinoSymbol.Key, null, modifier = Modifier.size(18.dp))
                Text(esimText(language, "manual_install"), Modifier.padding(start = 8.dp).weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(if (showManual) CupertinoSymbol.ChevronDown else CupertinoSymbol.ChevronRight, null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .45f))
            }
            if (showManual) {
                if (profile.smdpAddress.isNotBlank()) CopyRow(context, "SM-DP+", profile.smdpAddress)
                if (profile.activationCode.isNotBlank()) CopyRow(context, esimText(language, "activation_code"), profile.activationCode)
                if (profile.iccid.isNotBlank()) CopyRow(context, "ICCID", profile.iccid)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(CupertinoSymbol.Refresh, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
            Text(if (profile.usageAvailable) esimText(language, "provider_sync") else esimText(language, "pending_sync"), Modifier.padding(start = 7.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
        }
    }
}

@Composable
private fun ESIMUsageRing(profile: ClientESIMProfile) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f)
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawArc(trackColor, -90f, 360f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            if (profile.usageAvailable) drawArc(IumrahColors.CareDark, -90f, (360f * profile.remainingFraction).toFloat(), false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
        }
        if (profile.usageAvailable) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dataText(profile.remainingMB), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("left", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text("AUTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
            }
        }
    }
}

@Composable
private fun ESIMMetric(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .72f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CopyRow(context: Context, title: String, value: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .58f)).clickable { copy(context, value) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
            Text(value, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(CupertinoSymbol.Copy, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    }
}

@Composable
private fun ESIMQRCode(payload: String) {
    val bitmap = remember(payload) { makeESIMQr(payload) }
    Box(Modifier.size(214.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

private fun makeESIMQr(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) for (x in 0 until matrix.width) pixels[y * matrix.width + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height) }
}.getOrNull()

@Composable
private fun ESIMCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(IumrahDesign.CardRadius)).background(MaterialTheme.colorScheme.surface).border(.7.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .065f), RoundedCornerShape(IumrahDesign.CardRadius)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
}

@Composable
private fun PrimaryButton(title: String, onClick: () -> Unit) {
    val background = MaterialTheme.colorScheme.primary
    val foreground = MaterialTheme.colorScheme.onPrimary
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth().height(IumrahDesign.ControlHeight), cornerRadius = IumrahDesign.CompactRadius, background = background, pressedBackgroundAlpha = .84f) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Icon(CupertinoSymbol.ArrowUpRight, null, modifier = Modifier.size(17.dp), tint = foreground.copy(alpha = .78f))
        }
    }
}

@Composable
private fun SecondaryButton(title: String, onClick: () -> Unit) {
    IumrahPressable(onClick = onClick, modifier = Modifier.fillMaxWidth().height(50.dp), cornerRadius = IumrahDesign.CompactRadius, background = MaterialTheme.colorScheme.surfaceVariant) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
    }
}

private fun ClientESIMProfile.qrPayload(): String = lpaString.trim().ifBlank {
    if (smdpAddress.isNotBlank() && activationCode.isNotBlank()) "LPA:1\$$smdpAddress\$$activationCode" else ""
}
private fun ClientESIMProfile.statusText(language: AppLanguage): String {
    val raw = listOfNotNull(providerSmdpStatus, providerStatus, status).joinToString(" ").lowercase()
    return when {
        "expired" in raw -> esimText(language, "status_expired")
        "used up" in raw || "used_up" in raw -> esimText(language, "status_used_up")
        listOf("in use", "in_use", "enabled", "active").any { it in raw } -> esimText(language, "status_active")
        "install" in raw || "onboard" in raw -> esimText(language, "status_installed")
        else -> esimText(language, "status_ready")
    }
}
private fun ClientESIMProfile.validityText(language: AppLanguage): String? = when {
    !expiresAt.isNullOrBlank() -> {
        // Display the date in the user's locale, as DateFormatter does in SwiftUI.
        val raw = expiresAt.trim()
        val day = runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate() }
            .recoverCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
        val date = day?.format(DateTimeFormatter.ofPattern("d MMM yyyy", language.locale)) ?: raw.take(10)
        "${esimText(language, "valid_until")} $date"
    }
    (validityDays ?: 0) > 0 -> "$validityDays ${esimText(language, "days")}"
    else -> null
}
private fun dataText(mb: Double): String = if (mb >= 1024)
    String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
else "${max(0.0, mb).roundToInt()} MB"
private fun copy(context: Context, value: String) { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("iumrah eSIM", value)) }

private fun esimText(language: AppLanguage, key: String): String {
    val ru = mapOf(
        "header_subtitle" to "Связь в Саудовской Аравии", "package_badge" to "ТОЛЬКО В ПАКЕТЕ · V1", "intro_title" to "Интернет уже внутри вашей умры",
        "intro_body" to "В первой версии eSIM предоставляется только в составе пакета iumrah. Отдельная покупка появится позже. Активация, статус и остаток трафика доступны прямо в приложении.",
        "waiting_title" to "eSIM будет выдана к вашей поездке", "waiting_body" to "После подтверждения и подготовки поездки iumrah добавит eSIM к вашему бронированию. Здесь автоматически появятся активация, QR-код и остаток интернета.",
        "not_included_title" to "eSIM не включена в этот пакет", "not_included_body" to "Эта поездка была создана без eSIM. Новые пакеты iumrah уже подготовлены для eSIM внутри пакета; отдельная покупка появится позже.",
        "refresh" to "Проверить eSIM", "no_trip_title" to "Сначала создайте Umrah-пакет", "no_trip_body" to "В этой версии отдельная продажа eSIM отключена. Добавьте eSIM вместе с вашим пакетом умры.", "build_package" to "Собрать пакет",
        "tariffs_title" to "Доступные форматы", "tariffs_subtitle" to "Тариф подбирается для вашей поездки", "preview_disclaimer" to "Показанные объёмы — варианты для интерфейса. Конкретный тариф и срок будут указаны после выдачи eSIM к вашей поездке.",
        "recommended" to "ОПТИМАЛЬНО", "in_package" to "В пакете", "days" to "дней", "privacy_title" to "Данные активации защищены", "privacy_body" to "QR, Activation Code и ICCID доступны только владельцу конкретной поездки после авторизации.",
        "refresh_unavailable" to "Сейчас не удалось обновить eSIM. Уже загруженные данные остаются доступны.", "your_plan" to "Ваш тариф", "valid_until" to "Действует до", "remaining" to "Осталось", "used" to "Использовано", "activate" to "Активировать eSIM", "open_esim_settings" to "Открыть настройки eSIM", "settings_guidance" to "Настройте eSIM в системе Android. QR-код и данные ручной установки доступны ниже.", "settings_unavailable" to "Системная страница eSIM недоступна. Используйте QR-код на другом устройстве или ручные данные.", "esim_unsupported" to "На этом устройстве eSIM не поддерживается или отключена. QR-код и ручные данные остаются доступны.", "qr_help" to "Отсканируйте этот QR другим устройством, если системная установка недоступна.",
        "activation_code" to "Код активации", "manual_install" to "Данные ручной установки", "status_active" to "Активна", "status_installed" to "Установлена", "status_expired" to "Истекла", "status_used_up" to "Трафик закончился", "status_ready" to "Готова к активации", "provider_sync" to "Остаток обновляется автоматически", "pending_sync" to "Подключаем автоматический остаток", "usage_pending" to "Получаем остаток трафика у оператора…",
    )
    val en = mapOf(
        "header_subtitle" to "Connectivity in Saudi Arabia", "package_badge" to "PACKAGE ONLY · V1", "intro_title" to "Internet is part of your Umrah",
        "intro_body" to "In the first version, eSIM is available only as part of an iumrah package. Standalone purchase will come later. Activation, status and data balance are available directly in the app.",
        "waiting_title" to "Your eSIM will be assigned to this trip", "waiting_body" to "Once the trip is prepared, iumrah will attach an eSIM to your booking. Activation, QR code and data balance will appear here automatically.",
        "not_included_title" to "eSIM is not included in this package", "not_included_body" to "This trip was created without eSIM. New iumrah packages are prepared for package-included eSIM; standalone purchase will come later.",
        "refresh" to "Check eSIM", "no_trip_title" to "Build an Umrah package first", "no_trip_body" to "Standalone eSIM sales are disabled in this version. Add connectivity with your Umrah package.", "build_package" to "Build package",
        "tariffs_title" to "Available formats", "tariffs_subtitle" to "The plan is selected for your trip", "preview_disclaimer" to "Displayed data sizes are interface previews. Your exact plan and validity will be shown after the eSIM is assigned.",
        "recommended" to "RECOMMENDED", "in_package" to "In package", "days" to "days", "privacy_title" to "Activation data is protected", "privacy_body" to "QR, Activation Code and ICCID are available only to the owner of the specific trip after authorization.",
        "refresh_unavailable" to "eSIM could not be refreshed right now. Previously loaded data remains available.", "your_plan" to "Your plan", "valid_until" to "Valid until", "remaining" to "Remaining", "used" to "Used", "activate" to "Activate eSIM", "open_esim_settings" to "Open eSIM settings", "settings_guidance" to "Install the eSIM in Android system settings. QR and manual activation details are below.", "settings_unavailable" to "Android eSIM settings are unavailable. Use the QR on another device or the manual details.", "esim_unsupported" to "eSIM is unsupported or disabled on this device. QR and manual details remain available.", "qr_help" to "Scan this QR from another device if system installation is unavailable.",
        "activation_code" to "Activation code", "manual_install" to "Manual installation data", "status_active" to "Active", "status_installed" to "Installed", "status_expired" to "Expired", "status_used_up" to "Data used up", "status_ready" to "Ready to activate", "provider_sync" to "Balance updates automatically", "pending_sync" to "Connecting automatic balance", "usage_pending" to "Fetching your data balance from the carrier…",
    )
    val uz = mapOf(
        "header_subtitle" to "Saudiya Arabistonida aloqa", "package_badge" to "FAQAT PAKETDA · V1", "intro_title" to "Internet Umra paketingiz ichida", "intro_body" to "Birinchi versiyada eSIM faqat iumrah paketi tarkibida beriladi. Alohida xarid keyinroq qo‘shiladi. Faollashtirish, holat va trafik qoldig‘i ilovaning o‘zida ko‘rinadi.",
        "waiting_title" to "eSIM safaringizga biriktiriladi", "waiting_body" to "Safar tayyorlangach iumrah eSIM’ni broningizga qo‘shadi. Faollashtirish, QR-kod va internet qoldig‘i shu yerda avtomatik paydo bo‘ladi.", "not_included_title" to "eSIM bu paketga kiritilmagan", "not_included_body" to "Bu safar eSIM’siz yaratilgan. Yangi iumrah paketlari paket ichidagi eSIM uchun tayyor; alohida xarid keyinroq qo‘shiladi.",
        "refresh" to "eSIM’ni tekshirish", "no_trip_title" to "Avval Umra paketini yarating", "no_trip_body" to "Bu versiyada alohida eSIM savdosi o‘chirilgan. eSIM’ni Umra paketingiz bilan oling.", "build_package" to "Paket yaratish", "tariffs_title" to "Mavjud formatlar", "tariffs_subtitle" to "Tarif safaringiz uchun tanlanadi", "preview_disclaimer" to "Ko‘rsatilgan hajmlar interfeys namunalari. Aniq tarif va muddat eSIM biriktirilgach ko‘rsatiladi.",
        "recommended" to "TAVSIYA", "in_package" to "Paketda", "days" to "kun", "privacy_title" to "Faollashtirish ma’lumotlari himoyalangan", "privacy_body" to "QR, Activation Code va ICCID faqat shu safar egasiga avtorizatsiyadan keyin ko‘rsatiladi.", "refresh_unavailable" to "Hozir eSIM yangilanmadi. Oldin yuklangan ma’lumotlar saqlanadi.", "your_plan" to "Tarifingiz", "valid_until" to "Amal qiladi", "remaining" to "Qoldi", "used" to "Ishlatildi", "activate" to "eSIM’ni faollashtirish", "open_esim_settings" to "eSIM sozlamalarini ochish", "settings_guidance" to "eSIM’ni Android tizim sozlamalarida o‘rnating. QR va qo‘lda ulash ma’lumotlari pastda.", "settings_unavailable" to "Android eSIM sozlamalari ochilmadi. QR yoki qo‘lda ulash ma’lumotlaridan foydalaning.", "esim_unsupported" to "Bu qurilmada eSIM qo‘llab-quvvatlanmaydi yoki o‘chiq. QR va qo‘lda ulash ma’lumotlari mavjud.", "qr_help" to "Tizimli o‘rnatish ishlamasa QR-kodni boshqa qurilmadan skanerlang.", "activation_code" to "Faollashtirish kodi", "manual_install" to "Qo‘lda o‘rnatish ma’lumotlari", "status_active" to "Faol", "status_installed" to "O‘rnatilgan", "status_expired" to "Muddati tugagan", "status_used_up" to "Trafik tugagan", "status_ready" to "Faollashtirishga tayyor", "provider_sync" to "Qoldiq avtomatik yangilanadi", "pending_sync" to "Avtomatik qoldiq ulanmoqda", "usage_pending" to "Operator orqali trafik qoldig‘i olinmoqda…",
    )
    val cy = mapOf(
        "header_subtitle" to "Саудия Арабистонида алоқа", "package_badge" to "ФАҚАТ ПАКЕТДА · V1", "intro_title" to "Интернет Умра пакетингиз ичида", "intro_body" to "Биринчи версияда eSIM фақат iumrah пакети таркибида берилади. Алоҳида харид кейинроқ қўшилади. Фаоллаштириш, ҳолат ва трафик қолдиғи илованинг ўзида кўринади.",
        "waiting_title" to "eSIM сафарингизга бириктирилади", "waiting_body" to "Сафар тайёрлангач iumrah eSIM’ни бронга қўшади. Фаоллаштириш, QR-код ва интернет қолдиғи шу ерда автоматик пайдо бўлади.", "not_included_title" to "eSIM бу пакетга киритилмаган", "not_included_body" to "Бу сафар eSIM’сиз яратилган. Янги iumrah пакетлари пакет ичидаги eSIM учун тайёр; алоҳида харид кейинроқ қўшилади.",
        "refresh" to "eSIM’ни текшириш", "no_trip_title" to "Аввал Умра пакетини яратинг", "no_trip_body" to "Бу версияда алоҳида eSIM савдоси ўчирилган. eSIM’ни Умра пакетингиз билан олинг.", "build_package" to "Пакет яратиш", "tariffs_title" to "Мавжуд форматлар", "tariffs_subtitle" to "Тариф сафарингиз учун танланади", "preview_disclaimer" to "Кўрсатилган ҳажмлар интерфейс намуналари. Аниқ тариф ва муддат eSIM бириктирилгач кўрсатилади.",
        "recommended" to "ТАВСИЯ", "in_package" to "Пакетда", "days" to "кун", "privacy_title" to "Фаоллаштириш маълумотлари ҳимояланган", "privacy_body" to "QR, Activation Code ва ICCID фақат шу сафар эгасига авторизациядан кейин кўрсатилади.", "refresh_unavailable" to "Ҳозир eSIM янгиланмади. Олдин юкланган маълумотлар сақланади.", "your_plan" to "Тарифингиз", "valid_until" to "Амал қилади", "remaining" to "Қолди", "used" to "Ишлатилди", "activate" to "eSIM’ни фаоллаштириш", "open_esim_settings" to "eSIM созламаларини очиш", "settings_guidance" to "eSIM’ни Android тизим созламаларида ўрнатинг. QR ва қўлда улаш маълумотлари пастда.", "settings_unavailable" to "Android eSIM созламалари очилмади. QR ёки қўлда улаш маълумотларидан фойдаланинг.", "esim_unsupported" to "Бу қурилмада eSIM қўллаб-қувватланмайди ёки ўчиқ. QR ва қўлда улаш маълумотлари мавжуд.", "qr_help" to "Тизимли ўрнатиш ишламаса QR-кодни бошқа қурилмадан сканерланг.", "activation_code" to "Фаоллаштириш коди", "manual_install" to "Қўлда ўрнатиш маълумотлари", "status_active" to "Фаол", "status_installed" to "Ўрнатилган", "status_expired" to "Муддати тугаган", "status_used_up" to "Трафик тугаган", "status_ready" to "Фаоллаштиришга тайёр", "provider_sync" to "Қолдиқ автоматик янгиланади", "pending_sync" to "Автоматик қолдиқ уланмоқда", "usage_pending" to "Оператор орқали трафик қолдиғи олинмоқда…",
    )
    return when (language) { AppLanguage.RUSSIAN -> ru[key]; AppLanguage.ENGLISH -> en[key] ?: ru[key]; AppLanguage.UZBEK -> uz[key] ?: ru[key]; AppLanguage.UZBEK_CYRILLIC -> cy[key] ?: ru[key] } ?: key
}
