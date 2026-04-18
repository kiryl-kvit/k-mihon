package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.AnimeEpisode

@Serializable
data class BackupAnimeEpisode(
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
    fun toEpisodeImpl(): AnimeEpisode {
        return AnimeEpisode.create().copy(
            url = this@BackupAnimeEpisode.url,
            name = this@BackupAnimeEpisode.name,
            watched = this@BackupAnimeEpisode.watched,
            completed = this@BackupAnimeEpisode.completed,
            dateFetch = this@BackupAnimeEpisode.dateFetch,
            dateUpload = this@BackupAnimeEpisode.dateUpload,
            episodeNumber = this@BackupAnimeEpisode.episodeNumber.toDouble(),
            sourceOrder = this@BackupAnimeEpisode.sourceOrder,
            lastModifiedAt = this@BackupAnimeEpisode.lastModifiedAt,
            version = this@BackupAnimeEpisode.version,
        )
    }
}
