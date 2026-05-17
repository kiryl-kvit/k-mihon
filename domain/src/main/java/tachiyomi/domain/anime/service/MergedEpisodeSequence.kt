package tachiyomi.domain.anime.service

import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.library.service.entrySortComparator
import tachiyomi.domain.library.service.groupedByMergedMember
import tachiyomi.domain.library.service.sortedForMergedDisplay
import tachiyomi.domain.library.service.sortedForReading

fun List<AnimeEpisode>.sortedForMergedDisplay(
    anime: AnimeTitle,
    mergedAnimeIds: List<Long> = map(AnimeEpisode::animeId).distinct(),
): List<AnimeEpisode> {
    return sortedForMergedDisplay(
        mergedIds = mergedAnimeIds,
        idSelector = AnimeEpisode::animeId,
        itemComparator = anime.episodeSortComparator(),
    )
}

fun List<AnimeEpisode>.sortedForReading(
    anime: AnimeTitle,
    mergedAnimeIds: List<Long> = map(AnimeEpisode::animeId).distinct(),
): List<AnimeEpisode> {
    return sortedForReading(
        mergedIds = mergedAnimeIds,
        idSelector = AnimeEpisode::animeId,
        itemComparator = anime.episodeSortComparator(sortDescending = false),
        sortDescending = anime.sortDescending(),
    )
}

fun List<AnimeEpisode>.groupedByMergedMember(
    mergedAnimeIds: List<Long> = map(AnimeEpisode::animeId).distinct(),
): List<Pair<Long, List<AnimeEpisode>>> {
    return groupedByMergedMember(
        mergedIds = mergedAnimeIds,
        idSelector = AnimeEpisode::animeId,
    )
}

fun AnimeTitle.episodeSortComparator(
    sortDescending: Boolean = this.sortDescending(),
): Comparator<AnimeEpisode> {
    return entrySortComparator(
        sorting = sorting,
        sortDescending = sortDescending,
        sortingSourceFlag = AnimeTitle.EPISODE_SORTING_SOURCE,
        sortingNumberFlag = AnimeTitle.EPISODE_SORTING_NUMBER,
        sortingUploadDateFlag = AnimeTitle.EPISODE_SORTING_UPLOAD_DATE,
        sortingAlphabetFlag = AnimeTitle.EPISODE_SORTING_ALPHABET,
        numberSelector = AnimeEpisode::episodeNumber,
        dateUploadSelector = AnimeEpisode::dateUpload,
        nameSelector = AnimeEpisode::name,
        urlSelector = AnimeEpisode::url,
        sourceOrderSelector = AnimeEpisode::sourceOrder,
        sourceOrderNewestFirst = false,
    )
}
