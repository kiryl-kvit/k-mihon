package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.AnimeHistory
import java.util.Date

@Serializable
data class BackupAnimeHistory(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var lastWatched: Long,
    @ProtoNumber(3) var watchedDuration: Long = 0,
) {
    fun getHistoryImpl(): AnimeHistory {
        return AnimeHistory.create().copy(
            watchedAt = Date(lastWatched),
            watchedDuration = watchedDuration,
        )
    }
}
