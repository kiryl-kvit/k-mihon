package tachiyomi.data.source

import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.source.repository.AnimeSourcePagingSource
import tachiyomi.domain.source.repository.AnimeSourceRepository
import tachiyomi.domain.source.service.AnimeSourceManager

class AnimeSourceRepositoryImpl(
    private val videoSourceManager: AnimeSourceManager,
) : AnimeSourceRepository {

    override fun search(
        sourceId: Long,
        query: String,
        filterList: FilterList,
    ): AnimeSourcePagingSource {
        val source = videoSourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourceSearchPagingSource(source, query, filterList)
    }

    override fun getPopular(sourceId: Long): AnimeSourcePagingSource {
        val source = videoSourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourcePopularPagingSource(source)
    }

    override fun getLatest(sourceId: Long): AnimeSourcePagingSource {
        val source = videoSourceManager.get(sourceId) as AnimeCatalogueSource
        return AnimeSourceLatestPagingSource(source)
    }
}
