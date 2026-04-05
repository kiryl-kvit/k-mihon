package tachiyomi.domain.history.interactor

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.sortedForReading
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.source.service.HiddenSourceIds

class GetNextChapters(
    private val getManga: GetManga,
    private val getMangaWithChapters: GetMangaWithChapters,
    private val historyRepository: HistoryRepository,
    private val hiddenSourceIds: HiddenSourceIds,
) {

    suspend fun await(onlyUnread: Boolean = true): List<Chapter> {
        val history = historyRepository.getLastHistory() ?: return emptyList()
        if (history.coverData.sourceId in hiddenSourceIds.get()) return emptyList()
        return await(history.mangaId, history.chapterId, onlyUnread)
    }

    suspend fun await(mangaId: Long, onlyUnread: Boolean = true): List<Chapter> {
        val manga = getManga.await(mangaId) ?: return emptyList()
        val chapters = getMangaWithChapters.awaitChapters(mangaId, applyScanlatorFilter = true)
            .sortedForReading(manga)

        return if (onlyUnread) {
            chapters.filterNot { it.read }
        } else {
            chapters
        }
    }

    suspend fun await(
        mangaId: Long,
        fromChapterId: Long,
        onlyUnread: Boolean = true,
    ): List<Chapter> {
        val allChapters = await(mangaId, onlyUnread = false)
        val currChapterIndex = allChapters.indexOfFirst { it.id == fromChapterId }
        if (currChapterIndex == -1) {
            return if (onlyUnread) allChapters.filterNot(Chapter::read) else allChapters
        }

        val currentOrFollowing = allChapters.drop(currChapterIndex)

        if (onlyUnread) {
            return currentOrFollowing.filterNot(Chapter::read)
        }

        // The "next chapter" is either:
        // - The current chapter if it isn't completely read
        // - The chapters after the current chapter if the current one is completely read
        val fromChapter = allChapters[currChapterIndex]
        return if (fromChapter != null && !fromChapter.read) {
            currentOrFollowing
        } else {
            currentOrFollowing.drop(1)
        }
    }
}
