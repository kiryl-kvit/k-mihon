package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimePlaybackPreferences

interface AnimePlaybackPreferencesRepository {

    suspend fun getByAnimeId(animeId: Long): AnimePlaybackPreferences?

    fun getByAnimeIdAsFlow(animeId: Long): Flow<AnimePlaybackPreferences?>

    suspend fun upsert(preferences: AnimePlaybackPreferences)
}
