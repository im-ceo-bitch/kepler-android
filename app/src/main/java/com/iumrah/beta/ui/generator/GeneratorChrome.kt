package com.iumrah.beta.ui.generator

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iumrah.beta.core.design.IumrahHaptics
import com.iumrah.beta.core.design.iosSpring
import com.iumrah.beta.core.localization.L10n
import com.iumrah.beta.core.navigation.AppChromeStore
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.ui.components.IumrahPressable
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import com.iumrah.beta.ui.cupertino.Icon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Literal Android mirror of the SwiftUI TripProgressStage / IumrahGeneratorHeader geometry. */
enum class GeneratorStage(val raw: Int, val key: String, val icon: CupertinoSymbol) {
    TRIP(1, "step_trip", CupertinoSymbol.CalendarClock),
    HOTEL(2, "step_hotel", CupertinoSymbol.Hotel),
    FLIGHT(3, "step_flight", CupertinoSymbol.AirplaneTakeoff),
    TRANSFER(4, "step_transfer", CupertinoSymbol.Car),
    READY(5, "step_ready", CupertinoSymbol.CheckCircle),
}

object GeneratorGeometry {
    val pagePadding = 18.dp
    val cardRadius = 28.dp
    val heroRadius = 34.dp
    val compactRadius = 19.dp
    val controlHeight = 56.dp
    val glassIconSize = 46.dp
}

@Composable
fun generatorPageColor(): Color {
    val dark = MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue < 0.75f
    return if (dark) Color.Black else Color.White
}

@Composable
fun generatorCardColor(): Color {
    val dark = generatorPageColor() == Color.Black
    return if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
}

@Composable
fun generatorRaisedColor(): Color {
    val dark = generatorPageColor() == Color.Black
    return if (dark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
}

private fun stageTint(stage: GeneratorStage): Color = when (stage) {
    GeneratorStage.TRIP -> Color(0xFFFF9500)      // systemOrange / calendar
    GeneratorStage.HOTEL -> Color(0xFF5856D6)     // systemIndigo
    GeneratorStage.FLIGHT -> Color(0xFF007AFF)    // systemBlue
    GeneratorStage.TRANSFER -> Color(0xFF32ADE6)  // systemCyan
    GeneratorStage.READY -> Color(0xFF34C759)     // systemGreen
}

@Composable
fun GeneratorHeader(
    stage: GeneratorStage,
    language: AppLanguage,
    chrome: AppChromeStore,
    currentPriceText: String? = null,
    modifier: Modifier = Modifier,
) {
    val page = generatorPageColor()
    val card = generatorCardColor()
    val fg = if (page == Color.White) Color.Black else Color.White
    val secondary = fg.copy(alpha = .55f)
    val shape = RoundedCornerShape(28.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            // SwiftUI: a single glass chrome container. On Android we keep the
            // fallback opaque, rather than faking glass with blur/opacity.
            .shadow(12.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .035f), spotColor = Color.Black.copy(alpha = .055f))
            .clip(shape)
            .background(card)
            .border(.7.dp, fg.copy(alpha = .07f), shape)
            .padding(horizontal = 10.dp)
            .padding(top = 7.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            GeneratorStageCarousel(
                stage = stage,
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
            )

            IumrahPressable(
                onClick = chrome::back,
                modifier = Modifier.align(Alignment.CenterStart).offset(x = 2.dp).size(40.dp),
                cornerRadius = 99.dp,
                background = generatorRaisedColor(),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(CupertinoSymbol.ChevronLeft, backLabel(language), Modifier.size(18.dp), tint = fg)
                }
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${stepPrefix(language)} ${stage.raw} / 5",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = secondary,
                )
                Spacer(Modifier.weight(1f))
                if (currentPriceText != null) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            currentTotalLabel(language),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = secondary,
                            letterSpacing = .7.sp,
                        )
                        Text(currentPriceText, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
                    }
                } else {
                    Text(
                        L10n.text(stage.key, language),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GeneratorStage.entries.forEach { item ->
                    val active = item == stage
                    val done = item.raw < stage.raw
                    val fill = when {
                        done -> Color(0xFF74A187)
                        active -> fg
                        else -> secondary.copy(alpha = .18f)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(if (active) 5.dp else 3.5.dp)
                            .clip(CircleShape)
                            .background(fill),
                    )
                }
            }
        }
    }
}

@Composable
private fun GeneratorStageCarousel(stage: GeneratorStage, modifier: Modifier = Modifier) {
    val card = generatorCardColor()
    val fg = if (generatorPageColor() == Color.White) Color.Black else Color.White
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val targetIndex = (stage.raw - 1).toFloat()
    val position = remember { Animatable(targetIndex) }
    var dragTranslationPx by remember { mutableFloatStateOf(0f) }
    var resetJob: Job? = remember { null }
    val itemSpacingPx = with(density) { 68.dp.toPx() }

    val breathing = rememberInfiniteTransition(label = "generator-glow")
    val breath by breathing.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "generator-glow-breath",
    )

    LaunchedEffect(stage) {
        resetJob?.cancel()
        dragTranslationPx = 0f
        position.snapTo(targetIndex)
        delay(260)
        // SwiftUI deliberately performs one continuous 360° presentation lap.
        position.animateTo(targetIndex + GeneratorStage.entries.size, tween(1550, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(
        modifier = modifier.pointerInput(stage) {
            detectHorizontalDragGestures(
                onDragStart = { resetJob?.cancel() },
                onHorizontalDrag = { _, amount -> dragTranslationPx += amount },
                onDragCancel = {
                    dragTranslationPx = 0f
                    resetJob = scope.launch {
                        delay(1000)
                        position.animateTo(nearestEquivalent(targetIndex, position.value), iosSpring(.62f, .87f))
                    }
                },
                onDragEnd = {
                    val steps = (-dragTranslationPx / itemSpacingPx).roundToInt().coerceIn(-2, 2)
                    dragTranslationPx = 0f
                    if (steps != 0) IumrahHaptics.selection(view)
                    scope.launch {
                        if (steps != 0) position.animateTo(position.value + steps, iosSpring(.46f, .84f))
                        resetJob?.cancel()
                        resetJob = launch {
                            delay(1000)
                            position.animateTo(nearestEquivalent(targetIndex, position.value), iosSpring(.62f, .87f))
                        }
                    }
                },
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        val radiusDp = minOf(88f, maxOf(54f, maxWidth.value * .37f))
        val radiusPx = with(density) { radiusDp.dp.toPx() }
        val displayed = position.value - (dragTranslationPx / itemSpacingPx)
        val active = GeneratorStage.entries[wrapped(displayed.roundToInt())]

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val glowWidth = (142f + 16f * breath).dp
            val glowHeight = (38f + 6f * breath).dp
            Box(
                Modifier
                    .size(glowWidth, glowHeight)
                    .offset(y = 14.dp)
                    .blur(15.dp)
                    .graphicsLayer { alpha = .68f + .20f * breath }
                    .background(
                        Brush.radialGradient(
                            colors = listOf(stageTint(active).copy(alpha = .27f), stageTint(active).copy(alpha = .10f), Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )

            GeneratorStage.entries.forEachIndexed { index, item ->
                val angle = (index.toFloat() - displayed) * (2.0 * PI / GeneratorStage.entries.size).toFloat()
                val depth = cos(angle)
                val frontness = ((depth + 1f) / 2f).coerceIn(0f, 1f)
                val x = sin(angle) * radiusPx
                val emphasis = frontness.pow(4.5f)
                val tileOpacity = .06f + .90f * emphasis
                val scale = .56f + .46f * frontness
                val alpha = .14f + .86f * frontness.pow(.9f)
                val blurDp = 5.5f * (1f - frontness)
                val yDp = 6f * (1f - frontness)
                val tileShape = RoundedCornerShape(20.dp)

                Box(
                    Modifier
                        .size(64.dp)
                        .graphicsLayer {
                            translationX = x
                            translationY = with(density) { yDp.dp.toPx() }
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            rotationY = -sin(angle) * 18f
                            cameraDistance = 12f * density.density
                        }
                        .blur(blurDp.dp)
                        .shadow((14f * emphasis).dp, tileShape, clip = false, ambientColor = stageTint(item).copy(alpha = .13f * emphasis), spotColor = stageTint(item).copy(alpha = .13f * emphasis))
                        .clip(tileShape)
                        .background(card.copy(alpha = tileOpacity))
                        .border(.75.dp, fg.copy(alpha = .018f + .045f * emphasis), tileShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        item.icon,
                        null,
                        Modifier.size((25f + 5f * frontness).dp),
                        tint = stageTint(item),
                    )
                }
            }
        }
    }
}

private fun wrapped(index: Int): Int {
    val count = GeneratorStage.entries.size
    return ((index % count) + count) % count
}

private fun nearestEquivalent(index: Float, current: Float): Float {
    val count = GeneratorStage.entries.size.toFloat()
    val revolutions = ((current - index) / count).roundToInt()
    return index + revolutions * count
}

@Composable
fun GeneratorCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fg = if (generatorPageColor() == Color.White) Color.Black else Color.White
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = .045f), spotColor = Color.Black.copy(alpha = .045f))
            .clip(shape)
            .background(generatorCardColor())
            .border(.7.dp, fg.copy(alpha = .075f), shape)
            .padding(18.dp),
    ) { content() }
}

@Composable
fun GeneratorPrimaryButton(
    title: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val page = generatorPageColor()
    val bg = if (page == Color.White) Color.Black else Color(0xFFF5F5F5)
    val fg = if (page == Color.White) Color.White else Color(0xFF121315)
    IumrahPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        cornerRadius = 19.dp,
        background = bg.copy(alpha = if (enabled) 1f else .45f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = fg, textAlign = TextAlign.Center)
        }
    }
}

private fun stepPrefix(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Шаг"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Step"
    AppLanguage.UZBEK -> "Bosqich"
    AppLanguage.UZBEK_CYRILLIC -> "Босқич"
}

private fun currentTotalLabel(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "ТЕКУЩАЯ ЦЕНА"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "CURRENT TOTAL"
    AppLanguage.UZBEK -> "JORIY NARX"
    AppLanguage.UZBEK_CYRILLIC -> "ЖОРИЙ НАРХ"
}

private fun backLabel(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "Назад"
    AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Back"
    AppLanguage.UZBEK -> "Orqaga"
    AppLanguage.UZBEK_CYRILLIC -> "Орқага"
}
