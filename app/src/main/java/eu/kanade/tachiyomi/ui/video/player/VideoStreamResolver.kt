package eu.kanade.tachiyomi.ui.video.player

import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection

interface VideoStreamResolver {
    suspend operator fun invoke(
        animeId: Long,
        episodeId: Long,
        selection: VideoPlaybackSelection? = null,
    ): ResolveVideoStream.Result
}
