package tachiyomi.data.anime

import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.model.AnimeDownloadQualityMode

object AnimeDownloadPreferencesMapper {
    fun mapPreferences(
        @Suppress("UNUSED_PARAMETER")
        id: Long,
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        animeId: Long,
        dubKey: String?,
        streamKey: String?,
        subtitleKey: String?,
        qualityMode: String,
        updatedAt: Long,
    ): AnimeDownloadPreferences {
        return AnimeDownloadPreferences(
            animeId = animeId,
            dubKey = dubKey,
            streamKey = streamKey,
            subtitleKey = subtitleKey,
            qualityMode = qualityMode.fromDatabaseValue(),
            updatedAt = updatedAt,
        )
    }

    fun encodeQualityMode(mode: AnimeDownloadQualityMode): String {
        return when (mode) {
            AnimeDownloadQualityMode.BEST -> "best"
            AnimeDownloadQualityMode.BALANCED -> "balanced"
            AnimeDownloadQualityMode.DATA_SAVING -> "data_saving"
        }
    }

    private fun String.fromDatabaseValue(): AnimeDownloadQualityMode {
        return when (this) {
            "best" -> AnimeDownloadQualityMode.BEST
            "data_saving" -> AnimeDownloadQualityMode.DATA_SAVING
            else -> AnimeDownloadQualityMode.BALANCED
        }
    }
}
