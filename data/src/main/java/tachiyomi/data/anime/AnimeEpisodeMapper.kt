package tachiyomi.data.anime

import tachiyomi.domain.anime.model.AnimeEpisode

object AnimeEpisodeMapper {
    fun mapEpisode(
        id: Long,
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        animeId: Long,
        url: String,
        name: String,
        watched: Boolean,
        completed: Boolean,
        episodeNumber: Double,
        sourceOrder: Long,
        dateFetch: Long,
        dateUpload: Long,
        lastModifiedAt: Long,
        version: Long,
    ): AnimeEpisode = AnimeEpisode(
        id = id,
        animeId = animeId,
        watched = watched,
        completed = completed,
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        url = url,
        name = name,
        dateUpload = dateUpload,
        episodeNumber = episodeNumber,
        lastModifiedAt = lastModifiedAt,
        version = version,
    )
}
