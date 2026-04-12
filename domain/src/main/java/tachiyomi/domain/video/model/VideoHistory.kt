package tachiyomi.domain.video.model

import java.util.Date

data class VideoHistory(
    val id: Long,
    val episodeId: Long,
    val watchedAt: Date?,
    val watchedDuration: Long,
) {
    companion object {
        fun create() = VideoHistory(
            id = -1L,
            episodeId = -1L,
            watchedAt = null,
            watchedDuration = -1L,
        )
    }
}
