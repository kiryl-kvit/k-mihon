package eu.kanade.tachiyomi.ui.anime

import androidx.compose.runtime.Immutable
import android.app.Application
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.anime.model.toMangaCover
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.source.service.AnimeSourceManager
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeLibraryScreenModel(
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val animeEpisodeRepository: AnimeEpisodeRepository = Injekt.get(),
    private val animePlaybackStateRepository: AnimePlaybackStateRepository = Injekt.get(),
    private val animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val application: Application = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State.Loading) {

    init {
        screenModelScope.launchIO {
            combine(
                animeRepository.getFavoritesAsFlow(),
                animeHistoryRepository.getLastHistoryAsFlow(),
            ) { favorites, lastHistory ->
                favorites to lastHistory
            }
                .flatMapLatest { (favorites, lastHistory) ->
                    val animeIds = favorites.map { it.id }
                    if (animeIds.isEmpty()) {
                        return@flatMapLatest flowOf(
                            State.Success(
                                animes = emptyList<AnimeLibraryItem>().toImmutableList(),
                                continueWatching = lastHistory?.toContinueWatchingItem(),
                            ),
                        )
                    }

                    combine(
                        animeEpisodeRepository.getEpisodesByAnimeIdsAsFlow(animeIds),
                        combine(animeIds.map(animePlaybackStateRepository::getByAnimeIdAsFlow)) { playbackLists ->
                            playbackLists.flatMap { it }
                        },
                    ) { episodes, playbackStates ->
                        buildState(
                            favorites = favorites,
                            lastHistory = lastHistory,
                            episodes = episodes,
                            playbackStates = playbackStates,
                        )
                    }
                }
                .catch { e ->
                    logcat(LogPriority.ERROR, e)
                    mutableState.value = State.Error(withUIContext { application.stringResource(tachiyomi.i18n.MR.strings.unknown_error) })
                }
                .collectLatest { mutableState.value = it }
        }
    }

    private fun buildState(
        favorites: List<AnimeTitle>,
        lastHistory: AnimeHistoryWithRelations?,
        episodes: List<AnimeEpisode>,
        playbackStates: List<AnimePlaybackState>,
    ): State.Success {
        val episodesByAnimeId = episodes.groupBy { it.animeId }
        val playbackStateByEpisodeId = playbackStates.associateBy { it.episodeId }

        return State.Success(
            animes = favorites
                .sortedWith(compareByDescending<AnimeTitle> { it.favoriteModifiedAt ?: it.dateAdded }
                    .thenBy { it.displayTitle.lowercase() })
                .map { anime ->
                    val animeEpisodes = episodesByAnimeId[anime.id].orEmpty()
                    val unwatchedCount = animeEpisodes.count { !it.completed }
                    val inProgressPlayback = animeEpisodes
                        .asSequence()
                        .mapNotNull { episode -> playbackStateByEpisodeId[episode.id] }
                        .filter { !it.completed && it.positionMs > 0L && it.durationMs > 0L }
                        .maxByOrNull { it.lastWatchedAt }
                    val primaryEpisode = inProgressPlayback
                        ?.let { playbackState ->
                            animeEpisodes.firstOrNull { it.id == playbackState.episodeId }
                        }
                        ?: animeEpisodes.firstOrNull { !it.completed }
                        ?: animeEpisodes.firstOrNull()

                    AnimeLibraryItem(
                        animeId = anime.id,
                        title = anime.displayTitle,
                        coverData = anime.toMangaCover(),
                        sourceName = animeSourceManager.get(anime.source)?.name,
                        sourceId = anime.source,
                        primaryEpisodeId = primaryEpisode?.id,
                        unwatchedCount = unwatchedCount,
                        hasInProgress = inProgressPlayback != null,
                        progressFraction = inProgressPlayback?.progressFraction(),
                    )
                }
                .toImmutableList(),
            continueWatching = lastHistory?.toContinueWatchingItem(),
        )
    }

    @Immutable
    data class AnimeLibraryItem(
        val animeId: Long,
        val title: String,
        val coverData: tachiyomi.domain.manga.model.MangaCover,
        val sourceName: String?,
        val sourceId: Long,
        val primaryEpisodeId: Long?,
        val unwatchedCount: Int,
        val hasInProgress: Boolean,
        val progressFraction: Float?,
    )

    @Immutable
    data class ContinueWatchingItem(
        val animeId: Long,
        val episodeId: Long,
        val title: String,
        val episodeName: String,
        val coverData: tachiyomi.domain.manga.model.MangaCover,
        val watchedAt: Long,
    )

    sealed interface State {
        data object Loading : State

        data class Error(val message: String) : State

        @Immutable
        data class Success(
            val animes: ImmutableList<AnimeLibraryItem>,
            val continueWatching: ContinueWatchingItem?,
        ) : State
    }
}

private fun AnimeHistoryWithRelations.toContinueWatchingItem(): AnimeLibraryScreenModel.ContinueWatchingItem {
    return AnimeLibraryScreenModel.ContinueWatchingItem(
        animeId = animeId,
        episodeId = episodeId,
        title = title,
        episodeName = episodeName,
        coverData = coverData,
        watchedAt = watchedAt?.time ?: System.currentTimeMillis(),
    )
}

private fun AnimePlaybackState.progressFraction(): Float {
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
