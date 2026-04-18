package tachiyomi.domain.source.repository

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.source.model.FilterList
import tachiyomi.domain.anime.model.AnimeTitle

typealias AnimeSourcePagingSource = PagingSource<Long, AnimeTitle>

interface AnimeSourceRepository {

    fun search(sourceId: Long, query: String, filterList: FilterList): AnimeSourcePagingSource

    fun getPopular(sourceId: Long): AnimeSourcePagingSource

    fun getLatest(sourceId: Long): AnimeSourcePagingSource
}
