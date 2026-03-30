package tachiyomi.domain.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.source.service.HiddenSourceIds
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
    private val hiddenSourceIds: HiddenSourceIds,
) {

    suspend fun await(): List<LibraryManga> {
        return filterHiddenSources(mangaRepository.getLibraryManga(), hiddenSourceIds.get())
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return combine(
            mangaRepository.getLibraryMangaAsFlow(),
            hiddenSourceIds.subscribe(),
            ::filterHiddenSources,
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

    private fun filterHiddenSources(
        libraryManga: List<LibraryManga>,
        hiddenSources: Set<Long>,
    ): List<LibraryManga> {
        return libraryManga.filterNot { it.manga.source in hiddenSources }
    }
}
