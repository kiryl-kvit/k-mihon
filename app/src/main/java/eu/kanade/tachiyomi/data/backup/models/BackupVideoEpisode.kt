package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.video.model.VideoEpisode

@Serializable
data class BackupVideoEpisode(
    @ProtoNumber(1) var url: String,
    @ProtoNumber(2) var name: String,
    @ProtoNumber(3) var watched: Boolean = false,
    @ProtoNumber(4) var completed: Boolean = false,
    @ProtoNumber(5) var dateFetch: Long = 0,
    @ProtoNumber(6) var dateUpload: Long = 0,
    @ProtoNumber(7) var episodeNumber: Float = 0F,
    @ProtoNumber(8) var sourceOrder: Long = 0,
    @ProtoNumber(9) var lastModifiedAt: Long = 0,
    @ProtoNumber(10) var version: Long = 0,
) {
    fun toEpisodeImpl(): VideoEpisode {
        return VideoEpisode.create().copy(
            url = this@BackupVideoEpisode.url,
            name = this@BackupVideoEpisode.name,
            watched = this@BackupVideoEpisode.watched,
            completed = this@BackupVideoEpisode.completed,
            dateFetch = this@BackupVideoEpisode.dateFetch,
            dateUpload = this@BackupVideoEpisode.dateUpload,
            episodeNumber = this@BackupVideoEpisode.episodeNumber.toDouble(),
            sourceOrder = this@BackupVideoEpisode.sourceOrder,
            lastModifiedAt = this@BackupVideoEpisode.lastModifiedAt,
            version = this@BackupVideoEpisode.version,
        )
    }
}
