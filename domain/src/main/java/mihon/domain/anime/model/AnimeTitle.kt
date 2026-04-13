package mihon.domain.anime.model

import eu.kanade.tachiyomi.source.model.SAnime
import tachiyomi.domain.anime.model.AnimeTitle

fun AnimeTitle.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = title
    it.description = description
    it.genre = genre?.joinToString()
    it.thumbnail_url = thumbnailUrl
    it.initialized = initialized
}
