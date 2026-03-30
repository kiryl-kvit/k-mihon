package tachiyomi.domain.library.model

import tachiyomi.domain.manga.model.Manga

data class LibraryManga(
    val manga: Manga,
    val categories: List<Long>,
    val totalChapters: Long,
    val readCount: Long,
    val bookmarkCount: Long,
    val latestUpload: Long,
    val chapterFetchedAt: Long,
    val lastRead: Long,
    val memberMangaIds: List<Long> = listOf(manga.id),
    val memberMangas: List<Manga> = listOf(manga),
    val displaySourceId: Long = manga.source,
    val sourceIds: Set<Long> = setOf(manga.source),
) {
    val id: Long = manga.id

    val isMerged: Boolean = memberMangaIds.size > 1

    fun containsSource(sourceId: Long): Boolean = sourceId in sourceIds

    val unreadCount
        get() = totalChapters - readCount

    val hasBookmarks
        get() = bookmarkCount > 0

    val hasStarted = readCount > 0

    companion object {
        const val MULTI_SOURCE_ID = Long.MIN_VALUE
    }
}
