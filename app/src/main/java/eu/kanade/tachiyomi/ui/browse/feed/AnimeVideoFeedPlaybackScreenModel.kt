package eu.kanade.tachiyomi.ui.browse.feed

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.ui.video.player.ResolveVideoStream
import eu.kanade.tachiyomi.ui.video.player.VideoPlaybackSession
import eu.kanade.tachiyomi.ui.video.player.VideoStreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.interactor.SyncAnimeWithSource
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import tachiyomi.domain.anime.service.sortedForReading
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeVideoFeedPlaybackScreenModel(
    private val syncAnimeWithSource: SyncAnimeWithSource = Injekt.get(),
    private val animeEpisodeRepository: AnimeEpisodeRepository = Injekt.get(),
    private val animePlaybackStateRepository: AnimePlaybackStateRepository = Injekt.get(),
    private val animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
    private val resolveVideoStream: VideoStreamResolver = Injekt.get(),
) : StateScreenModel<AnimeVideoFeedPlaybackScreenModel.State>(State()) {

    private val playbackSessions = mutableMapOf<Long, VideoPlaybackSession>()
    private val loadJobs = mutableMapOf<Long, Job>()
    private val loadTokens = mutableMapOf<Long, Any>()
    private val persistMutex = Mutex()

    fun load(anime: AnimeTitle, force: Boolean = false) {
        val existing = state.value.items[anime.id]
        if (!force && (existing is ItemState.Loading || existing is ItemState.Ready)) {
            logcat(LogPriority.DEBUG) {
                "AnimeFeed: skipping load for animeId=${anime.id}, already ${existing::class.simpleName}"
            }
            return
        }

        val replacedJob = loadJobs.remove(anime.id)
        if (replacedJob != null) {
            logcat(LogPriority.DEBUG) { "AnimeFeed: replacing in-flight load for animeId=${anime.id}" }
            replacedJob.cancel()
        }
        val token = Any()
        loadTokens[anime.id] = token
        logcat(LogPriority.DEBUG) { "AnimeFeed: starting load for animeId=${anime.id} force=$force" }

        mutableState.update { current ->
            current.copy(items = current.items + (anime.id to ItemState.Loading(anime)))
        }

        val job = screenModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                val nextState = resolveItem(anime, forceSync = force)

                mutableState.update { current ->
                    if (loadTokens[anime.id] !== token) return@update current
                    current.copy(items = current.items + (anime.id to nextState))
                }
                logcat(LogPriority.DEBUG) {
                    "AnimeFeed: load completed for animeId=${anime.id} -> ${nextState::class.simpleName}"
                }
            } catch (e: CancellationException) {
                logcat(LogPriority.DEBUG) { "AnimeFeed: load cancelled for animeId=${anime.id}" }
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.DEBUG, e) { "AnimeFeed: load failed for animeId=${anime.id}" }
                mutableState.update { current ->
                    if (loadTokens[anime.id] !== token) return@update current
                    current.copy(
                        items = current.items + (
                            anime.id to ItemState.Error(anime = anime, failure = Failure.Unexpected(e))
                            ),
                    )
                }
            } finally {
                if (loadTokens[anime.id] === token) {
                    loadTokens.remove(anime.id)
                    loadJobs.remove(anime.id)
                }
            }
        }
        loadJobs[anime.id] = job
        job.start()
    }

    fun retainActiveLoads(animeIds: Set<Long>) {
        val cancelledIds = loadJobs.keys.filterNot(animeIds::contains).toSet()
        if (cancelledIds.isEmpty()) {
            logcat(LogPriority.DEBUG) {
                "AnimeFeed: retainActiveLoads active=$animeIds (nothing to cancel, ${loadJobs.size} in-flight)"
            }
            return
        }

        logcat(LogPriority.DEBUG) { "AnimeFeed: retainActiveLoads active=$animeIds cancelling=$cancelledIds" }
        cancelledIds.forEach { animeId ->
            loadJobs.remove(animeId)?.cancel()
            loadTokens.remove(animeId)
        }

        mutableState.update { current ->
            current.copy(
                items = current.items.filterNot { (animeId, itemState) ->
                    animeId in cancelledIds && itemState is ItemState.Loading
                },
            )
        }
    }

    override fun onDispose() {
        super.onDispose()
        val count = loadJobs.size
        logcat(LogPriority.DEBUG) { "AnimeFeed: onDispose cancelling $count in-flight loads" }
        loadJobs.values.forEach(Job::cancel)
        loadJobs.clear()
        loadTokens.clear()
    }

    fun retry(anime: AnimeTitle) {
        load(anime, force = true)
    }

    fun persistPlayback(episodeId: Long, positionMs: Long, durationMs: Long) {
        val session = playbackSessions.getOrPut(episodeId) { VideoPlaybackSession(episodeId) }
        val snapshot = session.snapshot(positionMs = positionMs, durationMs = durationMs)

        screenModelScope.launchIO {
            withContext(NonCancellable) {
                persistMutex.withLock {
                    animePlaybackStateRepository.upsertAndSyncEpisodeState(snapshot.playbackState)
                    snapshot.historyUpdate?.let { animeHistoryRepository.upsertHistory(it) }
                }
            }
        }
    }

    private suspend fun resolveItem(anime: AnimeTitle, forceSync: Boolean): ItemState {
        var episodes = animeEpisodeRepository.getEpisodesByAnimeId(anime.id)
            .sortedForReading(anime)

        if (forceSync || episodes.isEmpty()) {
            syncAnimeWithSource(anime)
            episodes = animeEpisodeRepository.getEpisodesByAnimeId(anime.id)
                .sortedForReading(anime)
        }

        if (episodes.isEmpty()) {
            return ItemState.Error(anime = anime, failure = Failure.NoEpisode)
        }

        val playbackStateByEpisodeId = animePlaybackStateRepository
            .getByAnimeIdAsFlow(anime.id)
            .first()
            .associateBy(AnimePlaybackState::episodeId)
        val episode = episodes.selectPrimaryEpisode(playbackStateByEpisodeId)
            ?: return ItemState.Error(anime = anime, failure = Failure.NoEpisode)
        val resumePositionMs = playbackStateByEpisodeId[episode.id]
            ?.takeUnless(AnimePlaybackState::completed)
            ?.positionMs
            ?.coerceAtLeast(0L)
            ?: 0L

        val result = resolveVideoStream(
            animeId = anime.id,
            ownerAnimeId = episode.animeId,
            episodeId = episode.id,
            selection = null,
        )

        return when (result) {
            is ResolveVideoStream.Result.Success -> {
                playbackSessions[episode.id] = VideoPlaybackSession(episode.id).also { session ->
                    session.restore(resumePositionMs)
                }
                ItemState.Ready(
                    anime = anime,
                    episode = episode,
                    result = result,
                    resumePositionMs = resumePositionMs,
                )
            }
            is ResolveVideoStream.Result.Error -> ItemState.Error(
                anime = anime,
                failure = Failure.Stream(result.reason),
            )
        }
    }

    data class State(
        val items: Map<Long, ItemState> = emptyMap(),
    )

    sealed interface ItemState {
        val anime: AnimeTitle

        data class Loading(
            override val anime: AnimeTitle,
        ) : ItemState

        data class Ready(
            override val anime: AnimeTitle,
            val episode: AnimeEpisode,
            val result: ResolveVideoStream.Result.Success,
            val resumePositionMs: Long,
        ) : ItemState

        data class Error(
            override val anime: AnimeTitle,
            val failure: Failure,
        ) : ItemState
    }

    sealed interface Failure {
        data object NoEpisode : Failure
        data class Stream(val reason: ResolveVideoStream.Reason) : Failure
        data class Unexpected(val throwable: Throwable) : Failure
    }
}

private fun List<AnimeEpisode>.selectPrimaryEpisode(
    playbackStateByEpisodeId: Map<Long, AnimePlaybackState>,
): AnimeEpisode? {
    val inProgressEpisode = asSequence()
        .mapNotNull { episode ->
            val playbackState = playbackStateByEpisodeId[episode.id] ?: return@mapNotNull null
            if (playbackState.completed || playbackState.positionMs <= 0L) return@mapNotNull null
            episode to playbackState
        }
        .maxByOrNull { (_, playbackState) -> playbackState.lastWatchedAt }
        ?.first
    if (inProgressEpisode != null) return inProgressEpisode

    return firstOrNull { !it.completed } ?: firstOrNull()
}
