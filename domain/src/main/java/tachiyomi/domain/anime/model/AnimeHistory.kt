package tachiyomi.domain.anime.model

import java.util.Date

data class AnimeHistory(
    val id: Long,
    val episodeId: Long,
    val watchedAt: Date?,
    val watchedDuration: Long,
) {
    companion object {
        fun create() = AnimeHistory(
            id = -1L,
            episodeId = -1L,
            watchedAt = null,
            watchedDuration = -1L,
        )
    }
}
