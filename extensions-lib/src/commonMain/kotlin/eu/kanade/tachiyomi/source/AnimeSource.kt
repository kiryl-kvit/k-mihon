package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection

/**
 * A basic interface for creating an anime source.
 */
interface AnimeSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Get the updated details for an anime title.
     *
     * @param anime the title to update.
     * @return the updated title.
     */
    suspend fun getAnimeDetails(anime: SAnime): SAnime

    /**
     * Get all the available episodes for an anime title.
     *
     * @param anime the title to update.
     * @return the episodes for the title.
     */
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode>

    /**
     * Resolve playback metadata and playable streams for an episode.
     *
     * Sources may expose dub and source quality choices. When a requested selection isn't
     * available for the episode, the source should return the resolved fallback selection in the
     * resulting [VideoPlaybackData].
     *
     * @param episode the episode.
     * @param selection the preferred playback selection.
     * @return playback metadata and playable streams for the episode.
     */
    suspend fun getPlaybackData(
        episode: SEpisode,
        selection: VideoPlaybackSelection = VideoPlaybackSelection(),
    ): VideoPlaybackData
}
