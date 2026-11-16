package com.iumrah.beta.ui.booking

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.iosSpring
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.models.booking.StoredBookingSession
import kotlin.math.*

private data class BookingDomePoint(val x: Float, val y: Float, val depth: Float)

/**
 * Pixel-parity port of iOS IumrahBookingDomeCard.
 * Keep the literal `cardFlip` token: Stage 007 verifies that the native flip remains present.
 */
@Composable
fun IumrahBookingDomeCard(
    session: StoredBookingSession,
    language: AppLanguage = AppLanguage.ENGLISH,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit = {},
) {
    var flipped by remember(session.id) { mutableStateOf(false) }
    val cardFlip by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = iosSpring(responseSeconds = .66f, dampingFraction = .84f),
        label = "booking-cardFlip",
    )
    val view = LocalView.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val cameraDistancePx = with(density) { 28.dp.toPx() * 18f }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.60f)
            .shadow(
                elevation = 22.dp,
                shape = RoundedCornerShape(22.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = .10f),
                spotColor = Color.Black.copy(alpha = .18f),
            )
            .clickable(interactionSource = interaction, indication = null) {
                IumrahHaptics.soft(view)
                flipped = !flipped
            },
    ) {
        // Separate faces intentionally own their surface just like SwiftUI.
        BookingDomeSurface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = -cardFlip
                    cameraDistance = cameraDistancePx
                    alpha = if (cardFlip < 90f) 1f else 0f
                },
        ) {
            BookingDomeFront()
        }

        BookingDomeSurface(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = 180f - cardFlip
                    cameraDistance = cameraDistancePx
                    alpha = if (cardFlip >= 90f) 1f else 0f
                },
        ) {
            BookingDomeBack(session, language, onOpen)
        }
    }
}

@Composable
private fun BookingDomeSurface(
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(red = .075f, green = .076f, blue = .082f),
                        Color(red = .032f, green = .033f, blue = .038f),
                    ),
                ),
            ),
    ) {
        content()
        Canvas(Modifier.fillMaxSize()) {
            val stroke = .8.dp.toPx()
            drawRoundRect(
                color = Color.White.copy(alpha = .085f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun BoxScope.BookingDomeFront() {
    SpectralBookingDome(Modifier.fillMaxSize())
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 16.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            "iumrah Booking",
            color = Color.White,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-.25).sp,
        )
        BookingActivityDots()
    }
}

@Composable
private fun BoxScope.BookingDomeBack(
    session: StoredBookingSession,
    language: AppLanguage,
    onOpen: () -> Unit,
) {
    // Restrained light falloff from top trailing, matching the iOS reverse face.
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .07f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.width * .72f,
            ),
            radius = size.width * .72f,
            center = Offset(size.width, 0f),
        )
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp),
    ) {
        Text(
            "iumrah Booking",
            color = Color.White.copy(alpha = .96f),
            fontSize = 18.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-.25).sp,
        )

        Spacer(Modifier.weight(1f))

        Text(
            session.travelerName?.trim()?.takeIf { it.isNotEmpty() } ?: bookingText(language, "Ваша Umrah", "Your Umrah", "Sizning Umrangiz", "Сизнинг Умрангиз"),
            color = Color.White,
            fontSize = 27.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-.55).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(.7.dp).background(Color.White.copy(alpha = .13f)))
        Spacer(Modifier.height(11.dp))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                bookingText(language, "Ваш Booking ID", "Your Booking ID", "Sizning Booking ID", "Сизнинг Booking ID"),
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = .52f),
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                session.displayBookingNumber,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-.8).sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BookingActivityDots() {
    val transition = rememberInfiniteTransition(label = "booking-activity-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1366, easing = LinearEasing)),
        label = "booking-activity-phase",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
        repeat(3) { index ->
            val wave = .5f + .5f * sin(phase - index * 1.45f)
            Box(
                Modifier
                    .size(4.2.dp)
                    .graphicsLayer {
                        scaleX = .82f + .18f * wave
                        scaleY = .82f + .18f * wave
                        alpha = .24f + .76f * wave
                    }
                    .background(Color.White, RoundedCornerShape(99.dp)),
            )
        }
    }
}

@Composable
internal fun SpectralBookingDome(modifier: Modifier) {
    val points = remember {
        buildList {
            val ringCount = 18
            val thetaStart = .085
            val thetaEnd = Math.PI / 2.0 - .115
            for (ring in 0 until ringCount) {
                val fraction = ring.toDouble() / max(1, ringCount - 1).toDouble()
                val theta = thetaStart + (thetaEnd - thetaStart) * fraction
                val sinTheta = sin(theta)
                val cosTheta = cos(theta)
                val count = max(6, (48.0 * sinTheta).roundToInt())
                val stagger = if (ring % 2 == 0) 0.0 else .25
                for (index in 0 until count) {
                    val phi = Math.PI * ((index.toDouble() + .5 + stagger) / count.toDouble())
                    val depth = sinTheta * sin(phi)
                    val rawX = sinTheta * cos(phi)
                    val rawY = -cosTheta
                    val x = rawX * (.94 + .06 * depth)
                    add(BookingDomePoint(x.toFloat(), rawY.toFloat(), depth.toFloat()))
                }
            }
        }.sortedBy { it.depth }
    }

    val transition = rememberInfiniteTransition(label = "booking-dome-spectrum")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing)),
        label = "booking-dome-spectrum-phase",
    )

    Canvas(modifier) {
        if (size.width <= 1f || size.height <= 1f) return@Canvas
        val center = Offset(size.width * .5f, size.height * 1.005f)
        val sphereRadius = min(size.width * .49f, size.height * .79f)
        val unitDot = size.width * .00615f

        points.forEach { point ->
            val depth = point.depth
            val perspective = .88f + .18f * depth
            val x = center.x + point.x * sphereRadius * perspective
            val y = center.y + point.y * sphereRadius * (.90f + .18f * depth)
            val radius = unitDot * (.66f + .68f * depth)
            val nx = point.x
            val ny = point.y + 1f

            var hue = .585f + progress + .235f * nx + .055f * ny + .045f * depth
            hue %= 1f
            if (hue < 0f) hue += 1f

            val travel = .5f + .5f * sin((2f * Math.PI).toFloat() * (progress + .30f * nx - .10f * ny + .07f * depth))
            val breathe = .5f + .5f * sin((2f * Math.PI).toFloat() * (2f * progress + .08f))
            val globalLight = .20f + .80f * breathe
            val intensity = (.10f + .90f * (.28f + .72f * travel) * globalLight).coerceIn(.06f, 1f)
            val spectral = Color.hsv(hue * 360f, .78f, 1f)
            val p = Offset(x, y)

            drawCircle(Color.White.copy(alpha = .17f + .16f * depth), radius, p)
            drawCircle(spectral.copy(alpha = .10f * intensity), radius * 1.62f, p)
            drawCircle(spectral.copy(alpha = .92f * intensity), radius, p)
            drawCircle(Color(red = .018f, green = .019f, blue = .023f, alpha = .92f), radius * .43f, p)
            drawCircle(
                Color.White.copy(alpha = .26f * intensity),
                max(.28f, radius * .17f),
                Offset(x - radius * .27f, y - radius * .28f),
            )
        }
    }
}
