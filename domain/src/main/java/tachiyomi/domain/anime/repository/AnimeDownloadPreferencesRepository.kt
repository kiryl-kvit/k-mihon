package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeDownloadPreferences

interface AnimeDownloadPreferencesRepository {

    suspend fun getByAnimeId(animeId: Long): AnimeDownloadPreferences?

    fun getByAnimeIdAsFlow(animeId: Long): Flow<AnimeDownloadPreferences?>

    suspend fun upsert(preferences: AnimeDownloadPreferences)
}
