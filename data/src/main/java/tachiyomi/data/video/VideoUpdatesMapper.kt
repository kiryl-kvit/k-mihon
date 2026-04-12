package tachiyomi.data.video

import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.video.model.VideoUpdatesWithRelations

object VideoUpdatesMapper {
    fun mapUpdatesWithRelations(
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        videoId: Long,
        videoTitle: String,
        episodeId: Long,
        episodeName: String,
        episodeUrl: String,
        watched: Boolean,
        completed: Boolean,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
        dateFetch: Long,
    ): VideoUpdatesWithRelations = VideoUpdatesWithRelations(
        videoId = videoId,
        videoTitle = videoTitle,
        episodeId = episodeId,
        episodeName = episodeName,
        episodeUrl = episodeUrl,
        watched = watched,
        completed = completed,
        sourceId = sourceId,
        dateFetch = dateFetch,
        coverData = MangaCover(
            mangaId = videoId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
