package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SAnimeHoverPreview

/**
 * Optional capability for anime sources that expose a short inline hover preview clip.
 */
interface AnimeHoverPreviewSource : AnimeSource {

    suspend fun getAnimeHoverPreview(anime: SAnime): SAnimeHoverPreview?
}
