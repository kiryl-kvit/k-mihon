package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.AnimeTitle

@Serializable
data class BackupAnime(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var description: String? = null,
    @ProtoNumber(5) var genre: List<String> = emptyList(),
    @ProtoNumber(6) var thumbnailUrl: String? = null,
    @ProtoNumber(7) var dateAdded: Long = 0,
    @ProtoNumber(8) var episodes: List<BackupAnimeEpisode> = emptyList(),
    @ProtoNumber(9) var categories: List<Long> = emptyList(),
    @ProtoNumber(10) var history: List<BackupAnimeHistory> = emptyList(),
    @ProtoNumber(11) var playbackStates: List<BackupAnimePlaybackState> = emptyList(),
    @ProtoNumber(12) var favorite: Boolean = true,
    @ProtoNumber(13) var initialized: Boolean = false,
    @ProtoNumber(14) var lastUpdate: Long = 0,
    @ProtoNumber(15) var nextUpdate: Long = 0,
    @ProtoNumber(16) var lastModifiedAt: Long = 0,
    @ProtoNumber(17) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(18) var version: Long = 0,
    @ProtoNumber(19) var notes: String = "",
    @ProtoNumber(20) var displayName: String? = null,
    @ProtoNumber(21) var playbackPreferences: BackupAnimePlaybackPreferences? = null,
) {
    fun getAnimeImpl(): AnimeTitle {
        return AnimeTitle.create().copy(
            url = this@BackupAnime.url,
            title = this@BackupAnime.title,
            displayName = this@BackupAnime.displayName,
            description = this@BackupAnime.description,
            genre = this@BackupAnime.genre,
            thumbnailUrl = this@BackupAnime.thumbnailUrl,
            favorite = this@BackupAnime.favorite,
            source = this@BackupAnime.source,
            dateAdded = this@BackupAnime.dateAdded,
            initialized = this@BackupAnime.initialized,
            lastUpdate = this@BackupAnime.lastUpdate,
            nextUpdate = this@BackupAnime.nextUpdate,
            lastModifiedAt = this@BackupAnime.lastModifiedAt,
            favoriteModifiedAt = this@BackupAnime.favoriteModifiedAt,
            version = this@BackupAnime.version,
            notes = this@BackupAnime.notes,
        )
    }
}
