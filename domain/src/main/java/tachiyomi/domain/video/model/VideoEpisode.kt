package tachiyomi.domain.video.model

data class VideoEpisode(
    val id: Long,
    val videoId: Long,
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
        fun create() = VideoEpisode(
            id = -1L,
            videoId = -1L,
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
