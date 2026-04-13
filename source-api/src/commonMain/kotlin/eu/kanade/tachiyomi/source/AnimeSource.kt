package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.VideoStream

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
     * Get the playable streams for an episode.
     * Streams should be returned in the preferred selection order.
     *
     * @param episode the episode.
     * @return the playable streams for the episode.
     */
    suspend fun getStreamList(episode: SEpisode): List<VideoStream>
}
