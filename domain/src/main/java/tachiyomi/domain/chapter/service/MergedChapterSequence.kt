package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga

private fun List<Long>.orderedPresentIds(chapters: List<Chapter>): List<Long> {
    if (isEmpty()) return chapters.map(Chapter::mangaId).distinct()
    val presentIds = chapters.map(Chapter::mangaId).toSet()
    return asSequence()
        .filter { it in presentIds }
        .toList()
}

fun List<Chapter>.sortedForMergedDisplay(
    manga: Manga,
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Chapter> {
    if (mergedMangaIds.size <= 1) {
        return sortedWith(getChapterSort(manga))
    }

    val chapterSort = getChapterSort(manga)
    return mergedMangaIds.orderedPresentIds(this)
        .flatMap { mangaId ->
            asSequence()
                .filter { it.mangaId == mangaId }
                .sortedWith(chapterSort)
                .toList()
        }
}

fun List<Chapter>.sortedForReading(
    manga: Manga,
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Chapter> {
    if (mergedMangaIds.size <= 1) {
        return sortedWith(getChapterSort(manga, sortDescending = false))
    }

    val readingSort = getChapterSort(manga, sortDescending = false)
    val orderedMergedIds = mergedMangaIds.orderedPresentIds(this).let { ids ->
        if (manga.sortDescending()) ids.asReversed() else ids
    }
    return orderedMergedIds
        .flatMap { mangaId ->
            asSequence()
                .filter { it.mangaId == mangaId }
                .sortedWith(readingSort)
                .toList()
        }
}

fun List<Chapter>.groupedByMergedMember(
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Pair<Long, List<Chapter>>> {
    return mergedMangaIds.orderedPresentIds(this)
        .mapNotNull { mangaId ->
            val memberChapters = filter { it.mangaId == mangaId }
            memberChapters.takeIf { it.isNotEmpty() }?.let { mangaId to it }
        }
}
