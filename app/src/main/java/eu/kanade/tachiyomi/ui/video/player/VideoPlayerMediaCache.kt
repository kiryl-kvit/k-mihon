package eu.kanade.tachiyomi.ui.video.player

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(markerClass = [UnstableApi::class])
class VideoPlayerMediaCache(
    context: Application,
) {
    val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, CACHE_DIRECTORY_NAME),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    private companion object {
        private const val CACHE_DIRECTORY_NAME = "anime_player_media"
        private const val MAX_CACHE_BYTES = 256L * 1024L * 1024L
    }
}
