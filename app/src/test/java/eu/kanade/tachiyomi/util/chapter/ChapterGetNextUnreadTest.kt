package eu.kanade.tachiyomi.util.chapter

import android.content.Context
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.ChapterList
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

class ChapterGetNextUnreadTest {

    @BeforeEach
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        every { context.getString(any()) } returns ""
        val preferenceStore = InMemoryPreferenceStore()
        Injekt.addSingleton(fullType<BasePreferences>(), BasePreferences(context, preferenceStore))
        Injekt.addSingleton(fullType<LibraryPreferences>(), LibraryPreferences(preferenceStore))
    }

    @Test
    fun `returns oldest unread chapter for descending sorted chapter items`() {
        val manga = Manga.create().copy(id = 1L)
        val source = mockk<Source>()
        val downloadManager = mockk<DownloadManager>(relaxed = true)
        val chapters = listOf(
            chapterItem(id = 105, manga = manga, source = source, sourceOrder = 0, read = false),
            chapterItem(id = 104, manga = manga, source = source, sourceOrder = 1, read = false),
            chapterItem(id = 103, manga = manga, source = source, sourceOrder = 2, read = true),
        )

        chapters.getNextUnread(manga, downloadManager)?.id shouldBe 104L
    }

    @Test
    fun `respects downloaded only filter for merged chapter items`() {
        val primaryManga = Manga.create().copy(
            id = 1L,
            chapterFlags = Manga.CHAPTER_SORT_DESC or Manga.CHAPTER_SORTING_SOURCE or Manga.CHAPTER_SHOW_DOWNLOADED,
        )
        val mergedManga = Manga.create().copy(id = 2L, title = "Bottom")
        val source = mockk<Source>()
        val downloadManager = mockk<DownloadManager>()
        val chapters = listOf(
            chapterItem(id = 101, manga = primaryManga, source = source, sourceOrder = 0, read = false),
            chapterItem(id = 201, manga = mergedManga, source = source, sourceOrder = 1, read = false),
        )

        every {
            downloadManager.isChapterDownloaded(any(), any(), "/chapter/101", primaryManga.title, primaryManga.source)
        } returns false
        every {
            downloadManager.isChapterDownloaded(any(), any(), "/chapter/201", mergedManga.title, mergedManga.source)
        } returns true

        chapters.getNextUnread(primaryManga, downloadManager)?.id shouldBe 201L
    }

    @Test
    fun `respects descending merged group traversal for chapter items`() {
        val primaryManga = Manga.create().copy(
            id = 1L,
            chapterFlags = Manga.CHAPTER_SORT_DESC or Manga.CHAPTER_SORTING_SOURCE,
        )
        val mergedManga = Manga.create().copy(id = 2L, title = "Bottom")
        val source = mockk<Source>()
        val downloadManager = mockk<DownloadManager>(relaxed = true)
        val chapters = listOf(
            chapterItem(id = 101, manga = primaryManga, source = source, sourceOrder = 0, read = false),
            chapterItem(id = 201, manga = mergedManga, source = source, sourceOrder = 1, read = false),
        )

        chapters.getNextUnread(primaryManga, downloadManager)?.id shouldBe 201L
    }

    private fun chapterItem(
        id: Long,
        manga: Manga,
        source: Source,
        sourceOrder: Long,
        read: Boolean,
    ): ChapterList.Item {
        return ChapterList.Item(
            chapter = Chapter.create().copy(
                id = id,
                mangaId = manga.id,
                sourceOrder = sourceOrder,
                read = read,
                name = "Chapter $id",
                url = "/chapter/$id",
            ),
            manga = manga,
            source = source,
            downloadState = Download.State.NOT_DOWNLOADED,
            downloadProgress = 0,
        )
    }
}
