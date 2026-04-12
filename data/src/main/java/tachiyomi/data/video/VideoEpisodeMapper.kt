package tachiyomi.data.video

import tachiyomi.domain.video.model.VideoEpisode

object VideoEpisodeMapper {
    fun mapEpisode(
        id: Long,
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        videoId: Long,
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
    ): VideoEpisode = VideoEpisode(
        id = id,
        videoId = videoId,
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
