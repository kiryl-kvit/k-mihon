package tachiyomi.domain.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.manga.model.MangaMerge

interface MergedMangaRepository {

    suspend fun getAll(): List<MangaMerge>

    fun subscribeAll(): Flow<List<MangaMerge>>

    suspend fun getGroupByMangaId(mangaId: Long): List<MangaMerge>

    fun subscribeGroupByMangaId(mangaId: Long): Flow<List<MangaMerge>>

    suspend fun getGroupByTargetId(targetMangaId: Long): List<MangaMerge>

    suspend fun getTargetId(mangaId: Long): Long?

    fun subscribeTargetId(mangaId: Long): Flow<Long?>

    suspend fun upsertGroup(targetMangaId: Long, orderedMangaIds: List<Long>)

    suspend fun removeMembers(targetMangaId: Long, mangaIds: List<Long>)

    suspend fun deleteGroup(targetMangaId: Long)
}
