package com.iumrah.beta.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iumrah.beta.R

/** First cold launch only. The iOS 17 splash has no animated sphere, timed
 * sequence or world map. The complete app tree is mounted immediately below
 * this one-frame static wordmark, preserving deep links and quick actions.
 * The world map animation elsewhere in the app is intentionally untouched.
 */
private object LaunchGate {
    private var shown = false
    @Synchronized fun claim(): Boolean = if (shown) false else { shown = true; true }
}

@Composable
internal fun IumrahSignatureLaunchHost(
    onboarded: Boolean,
    skip: Boolean,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val context = LocalContext.current
    var visible by remember { mutableStateOf(LaunchGate.claim() && !skip) }
    val window = (context as? android.app.Activity)?.window

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { visible = false }
    DisposableEffect(visible, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val old = controller?.isAppearanceLightStatusBars
        if (visible) controller?.isAppearanceLightStatusBars = false
        onDispose { if (old != null) controller?.isAppearanceLightStatusBars = old }
    }
    LaunchedEffect(visible, skip) {
        if (skip) { visible = false; return@LaunchedEffect }
        if (!visible) return@LaunchedEffect
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // One actual rendered frame, not an artificial multi-beat timer.
        withFrameNanos { }
        visible = false
    }
    Box(Modifier.fillMaxSize()) {
        content()
        if (visible) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.iumrah_header_wordmark_light),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(.62f).widthIn(max = 248.dp),
                    contentScale = ContentScale.Fit,
                )
                // Prevent tapping a CTA below the handoff artwork.
                Box(Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown().consume() }
                })
            }
        }
    }
}
