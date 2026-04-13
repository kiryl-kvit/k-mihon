package tachiyomi.data.anime

import tachiyomi.data.Animes
import tachiyomi.data.StringListColumnAdapter
import tachiyomi.domain.anime.model.AnimeTitle

object AnimeMapper {
    fun mapAnime(anime: Animes): AnimeTitle {
        return mapAnime(
            id = anime._id,
            profileId = anime.profile_id,
            source = anime.source,
            url = anime.url,
            title = anime.title,
            displayName = anime.display_name,
            description = anime.description,
            genre = anime.genre,
            thumbnailUrl = anime.thumbnail_url,
            favorite = anime.favorite,
            initialized = anime.initialized,
            lastUpdate = anime.last_update,
            nextUpdate = anime.next_update,
            dateAdded = anime.date_added,
            lastModifiedAt = anime.last_modified_at,
            favoriteModifiedAt = anime.favorite_modified_at,
            version = anime.version,
            notes = anime.notes,
        )
    }

    fun mapAnime(
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
    ): AnimeTitle = AnimeTitle(
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
