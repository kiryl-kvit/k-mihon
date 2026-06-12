package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Immutable
import androidx.paging.PagingSource
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.model.FilterStateNode
import eu.kanade.domain.source.model.SourceFeedAnchor
import eu.kanade.domain.source.model.SourceFeedTimeline
import eu.kanade.domain.source.model.applySnapshot
import eu.kanade.domain.source.service.BrowseFeedService
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.resolveFilterList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.source.interactor.GetRemoteAnime
import tachiyomi.domain.source.repository.AnimeSourcePagingSource
import tachiyomi.domain.source.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeChronologicalFeedScreenModel(
    private val feedId: String,
    private val sourceId: Long,
    private val listingQuery: String?,
    private val initialFilterSnapshot: List<FilterStateNode>,
    private val chronological: Boolean = true,
    private val browseFeedService: BrowseFeedService = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val getRemoteAnime: GetRemoteAnime = Injekt.get(),
    private val getAnime: GetAnime = Injekt.get(),
) : StateScreenModel<AnimeChronologicalFeedScreenModel.State>(initialState(browseFeedService, feedId)) {

    private val source = requireNotNull(animeSourceManager.get(sourceId)) as AnimeCatalogueSource
    private val hideInLibraryItems = sourcePreferences.hideInLibraryItems.get()
    private var currentSavedAnchor = browseFeedService.anchorSnapshot(feedId)
    private var persistedAnchor = currentSavedAnchor
    private var resolvedFilters: FilterList? = null

    init {
        screenModelScope.launchIO {
            browseFeedService.timeline(feedId).collectLatest { timeline ->
                mutableState.update {
                    it.copy(
                        animeIds = timeline.mangaIds,
                        nextPageKey = timeline.nextPageKey,
                        hasLoaded = it.hasLoaded || timeline.mangaIds.isNotEmpty(),
                    )
                }
            }
        }

        screenModelScope.launchIO {
            if (hideInLibraryItems) {
                normalizeTimeline()
            }

            if (browseFeedService.timelineSnapshot(feedId).mangaIds.isEmpty()) {
                refreshInternal(manual = false)
            }
        }
    }

    fun refresh(manual: Boolean = false) {
        if (state.value.isRefreshing) return
        screenModelScope.launchIO {
            refreshInternal(manual = manual)
        }
    }

    fun loadMore() {
        val currentState = state.value
        if (currentState.isRefreshing || currentState.isAppending || currentState.nextPageKey == null) return

        screenModelScope.launchIO {
            appendInternal()
        }
    }

    fun saveAnchor(animeId: Long?, scrollOffset: Int) {
        val anchor = SourceFeedAnchor(
            mangaId = animeId,
            scrollOffset = scrollOffset,
        )

        if (currentSavedAnchor == anchor) return

        currentSavedAnchor = anchor
        if (persistedAnchor != anchor) {
            persistedAnchor = anchor
            browseFeedService.saveAnchor(feedId = feedId, anchor = anchor)
        }
    }

    fun savedAnchorSnapshot(): SourceFeedAnchor {
        return currentSavedAnchor
    }

    fun consumeNewItemsIndicator() {
        mutableState.update {
            if (it.newItemsAvailableCount == 0) it else it.copy(newItemsAvailableCount = 0)
        }
    }

    suspend fun subscribeAnime(animeId: Long): Flow<AnimeTitle> {
        return getAnime.subscribe(animeId)
    }

    private suspend fun refreshInternal(manual: Boolean) {
        if (!chronological) {
            refreshCurrentPageInternal(manual = manual)
            return
        }

        val currentState = state.value
        val existingIds = currentState.animeIds
        val existingIdSet = existingIds.toHashSet()
        val prependedIds = mutableListOf<Long>()
        var nextPageKey: Long? = currentState.nextPageKey
        var currentPageKey: Long? = null
        var pageCount = 0
        var error: Throwable? = null

        mutableState.update {
            it.copy(
                isRefreshing = true,
                isManualRefresh = manual,
                newItemsAvailableCount = if (manual) 0 else it.newItemsAvailableCount,
                error = null,
            )
        }

        val pagingSource = try {
            newPagingSource()
        } catch (e: Throwable) {
            mutableState.update {
                it.copy(
                    isRefreshing = false,
                    isManualRefresh = false,
                    hasLoaded = true,
                    error = e,
                )
            }
            return
        }

        while (pageCount < MAX_REFRESH_PAGES) {
            val page = try {
                loadPage(pagingSource, currentPageKey)
            } catch (e: Throwable) {
                error = e
                break
            }

            pageCount++
            val pageIds = visibleIds(page.data)

            if (existingIds.isEmpty()) {
                prependedIds += pageIds
                nextPageKey = page.nextKey
                break
            }

            val overlapIndex = pageIds.indexOfFirst { it in existingIdSet }
            if (overlapIndex >= 0) {
                prependedIds += pageIds.take(overlapIndex)
                break
            }

            prependedIds += pageIds.filterNot(existingIdSet::contains)
            currentPageKey = page.nextKey
            if (currentPageKey == null) {
                break
            }
        }

        val mergedIds = if (existingIds.isEmpty()) prependedIds else (prependedIds + existingIds).distinct()

        persistTimeline(
            animeIds = mergedIds,
            nextPageKey = nextPageKey,
        )

        mutableState.update {
            it.copy(
                animeIds = mergedIds,
                nextPageKey = nextPageKey,
                isRefreshing = false,
                isManualRefresh = false,
                newItemsAvailableCount = if (manual && error == null) prependedIds.size else 0,
                hasLoaded = true,
                error = error,
            )
        }
    }

    private suspend fun refreshCurrentPageInternal(manual: Boolean) {
        var error: Throwable? = null

        mutableState.update {
            it.copy(
                isRefreshing = true,
                isManualRefresh = manual,
                newItemsAvailableCount = 0,
                error = null,
            )
        }

        val pagingSource = try {
            newPagingSource()
        } catch (e: Throwable) {
            mutableState.update {
                it.copy(
                    isRefreshing = false,
                    isManualRefresh = false,
                    hasLoaded = true,
                    error = e,
                )
            }
            return
        }

        val page = try {
            loadPage(pagingSource, null)
        } catch (e: Throwable) {
            error = e
            null
        }

        val ids = page?.data?.let(::visibleIds).orEmpty()
        val nextPageKey = page?.nextKey

        if (page != null) {
            persistTimeline(
                animeIds = ids,
                nextPageKey = nextPageKey,
            )
        }

        mutableState.update {
            it.copy(
                animeIds = if (page != null) ids else it.animeIds,
                nextPageKey = if (page != null) nextPageKey else it.nextPageKey,
                isRefreshing = false,
                isManualRefresh = false,
                newItemsAvailableCount = 0,
                hasLoaded = true,
                error = error,
            )
        }
    }

    private suspend fun appendInternal() {
        val currentState = state.value
        var currentPageKey = currentState.nextPageKey ?: return
        val currentIds = currentState.animeIds.toMutableList()
        val currentIdSet = currentIds.toHashSet()
        var nextPageKey = currentState.nextPageKey
        var error: Throwable? = null
        var pagesScanned = 0

        mutableState.update {
            it.copy(
                isAppending = true,
                error = null,
            )
        }

        val pagingSource = try {
            newPagingSource()
        } catch (e: Throwable) {
            mutableState.update {
                it.copy(
                    isAppending = false,
                    hasLoaded = true,
                    error = e,
                )
            }
            return
        }

        while (pagesScanned < MAX_APPEND_PAGE_SCANS) {
            val page = try {
                loadPage(pagingSource, currentPageKey)
            } catch (e: Throwable) {
                error = e
                break
            }

            pagesScanned++

            val newIds = visibleIds(page.data).filter(currentIdSet::add)
            nextPageKey = page.nextKey

            if (newIds.isNotEmpty()) {
                currentIds += newIds
                break
            }

            currentPageKey = page.nextKey ?: break
        }

        persistTimeline(
            animeIds = currentIds,
            nextPageKey = nextPageKey,
        )

        mutableState.update {
            it.copy(
                animeIds = currentIds,
                nextPageKey = nextPageKey,
                isAppending = false,
                hasLoaded = true,
                error = error,
            )
        }
    }

    private suspend fun newPagingSource(): AnimeSourcePagingSource {
        return getRemoteAnime(
            sourceId = sourceId,
            query = listingQuery.orEmpty(),
            filterList = ensureFiltersLoaded(),
        )
    }

    private suspend fun loadPage(
        pagingSource: AnimeSourcePagingSource,
        pageKey: Long?,
    ): PagingSource.LoadResult.Page<Long, AnimeTitle> {
        val params: PagingSource.LoadParams<Long> = if (pageKey == null) {
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            )
        } else {
            PagingSource.LoadParams.Append(
                key = pageKey,
                loadSize = PAGE_SIZE,
                placeholdersEnabled = false,
            )
        }

        return when (val result = pagingSource.load(params)) {
            is PagingSource.LoadResult.Page -> result
            is PagingSource.LoadResult.Error -> throw result.throwable
            is PagingSource.LoadResult.Invalid -> error("Invalid paging state for feed $feedId")
        }
    }

    private suspend fun ensureFiltersLoaded(): FilterList {
        resolvedFilters?.let { return it }
        val filters = source.resolveFilterList().applySnapshot(initialFilterSnapshot)
        resolvedFilters = filters
        return filters
    }

    private fun visibleIds(anime: List<AnimeTitle>): List<Long> {
        if (!hideInLibraryItems) return anime.map(AnimeTitle::id)
        return anime.filterNot { it.favorite }.map(AnimeTitle::id)
    }

    private suspend fun normalizeTimeline() {
        val timeline = browseFeedService.timelineSnapshot(feedId)
        if (timeline.mangaIds.isEmpty()) return

        val anchor = browseFeedService.anchorSnapshot(feedId)
        val normalizedIds = timeline.mangaIds.filter { animeId ->
            getAnime.await(animeId)?.favorite == false
        }
        val normalizedAnchor = anchor.takeIf {
            anchor.mangaId == null || anchor.mangaId in normalizedIds
        } ?: SourceFeedAnchor()

        if (normalizedIds == timeline.mangaIds && normalizedAnchor == anchor) return

        persistTimeline(
            animeIds = normalizedIds,
            nextPageKey = timeline.nextPageKey,
        )

        if (normalizedAnchor != anchor) {
            currentSavedAnchor = normalizedAnchor
            persistedAnchor = normalizedAnchor
            browseFeedService.saveAnchor(feedId, normalizedAnchor)
        }

        mutableState.update {
            it.copy(
                animeIds = normalizedIds,
                nextPageKey = timeline.nextPageKey,
                savedAnchor = normalizedAnchor,
                hasLoaded = normalizedIds.isNotEmpty(),
            )
        }
    }

    private fun persistTimeline(animeIds: List<Long>, nextPageKey: Long?) {
        browseFeedService.saveTimeline(
            feedId = feedId,
            timeline = SourceFeedTimeline(
                mangaIds = animeIds,
                nextPageKey = nextPageKey,
            ),
        )
    }

    @Immutable
    data class State(
        val animeIds: List<Long> = emptyList(),
        val nextPageKey: Long? = null,
        val savedAnchor: SourceFeedAnchor = SourceFeedAnchor(),
        val isRefreshing: Boolean = false,
        val isManualRefresh: Boolean = false,
        val isAppending: Boolean = false,
        val newItemsAvailableCount: Int = 0,
        val hasLoaded: Boolean = false,
        val error: Throwable? = null,
    )

    companion object {
        private const val PAGE_SIZE = 25
        private const val MAX_REFRESH_PAGES = 10
        private const val MAX_APPEND_PAGE_SCANS = 10

        private fun initialState(
            browseFeedService: BrowseFeedService,
            feedId: String,
        ): State {
            val timeline = browseFeedService.timelineSnapshot(feedId)
            return State(
                animeIds = timeline.mangaIds,
                nextPageKey = timeline.nextPageKey,
                savedAnchor = browseFeedService.anchorSnapshot(feedId),
                hasLoaded = timeline.mangaIds.isNotEmpty(),
            )
        }
    }
}
