package tachiyomi.domain.anime.model

import java.util.Date

data class AnimeHistoryUpdate(
    val episodeId: Long,
    val watchedAt: Date,
    val sessionWatchedDuration: Long,
)
