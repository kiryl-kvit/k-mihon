package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimePlaybackState

interface AnimePlaybackStateRepository {

    suspend fun getByEpisodeId(episodeId: Long): AnimePlaybackState?

    fun getByEpisodeIdAsFlow(episodeId: Long): Flow<AnimePlaybackState?>

    fun getByAnimeIdAsFlow(animeId: Long): Flow<List<AnimePlaybackState>>

    suspend fun upsert(state: AnimePlaybackState)

    suspend fun upsertAndSyncEpisodeState(state: AnimePlaybackState)
}
