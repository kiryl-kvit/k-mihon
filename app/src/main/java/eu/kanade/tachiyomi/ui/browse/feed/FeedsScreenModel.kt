package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.interactor.GetEnabledSources
import eu.kanade.domain.source.model.BUILTIN_LATEST_PRESET_ID
import eu.kanade.domain.source.model.BUILTIN_POPULAR_PRESET_ID
import eu.kanade.domain.source.model.SourceFeed
import eu.kanade.domain.source.model.SourceFeedPreset
import eu.kanade.domain.source.model.latestFeedPreset
import eu.kanade.domain.source.model.popularFeedPreset
import eu.kanade.domain.source.service.BrowseFeedService
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.Source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.UUID

class FeedsScreenModel(
    private val browseFeedService: BrowseFeedService = Injekt.get(),
    private val getEnabledSources: GetEnabledSources = Injekt.get(),
) : StateScreenModel<FeedsScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            getEnabledSources.subscribe().collectLatest { sources ->
                mutableState.update { state ->
                    state.copy(
                        sources = sources
                            .groupBy { it.id }
                            .values
                            .map { entries ->
                                entries.firstOrNull { !it.isUsedLast } ?: entries.first()
                            }
                            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                            .toImmutableList(),
                    )
                }
            }
        }

        screenModelScope.launchIO {
            browseFeedService.state().collectLatest { browseState ->
                mutableState.update { state ->
                    val selectedFeedId = resolveSelectedFeedId(
                        requestedId = browseState.selectedFeedId,
                        feeds = browseState.feeds,
                    )
                    val nextDialog = when {
                        browseState.feeds.isEmpty() && state.dialog == Dialog.ManageFeeds -> null
                        else -> state.dialog
                    }
                    state.copy(
                        presets = browseState.presets.toImmutableList(),
                        feeds = browseState.feeds.toImmutableList(),
                        selectedFeedId = selectedFeedId,
                        dialog = nextDialog,
                    )
                }
            }
        }
    }

    fun showCreateDialog() {
        mutableState.update { it.copy(dialog = Dialog.SelectSource) }
    }

    fun showManageDialog() {
        mutableState.update {
            if (it.feeds.isEmpty()) {
                it.copy(dialog = null)
            } else {
                it.copy(dialog = Dialog.ManageFeeds)
            }
        }
    }

    fun closeDialog() {
        mutableState.update { it.copy(dialog = null) }
    }

    fun selectSource(source: Source) {
        mutableState.update { it.copy(dialog = Dialog.SelectPreset(source.id)) }
    }

    fun selectFeed(feedId: String) {
        browseFeedService.selectFeed(feedId)
        mutableState.update { it.copy(selectedFeedId = feedId) }
    }

    fun createFeed(sourceId: Long, presetId: String) {
        val existing = state.value.feeds.firstOrNull {
            it.sourceId == sourceId && it.presetId == presetId
        }
        if (existing != null) {
            browseFeedService.updateFeed(existing.copy(enabled = true))
            browseFeedService.selectFeed(existing.id)
            closeDialog()
            return
        }

        browseFeedService.createFeed(
            SourceFeed(
                id = UUID.randomUUID().toString(),
                sourceId = sourceId,
                presetId = presetId,
                enabled = true,
            ),
        )
        closeDialog()
    }

    fun toggleFeed(feedId: String, enabled: Boolean) {
        val feed = state.value.feeds.firstOrNull { it.id == feedId } ?: return
        browseFeedService.updateFeed(feed.copy(enabled = enabled))
    }

    fun removeFeed(feedId: String) {
        browseFeedService.removeFeed(feedId)
    }

    fun presetsFor(source: Source): List<SourceFeedPreset> {
        val builtin = buildList {
            add(popularFeedPreset(source.id, "Popular"))
            if (source.supportsLatest) {
                add(latestFeedPreset(source.id, "Latest"))
            }
        }
        val custom = state.value.presets.filter { it.sourceId == source.id }
        return builtin + custom
    }

    fun activeFeed(): SourceFeed? {
        return state.value.feeds.firstOrNull { it.id == state.value.selectedFeedId && it.enabled }
    }

    fun presetFor(feed: SourceFeed): SourceFeedPreset? {
        val source = state.value.sources.firstOrNull { it.id == feed.sourceId } ?: return null
        return when (feed.presetId) {
            BUILTIN_POPULAR_PRESET_ID -> popularFeedPreset(source.id, "Popular")
            BUILTIN_LATEST_PRESET_ID -> latestFeedPreset(source.id, "Latest")
            else -> state.value.presets.firstOrNull { it.id == feed.presetId }
        }
    }

    fun sourceFor(sourceId: Long): Source? {
        return state.value.sources.firstOrNull { it.id == sourceId }
    }

    private fun resolveSelectedFeedId(requestedId: String?, feeds: List<SourceFeed>): String? {
        val enabledFeeds = feeds.filter { it.enabled }
        return when {
            enabledFeeds.isEmpty() -> null
            requestedId != null && enabledFeeds.any { it.id == requestedId } -> requestedId
            else -> enabledFeeds.first().id.also(browseFeedService::selectFeed)
        }
    }

    sealed interface Dialog {
        data object SelectSource : Dialog
        data class SelectPreset(val sourceId: Long) : Dialog
        data object ManageFeeds : Dialog
    }

    @Immutable
    data class State(
        val sources: ImmutableList<Source> = persistentListOf(),
        val presets: ImmutableList<SourceFeedPreset> = persistentListOf(),
        val feeds: ImmutableList<SourceFeed> = persistentListOf(),
        val selectedFeedId: String? = null,
        val dialog: Dialog? = null,
    ) {
        val enabledFeeds: ImmutableList<SourceFeed>
            get() = feeds.filter { it.enabled }.toImmutableList()
    }
}
