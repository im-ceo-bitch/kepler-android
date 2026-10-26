package com.iumrah.beta.ui.components

import android.graphics.RuntimeShader
import android.graphics.RenderEffect as AndroidRenderEffect
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.iumrah.beta.R
import com.iumrah.beta.core.design.IumrahColors
import kotlin.math.sin

/**
 * Mirrors Sources/Core/Components/IumrahWorldDotsHeaderBackground.swift.
 * Original geographic atlas is byte-for-byte identical to the source iOS PNG.
 * Android API 33+ runs the SAME per-pixel wave formula as the iOS Metal shader
 * in native AGSL. Earlier Android versions retain the original atlas and
 * the mono fade with a compatible gradient approximation (not shader parity).
 * Pure decoration: neither consumes touch events nor emits semantics.
 */
@Composable
fun IumrahSignatureWorldBackground(
    modifier: Modifier = Modifier,
    mapOpacity: Float = .34f,
    waveSpeed: Float = .56f,
    waveIntensity: Float = .78f,
) {
    val context = LocalContext.current
    val motionAllowed = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true)
    }
    // On iOS the shader receives elapsed seconds (TimelineView 30 FPS).
    // Keeping time in SECONDS (not a 2π sweep) preserves wave propagation.
    val clock = rememberInfiniteTransition(label = "iumrah-world-dots-wave")
    val elapsedSeconds by clock.animateFloat(
        initialValue = 0f,
        targetValue = 3600f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_600_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "world-dots-elapsed-seconds",
    )
    val t = if (motionAllowed) elapsedSeconds else 0f
    val gain = mapOpacity.coerceIn(0f, 1f)
    val intensity = if (motionAllowed) waveIntensity.coerceIn(0f, 1f) else 0f
    val speed = waveSpeed.coerceIn(0f, 3f)

    BoxWithConstraints(
        modifier = modifier
            .background(IumrahColors.SignatureBlack)
            .clipToBounds()
            .clearAndSetSemantics { },
    ) {
        val width = maxWidth
        val mapWidth = (width * .94f).coerceIn(300.dp, 920.dp)
        // iOS frame center: x=headerWidth*.57, y=headerHeight*.50;
        // Compose centers child then offsets by +.07*headerWidth.
        Box(
            Modifier.fillMaxSize().drawWithContent {
                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawContent()
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Transparent,
                        .25f to Color.White.copy(alpha = .32f),
                        .61f to Color.White,
                        1f to Color.White,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                drawContext.canvas.restore()
            },
            contentAlignment = Alignment.Center,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val shader = remember { RuntimeShader(WORLD_DOT_WAVE_AGSL) }
                val effect = remember(shader) {
                    AndroidRenderEffect.createRuntimeShaderEffect(shader, "sourceImage")
                        .asComposeRenderEffect()
                }
                Image(
                    painter = painterResource(R.drawable.iumrah_world_dots),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(mapWidth)
                        .height(mapWidth / 2)
                        .offset(x = width * .07f)
                        .graphicsLayer {
                            // Animation state is read here so RenderNode redraws
                            // its RuntimeShader as t changes, without CPU bitmap copies.
                            shader.setFloatUniform("time", t)
                            shader.setFloatUniform("width", size.width)
                            shader.setFloatUniform("height", size.height)
                            shader.setFloatUniform("speed", speed)
                            shader.setFloatUniform("waveIntensity", intensity)
                            shader.setFloatUniform("overallOpacity", gain)
                            renderEffect = effect
                        },
                )
            } else {
                // RuntimeShader (AGSL) is unavailable before Android 13.
                // Preserve the actual dot atlas and header mask; no hand-drawn dots.
                val sweep = .15f * sin(t * speed)
                Image(
                    painter = painterResource(R.drawable.iumrah_world_dots),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .width(mapWidth)
                        .height(mapWidth / 2)
                        .offset(x = width * .07f)
                        .graphicsLayer { alpha = gain }
                        .drawWithContent {
                            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                            drawContent()
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.White.copy(alpha = .20f),
                                    (.23f + sweep).coerceIn(.02f, .40f) to Color.White.copy(alpha = .48f),
                                    (.59f + sweep).coerceIn(.41f, .78f) to Color.White.copy(alpha = .62f + .27f * intensity),
                                    1f to Color.White.copy(alpha = .75f),
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                            drawContext.canvas.restore()
                        },
                )
            }
        }
    }
}

/** Mechanical AGSL port of IumrahWorldDotsShaders.metal iumrahWorldDotWave().
 * Original constants/equations are kept in the same order for auditability.
 * The input shader holds the original premultiplied geographic atlas.
 */
private val WORLD_DOT_WAVE_AGSL = """
    uniform shader sourceImage;
    uniform float time;
    uniform float width;
    uniform float height;
    uniform float speed;
    uniform float waveIntensity;
    uniform float overallOpacity;

    half4 main(float2 position) {
        half4 source = sourceImage.eval(position);
        if (source.a <= 0.0001) { return half4(0.0); }
        float2 uv = position / max(float2(width, height), float2(1.0));
        float intensity = clamp(waveIntensity, 0.0, 1.0);
        float gain = clamp(overallOpacity, 0.0, 1.0);
        float motion = time * speed;

        float bend = 0.17 * sin(uv.y * 8.5 + motion * 0.23)
                   + 0.053 * cos(uv.y * 17.0 - motion * 0.13);
        float travel = (uv.x * 1.72 - motion * 0.14 + bend) * 6.2831853;
        float lightPhase = sin(travel);
        float highlight = pow(max(lightPhase, 0.0), 3.0);
        float shadow = pow(max(-lightPhase, 0.0), 2.0);
        float breathing = 0.5 + 0.5 * sin(
            (uv.x * 0.8 + uv.y * 0.4) * 9.1 - motion * 0.44
        );
        float dynamicLuminance = clamp(
            0.46 + 0.51 * highlight - 0.30 * shadow + 0.048 * breathing,
            0.18, 1.0
        );
        half brightness = half(mix(0.67, dynamicLuminance, intensity) * gain);
        return source * brightness;
    }
""".trimIndent()

/**
 * Exact equivalent of SwiftUI .padding(.horizontal, -18) inside an
 * 18dp-padded page. RequiredWidth is NOT used: Compose may silently center
 * an out-of-constraints child; explicit placement fixes the bleed origin.
 * This modifier keeps the externally reported width unchanged and places
 * its actual contents 18dp to the left with 18dp additional width at right.
 */
fun Modifier.iumrahSignatureBleed(horizontal: Dp = 18.dp): Modifier = this.layout { measurable, constraints ->
    val bleedPx = horizontal.roundToPx()
    val fullWidth = constraints.maxWidth + bleedPx * 2
    val placeable = measurable.measure(
        constraints.copy(minWidth = fullWidth, maxWidth = fullWidth)
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(-bleedPx, 0)
    }
}
