package tachiyomi.domain.video.model

data class VideoTitleUpdate(
    val id: Long,
    val source: Long? = null,
    val url: String? = null,
    val title: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val thumbnailUrl: String? = null,
    val favorite: Boolean? = null,
    val initialized: Boolean? = null,
    val lastUpdate: Long? = null,
    val nextUpdate: Long? = null,
    val dateAdded: Long? = null,
    val version: Long? = null,
    val notes: String? = null,
)
