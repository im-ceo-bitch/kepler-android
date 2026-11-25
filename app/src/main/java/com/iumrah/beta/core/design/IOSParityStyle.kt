package com.iumrah.beta.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.iumrah.beta.core.navigation.AppRoute
import com.iumrah.beta.core.navigation.AppTab

/** Explicitly opt in only inside non-Home destinations. Home, launch and
 * onboarding retain the original shared component implementations. */
internal val LocalIOSParity = staticCompositionLocalOf { false }
internal val LocalParityTab = staticCompositionLocalOf<AppTab?> { null }
internal val LocalParityRoute = staticCompositionLocalOf<AppRoute?> { null }
internal val LocalIOSSidebarVisible = staticCompositionLocalOf { false }
internal val LocalIOSLayout = staticCompositionLocalOf { IOSLayoutProfile.resolve(0f) }

/** Continuous-corner approximation, not a claim to reproduce Apple's private
 * curve. Tangents meet the straight edges continuously; tiny sizes clamp radius. */
internal data class IOSContinuousShape(val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }.coerceIn(0f, minOf(w, h) / 2f)
        if (r == 0f) return Outline.Rectangle(androidx.compose.ui.geometry.Rect(0f, 0f, w, h))
        val k = r * .72f
        return Outline.Generic(Path().apply {
            moveTo(r, 0f); lineTo(w-r, 0f)
            cubicTo(w-r+k, 0f, w, r-k, w, r)
            lineTo(w, h-r); cubicTo(w, h-r+k, w-r+k, h, w-r, h)
            lineTo(r, h); cubicTo(r-k, h, 0f, h-r+k, 0f, h-r)
            lineTo(0f, r); cubicTo(0f, r-k, r-k, 0f, r, 0f)
            close()
        })
    }
}

@Composable
internal fun IOSParityScope(tab: AppTab, route: AppRoute, profile: IOSLayoutProfile, immersive: Boolean, sidebarVisible: Boolean, content: @Composable () -> Unit) {
    val enabled = true
    CompositionLocalProvider(LocalIOSParity provides enabled, LocalParityTab provides tab,
        LocalParityRoute provides route, LocalIOSLayout provides profile, LocalIOSSidebarVisible provides (enabled && sidebarVisible)) {
        if (!enabled) content() else {
            val base = MaterialTheme.colorScheme
            val dark = base.background.luminance() < .5f
            val colors = base.copy(surfaceVariant = if (dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                primary = if (dark) Color(0xFFF5F5F5) else Color.Black,
                onPrimary = if (dark) Color(0xFF121316) else Color.White)
            MaterialTheme(colorScheme = colors) {
                ProvideTextStyle(TextStyle(fontFamily = IumrahFontFamily, platformStyle = PlatformTextStyle(includeFontPadding = false))) {
                    // AppNavigationContainer uses root readable width even for its
                    // destinations. Immersive maps/ritual screens remain full bleed.
                    val max = profile.rootMaxWidth
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        Box(if (immersive || max == null) Modifier.fillMaxWidth() else Modifier.widthIn(max = max.dp).fillMaxWidth()) { content() }
                    }
                }
            }
        }
    }
}

/** Glass is reserved for navigation chrome. This is a deterministic layered
 * approximation (no backdrop sampling, refraction or GlassEffectContainer). */
@Composable
internal fun Modifier.iosChromeSurface(shape: Shape, onPhotography: Boolean = false): Modifier {
    val dark = onPhotography || MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = if (onPhotography) listOf(Color.White.copy(alpha=.18f), Color.White.copy(alpha=.07f))
        else if (dark) listOf(Color(0xE638383A), Color(0xE61C1C1E))
        else listOf(Color(0xF5FFFFFF), Color(0xEBF2F2F7))
    val edge = if (dark) Color.White.copy(alpha=.22f) else Color.Black.copy(alpha=.10f)
    return clip(shape).background(Brush.verticalGradient(colors)).border(.7.dp, edge, shape)
}
