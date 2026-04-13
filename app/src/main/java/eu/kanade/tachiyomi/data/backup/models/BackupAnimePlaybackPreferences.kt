package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.anime.model.AnimePlaybackPreferences
import tachiyomi.domain.anime.model.PlayerQualityMode

@Serializable
data class BackupAnimePlaybackPreferences(
    @ProtoNumber(1) var dubKey: String? = null,
    @ProtoNumber(2) var streamKey: String? = null,
    @ProtoNumber(3) var sourceQualityKey: String? = null,
    @ProtoNumber(4) var playerQualityMode: String = "auto",
    @ProtoNumber(5) var playerQualityHeight: Int? = null,
    @ProtoNumber(6) var updatedAt: Long = 0,
) {
    fun toPlaybackPreferences(): AnimePlaybackPreferences {
        return AnimePlaybackPreferences(
            animeId = -1L,
            dubKey = dubKey,
            streamKey = streamKey,
            sourceQualityKey = sourceQualityKey,
            playerQualityMode = when (playerQualityMode) {
                "specific_height" -> PlayerQualityMode.SPECIFIC_HEIGHT
                else -> PlayerQualityMode.AUTO
            },
            playerQualityHeight = playerQualityHeight,
            updatedAt = updatedAt,
        )
    }
}
