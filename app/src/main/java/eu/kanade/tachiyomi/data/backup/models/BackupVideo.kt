package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.video.model.VideoTitle

@Serializable
data class BackupVideo(
    @ProtoNumber(1) var source: Long,
    @ProtoNumber(2) var url: String,
    @ProtoNumber(3) var title: String = "",
    @ProtoNumber(4) var description: String? = null,
    @ProtoNumber(5) var genre: List<String> = emptyList(),
    @ProtoNumber(6) var thumbnailUrl: String? = null,
    @ProtoNumber(7) var dateAdded: Long = 0,
    @ProtoNumber(8) var episodes: List<BackupVideoEpisode> = emptyList(),
    @ProtoNumber(9) var categories: List<Long> = emptyList(),
    @ProtoNumber(10) var history: List<BackupVideoHistory> = emptyList(),
    @ProtoNumber(11) var playbackStates: List<BackupVideoPlaybackState> = emptyList(),
    @ProtoNumber(12) var favorite: Boolean = true,
    @ProtoNumber(13) var initialized: Boolean = false,
    @ProtoNumber(14) var lastUpdate: Long = 0,
    @ProtoNumber(15) var nextUpdate: Long = 0,
    @ProtoNumber(16) var lastModifiedAt: Long = 0,
    @ProtoNumber(17) var favoriteModifiedAt: Long? = null,
    @ProtoNumber(18) var version: Long = 0,
    @ProtoNumber(19) var notes: String = "",
    @ProtoNumber(20) var displayName: String? = null,
) {
    fun getVideoImpl(): VideoTitle {
        return VideoTitle.create().copy(
            url = this@BackupVideo.url,
            title = this@BackupVideo.title,
            displayName = this@BackupVideo.displayName,
            description = this@BackupVideo.description,
            genre = this@BackupVideo.genre,
            thumbnailUrl = this@BackupVideo.thumbnailUrl,
            favorite = this@BackupVideo.favorite,
            source = this@BackupVideo.source,
            dateAdded = this@BackupVideo.dateAdded,
            initialized = this@BackupVideo.initialized,
            lastUpdate = this@BackupVideo.lastUpdate,
            nextUpdate = this@BackupVideo.nextUpdate,
            lastModifiedAt = this@BackupVideo.lastModifiedAt,
            favoriteModifiedAt = this@BackupVideo.favoriteModifiedAt,
            version = this@BackupVideo.version,
            notes = this@BackupVideo.notes,
        )
    }
}
