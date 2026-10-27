package com.iumrah.beta.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * iumrah Galaxy motion language.
 *
 * Normal UI transitions use the characteristic One UI-style acceleration curve
 * (fast response, soft landing). Springs are reserved for physical transforms
 * such as cards, tabs and larger reveals.
 */
object IumrahMotion {
    const val PressedScale = 0.972f
    const val CardPressedScale = 0.982f

    val oneUiEasing = CubicBezierEasing(0.22f, 0.25f, 0.00f, 1.00f)

    val press: TweenSpec<Float> = tween(durationMillis = 105, easing = oneUiEasing)
    // IumrahPrimaryButtonStyle / IumrahSecondaryButtonStyle on iOS use
    // .easeOut(duration: 0.16), not the 105ms Android general-purpose press.
    val iosButtonEaseOut = CubicBezierEasing(0.0f, 0.0f, 0.58f, 1.0f)
    val iosButtonPress: TweenSpec<Float> = tween(durationMillis = 160, easing = iosButtonEaseOut)
    val iosButtonColor: TweenSpec<Color> = tween(durationMillis = 160, easing = iosButtonEaseOut)
    val tab: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 520f)
    val sidebar: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 330f)
    val softReveal: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 150f)
    val cinematic: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 120f)
    val cardFlip: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 105f)
    val selection: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 620f)
    val selectionDp: SpringSpec<Dp> = spring(dampingRatio = 0.88f, stiffness = 620f)
    val selectionColor: SpringSpec<Color> = spring(dampingRatio = 0.88f, stiffness = 620f)
    val content: SpringSpec<Float> = spring(dampingRatio = 0.92f, stiffness = 260f)

    val micro = tween<Float>(durationMillis = 130, easing = oneUiEasing)
    val fastFade = tween<Float>(durationMillis = 180, easing = oneUiEasing)
    val fastColor: TweenSpec<Color> = tween(durationMillis = 130, easing = oneUiEasing)
    val rootFade = tween<Float>(durationMillis = 250, easing = oneUiEasing)
    val vapor = tween<Float>(durationMillis = 620, easing = oneUiEasing)
    val vaporDp = tween<Dp>(durationMillis = 620, easing = oneUiEasing)
    val pageSnap = tween<Float>(durationMillis = 240, easing = oneUiEasing)
}

/** Kept for isolated parity code that still asks for an iOS-response spring. */
fun iosSpring(responseSeconds: Float, dampingFraction: Float): SpringSpec<Float> {
    val omega = (2.0 * Math.PI / responseSeconds.coerceAtLeast(0.08f)).toFloat()
    val stiffness = (omega * omega).coerceIn(Spring.StiffnessVeryLow, 1200f)
    return spring(dampingRatio = dampingFraction.coerceIn(0.01f, 1f), stiffness = stiffness)
}
