package tachiyomi.domain.anime.model

import tachiyomi.domain.manga.model.MangaCover

data class AnimeUpdatesWithRelations(
    val animeId: Long,
    val animeTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val episodeUrl: String,
    val watched: Boolean,
    val completed: Boolean,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
)
