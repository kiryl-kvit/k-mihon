package tachiyomi.domain.anime.model

data class AnimeEpisode(
    val id: Long,
    val animeId: Long,
    val watched: Boolean,
    val completed: Boolean,
    val dateFetch: Long,
    val sourceOrder: Long,
    val url: String,
    val name: String,
    val dateUpload: Long,
    val episodeNumber: Double,
    val lastModifiedAt: Long,
    val version: Long,
) {
    companion object {
        fun create() = AnimeEpisode(
            id = -1L,
            animeId = -1L,
            watched = false,
            completed = false,
            dateFetch = 0L,
            sourceOrder = 0L,
            url = "",
            name = "",
            dateUpload = -1L,
            episodeNumber = -1.0,
            lastModifiedAt = 0L,
            version = 1L,
        )
    }
}
