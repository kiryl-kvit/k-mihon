package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.groupedByMergedMember
import tachiyomi.domain.library.service.sortedForMergedDisplay
import tachiyomi.domain.library.service.sortedForReading
import tachiyomi.domain.manga.model.Manga

fun List<Chapter>.sortedForMergedDisplay(
    manga: Manga,
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Chapter> {
    return sortedForMergedDisplay(
        mergedIds = mergedMangaIds,
        idSelector = Chapter::mangaId,
        itemComparator = getChapterSort(manga).let { cmp -> Comparator { a, b -> cmp(a, b) } },
    )
}

fun List<Chapter>.sortedForReading(
    manga: Manga,
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Chapter> {
    return sortedForReading(
        mergedIds = mergedMangaIds,
        idSelector = Chapter::mangaId,
        itemComparator = getChapterSort(manga, sortDescending = false).let { cmp -> Comparator { a, b -> cmp(a, b) } },
        sortDescending = manga.sortDescending(),
    )
}

fun List<Chapter>.groupedByMergedMember(
    mergedMangaIds: List<Long> = map(Chapter::mangaId).distinct(),
): List<Pair<Long, List<Chapter>>> {
    return groupedByMergedMember(
        mergedIds = mergedMangaIds,
        idSelector = Chapter::mangaId,
    )
}
