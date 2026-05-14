package tachiyomi.domain.chapter.service

import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.entrySortComparator
import tachiyomi.domain.manga.model.Manga

fun getChapterSort(
    manga: Manga,
    sortDescending: Boolean = manga.sortDescending(),
): (
    Chapter,
    Chapter,
) -> Int {
    val comparator = entrySortComparator(
        sorting = manga.sorting,
        sortDescending = sortDescending,
        sortingSourceFlag = Manga.CHAPTER_SORTING_SOURCE,
        sortingNumberFlag = Manga.CHAPTER_SORTING_NUMBER,
        sortingUploadDateFlag = Manga.CHAPTER_SORTING_UPLOAD_DATE,
        sortingAlphabetFlag = Manga.CHAPTER_SORTING_ALPHABET,
        numberSelector = Chapter::chapterNumber,
        dateUploadSelector = Chapter::dateUpload,
        nameSelector = Chapter::name,
        urlSelector = Chapter::url,
        sourceOrderSelector = Chapter::sourceOrder,
    )
    return { c1, c2 -> comparator.compare(c1, c2) }
}
