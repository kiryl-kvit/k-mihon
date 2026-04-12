package tachiyomi.domain.video.model

import tachiyomi.domain.manga.model.MangaCover
import java.util.Date

data class VideoHistoryWithRelations(
    val id: Long,
    val episodeId: Long,
    val videoId: Long,
    val title: String,
    val episodeName: String,
    val watchedAt: Date?,
    val watchedDuration: Long,
    val coverData: MangaCover,
)
