package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeEpisodeUpdate

interface AnimeEpisodeRepository {

    suspend fun addAll(episodes: List<AnimeEpisode>): List<AnimeEpisode>

    suspend fun update(episodeUpdate: AnimeEpisodeUpdate)

    suspend fun updateAll(episodeUpdates: List<AnimeEpisodeUpdate>)

    suspend fun removeEpisodesWithIds(episodeIds: List<Long>)

    suspend fun getEpisodesByAnimeId(animeId: Long): List<AnimeEpisode>

    fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeEpisode>>

    fun getEpisodesByAnimeIdsAsFlow(animeIds: List<Long>): Flow<List<AnimeEpisode>>

    suspend fun getEpisodeById(id: Long): AnimeEpisode?

    suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): AnimeEpisode?
}
