package tachiyomi.domain.video.model

import androidx.compose.runtime.Immutable
import java.io.Serializable

@Immutable
data class VideoTitle(
    val id: Long,
    val source: Long,
    val favorite: Boolean,
    val lastUpdate: Long,
    val nextUpdate: Long,
    val dateAdded: Long,
    val url: String,
    val title: String,
    val displayName: String?,
    val description: String?,
    val genre: List<String>?,
    val thumbnailUrl: String?,
    val initialized: Boolean,
    val lastModifiedAt: Long,
    val favoriteModifiedAt: Long?,
    val version: Long,
    val notes: String,
) : Serializable {

    val displayTitle: String
        get() = displayName?.takeIf { it.isNotBlank() } ?: title

    companion object {
        fun create() = VideoTitle(
            id = -1L,
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            dateAdded = 0L,
            url = "",
            title = "",
            displayName = null,
            description = null,
            genre = null,
            thumbnailUrl = null,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
            notes = "",
        )
    }
}
