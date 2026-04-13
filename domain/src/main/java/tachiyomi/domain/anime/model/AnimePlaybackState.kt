package tachiyomi.domain.anime.model

data class AnimePlaybackState(
    val episodeId: Long,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val lastWatchedAt: Long,
)
