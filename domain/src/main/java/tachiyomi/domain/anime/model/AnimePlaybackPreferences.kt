package tachiyomi.domain.anime.model

data class AnimePlaybackPreferences(
    val animeId: Long,
    val dubKey: String?,
    val streamKey: String?,
    val sourceQualityKey: String?,
    val playerQualityMode: PlayerQualityMode,
    val playerQualityHeight: Int?,
    val updatedAt: Long,
)

enum class PlayerQualityMode {
    AUTO,
    SPECIFIC_HEIGHT,
}
