package tachiyomi.domain.anime.model

data class AnimeEpisodeUpdate(
    val id: Long,
    val animeId: Long? = null,
    val url: String? = null,
    val name: String? = null,
    val watched: Boolean? = null,
    val completed: Boolean? = null,
    val episodeNumber: Double? = null,
    val sourceOrder: Long? = null,
    val dateFetch: Long? = null,
    val dateUpload: Long? = null,
    val version: Long? = null,
)
