package tachiyomi.domain.manga.interactor

import tachiyomi.domain.manga.repository.MergedMangaRepository

class UpdateMergedManga(
    private val repository: MergedMangaRepository,
) {

    suspend fun awaitMerge(targetMangaId: Long, orderedMangaIds: List<Long>) {
        repository.upsertGroup(targetMangaId, orderedMangaIds)
    }

    suspend fun awaitRemoveMembers(targetMangaId: Long, mangaIds: List<Long>) {
        repository.removeMembers(targetMangaId, mangaIds)
    }

    suspend fun awaitDeleteGroup(targetMangaId: Long) {
        repository.deleteGroup(targetMangaId)
    }
}
