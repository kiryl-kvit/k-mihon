package eu.kanade.tachiyomi.data.anime.download

import android.content.Context
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.source.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadManager(
    context: Context,
    private val cache: AnimeDownloadCache = AnimeDownloadCache(context),
    private val provider: AnimeDownloadProvider = AnimeDownloadProvider(context),
    private val downloader: AnimeDownloader = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
) {

    private val store = AnimeDownloadStore(context)
    private val notifier = AnimeDownloadNotifier(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private var processorJob: Job? = null

    init {
        scope.launch {
            val downloads = store.restore()
            if (downloads.isNotEmpty()) {
                addAllToQueue(downloads)
            }
        }
        scope.launch {
            progressFlow().collectLatest { download ->
                if (download.status == AnimeDownload.State.DOWNLOADING || download.status == AnimeDownload.State.RESOLVING) {
                    notifier.onProgressChange(download)
                }
            }
        }
    }

    fun startDownloads() {
        if (queueState.value.isEmpty()) return
        queueState.value.forEach { download ->
            if (download.status != AnimeDownload.State.DOWNLOADED) {
                download.status = AnimeDownload.State.QUEUE
                download.failure = null
            }
        }
        _isRunning.value = true
        launchProcessorIfNeeded()
    }

    fun pauseDownloads() {
        processorJob?.cancel()
        processorJob = null
        queueState.value
            .filter { it.status == AnimeDownload.State.RESOLVING || it.status == AnimeDownload.State.DOWNLOADING }
            .forEach { it.status = AnimeDownload.State.QUEUE }
        _isRunning.value = false
        notifier.onPaused()
    }

    fun clearQueue() {
        _queueState.value = emptyList()
        processorJob?.cancel()
        processorJob = null
        _isRunning.value = false
        store.clear()
        notifier.onComplete()
    }

    fun queueEpisodes(
        anime: AnimeTitle,
        episodes: List<AnimeEpisode>,
        preferences: AnimeDownloadPreferences,
        autoStart: Boolean = true,
    ) {
        if (episodes.isEmpty()) return

        val episodeIds = episodes.map(AnimeEpisode::id).toSet()
        val existing = queueState.value.filter { it.episode.id in episodeIds }
        if (existing.isNotEmpty()) {
            _queueState.update { current -> current.filterNot { it.episode.id in episodeIds } }
            store.removeAll(existing)
        }

        val newDownloads = episodes
            .sortedByDescending { it.sourceOrder }
            .map {
                AnimeDownload(
                    anime = anime,
                    episode = it,
                    preferences = preferences.copy(animeId = anime.id),
                )
            }

        if (newDownloads.isEmpty()) {
            if (autoStart) {
                startDownloads()
            }
            return
        }

        addAllToQueue(newDownloads)
        if (autoStart) {
            startDownloads()
        }
    }

    fun removeFromQueue(episodeIds: Collection<Long>) {
        if (episodeIds.isEmpty()) return
        val toRemove = queueState.value.filter { it.episode.id in episodeIds }
        if (toRemove.isEmpty()) return
        _queueState.update { current -> current.filterNot { it.episode.id in episodeIds } }
        store.removeAll(toRemove)
        if (_queueState.value.isEmpty()) {
            processorJob?.cancel()
            processorJob = null
            _isRunning.value = false
        }
    }

    fun reorderQueue(downloads: List<AnimeDownload>) {
        updateQueue(downloads)
    }

    suspend fun deleteEpisodes(anime: AnimeTitle, episodes: List<AnimeEpisode>) {
        if (episodes.isEmpty()) return
        removeFromQueue(episodes.map(AnimeEpisode::id))
        val source = sourceManager.get(anime.source) ?: return
        val (_, episodeDirs) = provider.findEpisodeDirs(episodes, anime, source)
        episodeDirs.forEach { it.delete() }
        cache.removeEpisodes(episodes, anime)
    }

    fun isEpisodeDownloaded(
        episodeName: String,
        episodeUrl: String,
        animeTitle: String,
        sourceId: Long,
        skipCache: Boolean = false,
    ): Boolean {
        return cache.isEpisodeDownloaded(episodeName, episodeUrl, animeTitle, sourceId, skipCache)
    }

    fun getDownloadCount(anime: AnimeTitle): Int {
        return cache.getDownloadCount(anime)
    }

    fun getTotalDownloadCount(): Int {
        return cache.getTotalDownloadCount()
    }

    fun statusFlow(): Flow<AnimeDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.statusFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AnimeDownload.State.DOWNLOADING }.asFlow(),
            )
        }

    fun progressFlow(): Flow<AnimeDownload> = queueState
        .flatMapLatest { downloads ->
            downloads
                .map { download ->
                    download.progressFlow.drop(1).map { download }
                }
                .merge()
        }
        .onStart {
            emitAll(
                queueState.value.filter { download -> download.status == AnimeDownload.State.DOWNLOADING }.asFlow(),
            )
        }

    private fun addAllToQueue(downloads: List<AnimeDownload>) {
        _queueState.update { current -> current + downloads }
        store.addAll(downloads)
        if (_isRunning.value) {
            launchProcessorIfNeeded()
        }
    }

    private fun updateQueue(downloads: List<AnimeDownload>) {
        _queueState.value = downloads
        store.clear()
        store.addAll(downloads)
    }

    private fun launchProcessorIfNeeded() {
        if (processorJob?.isActive == true) return
        processorJob = scope.launch {
            while (_isRunning.value) {
                val next = queueState.value.firstOrNull { it.status == AnimeDownload.State.QUEUE } ?: break
                try {
                    notifier.onProgressChange(next)
                    val failure = downloader.download(next)
                    if (failure == null) {
                        _queueState.update { current -> current.filterNot { it.episode.id == next.episode.id } }
                        store.remove(next)
                    } else {
                        next.progress = 0
                        next.failure = failure
                        next.status = AnimeDownload.State.ERROR
                        notifier.onError(next)
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    next.progress = 0
                    next.failure = AnimeDownloadFailure(
                        reason = AnimeDownloadFailure.Reason.UNKNOWN,
                        message = e.message,
                    )
                    next.status = AnimeDownload.State.ERROR
                    notifier.onError(next)
                }
            }
            _isRunning.value = false
            processorJob = null
            notifier.onComplete()
        }
    }

    companion object {
        const val TMP_DIR_SUFFIX = "_tmp"
    }
}
