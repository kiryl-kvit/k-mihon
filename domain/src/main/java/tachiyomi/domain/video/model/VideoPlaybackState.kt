package tachiyomi.domain.video.model

data class VideoPlaybackState(
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastWatchedAt: Long,
)
