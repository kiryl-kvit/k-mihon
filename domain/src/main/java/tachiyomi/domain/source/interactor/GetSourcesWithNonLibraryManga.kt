package tachiyomi.domain.source.interactor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import tachiyomi.domain.source.model.SourceWithCount
import tachiyomi.domain.source.repository.SourceRepository
import tachiyomi.domain.source.service.HiddenSourceIds

class GetSourcesWithNonLibraryManga(
    private val repository: SourceRepository,
    private val hiddenSourceIds: HiddenSourceIds,
) {

    fun subscribe(): Flow<List<SourceWithCount>> {
        return combine(
            repository.getSourcesWithNonLibraryManga(),
            hiddenSourceIds.subscribe(),
        ) { sources, hiddenSources ->
            sources.filterNot { it.id in hiddenSources }
        }
    }
}
