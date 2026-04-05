package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.service.sortedForReading
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.applyFilter

/**
 * Gets next unread chapter with filters and sorting applied
 */
suspend fun List<Chapter>.getNextUnread(manga: Manga, downloadManager: DownloadManager): Chapter? {
    val unreadFilter = manga.unreadFilter
    val bookmarkedFilter = manga.bookmarkedFilter

    return filter { chapter -> applyFilter(unreadFilter) { !chapter.read } }
        .filter { chapter -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
        .sortedForReading(manga)
        .firstOrNull { !it.read }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(manga: Manga): Chapter? {
    return map(ChapterList.Item::chapter)
        .sortedForReading(manga)
        .firstOrNull { !it.read }
}
