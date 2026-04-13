package eu.kanade.tachiyomi.ui.anime.browse.extension

import android.content.Context
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.extension.interactor.GetAnimeExtensionSources
import eu.kanade.domain.extension.interactor.AnimeExtensionSourceItem
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.domain.source.interactor.ToggleAnimeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionDetailsScreenModel(
    pkgName: String,
    context: Context,
    private val extensionManager: ExtensionManager = Injekt.get(),
    private val getVideoExtensionSources: GetAnimeExtensionSources = Injekt.get(),
    private val toggleAnimeSource: ToggleAnimeSource = Injekt.get(),
    private val toggleIncognito: ToggleIncognito = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<AnimeExtensionDetailsScreenModel.State>(State()) {

    private val _events: Channel<AnimeExtensionDetailsEvent> = Channel()
    val events: Flow<AnimeExtensionDetailsEvent> = _events.receiveAsFlow()

    init {
        screenModelScope.launch {
            launch {
                extensionManager.installedExtensionsFlow
                    .map { extensions ->
                        extensions
                            .filterIsInstance<Extension.InstalledAnime>()
                            .firstOrNull { extension -> extension.pkgName == pkgName }
                    }
                    .collectLatest { extension ->
                        if (extension == null) {
                            _events.send(AnimeExtensionDetailsEvent.Uninstalled)
                            return@collectLatest
                        }
                        mutableState.update { state ->
                            state.copy(extension = extension)
                        }
                    }
            }
            launch {
                state.collectLatest { state ->
                    if (state.extension == null) return@collectLatest
                    getVideoExtensionSources.subscribe(state.extension)
                        .map {
                            it.sortedWith(
                                compareBy(
                                    { !it.enabled },
                                    { item ->
                                        item.source.name.takeIf { item.labelAsName }
                                            ?: LocaleHelper.getSourceDisplayName(item.source.lang, context).lowercase()
                                    },
                                ),
                            )
                        }
                        .catch { throwable ->
                            logcat(LogPriority.ERROR, throwable)
                            mutableState.update { it.copy(_sources = persistentListOf()) }
                        }
                        .collectLatest { sources ->
                            mutableState.update { it.copy(_sources = sources.toImmutableList()) }
                        }
                }
            }
            launch {
                preferences.incognitoExtensions
                    .changes()
                    .map { pkgName in it }
                    .distinctUntilChanged()
                    .collectLatest { isIncognito ->
                        mutableState.update { it.copy(isIncognito = isIncognito) }
                    }
            }
        }
    }

    fun uninstallExtension() {
        val extension = state.value.extension ?: return
        extensionManager.uninstallExtension(extension)
    }

    fun toggleSource(sourceId: Long) {
        toggleAnimeSource.await(sourceId)
    }

    fun toggleSources(enable: Boolean) {
        state.value.extension?.sources
            ?.map { it.id }
            ?.let { toggleAnimeSource.await(it, enable) }
    }

    fun toggleIncognito(enable: Boolean) {
        state.value.extension?.pkgName?.let { packageName ->
            toggleIncognito.await(packageName, enable)
        }
    }

    @Immutable
    data class State(
        val extension: Extension.InstalledAnime? = null,
        val isIncognito: Boolean = false,
        private val _sources: ImmutableList<AnimeExtensionSourceItem>? = null,
    ) {
        val sources: ImmutableList<AnimeExtensionSourceItem>
            get() = _sources ?: persistentListOf()

        val isLoading: Boolean
            get() = extension == null || _sources == null
    }
}

sealed interface AnimeExtensionDetailsEvent {
    data object Uninstalled : AnimeExtensionDetailsEvent
}
