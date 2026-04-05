package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadCache
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun Chapter.isDownloaded(
    manga: Manga,
    downloadCache: DownloadCache = Injekt.get(),
): Boolean {
    return manga.isLocal() || downloadCache.isChapterDownloaded(name, scanlator, url, manga.title, manga.source, false)
}

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloaded(mangaById: Map<Long, Manga>): List<Chapter> {
    return filter { chapter ->
        val chapterManga = mangaById[chapter.mangaId] ?: return@filter false
        chapter.isDownloaded(chapterManga)
    }
}
