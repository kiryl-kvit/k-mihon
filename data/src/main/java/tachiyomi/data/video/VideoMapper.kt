package tachiyomi.data.video

import tachiyomi.data.Videos
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.video.model.VideoTitle

object VideoMapper {
    fun mapVideo(video: Videos): VideoTitle {
        return mapVideo(
            id = video._id,
            profileId = video.profile_id,
            source = video.source,
            url = video.url,
            title = video.title,
            displayName = video.display_name,
            description = video.description,
            genre = video.genre,
            thumbnailUrl = video.thumbnail_url,
            favorite = video.favorite,
            initialized = video.initialized,
            lastUpdate = video.last_update,
            nextUpdate = video.next_update,
            dateAdded = video.date_added,
            lastModifiedAt = video.last_modified_at,
            favoriteModifiedAt = video.favorite_modified_at,
            version = video.version,
            notes = video.notes,
        )
    }

    fun mapVideo(
        id: Long,
        @Suppress("UNUSED_PARAMETER")
        profileId: Long,
        source: Long,
        url: String,
        title: String,
        displayName: String?,
        description: String?,
        genre: List<String>?,
        thumbnailUrl: String?,
        favorite: Boolean,
        initialized: Boolean,
        lastUpdate: Long?,
        nextUpdate: Long?,
        dateAdded: Long,
        lastModifiedAt: Long,
        favoriteModifiedAt: Long?,
        version: Long,
        notes: String,
    ): VideoTitle = VideoTitle(
        id = id,
        source = source,
        favorite = favorite,
        lastUpdate = lastUpdate ?: 0L,
        nextUpdate = nextUpdate ?: 0L,
        dateAdded = dateAdded,
        url = url,
        title = title,
        displayName = displayName,
        description = description,
        genre = genre,
        thumbnailUrl = thumbnailUrl,
        initialized = initialized,
        lastModifiedAt = lastModifiedAt,
        favoriteModifiedAt = favoriteModifiedAt,
        version = version,
        notes = notes,
    )

    fun encodeGenre(genre: List<String>?): String? {
        return genre?.let(StringListColumnAdapter::encode)
    }
}
