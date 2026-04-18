package tachiyomi.data.anime

import tachiyomi.domain.anime.model.AnimePlaybackState

object AnimePlaybackStateMapper {
    fun mapState(
        @Suppress("UNUSED_PARAMETER")
        id: Long,
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        episodeId: Long,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean,
        lastWatchedAt: Long,
    ): AnimePlaybackState = AnimePlaybackState(
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
        lastWatchedAt = lastWatchedAt,
    )
}
