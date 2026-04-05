package tachiyomi.domain.history.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.history.repository.HistoryRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.HiddenSourceIds

class GetNextChaptersTest {

    private val getManga = mockk<GetManga>()
    private val getMangaWithChapters = mockk<GetMangaWithChapters>()
    private val historyRepository = mockk<HistoryRepository>()
    private val hiddenSourceIds = mockk<HiddenSourceIds>()

    private val getNextChapters = GetNextChapters(
        getManga = getManga,
        getMangaWithChapters = getMangaWithChapters,
        historyRepository = historyRepository,
        hiddenSourceIds = hiddenSourceIds,
    )

    @Test
    fun `await sorts chapters into reading order`() = runTest {
        val mangaId = 1L
        val manga = Manga.create().copy(id = mangaId)
        val chapters = listOf(
            chapter(id = 105, mangaId = mangaId, sourceOrder = 0, read = false),
            chapter(id = 104, mangaId = mangaId, sourceOrder = 1, read = false),
            chapter(id = 103, mangaId = mangaId, sourceOrder = 2, read = true),
        )

        coEvery { getManga.await(mangaId) } returns manga
        coEvery { getMangaWithChapters.awaitChapters(mangaId, true) } returns chapters

        getNextChapters.await(mangaId, onlyUnread = false).map(Chapter::id) shouldBe listOf(103L, 104L, 105L)
    }

    @Test
    fun `await from chapter returns following chapters in reading order`() = runTest {
        val mangaId = 1L
        val manga = Manga.create().copy(id = mangaId)
        val chapters = listOf(
            chapter(id = 105, mangaId = mangaId, sourceOrder = 0, read = false),
            chapter(id = 104, mangaId = mangaId, sourceOrder = 1, read = false),
            chapter(id = 103, mangaId = mangaId, sourceOrder = 2, read = true),
        )

        coEvery { getManga.await(mangaId) } returns manga
        coEvery { getMangaWithChapters.awaitChapters(mangaId, true) } returns chapters

        getNextChapters.await(mangaId, fromChapterId = 104L, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(104L, 105L)
    }

    @Test
    fun `await from chapter preserves merged member order`() = runTest {
        val mangaId = 1L
        val mergedMemberId = 2L
        val manga = Manga.create().copy(
            id = mangaId,
            chapterFlags = Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SORTING_SOURCE,
        )
        val chapters = listOf(
            chapter(id = 101, mangaId = mangaId, sourceOrder = 0, read = true),
            chapter(id = 201, mangaId = mergedMemberId, sourceOrder = 1, read = false),
        )

        coEvery { getManga.await(mangaId) } returns manga
        coEvery { getMangaWithChapters.awaitChapters(mangaId, true) } returns chapters

        getNextChapters.await(mangaId, fromChapterId = 101L, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(201L)
    }

    @Test
    fun `await from chapter respects sort aware merged reading order`() = runTest {
        val mangaId = 1L
        val mergedMemberId = 2L
        val manga = Manga.create().copy(
            id = mangaId,
            chapterFlags = Manga.CHAPTER_SORT_DESC or Manga.CHAPTER_SORTING_NUMBER,
        )
        val chapters = listOf(
            chapter(id = 101, mangaId = mangaId, sourceOrder = 0, chapterNumber = 1.0, read = false),
            chapter(id = 203, mangaId = mergedMemberId, sourceOrder = 0, chapterNumber = 3.0, read = true),
            chapter(id = 202, mangaId = mergedMemberId, sourceOrder = 1, chapterNumber = 2.0, read = false),
            chapter(id = 201, mangaId = mergedMemberId, sourceOrder = 2, chapterNumber = 1.0, read = false),
        )

        coEvery { getManga.await(mangaId) } returns manga
        coEvery { getMangaWithChapters.awaitChapters(mangaId, true) } returns chapters

        getNextChapters.await(mangaId, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(201L, 202L, 203L, 101L)

        getNextChapters.await(mangaId, fromChapterId = 203L, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(101L)
    }

    @Test
    fun `await from chapter goes from prologue to next merged chapter`() = runTest {
        val mangaId = 1L
        val mergedMemberId = 2L
        val manga = Manga.create().copy(
            id = mangaId,
            chapterFlags = Manga.CHAPTER_SORT_DESC or Manga.CHAPTER_SORTING_SOURCE,
        )
        val chapters = listOf(
            chapter(id = 101, mangaId = mangaId, sourceOrder = 2, chapterNumber = 1.0, read = false),
            chapter(id = 100, mangaId = mergedMemberId, sourceOrder = 5, chapterNumber = 0.0, read = true),
            chapter(id = 201, mangaId = mergedMemberId, sourceOrder = 4, chapterNumber = 1.0, read = false),
            chapter(id = 202, mangaId = mergedMemberId, sourceOrder = 3, chapterNumber = 2.0, read = false),
        )

        coEvery { getManga.await(mangaId) } returns manga
        coEvery { getMangaWithChapters.awaitChapters(mangaId, true) } returns chapters

        getNextChapters.await(mangaId, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(100L, 201L, 202L, 101L)

        getNextChapters.await(mangaId, fromChapterId = 100L, onlyUnread = false).map(Chapter::id) shouldBe
            listOf(201L, 202L, 101L)
    }

    private fun chapter(
        id: Long,
        mangaId: Long,
        sourceOrder: Long,
        chapterNumber: Double = -1.0,
        read: Boolean,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = mangaId,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
            read = read,
            name = "Chapter $id",
            url = "/chapter/$id",
        )
    }
}
