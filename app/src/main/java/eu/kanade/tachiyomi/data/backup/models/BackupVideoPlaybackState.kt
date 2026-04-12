package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.video.model.VideoPlaybackState

@Serializable
data class BackupVideoPlaybackState(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var positionMs: Long,
    @ProtoNumber(3) var durationMs: Long,
    @ProtoNumber(4) var completed: Boolean,
    @ProtoNumber(5) var lastWatchedAt: Long,
) {
    fun toPlaybackState(): VideoPlaybackState {
        return VideoPlaybackState(
            episodeId = -1L,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed,
            lastWatchedAt = lastWatchedAt,
        )
    }
}
