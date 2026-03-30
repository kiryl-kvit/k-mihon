package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaMerge
import tachiyomi.domain.manga.repository.MergedMangaRepository

class GetMergedManga(
    private val repository: MergedMangaRepository,
) {

    suspend fun awaitAll(): List<MangaMerge> {
        return repository.getAll()
    }

    fun subscribeAll(): Flow<List<MangaMerge>> {
        return repository.subscribeAll()
    }

    suspend fun awaitGroupByMangaId(mangaId: Long): List<MangaMerge> {
        return repository.getGroupByMangaId(mangaId)
    }

    fun subscribeGroupByMangaId(mangaId: Long): Flow<List<MangaMerge>> {
        return repository.subscribeGroupByMangaId(mangaId)
    }

    suspend fun awaitGroupByTargetId(targetMangaId: Long): List<MangaMerge> {
        return repository.getGroupByTargetId(targetMangaId)
    }

    suspend fun awaitTargetId(mangaId: Long): Long? {
        return repository.getTargetId(mangaId)
    }

    fun subscribeTargetId(mangaId: Long): Flow<Long?> {
        return repository.subscribeTargetId(mangaId)
    }

    suspend fun awaitVisibleTargetId(mangaId: Long): Long {
        return repository.getTargetId(mangaId) ?: mangaId
    }
}
