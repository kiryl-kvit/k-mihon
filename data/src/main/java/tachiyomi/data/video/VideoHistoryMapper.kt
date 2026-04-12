package tachiyomi.data.video

import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.video.model.VideoHistory
import tachiyomi.domain.video.model.VideoHistoryWithRelations
import java.util.Date

object VideoHistoryMapper {
    fun mapHistory(
        id: Long,
        episodeId: Long,
        watchedAt: Date?,
        watchedDuration: Long,
    ): VideoHistory = VideoHistory(
        id = id,
        episodeId = episodeId,
        watchedAt = watchedAt,
        watchedDuration = watchedDuration,
    )

    fun mapHistoryWithRelations(
        id: Long,
        episodeId: Long,
        videoId: Long,
        title: String,
        episodeName: String,
        watchedAt: Date?,
        watchedDuration: Long,
        sourceId: Long,
        favorite: Boolean,
        thumbnailUrl: String?,
        coverLastModified: Long,
    ): VideoHistoryWithRelations = VideoHistoryWithRelations(
        id = id,
        episodeId = episodeId,
        videoId = videoId,
        title = title,
        episodeName = episodeName,
        watchedAt = watchedAt,
        watchedDuration = watchedDuration,
        coverData = MangaCover(
            mangaId = videoId,
            sourceId = sourceId,
            isMangaFavorite = favorite,
            url = thumbnailUrl,
            lastModified = coverLastModified,
        ),
    )
}
