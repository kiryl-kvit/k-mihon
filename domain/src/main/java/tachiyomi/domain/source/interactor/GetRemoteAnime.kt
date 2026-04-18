package tachiyomi.domain.source.interactor

import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.source.repository.AnimeSourcePagingSource
import tachiyomi.domain.source.repository.AnimeSourceRepository

class GetRemoteAnime(
    private val repository: AnimeSourceRepository,
) {

    operator fun invoke(sourceId: Long, query: String, filterList: FilterList): AnimeSourcePagingSource {
        return when (query) {
            QUERY_POPULAR -> repository.getPopular(sourceId)
            QUERY_LATEST -> repository.getLatest(sourceId)
            else -> repository.search(sourceId, query, filterList)
        }
    }

    companion object {
        const val QUERY_POPULAR = "eu.kanade.domain.source.interactor.VIDEO_POPULAR"
        const val QUERY_LATEST = "eu.kanade.domain.source.interactor.VIDEO_LATEST"
    }
}
