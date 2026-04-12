package tachiyomi.domain.video.model

data class VideoEpisodeUpdate(
    val id: Long,
    val videoId: Long? = null,
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
