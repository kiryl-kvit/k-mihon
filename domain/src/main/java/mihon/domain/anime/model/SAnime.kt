package mihon.domain.anime.model

import eu.kanade.tachiyomi.source.model.SAnime
import tachiyomi.domain.anime.model.AnimeTitle

fun SAnime.toDomainAnime(sourceId: Long): AnimeTitle {
    return AnimeTitle.create().copy(
        source = sourceId,
        url = url,
        title = title,
        description = description,
        genre = getGenres(),
        thumbnailUrl = thumbnail_url,
        initialized = initialized,
    )
}
