package eu.kanade.tachiyomi.ui.anime.browse

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.GetEnabledAnimeSources
import eu.kanade.domain.source.interactor.SourceListState
import eu.kanade.domain.source.interactor.SourceListUiMapper
import eu.kanade.domain.source.interactor.ToggleAnimeSource
import eu.kanade.domain.source.interactor.ToggleAnimeSourcePin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcesScreenModel(
    private val getEnabledAnimeSources: GetEnabledAnimeSources = Injekt.get(),
    private val toggleAnimeSource: ToggleAnimeSource = Injekt.get(),
    private val toggleAnimeSourcePin: ToggleAnimeSourcePin = Injekt.get(),
) : StateScreenModel<AnimeSourcesScreenModel.State>(State()) {

    private val _events = Channel<Event>(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getEnabledAnimeSources.subscribe()
                .catch {
                    logcat(LogPriority.ERROR, it)
                    _events.send(Event.FailedFetchingSources)
                }
                .collectLatest(::collectLatestSources)
        }
    }

    private fun collectLatestSources(sources: List<Source>) {
        mutableState.update { state ->
            state.copy(listState = SourceListUiMapper.map(sources))
        }
    }

    fun toggleSource(source: Source) {
        toggleAnimeSource.await(source)
    }

    fun togglePin(source: Source) {
        toggleAnimeSourcePin.await(source)
    }

    fun showSourceDialog(source: Source) {
        mutableState.update { it.copy(dialog = Dialog(source)) }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    sealed interface Event {
        data object FailedFetchingSources : Event
    }

    data class Dialog(val source: Source)

    data class State(
        val dialog: Dialog? = null,
        val listState: SourceListState = SourceListState(),
    ) {
        val isLoading get() = listState.isLoading
        val items get() = listState.items
        val isEmpty get() = listState.isEmpty
    }
}
