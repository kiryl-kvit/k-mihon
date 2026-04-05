package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.ui.manga.ChapterList
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

/**
 * Gets next unread chapter with filters and sorting applied
 */
suspend fun List<Chapter>.getNextUnread(
    manga: Manga,
    downloadManager: DownloadManager,
    mangaById: Map<Long, Manga> = associateBy { it.mangaId }.mapValues { manga },
): Chapter? {
    return sortedForResume(manga, mangaById, downloadManager)
        .firstOrNull { !it.read }
}

/**
 * Gets next unread chapter with filters and sorting applied
 */
fun List<ChapterList.Item>.getNextUnread(
    manga: Manga,
    downloadManager: DownloadManager,
): Chapter? {
    return map(ChapterList.Item::chapter)
        .sortedForResume(
            manga = manga,
            mangaById = associate { it.chapter.mangaId to it.manga },
            downloadManager = downloadManager,
        )
        .firstOrNull { !it.read }
}
