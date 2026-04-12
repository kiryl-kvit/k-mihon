package eu.kanade.tachiyomi.ui.video.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType

internal fun buildVideoPlayer(
    context: Context,
    networkHelper: NetworkHelper,
    stream: VideoStream,
): ExoPlayer {
    val requestHeaders = stream.request.headers
    val userAgent = requestHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        ?: networkHelper.defaultUserAgentProvider()

    val okHttpDataSourceFactory = OkHttpDataSource.Factory(networkHelper.client)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(requestHeaders)
    val dataSourceFactory = DefaultDataSource.Factory(context, okHttpDataSourceFactory)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

    return ExoPlayer.Builder(context, mediaSourceFactory)
        .build()
        .apply {
            setMediaItem(stream.toMediaItem())
            prepare()
            playWhenReady = true
        }
}

internal fun VideoStream.toMediaItem(): MediaItem {
    return MediaItem.Builder()
        .setUri(request.url)
        .setMimeType(mimeType ?: type.toMimeType())
        .build()
}

private fun VideoStreamType.toMimeType(): String? {
    return when (this) {
        VideoStreamType.HLS -> MimeTypes.APPLICATION_M3U8
        VideoStreamType.DASH -> MimeTypes.APPLICATION_MPD
        VideoStreamType.PROGRESSIVE -> MimeTypes.VIDEO_MP4
        VideoStreamType.UNKNOWN -> null
    }
}
