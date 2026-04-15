package eu.kanade.tachiyomi.ui.video.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackOption
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimePlaybackPreferences
import tachiyomi.domain.anime.model.PlayerQualityMode
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackPreferencesRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VideoPlayerViewModel @JvmOverloads constructor(
    private val savedState: SavedStateHandle,
    private val resolveVideoStream: VideoStreamResolver = Injekt.get<ResolveVideoStream>(),
    private val animePlaybackPreferencesRepository: AnimePlaybackPreferencesRepository = Injekt.get(),
    private val animeEpisodeRepository: AnimeEpisodeRepository = Injekt.get(),
    private val videoPlaybackStateRepository: AnimePlaybackStateRepository = Injekt.get(),
    private val videoHistoryRepository: AnimeHistoryRepository = Injekt.get(),
    private val resolveDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val persistenceDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = mutableEvents.asSharedFlow()

    private var initialized = false
    private var playbackSession: VideoPlaybackSession? = null
    private val persistMutex = Mutex()
    private var animeId: Long = INVALID_ID
    private var episodeId: Long = INVALID_ID
    private var applySelectionJob: Job? = null

    fun init(animeId: Long, episodeId: Long) {
        if (initialized) return
        initialized = true
        this.animeId = animeId
        this.episodeId = episodeId
        savedState[VIDEO_ID_KEY] = animeId
        savedState[EPISODE_ID_KEY] = episodeId

        viewModelScope.launch {
            resolvePlayback(initial = true)
        }
    }

    fun applySourceSelection(selection: VideoPlaybackSelection) {
        val current = mutableState.value as? State.Ready ?: return
        applySelectionJob?.cancel()
        applySelectionJob = viewModelScope.launch {
            persistPlaybackPreferences(
                animeId = current.animeId,
                sourceSelection = selection,
                adaptiveQuality = current.playback.currentAdaptiveQuality,
            )
            if (!isActive) return@launch
            resolvePlayback(
                selection = selection,
                preservePositionMs = current.resumePositionMs,
                showLoading = false,
            )
        }
    }

    fun selectAdaptiveQuality(preference: VideoAdaptiveQualityPreference) {
        val current = mutableState.value as? State.Ready ?: return
        mutableState.value = current.copy(
            playback = current.playback.copy(currentAdaptiveQuality = preference),
        )
        viewModelScope.launch {
            persistPlaybackPreferences(
                animeId = current.animeId,
                sourceSelection = current.playback.persistedSourceSelection,
                adaptiveQuality = preference,
            )
        }
    }

    fun updateAdaptiveQualities(options: List<VideoAdaptiveQualityOption>) {
        val current = mutableState.value as? State.Ready ?: return
        mutableState.value = current.copy(
            playback = current.playback.copy(adaptiveQualities = options),
        )
    }

    fun persistPlayback(positionMs: Long, durationMs: Long) {
        val current = mutableState.value as? State.Ready ?: return
        val session = playbackSession ?: VideoPlaybackSession(current.episodeId).also { playbackSession = it }
        val snapshot = session.snapshot(positionMs = positionMs, durationMs = durationMs)

        viewModelScope.launch(persistenceDispatcher) {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    videoPlaybackStateRepository.upsertAndSyncEpisodeState(snapshot.playbackState)
                    snapshot.historyUpdate?.let { historyUpdate ->
                        videoHistoryRepository.upsertHistory(historyUpdate)
                    }
                }
            }
        }
    }

    fun resetPlaybackBaseline(positionMs: Long) {
        playbackSession?.restore(positionMs)
        val current = mutableState.value as? State.Ready ?: return
        mutableState.value = current.copy(resumePositionMs = positionMs.coerceAtLeast(0L))
    }

    fun playPreviousEpisode() {
        val current = mutableState.value as? State.Ready ?: return
        val previousEpisodeId = current.previousEpisodeId ?: return
        viewModelScope.launch {
            playEpisode(previousEpisodeId)
        }
    }

    fun playNextEpisode() {
        val current = mutableState.value as? State.Ready ?: return
        val nextEpisodeId = current.nextEpisodeId ?: return
        viewModelScope.launch {
            playEpisode(nextEpisodeId)
        }
    }

    private suspend fun resolvePlayback(
        selection: VideoPlaybackSelection? = null,
        preservePositionMs: Long? = null,
        initial: Boolean = false,
        showLoading: Boolean = true,
    ) {
        val previousReady = mutableState.value as? State.Ready
        mutableState.value = if (showLoading || previousReady == null) {
            State.Loading
        } else {
            previousReady.copy(isSourceSwitching = true)
        }

        mutableState.value = when (val result = withContext(resolveDispatcher) { resolveVideoStream(animeId, episodeId, selection) }) {
            is ResolveVideoStream.Result.Success -> {
                val resumePositionMs = preservePositionMs
                    ?: videoPlaybackStateRepository.getByEpisodeId(result.episode.id)?.positionMs
                    ?: 0L
                val navigation = resolveEpisodeNavigation(result.video.id, result.episode.id)
                val playback = buildPlaybackUiState(result.playbackData, result.stream, result.savedPreferences)
                State.Ready(
                    animeId = result.video.id,
                    episodeId = result.episode.id,
                    previousEpisodeId = navigation.previousEpisodeId,
                    nextEpisodeId = navigation.nextEpisodeId,
                    videoTitle = result.video.displayTitle,
                    episodeName = result.episode.name,
                    streamLabel = playback.currentStreamLabel,
                    streamUrl = playback.currentStream.request.url,
                    stream = playback.currentStream,
                    playback = playback,
                    resumePositionMs = resumePositionMs,
                    isSourceSwitching = false,
                )
            }
            is ResolveVideoStream.Result.Error -> {
                if (!showLoading && previousReady != null) {
                    mutableEvents.tryEmit(Event.ShowMessage(result.reason.toMessage()))
                    previousReady.copy(isSourceSwitching = false)
                } else {
                    State.Error(result.reason.toMessage())
                }
            }
        }

        val current = mutableState.value
        if (current is State.Ready) {
            val session = playbackSession?.takeIf { !initial }
                ?: VideoPlaybackSession(current.episodeId)
            session.restore(current.resumePositionMs)
            playbackSession = session
        }
    }

    private suspend fun resolveEpisodeNavigation(animeId: Long, episodeId: Long): EpisodeNavigation {
        val sortedEpisodes = animeEpisodeRepository.getEpisodesByAnimeId(animeId)
            .sortedBy(AnimeEpisode::sourceOrder)
        val currentIndex = sortedEpisodes.indexOfFirst { it.id == episodeId }
        if (currentIndex == -1) return EpisodeNavigation()

        return EpisodeNavigation(
            previousEpisodeId = sortedEpisodes.getOrNull(currentIndex - 1)?.id,
            nextEpisodeId = sortedEpisodes.getOrNull(currentIndex + 1)?.id,
        )
    }

    private suspend fun playEpisode(targetEpisodeId: Long) {
        mutableState.value as? State.Ready ?: return
        applySelectionJob?.cancel()
        savedState[EPISODE_ID_KEY] = targetEpisodeId
        episodeId = targetEpisodeId
        playbackSession = null
        resolvePlayback(initial = true)
    }

    private fun buildPlaybackUiState(
        playbackData: VideoPlaybackData,
        currentStream: VideoStream,
        savedPreferences: AnimePlaybackPreferences,
    ): VideoPlaybackUiState {
        val streamOptions = playbackData.streams
            .filter { it.request.url.isNotBlank() }
            .map { stream ->
                VideoPlaybackOption(
                    key = stream.key.ifBlank { stream.label.ifBlank { stream.request.url } },
                    label = stream.label.ifBlank { stream.request.url },
                )
            }

        val adaptivePreference = when (savedPreferences.playerQualityMode) {
            PlayerQualityMode.AUTO -> VideoAdaptiveQualityPreference.Auto
            PlayerQualityMode.SPECIFIC_HEIGHT -> savedPreferences.playerQualityHeight
                ?.let(VideoAdaptiveQualityPreference::SpecificHeight)
                ?: VideoAdaptiveQualityPreference.Auto
        }

        return VideoPlaybackUiState(
            sourceSelection = playbackData.selection.copy(
                streamKey = currentStream.key.ifBlank { currentStream.label.ifBlank { currentStream.request.url } },
            ),
            preferredSourceQualityKey = savedPreferences.sourceQualityKey,
            currentStream = currentStream,
            currentStreamLabel = currentStream.label.ifBlank { currentStream.request.url },
            streamOptions = streamOptions,
            playbackData = playbackData,
            currentAdaptiveQuality = adaptivePreference,
        )
    }

    private suspend fun persistPlaybackPreferences(
        animeId: Long,
        sourceSelection: VideoPlaybackSelection,
        adaptiveQuality: VideoAdaptiveQualityPreference,
    ) {
        animePlaybackPreferencesRepository.upsert(
            AnimePlaybackPreferences(
                animeId = animeId,
                dubKey = sourceSelection.dubKey,
                streamKey = sourceSelection.streamKey,
                sourceQualityKey = sourceSelection.sourceQualityKey,
                playerQualityMode = adaptiveQuality.toPlayerQualityMode(),
                playerQualityHeight = adaptiveQuality.heightOrNull(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    sealed interface State {
        data object Loading : State

        data class Ready(
            val animeId: Long,
            val episodeId: Long,
            val previousEpisodeId: Long?,
            val nextEpisodeId: Long?,
            val videoTitle: String,
            val episodeName: String,
            val streamLabel: String,
            val streamUrl: String,
            val stream: VideoStream,
            val playback: VideoPlaybackUiState,
            val resumePositionMs: Long,
            val isSourceSwitching: Boolean = false,
        ) : State

        data class Error(val message: String) : State
    }

    sealed interface Event {
        data class ShowMessage(val message: String) : Event
    }

    private fun ResolveVideoStream.Reason.toMessage(): String {
        return when (this) {
            ResolveVideoStream.Reason.VideoNotFound -> "Video not found"
            ResolveVideoStream.Reason.EpisodeNotFound -> "Episode not found"
            ResolveVideoStream.Reason.EpisodeMismatch -> "Episode does not belong to the selected video"
            ResolveVideoStream.Reason.SourceLoadTimeout -> "Video source took too long to load"
            ResolveVideoStream.Reason.SourceNotFound -> "Video source not available"
            ResolveVideoStream.Reason.NoStreams -> "No playable streams returned"
            ResolveVideoStream.Reason.StreamFetchTimeout -> "Timed out while resolving streams"
            is ResolveVideoStream.Reason.StreamFetchFailed -> listOfNotNull(
                cause::class.simpleName,
                cause.message,
            ).joinToString(": ").ifBlank { "Failed to resolve streams" }
        }
    }

    companion object {
        private const val VIDEO_ID_KEY = "video_id"
        private const val EPISODE_ID_KEY = "episode_id"
        private const val INVALID_ID = -1L
    }
}

private data class EpisodeNavigation(
    val previousEpisodeId: Long? = null,
    val nextEpisodeId: Long? = null,
)
