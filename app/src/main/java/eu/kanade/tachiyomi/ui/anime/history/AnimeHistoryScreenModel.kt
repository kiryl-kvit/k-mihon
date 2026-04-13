package eu.kanade.tachiyomi.ui.anime.history

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeHistoryScreenModel(
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val animeHistoryRepository: AnimeHistoryRepository = Injekt.get(),
) : StateScreenModel<AnimeHistoryScreenModel.State>(State()) {

    private val _events = Channel<Event>(Channel.UNLIMITED)
    val events: Flow<Event> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            state.map { it.searchQuery }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    animeHistoryRepository.getHistory(query ?: "")
                        .distinctUntilChanged()
                        .catch { error ->
                            logcat(LogPriority.ERROR, error)
                            _events.send(Event.InternalError)
                        }
                        .map { history -> history.toUiModels() }
                }
                .collect { models ->
                    mutableState.update { it.copy(list = models.toImmutableList()) }
                }
        }
    }

    private fun List<AnimeHistoryWithRelations>.toUiModels(): List<AnimeHistoryUiModel> {
        return map { history -> AnimeHistoryUiModel.Item(history) }
            .insertSeparators { before, after ->
                val beforeDate = before?.history?.watchedAt?.time?.toLocalDate()
                val afterDate = after?.history?.watchedAt?.time?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> AnimeHistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun removeFromHistory(history: AnimeHistoryWithRelations) {
        screenModelScope.launchIO {
            animeHistoryRepository.resetHistory(history.id)
        }
    }

    fun removeAllFromHistory(animeId: Long) {
        screenModelScope.launchIO {
            animeHistoryRepository.resetHistoryByAnimeId(animeId)
        }
    }

    fun removeFromHistory(history: AnimeHistoryWithRelations, removeEverything: Boolean) {
        if (removeEverything) {
            removeAllFromHistory(history.animeId)
        } else {
            removeFromHistory(history)
        }
    }

    fun removeAllHistory() {
        screenModelScope.launchIO {
            val result = animeHistoryRepository.deleteAllHistory()
            if (!result) return@launchIO
            _events.send(Event.HistoryCleared)
        }
    }

    sealed interface Event {
        data object InternalError : Event
        data object HistoryCleared : Event
    }

    sealed interface Dialog {
        data class Delete(val history: AnimeHistoryWithRelations) : Dialog
        data object DeleteAll : Dialog
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val list: ImmutableList<AnimeHistoryUiModel>? = null,
        val dialog: Dialog? = null,
    )
}

sealed interface AnimeHistoryUiModel {
    data class Header(val date: java.time.LocalDate) : AnimeHistoryUiModel

    data class Item(
        val history: AnimeHistoryWithRelations,
    ) : AnimeHistoryUiModel
}
