package com.iumrah.beta.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.iumrah.beta.core.design.rememberIumrahMotionAllowed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.iumrah.beta.R

/**
 * Android parity for the iOS continuously moving payment-method strip.
 * The source artwork is shared with the iOS client and rendered as monochrome
 * template logos so it stays legible in light and dark appearance.
 */
@Composable
fun IumrahPaymentMethodsMarquee(
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val logos = listOf(
        R.drawable.payment_visa,
        R.drawable.payment_payme,
        R.drawable.payment_humo,
        R.drawable.payment_uzcard,
        R.drawable.payment_click,
    )
    val itemWidth = if (compact) 84.dp else 98.dp
    val itemHeight = if (compact) 26.dp else 32.dp
    val spacing = if (compact) 14.dp else 18.dp
    val groupWidth = (itemWidth + spacing) * logos.size.toFloat()
    val groupWidthPx = with(LocalDensity.current) { groupWidth.toPx() }
    val durationMillis = (groupWidth.value / (if (compact) 21f else 25f) * 1000f).toInt()
    val motionAllowed = rememberIumrahMotionAllowed()
    val offset = if (motionAllowed) {
        val transition = rememberInfiniteTransition(label = "payment-methods-marquee")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = groupWidthPx,
            animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Restart),
            label = "payment-methods-offset",
        )
        animated
    } else 0f
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f)

    Box(
        modifier = modifier
            .height(if (compact) 44.dp else 54.dp)
            .clipToBounds().clearAndSetSemantics { },
    ) {
        Row(
            modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer { translationX = -offset },
        ) {
            repeat(4) {
                logos.forEach { logo ->
                    Image(
                        painter = painterResource(logo),
                        contentDescription = null,
                        modifier = Modifier
                            .width(itemWidth)
                            .height(if (compact) 40.dp else 48.dp)
                            .padding(horizontal = 7.dp, vertical = ((if (compact) 40.dp else 48.dp) - itemHeight) / 2),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(tint),
                    )
                    Spacer(Modifier.width(spacing))
                }
            }
        }
    }
}
