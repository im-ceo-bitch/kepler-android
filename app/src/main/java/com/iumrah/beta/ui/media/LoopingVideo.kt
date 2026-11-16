package com.iumrah.beta.ui.media

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Native Media3 player used for the cinematic cards. Resources are resolved by
 * name so the UI remains buildable while large source-of-truth media is shipped
 * as separate ZIP patches on GitHub mobile.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopingRawVideo(
    resourceName: String,
    modifier: Modifier = Modifier,
    play: Boolean = true,
    muted: Boolean = true,
    fallback: @Composable () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resourceId = remember(resourceName) {
        context.resources.getIdentifier(resourceName, "raw", context.packageName)
    }
    if (resourceId == 0) {
        Box(modifier.background(Color.Black)) { fallback() }
        return
    }

    var ready by remember(resourceId) { mutableStateOf(false) }
    val player = remember(resourceId) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = if (muted) 0f else 1f
            setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(resourceId)))
            prepare()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val requestedPlay by rememberUpdatedState(play)
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                ready = playbackState == Player.STATE_READY
            }
        }
        val observer = LifecycleEventObserver { _, _ ->
            player.playWhenReady = requestedPlay && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        player.addListener(listener)
        ready = player.playbackState == Player.STATE_READY
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(play, muted, player) {
        player.volume = if (muted) 0f else 1f
        player.playWhenReady = play && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    Box(modifier) {
        if (!ready) fallback()
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    useController = false
                    isClickable = false
                    isFocusable = false
                    setOnTouchListener { _, _ -> false }
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                }
            },
            update = { it.player = player },
        )
    }
}
