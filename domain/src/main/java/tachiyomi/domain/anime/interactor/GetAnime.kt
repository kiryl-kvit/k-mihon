package tachiyomi.domain.anime.interactor

import kotlinx.coroutines.flow.Flow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.repository.AnimeRepository

class GetAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(id: Long): AnimeTitle? {
        return try {
            animeRepository.getAnimeById(id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            null
        }
    }

    suspend fun subscribe(id: Long): Flow<AnimeTitle> {
        return animeRepository.getAnimeByIdAsFlow(id)
    }

    fun subscribe(url: String, sourceId: Long): Flow<AnimeTitle?> {
        return animeRepository.getAnimeByUrlAndSourceIdAsFlow(url, sourceId)
    }
}
