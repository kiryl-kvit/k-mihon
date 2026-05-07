package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SAnimePreview

/**
 * Optional capability for anime sources that expose preview images for a title.
 */
interface AnimePreviewSource : AnimeSource {

    suspend fun getAnimePreview(anime: SAnime): List<SAnimePreview>
}
