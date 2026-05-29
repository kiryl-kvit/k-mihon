package eu.kanade.presentation.browse.components

import android.net.Uri
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.SAnimeHoverPreview
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@OptIn(UnstableApi::class)
@Composable
fun InlineAnimeHoverPreview(
    preview: SAnimeHoverPreview,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val networkHelper = remember { Injekt.get<NetworkHelper>() }
    var isReady by remember(preview.videoUrl, preview.headers) { mutableStateOf(false) }
    val player = remember(preview.videoUrl, preview.headers) {
        val userAgent = preview.headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
            ?: networkHelper.defaultUserAgentProvider()
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(networkHelper.client)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(preview.headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
        ExoPlayer.Builder(
            context,
            DefaultMediaSourceFactory(dataSourceFactory),
        ).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            setMediaItem(MediaItem.fromUri(Uri.parse(preview.videoUrl)))
            prepare()
        }
    }

    LaunchedEffect(player) {
        player.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> isReady = true
                        Player.STATE_ENDED -> onEnded()
                        else -> Unit
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    onEnded()
                }
            },
        )
    }

    DisposableEffect(player) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(if (isReady) 1f else 0f)
            .clip(androidx.compose.material3.MaterialTheme.shapes.extraSmall)
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { androidContext ->
                PlayerView(androidContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
