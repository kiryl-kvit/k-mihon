package eu.kanade.tachiyomi.ui.download

import android.view.MenuItem
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.databinding.DownloadListBinding
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DownloadQueueScreenModel(
    private val downloadManager: DownloadManager = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(emptyList<DownloadQueueHeaderItem>())
    val state = _state.asStateFlow()

    lateinit var controllerBinding: DownloadListBinding

    /**
     * Adapter containing the active downloads.
     */
    var adapter: DownloadQueueAdapter? = null

    /**
     * Map of jobs for active downloads.
     */
    private val progressJobs = mutableMapOf<Download, Job>()

    val listener = object : DownloadQueueAdapter.DownloadQueueItemListener {
        /**
         * Called when an item is released from a drag.
         *
         * @param position The position of the released item.
         */
        override fun onItemReleased(position: Int) {
            val adapter = adapter ?: return
            val downloads = adapter.headerItems.flatMap { header ->
                adapter.getSectionItems(header).map { item ->
                    (item as DownloadQueueItem).payloadAsDownload()
                }
            }
            reorder(downloads)
        }

        /**
         * Called when the menu item of a download is pressed
         *
         * @param position The position of the item
         * @param menuItem The menu Item pressed
         */
        override fun onMenuItemClick(position: Int, menuItem: MenuItem) {
            val item = adapter?.getItem(position) ?: return
            if (item is DownloadQueueItem) {
                when (menuItem.itemId) {
                    R.id.move_to_top, R.id.move_to_bottom -> {
                        val headerItems = adapter?.headerItems ?: return
                        val newDownloads = mutableListOf<Download>()
                        headerItems.forEach { headerItem ->
                            headerItem as DownloadQueueHeaderItem
                            if (headerItem == item.header) {
                                headerItem.removeSubItem(item)
                                if (menuItem.itemId == R.id.move_to_top) {
                                    headerItem.addSubItem(0, item)
                                } else {
                                    headerItem.addSubItem(item)
                                }
                            }
                            newDownloads.addAll(headerItem.subItems.map { it.payloadAsDownload() })
                        }
                        reorder(newDownloads)
                    }
                    R.id.move_to_top_series, R.id.move_to_bottom_series -> {
                        val selectedDownload = item.payloadAsDownload()
                        val (selectedSeries, otherSeries) = adapter?.currentItems
                            ?.filterIsInstance<DownloadQueueItem>()
                            ?.map(DownloadQueueItem::payloadAsDownload)
                            ?.partition { selectedDownload.manga.id == it.manga.id }
                            ?: Pair(emptyList(), emptyList())
                        if (menuItem.itemId == R.id.move_to_top_series) {
                            reorder(selectedSeries + otherSeries)
                        } else {
                            reorder(otherSeries + selectedSeries)
                        }
                    }
                    R.id.cancel_download -> {
                        cancel(listOf(item.payloadAsDownload()))
                    }
                    R.id.cancel_series -> {
                        val allDownloadsForSeries = adapter?.currentItems
                            ?.filterIsInstance<DownloadQueueItem>()
                            ?.filter { item.payloadAsDownload().manga.id == it.payloadAsDownload().manga.id }
                            ?.map(DownloadQueueItem::payloadAsDownload)
                        if (!allDownloadsForSeries.isNullOrEmpty()) {
                            cancel(allDownloadsForSeries)
                        }
                    }
                }
            }
        }
    }

    init {
        screenModelScope.launch {
            downloadManager.queueState
                .map { downloads ->
                    downloads
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
                }
                .collect { newList -> _state.update { newList } }
        }
    }

    override fun onDispose() {
        for (job in progressJobs.values) {
            job.cancel()
        }
        progressJobs.clear()
        adapter = null
    }

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getDownloadStatusFlow() = downloadManager.statusFlow()
    fun getDownloadProgressFlow() = downloadManager.progressFlow()

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    fun reorder(downloads: List<Download>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancel(downloads: List<Download>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(selector: (DownloadQueueItem) -> R, reverse: Boolean = false) {
        val adapter = adapter ?: return
        val newDownloads = mutableListOf<Download>()
        adapter.headerItems.forEach { headerItem ->
            headerItem as DownloadQueueHeaderItem
            headerItem.subItems = headerItem.subItems.sortedBy(selector).toMutableList().apply {
                if (reverse) {
                    reverse()
                }
            }
            newDownloads.addAll(headerItem.subItems.map { it.payloadAsDownload() })
        }
        reorder(newDownloads)
    }

    /**
     * Called when the status of a download changes.
     *
     * @param download the download whose status has changed.
     */
    fun onStatusChange(download: Download) {
        when (download.status) {
            Download.State.DOWNLOADING -> {
                launchProgressJob(download)
                // Initial update of the downloaded pages
                onUpdateDownloadedPages(download)
            }
            Download.State.DOWNLOADED -> {
                cancelProgressJob(download)
                onUpdateProgress(download)
                onUpdateDownloadedPages(download)
            }
            Download.State.ERROR -> cancelProgressJob(download)
            else -> {
                /* unused */
            }
        }
    }

    /**
     * Observe the progress of a download and notify the view.
     *
     * @param download the download to observe its progress.
     */
    private fun launchProgressJob(download: Download) {
        val job = screenModelScope.launch {
            while (download.pages == null) {
                delay(50)
            }

            val progressFlows = download.pages!!.map(Page::progressFlow)
            combine(progressFlows, Array<Int>::sum)
                .distinctUntilChanged()
                .debounce(50)
                .collectLatest {
                    onUpdateProgress(download)
                }
        }

        // Avoid leaking jobs
        progressJobs.remove(download)?.cancel()

        progressJobs[download] = job
    }

    /**
     * Unsubscribes the given download from the progress subscriptions.
     *
     * @param download the download to unsubscribe.
     */
    private fun cancelProgressJob(download: Download) {
        progressJobs.remove(download)?.cancel()
    }

    /**
     * Called when the progress of a download changes.
     *
     * @param download the download whose progress has changed.
     */
    private fun onUpdateProgress(download: Download) {
        getHolder(download)?.notifyProgress()
    }

    /**
     * Called when a page of a download is downloaded.
     *
     * @param download the download whose page has been downloaded.
     */
    fun onUpdateDownloadedPages(download: Download) {
        getHolder(download)?.notifyProgressText()
    }

    /**
     * Returns the holder for the given download.
     *
     * @param download the download to find.
     * @return the holder of the download or null if it's not bound.
     */
    private fun getHolder(download: Download): DownloadQueueHolder? {
        return controllerBinding.root.findViewHolderForItemId(download.chapter.id) as? DownloadQueueHolder
    }
}

internal fun DownloadQueueItem.payloadAsDownload(): Download {
    return payloadAs<Download>() ?: error("Download queue item payload is not a manga download")
}
