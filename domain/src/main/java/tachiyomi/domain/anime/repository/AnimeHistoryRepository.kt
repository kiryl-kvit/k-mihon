package tachiyomi.domain.anime.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations

interface AnimeHistoryRepository {

    fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>>

    suspend fun getLastHistory(): AnimeHistoryWithRelations?

    fun getLastHistoryAsFlow(): Flow<AnimeHistoryWithRelations?>

    suspend fun getTotalWatchedDuration(): Long

    suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory>

    suspend fun resetHistory(historyId: Long)

    suspend fun resetHistoryByAnimeId(animeId: Long)

    suspend fun deleteAllHistory(): Boolean

    suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate)
}
