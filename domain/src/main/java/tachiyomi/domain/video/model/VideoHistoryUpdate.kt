package tachiyomi.domain.video.model

import java.util.Date

data class VideoHistoryUpdate(
    val episodeId: Long,
    val watchedAt: Date,
    val sessionWatchedDuration: Long,
)
