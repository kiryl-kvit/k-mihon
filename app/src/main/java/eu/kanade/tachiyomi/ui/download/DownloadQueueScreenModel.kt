package eu.kanade.tachiyomi.ui.download

import android.view.MenuItem
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.anime.download.AnimeDownloadManager
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import tachiyomi.domain.source.service.AnimeSourceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadQueueScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(emptyList<DownloadQueueHeaderItem>())
    val state = _state.asStateFlow()

    lateinit var controllerBinding: DownloadListBinding
    var adapter: DownloadQueueAdapter? = null

    val listener = object : DownloadQueueAdapter.DownloadQueueItemListener {
        override fun onItemReleased(position: Int) {
            val adapter = adapter ?: return
            val reorderedManga = mutableListOf<Download>()
            val reorderedAnime = mutableListOf<AnimeDownload>()
            adapter.headerItems.forEach { header ->
                (header as DownloadQueueHeaderItem).subItems.forEach { item ->
                    when (item.model().contentType) {
                        DownloadQueueContentType.MANGA_CHAPTER -> reorderedManga += item.payloadAsDownload()
                        DownloadQueueContentType.ANIME_EPISODE -> reorderedAnime += item.payloadAsAnimeDownload()
                    }
                }
            }
            downloadManager.reorderQueue(reorderedManga)
            animeDownloadManager.reorderQueue(reorderedAnime)
        }

        override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
            val selectedItem = adapter?.getItem(position) as? DownloadQueueItem ?: return
            when (menuItem.itemId) {
                R.id.move_to_top, R.id.move_to_bottom -> {
                    val header = selectedItem.header as DownloadQueueHeaderItem
                    header.removeSubItem(selectedItem)
                    if (menuItem.itemId == R.id.move_to_top) {
                        header.addSubItem(0, selectedItem)
                    } else {
                        header.addSubItem(selectedItem)
                    }
                    onItemReleased(position)
                }
                R.id.move_to_top_series, R.id.move_to_bottom_series -> {
                    moveSeries(selectedItem, moveToTop = menuItem.itemId == R.id.move_to_top_series)
                }
                R.id.cancel_download -> {
                    cancelItem(selectedItem)
                }
                R.id.cancel_series -> {
                    cancelSeries(selectedItem)
                }
            }
        }
    }

    init {
        screenModelScope.launch {
            combine(downloadManager.queueState, animeDownloadManager.queueState) { mangaQueue, animeQueue ->
                val mangaHeaders = mangaQueue
                    .groupBy { it.source }
                    .map { entry ->
                        DownloadQueueHeaderItem(
                            DownloadQueueHeaderModel(
                                id = entry.key.id,
                                contentType = DownloadQueueContentType.MANGA_CHAPTER,
                                title = entry.key.name,
                                count = entry.value.size,
                            ),
                        ).apply {
                            addSubItems(
                                0,
                                entry.value.map { download ->
                                    DownloadQueueItem(
                                        payload = download,
                                        header = this,
                                        modelProvider = download::toDownloadQueueItemModel,
                                    )
                                },
                            )
                        }
                    }

                val animeHeaders = animeQueue
                    .groupBy { it.anime.source }
                    .map { entry ->
                        val sourceName = animeSourceManager.get(entry.key)?.name ?: entry.key.toString()
                        DownloadQueueHeaderItem(
                            DownloadQueueHeaderModel(
                                id = entry.key,
                                contentType = DownloadQueueContentType.ANIME_EPISODE,
                                title = sourceName,
                                count = entry.value.size,
                            ),
                        ).apply {
                            addSubItems(
                                0,
                                entry.value.map { download ->
                                    DownloadQueueItem(
                                        payload = download,
                                        header = this,
                                        modelProvider = download::toDownloadQueueItemModel,
                                    )
                                },
                            )
                        }
                    }

                mangaHeaders + animeHeaders
            }.collect { newList ->
                _state.update { newList }
            }
        }
    }

    override fun onDispose() {
        adapter = null
    }

    val isDownloaderRunning = combine(downloadManager.isDownloaderRunning, animeDownloadManager.isRunning) { manga, anime ->
        manga || anime
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getDownloadStatusFlow() = downloadManager.statusFlow()
    fun getDownloadProgressFlow() = downloadManager.progressFlow()
    fun getAnimeDownloadStatusFlow() = animeDownloadManager.statusFlow()
    fun getAnimeDownloadProgressFlow() = animeDownloadManager.progressFlow()

    fun startDownloads() {
        downloadManager.startDownloads()
        animeDownloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
        animeDownloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
        animeDownloadManager.clearQueue()
    }

    fun <R : Comparable<R>> reorderQueue(selector: (DownloadQueueItem) -> R, reverse: Boolean = false) {
        val adapter = adapter ?: return
        val reorderedManga = mutableListOf<Download>()
        val reorderedAnime = mutableListOf<AnimeDownload>()
        adapter.headerItems.forEach { headerItem ->
            val header = headerItem as DownloadQueueHeaderItem
            header.subItems = header.subItems.sortedBy(selector).toMutableList().apply {
                if (reverse) reverse()
            }
            header.subItems.forEach { item ->
                when (item.model().contentType) {
                    DownloadQueueContentType.MANGA_CHAPTER -> reorderedManga += item.payloadAsDownload()
                    DownloadQueueContentType.ANIME_EPISODE -> reorderedAnime += item.payloadAsAnimeDownload()
                }
            }
        }
        downloadManager.reorderQueue(reorderedManga)
        animeDownloadManager.reorderQueue(reorderedAnime)
    }

    fun onStatusChange(download: Download) {
        getHolder(download.chapter.id)?.notifyProgress()
        getHolder(download.chapter.id)?.notifyProgressText()
    }

    fun onUpdateDownloadedPages(download: Download) {
        getHolder(download.chapter.id)?.notifyProgress()
        getHolder(download.chapter.id)?.notifyProgressText()
    }

    fun onAnimeStatusChange(download: AnimeDownload) {
        getHolder(download.episode.id)?.notifyProgress()
        getHolder(download.episode.id)?.notifyProgressText()
    }

    fun onAnimeUpdateProgress(download: AnimeDownload) {
        getHolder(download.episode.id)?.notifyProgress()
        getHolder(download.episode.id)?.notifyProgressText()
    }

    private fun moveSeries(selectedItem: DownloadQueueItem, moveToTop: Boolean) {
        val adapter = adapter ?: return
        when (selectedItem.model().contentType) {
            DownloadQueueContentType.MANGA_CHAPTER -> {
                val selected = selectedItem.payloadAsDownload()
                val (series, others) = adapter.currentItems
                    .filterIsInstance<DownloadQueueItem>()
                    .filter { it.model().contentType == DownloadQueueContentType.MANGA_CHAPTER }
                    .map(DownloadQueueItem::payloadAsDownload)
                    .partition { it.manga.id == selected.manga.id }
                downloadManager.reorderQueue(if (moveToTop) series + others else others + series)
            }
            DownloadQueueContentType.ANIME_EPISODE -> {
                val selected = selectedItem.payloadAsAnimeDownload()
                val (series, others) = adapter.currentItems
                    .filterIsInstance<DownloadQueueItem>()
                    .filter { it.model().contentType == DownloadQueueContentType.ANIME_EPISODE }
                    .map(DownloadQueueItem::payloadAsAnimeDownload)
                    .partition { it.anime.id == selected.anime.id }
                animeDownloadManager.reorderQueue(if (moveToTop) series + others else others + series)
            }
        }
    }

    private fun cancelItem(selectedItem: DownloadQueueItem) {
        when (selectedItem.model().contentType) {
            DownloadQueueContentType.MANGA_CHAPTER -> {
                downloadManager.cancelQueuedDownloads(listOf(selectedItem.payloadAsDownload()))
            }
            DownloadQueueContentType.ANIME_EPISODE -> {
                animeDownloadManager.removeFromQueue(listOf(selectedItem.payloadAsAnimeDownload().episode.id))
            }
        }
    }

    private fun cancelSeries(selectedItem: DownloadQueueItem) {
        val adapter = adapter ?: return
        when (selectedItem.model().contentType) {
            DownloadQueueContentType.MANGA_CHAPTER -> {
                val selected = selectedItem.payloadAsDownload()
                val downloads = adapter.currentItems
                    .filterIsInstance<DownloadQueueItem>()
                    .filter { it.model().contentType == DownloadQueueContentType.MANGA_CHAPTER }
                    .map(DownloadQueueItem::payloadAsDownload)
                    .filter { it.manga.id == selected.manga.id }
                if (downloads.isNotEmpty()) {
                    downloadManager.cancelQueuedDownloads(downloads)
                }
            }
            DownloadQueueContentType.ANIME_EPISODE -> {
                val selected = selectedItem.payloadAsAnimeDownload()
                val episodeIds = adapter.currentItems
                    .filterIsInstance<DownloadQueueItem>()
                    .filter { it.model().contentType == DownloadQueueContentType.ANIME_EPISODE }
                    .map(DownloadQueueItem::payloadAsAnimeDownload)
                    .filter { it.anime.id == selected.anime.id }
                    .map { it.episode.id }
                if (episodeIds.isNotEmpty()) {
                    animeDownloadManager.removeFromQueue(episodeIds)
                }
            }
        }
    }

    private fun getHolder(itemId: Long): DownloadQueueHolder? {
        return controllerBinding.root.findViewHolderForItemId(itemId) as? DownloadQueueHolder
    }
}

internal fun DownloadQueueItem.payloadAsDownload(): Download {
    return payloadAs<Download>() ?: error("Download queue item payload is not a manga download")
}

internal fun DownloadQueueItem.payloadAsAnimeDownload(): AnimeDownload {
    return payloadAs<AnimeDownload>() ?: error("Download queue item payload is not an anime download")
}
