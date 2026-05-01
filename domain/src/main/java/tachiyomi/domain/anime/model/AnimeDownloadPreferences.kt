package tachiyomi.domain.anime.model

data class AnimeDownloadPreferences(
    val animeId: Long,
    val dubKey: String?,
    val streamKey: String?,
    val subtitleKey: String?,
    val qualityMode: AnimeDownloadQualityMode,
    val updatedAt: Long,
)

enum class AnimeDownloadQualityMode {
    BEST,
    BALANCED,
    DATA_SAVING,
}
