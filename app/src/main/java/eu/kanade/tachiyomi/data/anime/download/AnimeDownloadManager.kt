package eu.kanade.tachiyomi.data.anime.download

import android.content.Context
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeTitle

class AnimeDownloadManager(
    context: Context,
) {

    private val store = AnimeDownloadStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _queueState = MutableStateFlow<List<AnimeDownload>>(emptyList())
    val queueState = _queueState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    init {
        scope.launch {
            val downloads = store.restore()
            if (downloads.isNotEmpty()) {
                addAllToQueue(downloads)
            }
        }
    }

    fun startDownloads() {
        if (queueState.value.isEmpty()) return
        queueState.value.forEach { download ->
            if (download.status != AnimeDownload.State.DOWNLOADED) {
                download.status = AnimeDownload.State.QUEUE
            }
        }
        _isRunning.value = true
    }

    fun pauseDownloads() {
        _isRunning.value = false
    }

    fun clearQueue() {
        _queueState.value = emptyList()
        _isRunning.value = false
        store.clear()
    }

    fun queueEpisodes(
        anime: AnimeTitle,
        episodes: List<AnimeEpisode>,
        preferences: AnimeDownloadPreferences,
        autoStart: Boolean = true,
    ) {
        if (episodes.isEmpty()) return

        val queued = queueState.value
        val newDownloads = episodes
            .sortedByDescending { it.sourceOrder }
            .filter { episode -> queued.none { it.episode.id == episode.id } }
            .map {
                AnimeDownload(
                    anime = anime,
                    episode = it,
                    preferences = preferences.copy(animeId = anime.id),
                )
            }

        if (newDownloads.isEmpty()) return

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
            _isRunning.value = false
        }
    }

    fun reorderQueue(downloads: List<AnimeDownload>) {
        updateQueue(downloads)
    }

    private fun addAllToQueue(downloads: List<AnimeDownload>) {
        _queueState.update { current -> current + downloads }
        store.addAll(downloads)
    }

    private fun updateQueue(downloads: List<AnimeDownload>) {
        _queueState.value = downloads
        store.clear()
        store.addAll(downloads)
    }
}
