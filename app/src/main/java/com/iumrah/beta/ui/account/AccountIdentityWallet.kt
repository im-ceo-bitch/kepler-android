package com.iumrah.beta.ui.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahMotion
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.iosSpring
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.account.IumrahAccountProfile
import com.iumrah.beta.models.booking.BookingGeneratorFlightSnapshot
import com.iumrah.beta.models.booking.BookingHotelSelectionSnapshot
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt

private val IdentityTop = Color(0xFF131315)
private val IdentityBottom = Color(0xFF08080A)
private val IdentityShape = RoundedCornerShape(22.dp)

private fun awTr(language: AppLanguage, en: String, ru: String, uz: String, cy: String) = when (language) {
    AppLanguage.ENGLISH -> en
    AppLanguage.RUSSIAN -> ru
    AppLanguage.UZBEK -> uz
    AppLanguage.UZBEK_CYRILLIC -> cy
}

private data class DomePoint(val x: Float, val y: Float, val depth: Float)

private val domePoints: List<DomePoint> by lazy {
    val output = ArrayList<DomePoint>(620)
    val ringCount = 18
    val thetaStart = 0.085
    val thetaEnd = PI / 2.0 - 0.115
    repeat(ringCount) { ring ->
        val fraction = ring.toDouble() / (ringCount - 1).coerceAtLeast(1)
        val theta = thetaStart + ((thetaEnd - thetaStart) * fraction)
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        val count = maxOf(6, (48.0 * sinTheta).roundToInt())
        val stagger = if (ring % 2 == 0) 0.0 else 0.25
        repeat(count) { index ->
            val phi = PI * ((index.toDouble() + 0.5 + stagger) / count.toDouble())
            val depth = sinTheta * sin(phi)
            val rawX = sinTheta * cos(phi)
            val rawY = -cosTheta
            val x = rawX * (0.94 + (0.06 * depth))
            output += DomePoint(x.toFloat(), rawY.toFloat(), depth.toFloat())
        }
    }
    output.sortedBy { it.depth }
}

@Composable
fun IumrahIdentityHeroAndroid(
    profile: IumrahAccountProfile,
    language: AppLanguage,
) {
    var flipped by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    if (copied) {
        androidx.compose.runtime.LaunchedEffect(copied) {
            kotlinx.coroutines.delay(1_800)
            copied = false
        }
    }

    IdentityDomeCardAndroid(
        profile = profile,
        language = language,
        flipped = flipped,
        copied = copied,
        onFlip = { IumrahHaptics.soft(view); flipped = !flipped },
        onCopy = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("iumrah ID", normalizedIdentity(profile.iumrahID)))
            copied = true
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun IdentityDomeCardAndroid(
    profile: IumrahAccountProfile,
    language: AppLanguage,
    flipped: Boolean,
    copied: Boolean = false,
    onFlip: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = iosSpring(0.66f, 0.84f),
        label = "iumrah-id-flip",
    )
    val progress = (rotation / 180f).coerceIn(0f, 1f)
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    Box(
        modifier
            .aspectRatio(1.60f)
            .graphicsLayer {
                cameraDistance = 10.5f * density
            }
            .shadow(22.dp, IdentityShape, clip = false)
            .clip(IdentityShape)
            .clickable(onClick = onFlip),
    ) {
        IdentityFront(
            Modifier.fillMaxSize().graphicsLayer {
                rotationY = -rotation
                cameraDistance = 10.5f * density
                alpha = 1f - progress
            },
        )
        IdentityBack(
            profile = profile,
            language = language,
            copied = copied,
            onCopy = onCopy,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                rotationY = 180f - rotation
                cameraDistance = 10.5f * density
                alpha = progress
            },
        )
    }
}

@Composable
private fun IdentityFront(modifier: Modifier) {
    val infinite = rememberInfiniteTransition(label = "iumrah-id-dome")
    val progress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing)),
        label = "spectral-cycle",
    )
    Box(
        modifier
            .background(Brush.verticalGradient(listOf(IdentityTop, IdentityBottom)))
            .border(.8.dp, Color.White.copy(alpha = .085f), IdentityShape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .5f, size.height * 1.005f)
            val sphereRadius = minOf(size.width * .49f, size.height * .79f)
            val unitDot = size.width * .00615f
            domePoints.forEach { point ->
                val depth = point.depth
                val perspective = .88f + (.18f * depth)
                val x = center.x + point.x * sphereRadius * perspective
                val y = center.y + point.y * sphereRadius * (.90f + .18f * depth)
                val radius = unitDot * (.66f + .68f * depth)
                val nx = point.x
                val ny = point.y + 1f
                val hue = positiveMod(.585f + progress + .235f * nx + .055f * ny + .045f * depth, 1f)
                val travel = .5f + .5f * sin((2f * PI.toFloat()) * (progress + .30f * nx - .10f * ny + .07f * depth))
                val breathe = .5f + .5f * sin((2f * PI.toFloat()) * ((2f * progress) + .08f))
                val globalLight = .20f + .80f * breathe
                val intensity = (.10f + .90f * (.28f + .72f * travel) * globalLight).coerceIn(.06f, 1f)
                val spectral = Color.hsv(hue * 360f, .78f, 1f)
                val neutral = .17f + .16f * depth
                drawCircle(Color.White.copy(alpha = neutral), radius, Offset(x, y))
                drawCircle(spectral.copy(alpha = .10f * intensity), radius * 1.62f, Offset(x, y))
                drawCircle(spectral.copy(alpha = .92f * intensity), radius, Offset(x, y))
                drawCircle(Color(0xFF050506).copy(alpha = .92f), radius * .43f, Offset(x, y))
                drawCircle(Color.White.copy(alpha = .26f * intensity), maxOf(.28f, radius * .17f), Offset(x - radius * .27f, y - radius * .28f))
            }
        }
        Text(
            "iumrah ID",
            modifier = Modifier.padding(start = 18.dp, top = 16.dp),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-.35).sp,
        )
    }
}

@Composable
private fun IdentityBack(
    profile: IumrahAccountProfile,
    language: AppLanguage,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier,
) {
    val displayName = profile.displayName.trim().ifBlank {
        listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "iumrah" }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .background(Brush.verticalGradient(listOf(IdentityTop, IdentityBottom)))
            .border(.8.dp, Color.White.copy(alpha = .085f), IdentityShape),
    ) {
        val publicNameSize = (maxWidth.value * .078f).coerceIn(25f, 32f).sp
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = .07f), Color.Transparent),
                    center = Offset(size.width, 0f),
                    radius = size.width * .72f,
                ),
            )
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("iumrah ID", color = Color.White.copy(alpha = .96f), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.3).sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    displayName,
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontSize = publicNameSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.55).sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                PublicIdentityQr(normalizedIdentity(profile.iumrahID))
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(.7.dp).background(Color.White.copy(alpha = .13f)))
            Row(Modifier.padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("iumrah ID", color = Color.White.copy(alpha = .46f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Text(normalizedIdentity(profile.iumrahID), color = Color.White.copy(alpha = .92f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
                }
                Surface(onClick = onCopy, shape = CircleShape, color = Color.White.copy(alpha = .12f)) {
                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                        CupertinoIcon(if (copied) CupertinoSymbol.Checkmark else CupertinoSymbol.Copy, null, Modifier.size(13.dp), Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicIdentityQr(identity: String) {
    val value = "https://iumrah.app/id/$identity"
    val bitmap = remember(value) { makeQrBitmap(value) }
    if (bitmap != null) {
        Box(
            Modifier.size(106.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "iumrah ID QR", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    } else {
        Box(Modifier.size(106.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
            CupertinoIcon(CupertinoSymbol.Grid, null, Modifier.size(29.dp), Color.White.copy(alpha = .76f))
        }
    }
}

private fun makeQrBitmap(value: String): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 432, 432)
    val pixels = IntArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            pixels[y * matrix.width + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}.getOrNull()

@Composable
fun IumrahLockedIdentityCardAndroid(language: AppLanguage, onUnlock: () -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    val transition = rememberInfiniteTransition(label = "identity-seal")
    val shimmer by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "identity-seal-shimmer",
    )

    BoxWithConstraints(
        Modifier.fillMaxWidth().height(238.dp).shadow(24.dp, shape, clip = false).clip(shape)
            .background(Color.Black)
            .border(.8.dp, Color.White.copy(alpha = .10f), shape)
            .clickable(onClick = onUnlock),
    ) {
        val cardWidth = maxWidth
        val halfWidth = (maxWidth / 2) + 1.dp

        Column(
            Modifier.fillMaxSize().padding(24.dp).blur(1.2.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("iumrah ID", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text(
                        awTr(language, "DIGITAL PILGRIM IDENTITY", "ЦИФРОВАЯ ID-КАРТА ПАЛОМНИКА", "RAQAMLI ZIYORATCHI ID", "РАҚАМЛИ ЗИЁРАТЧИ ID"),
                        color = Color.White.copy(alpha = .34f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                    )
                }
                CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(20.dp), Color.White.copy(alpha = .40f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    awTr(language, "Your digital identity", "Ваша цифровая карта", "Sizning raqamli kartangiz", "Сизнинг рақамли картангиз"),
                    color = Color.White.copy(alpha = .22f), fontSize = 25.sp, fontWeight = FontWeight.Bold,
                )
                Text("••••••••", color = Color.White.copy(alpha = .24f), fontSize = 33.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily, letterSpacing = 3.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CupertinoIcon(CupertinoSymbol.Sparkles, null, Modifier.size(15.dp), Color.White.copy(alpha = .48f))
                Text(
                    awTr(language, "Unlock your iumrah ID", "Откройте свою iumrah ID", "iumrah ID kartangizni oching", "iumrah ID картангизни очинг"),
                    color = Color.White.copy(alpha = .48f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            repeat(2) { half ->
                val leading = half == 0
                Box(
                    Modifier.width(halfWidth).fillMaxHeight().clip(if (leading) RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp) else RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp))
                        .background(
                            Brush.linearGradient(
                                if (leading) listOf(Color(0xFF090A0C), Color.Black, Color(0xFF0F1014))
                                else listOf(Color(0xFF0F1014), Color.Black, Color(0xFF090A0C))
                            )
                        )
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        repeat(22) { index ->
                            val sx = ((index * 37) % 101) / 101f
                            val sy = ((index * 67 + 11) % 103) / 103f
                            val radius = ((index % 3) + 1) * .65f
                            drawCircle(
                                Color.White.copy(alpha = .08f + ((index % 4) * .025f)),
                                radius = radius,
                                center = Offset(size.width * sx, size.height * sy),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .width((halfWidth * .22f).coerceAtLeast(54.dp))
                            .height(370.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = (-halfWidth * .8f) + (halfWidth * 2.2f * shimmer))
                            .graphicsLayer { rotationZ = -14f }
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = .02f), Color.White.copy(alpha = .17f), Color.White.copy(alpha = .025f), Color.Transparent)
                                )
                            )
                    )
                    Box(
                        Modifier.align(if (leading) Alignment.CenterEnd else Alignment.CenterStart)
                            .width(.6.dp).fillMaxHeight().background(Color.White.copy(alpha = .09f))
                    )
                }
            }
        }

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = .58f)).border(.8.dp, Color.White.copy(alpha = .16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CupertinoIcon(CupertinoSymbol.Lock, null, Modifier.size(21.dp), Color.White)
            }
            Text(
                awTr(language, "Your iumrah ID is sealed", "Ваша iumrah ID закрыта", "iumrah ID kartangiz yopiq", "iumrah ID картангиз ёпиқ"),
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
            )
            Text(
                awTr(language, "Sign in or register to reveal your card", "Войдите или зарегистрируйтесь, чтобы открыть карту", "Kartani ochish uchun kiring yoki ro‘yxatdan o‘ting", "Картани очиш учун киринг ёки рўйхатдан ўтинг"),
                color = Color.White.copy(alpha = .62f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2,
            )
        }
    }
}

@Composable
fun IumrahTripWalletEntryAndroid(
    session: StoredBookingSession,
    profile: IumrahAccountProfile?,
    language: AppLanguage,
) {
    var presented by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().clickable { presented = true }.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("iumrah Wallet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    awTr(language, "Your ID, boarding passes and hotels", "Ваш ID, посадочные талоны и отели", "ID, boarding pass va mehmonxonalar bir joyda", "ID, boarding pass ва меҳмонхоналар бир жойда"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                    fontSize = 12.sp,
                )
            }
            CupertinoIcon(CupertinoSymbol.ArrowUpRight, null, Modifier.size(15.dp), MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        Image(
            painter = painterResource(R.drawable.iumrah_leather_wallet),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = 1.12f; scaleY = 1.12f },
            contentScale = ContentScale.FillWidth,
        )
    }
    if (presented) {
        IumrahTripWalletDialog(session, profile, language) { presented = false }
    }
}

private sealed interface WalletPage {
    data object Identity : WalletPage
    data class Flight(val value: BookingGeneratorFlightSnapshot, val outbound: Boolean) : WalletPage
    data class Hotel(val value: BookingHotelSelectionSnapshot, val makkah: Boolean) : WalletPage
}

@Composable
private fun IumrahTripWalletDialog(
    session: StoredBookingSession,
    profile: IumrahAccountProfile?,
    language: AppLanguage,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pages = remember(session, profile) {
        buildList {
            add(WalletPage.Identity)
            session.booking.generatorTrace?.outbound?.let { add(WalletPage.Flight(it, true)) }
            session.booking.generatorTrace?.inbound?.let { add(WalletPage.Flight(it, false)) }
            (session.hotelSelection ?: session.booking.hotelSelection)?.let { add(WalletPage.Hotel(it, true)) }
            (session.madinahHotelSelection ?: session.booking.madinahHotelSelection)?.let { add(WalletPage.Hotel(it, false)) }
        }
    }
    val listState = rememberLazyListState()
    val currentPage = listState.firstVisibleItemIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = .96f)).statusBarsPadding()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("iumrah Wallet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(walletSubtitle(pages.getOrNull(currentPage), language), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                    Surface(onClick = onDismiss, shape = CircleShape, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { CupertinoIcon(CupertinoSymbol.Close, null, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurface) }
                    }
                }
                Spacer(Modifier.weight(.35f))
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().height(560.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 26.dp),
                ) {
                    itemsIndexed(items = pages) { _: Int, page: WalletPage ->
                        Box(Modifier.fillParentMaxWidth().fillMaxHeight(), contentAlignment = Alignment.Center) {
                            when (page) {
                                WalletPage.Identity -> {
                                    val p = profile ?: IumrahAccountProfile(
                                        iumrahID = session.pilgrimID.orEmpty(),
                                        displayName = session.travelerName.orEmpty(),
                                    )
                                    var flip by remember { mutableStateOf(false) }
                                    IdentityDomeCardAndroid(
                                        profile = p,
                                        language = language,
                                        flipped = flip,
                                        onFlip = { flip = !flip },
                                        onCopy = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("iumrah ID", normalizedIdentity(p.iumrahID)))
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    )
                                }
                                is WalletPage.Flight -> WalletBoardingPass(page.value, page.outbound, session, language)
                                is WalletPage.Hotel -> WalletHotelCard(page.value, page.makkah, language)
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.indices.forEach { index ->
                        Box(
                            Modifier.width(if (index == currentPage) 22.dp else 7.dp).height(7.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (index == currentPage) .82f else .18f)),
                        )
                    }
                }
                Spacer(Modifier.weight(.55f))
            }
        }
    }
}

private fun walletSubtitle(page: WalletPage?, language: AppLanguage): String = when (page) {
    WalletPage.Identity -> awTr(language, "Pilgrim identity", "ID паломника", "Ziyoratchi ID", "Зиёратчи ID")
    is WalletPage.Flight -> if (page.outbound) awTr(language, "Outbound boarding pass", "Посадочный талон туда", "Borish boarding pass", "Бориш boarding pass") else awTr(language, "Return boarding pass", "Посадочный талон обратно", "Qaytish boarding pass", "Қайтиш boarding pass")
    is WalletPage.Hotel -> if (page.makkah) awTr(language, "Makkah hotel", "Отель в Мекке", "Makka mehmonxonasi", "Макка меҳмонхонаси") else awTr(language, "Madinah hotel", "Отель в Медине", "Madina mehmonxonasi", "Мадина меҳмонхонаси")
    null -> "iumrah Wallet"
}

@Composable
private fun WalletBoardingPass(
    flight: BookingGeneratorFlightSnapshot,
    outbound: Boolean,
    session: StoredBookingSession,
    language: AppLanguage,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Color.White).border(.8.dp, Color.Black.copy(alpha = .07f), shape).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.iumrah_flights_boarding_logo), null, Modifier.width(122.dp).height(48.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(flight.airline, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(flight.flightNumbers, color = Color.Black.copy(alpha = .56f), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
            }
        }
        Text(
            if (outbound) awTr(language, "OUTBOUND", "ТУДА", "BORISH", "БОРИШ") else awTr(language, "RETURN", "ОБРАТНО", "QAYTISH", "ҚАЙТИШ"),
            color = Color.Black.copy(alpha = .40f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AirportCode(flight.origin, Modifier.weight(1f), Alignment.Start)
            Column(Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CupertinoIcon(CupertinoSymbol.Airplane, null, Modifier.size(21.dp), Color.Black)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = .16f)))
                Text(if (flight.stops == null || flight.stops == 0) awTr(language, "Direct", "Прямой", "To‘g‘ridan", "Тўғридан") else "${flight.stops} stop", color = Color.Black.copy(alpha = .45f), fontSize = 10.sp)
            }
            AirportCode(flight.destination, Modifier.weight(1f), Alignment.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = .12f)))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(awTr(language, "BOOKING", "БРОНЬ", "BRON", "БРОН"), color = Color.Black.copy(alpha = .40f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(session.displayBookingNumber, color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = com.iumrah.beta.core.design.IumrahFontFamily)
            }
            Text(session.travelerName.orEmpty().ifBlank { profileName(session) }, color = Color.Black.copy(alpha = .64f), fontSize = 11.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AirportCode(code: String, modifier: Modifier, alignment: Alignment.Horizontal) {
    Column(modifier, horizontalAlignment = alignment) {
        Text(code, color = Color.Black, fontSize = 38.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WalletHotelCard(hotel: BookingHotelSelectionSnapshot, makkah: Boolean, language: AppLanguage) {
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(1.586f).clip(shape).background(Color.Black)) {
        hotel.coverImageURL?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .12f), Color.Black.copy(alpha = .92f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                if (makkah) awTr(language, "MAKKAH", "МЕККА", "MAKKA", "МАККА") else awTr(language, "MADINAH", "МЕДИНА", "MADINA", "МАДИНА"),
                color = Color.White.copy(alpha = .70f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            )
            Text(hotel.hotelName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            hotel.roomName?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = .65f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
        }
    }
}

private fun profileName(session: StoredBookingSession): String = session.booking.pilgrimProfile?.displayName.orEmpty()
private fun positiveMod(value: Float, divisor: Float): Float { val r = value % divisor; return if (r < 0f) r + divisor else r }
private fun normalizedIdentity(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return when {
        digits.isEmpty() -> raw
        digits.length >= 8 -> digits
        else -> digits.padStart(8, '0')
    }
}
