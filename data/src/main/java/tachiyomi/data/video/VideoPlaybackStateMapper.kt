package tachiyomi.data.video

import tachiyomi.domain.video.model.VideoPlaybackState

object VideoPlaybackStateMapper {
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
    ): VideoPlaybackState = VideoPlaybackState(
        episodeId = episodeId,
        positionMs = positionMs,
        durationMs = durationMs,
        completed = completed,
        lastWatchedAt = lastWatchedAt,
    )
}
