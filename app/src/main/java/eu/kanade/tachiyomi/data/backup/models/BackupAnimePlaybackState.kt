package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.AnimePlaybackState

@Serializable
data class BackupAnimePlaybackState(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var positionMs: Long,
    @ProtoNumber(3) var durationMs: Long,
    @ProtoNumber(4) var completed: Boolean,
    @ProtoNumber(5) var lastWatchedAt: Long,
) {
    fun toPlaybackState(): AnimePlaybackState {
        return AnimePlaybackState(
            episodeId = -1L,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed,
            lastWatchedAt = lastWatchedAt,
        )
    }
}
