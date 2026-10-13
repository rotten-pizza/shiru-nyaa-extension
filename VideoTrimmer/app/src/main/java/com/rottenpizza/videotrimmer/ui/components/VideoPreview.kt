package com.rottenpizza.videotrimmer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Thin Compose wrapper around a Media3 [PlayerView] bound to [player]. */
@UnstableApi
@Composable
fun VideoPreview(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = false
                setShowNextButton(false)
                setShowPreviousButton(false)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        onRelease = { it.player = null },
    )
}
