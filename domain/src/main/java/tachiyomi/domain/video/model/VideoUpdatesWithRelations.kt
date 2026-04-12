package tachiyomi.domain.video.model

import tachiyomi.domain.manga.model.MangaCover

data class VideoUpdatesWithRelations(
    val videoId: Long,
    val videoTitle: String,
    val episodeId: Long,
    val episodeName: String,
    val episodeUrl: String,
    val watched: Boolean,
    val completed: Boolean,
    val sourceId: Long,
    val dateFetch: Long,
    val coverData: MangaCover,
)
