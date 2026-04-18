package tachiyomi.domain.anime.model

import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

data class AnimeHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val animeId: Long,
    val title: String,
    val episodeName: String,
    val watchedAt: Date?,
    val watchedDuration: Long,
    val coverData: MangaCover,
)
