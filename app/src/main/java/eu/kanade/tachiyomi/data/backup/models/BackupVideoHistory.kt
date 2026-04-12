package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.video.model.VideoHistory
import java.util.Date

@Serializable
data class BackupVideoHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastWatched: Long,
    @ProtoNumber(3) var watchedDuration: Long = 0,
) {
    fun getHistoryImpl(): VideoHistory {
        return VideoHistory.create().copy(
            watchedAt = Date(lastWatched),
            watchedDuration = watchedDuration,
        )
    }
}
