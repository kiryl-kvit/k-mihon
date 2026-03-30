package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaMerge
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.repository.MergedMangaRepository
import tachiyomi.domain.source.service.HiddenSourceIds
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
    private val hiddenSourceIds: HiddenSourceIds,
    private val mergedMangaRepository: MergedMangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return collapseAndFilter(
            mangaRepository.getLibraryManga(),
            mergedMangaRepository.getAll(),
            hiddenSourceIds.get(),
        )
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return combine(
            mangaRepository.getLibraryMangaAsFlow(),
            mergedMangaRepository.subscribeAll(),
            hiddenSourceIds.subscribe(),
            ::collapseAndFilter,
        )
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }

    private fun collapseAndFilter(
        libraryManga: List<LibraryManga>,
        merges: List<MangaMerge>,
        hiddenSources: Set<Long>,
    ): List<LibraryManga> {
        val byId = libraryManga.associateBy { it.manga.id }
        val groupedMerges = merges.groupBy { it.targetId }

        val collapsed = mutableListOf<LibraryManga>()
        val consumedIds = mutableSetOf<Long>()

        groupedMerges.forEach { (targetId, group) ->
            val members = group.sortedBy { it.position }
                .mapNotNull { byId[it.mangaId] }
            if (members.size <= 1) return@forEach

            val target = members.firstOrNull { it.manga.id == targetId } ?: members.first()
            consumedIds += members.map { it.manga.id }
            collapsed += mergeLibraryManga(target, members)
        }

        collapsed += libraryManga.filterNot { it.manga.id in consumedIds }

        return collapsed.filterNot { item ->
            val visibleSources = item.sourceIds - hiddenSources
            visibleSources.isEmpty()
        }
    }

    private fun mergeLibraryManga(target: LibraryManga, members: List<LibraryManga>): LibraryManga {
        val categories = members.flatMap { it.categories }.distinct()
        val sourceIds = members.map { it.manga.source }.toSet()
        val displaySourceId = if (sourceIds.size > 1) LibraryManga.MULTI_SOURCE_ID else sourceIds.first()
        return target.copy(
            categories = categories,
            totalChapters = members.sumOf { it.totalChapters },
            readCount = members.sumOf { it.readCount },
            bookmarkCount = members.sumOf { it.bookmarkCount },
            latestUpload = members.maxOfOrNull { it.latestUpload } ?: 0L,
            chapterFetchedAt = members.maxOfOrNull { it.chapterFetchedAt } ?: 0L,
            lastRead = members.maxOfOrNull { it.lastRead } ?: 0L,
            memberMangaIds = members.map { it.manga.id },
            memberMangas = members.map { it.manga },
            displaySourceId = displaySourceId,
            sourceIds = sourceIds,
        )
    }
}
