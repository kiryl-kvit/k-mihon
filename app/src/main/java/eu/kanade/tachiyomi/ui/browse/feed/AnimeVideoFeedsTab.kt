package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.FeedListingMode
import eu.kanade.domain.source.model.FilterStateNode
import eu.kanade.domain.source.model.SourceFeed
import eu.kanade.domain.source.model.SourceFeedContentMode
import eu.kanade.domain.source.model.SourceFeedPreset
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.anime.pushSourceAnimeScreen
import eu.kanade.tachiyomi.ui.browse.source.SourceCatalogKind
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mihon.feature.profiles.core.ProfileManager
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.animeVideoFeedsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val profileManager = remember { Injekt.get<ProfileManager>() }
    val activeProfile by profileManager.activeProfile.collectAsState()
    val screenModel = rememberScreenModel(tag = "${SourceCatalogKind.ANIME.name}:video") {
        FeedsScreenModel(SourceCatalogKind.ANIME, SourceFeedContentMode.Video)
    }
    val state by screenModel.state.collectAsState()
    val singleEnabledFeed = state.enabledFeeds.singleOrNull()
    val singleEnabledFeedSource = singleEnabledFeed?.let { screenModel.sourceFor(it.sourceId) }
    val singleEnabledFeedPreset = singleEnabledFeed?.let(screenModel::presetFor)

    return TabContent(
        titleRes = MR.strings.browse_video_feeds,
        tabLabel = if (singleEnabledFeedSource != null && singleEnabledFeedPreset != null) {
            {
                AnimeVideoSingleFeedTabLabel(
                    source = singleEnabledFeedSource,
                    preset = singleEnabledFeedPreset,
                )
            }
        } else {
            null
        },
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_add),
                icon = Icons.Outlined.Add,
                onClick = screenModel::showCreateDialog,
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.browse_manage_video_feeds),
                onClick = screenModel::showManageDialog,
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            AnimeVideoFeedsTabContent(
                activeProfileId = activeProfile?.id,
                state = state,
                screenModel = screenModel,
                navigator = navigator,
                contentPadding = contentPadding,
                snackbarHostState = snackbarHostState,
            )
        },
    )
}

@Composable
private fun AnimeVideoSingleFeedTabLabel(
    source: Source,
    preset: SourceFeedPreset,
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        SourceIcon(
            source = source,
            modifier = Modifier
                .size(18.dp)
                .clip(MaterialTheme.shapes.extraSmall),
        )
        Text(
            text = preset.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun AnimeVideoFeedsTabContent(
    activeProfileId: Long?,
    state: FeedsScreenModel.State,
    screenModel: FeedsScreenModel,
    navigator: Navigator,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    if (!state.sourcesLoaded) {
        LoadingScreen()
        return
    }

    val activeFeed = screenModel.activeFeed()
    val activeSource = activeFeed?.let { screenModel.sourceFor(it.sourceId) }
    val activePreset = activeFeed?.let(screenModel::presetFor)
    val scope = rememberCoroutineScope()
    val getMergedAnime = remember { Injekt.get<tachiyomi.domain.anime.interactor.GetMergedAnime>() }
    val openSourceAnime: (Long) -> Unit = { animeId ->
        scope.launch {
            navigator.pushSourceAnimeScreen(animeId, getMergedAnime)
        }
    }

    if (state.enabledFeeds.isEmpty()) {
        EmptyScreen(
            message = stringResource(MR.strings.browse_video_feeds_empty),
            modifier = Modifier.padding(contentPadding),
        )
    } else if (activeFeed != null && activeSource != null && activePreset != null) {
        val enabledFeeds = state.enabledFeeds
        val contentStateHolder = rememberSaveableStateHolder()
        val chipListState = rememberLazyListState()
        val presetBehaviorKey = activePreset.behaviorKey()

        key(activeProfileId, activeFeed.id, presetBehaviorKey) {
            val timelineModel = rememberAnimeVideoFeedTimelineModel(
                activeProfileId = activeProfileId,
                activeFeedId = activeFeed.id,
                presetBehaviorKey = presetBehaviorKey,
                sourceId = activeSource.id,
                listingQuery = SourceCatalogKind.ANIME.requestQuery(activePreset),
                initialFilterSnapshot = activePreset.filters,
            )
            val timelineState by timelineModel.state.collectAsState()
            val playbackModel = rememberAnimeVideoFeedPlaybackModel(
                activeProfileId = activeProfileId,
                activeFeedId = activeFeed.id,
                presetBehaviorKey = presetBehaviorKey,
            )

            LaunchedEffect(activeProfileId) {
                screenModel.closeDialog()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                PullRefresh(
                    refreshing = timelineState.isRefreshing,
                    enabled = true,
                    onRefresh = { timelineModel.refresh(manual = true) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimeVideoFeedContentState(
                        stateHolder = contentStateHolder,
                        activeProfileId = activeProfileId,
                        activeFeedId = activeFeed.id,
                        presetBehaviorKey = presetBehaviorKey,
                    ) {
                        AnimeVideoFeedBrowseContent(
                            timelineModel = timelineModel,
                            playbackModel = playbackModel,
                            snackbarHostState = snackbarHostState,
                            contentPadding = contentPadding,
                            onAnimeClick = { openSourceAnime(it.id) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                AnimeVideoFeedSelectorBar(
                    feeds = enabledFeeds,
                    selectedFeedId = activeFeed.id,
                    chipListState = chipListState,
                    screenModel = screenModel,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        FeedsScreenModel.Dialog.SelectSource -> {
            AnimeVideoFeedSourcePickerDialog(
                sources = state.sources,
                onDismissRequest = screenModel::closeDialog,
                onSelectSource = screenModel::selectSource,
            )
        }
        is FeedsScreenModel.Dialog.SelectPreset -> {
            val source = screenModel.sourceFor(dialog.sourceId)
            if (source != null) {
                AnimeVideoFeedPresetPickerDialog(
                    source = source,
                    presets = screenModel.presetsFor(source),
                    onDismissRequest = screenModel::closeDialog,
                    onSelectPreset = { preset -> screenModel.createFeed(source.id, preset.id) },
                )
            }
        }
        FeedsScreenModel.Dialog.ManageFeeds -> {
            AnimeVideoManageFeedsDialog(
                state = state,
                screenModel = screenModel,
                onDismissRequest = screenModel::closeDialog,
            )
        }
        null -> Unit
    }
}

@Composable
private fun AnimeVideoFeedContentState(
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
    content: @Composable () -> Unit,
) {
    stateHolder.SaveableStateProvider(
        key = "anime-video-feed-content-${activeProfileId ?: "none"}-$activeFeedId-$presetBehaviorKey",
    ) {
        content()
    }
}

@Composable
private fun AnimeVideoFeedSelectorBar(
    feeds: List<SourceFeed>,
    selectedFeedId: String,
    chipListState: LazyListState,
    screenModel: FeedsScreenModel,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(selectedFeedId, feeds) {
        val selectedIndex = feeds.indexOfFirst { it.id == selectedFeedId }
        if (selectedIndex < 0) return@LaunchedEffect

        val visibleItemIndexes = snapshotFlow {
            chipListState.layoutInfo.visibleItemsInfo.map { it.index }
        }
            .filter { it.isNotEmpty() }
            .first()

        if (selectedIndex !in visibleItemIndexes) {
            chipListState.scrollToItem(selectedIndex)
        }
    }

    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.72f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.small),
    ) {
        LazyRow(
            state = chipListState,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(feeds.size, key = { feeds[it].id }) { index ->
                val feed = feeds[index]
                val source = screenModel.sourceFor(feed.sourceId) ?: return@items
                val preset = screenModel.presetFor(feed) ?: return@items
                AnimeVideoFeedChip(
                    source = source,
                    preset = preset,
                    selected = selectedFeedId == feed.id,
                    onClick = { screenModel.selectFeed(feed.id) },
                )
            }
        }
    }
}

@Composable
private fun AnimeVideoFeedChip(
    source: Source,
    preset: SourceFeedPreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.74f)
            },
            labelColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        ),
        border = null,
        leadingIcon = {
            SourceIcon(
                source = source,
                modifier = Modifier
                    .size(FilterChipDefaults.IconSize)
                    .clip(MaterialTheme.shapes.extraSmall),
            )
        },
        trailingIcon = {
            val icon = when (preset.listingMode) {
                FeedListingMode.Popular -> Icons.Outlined.Favorite
                FeedListingMode.Latest -> Icons.Outlined.NewReleases
                FeedListingMode.Search -> null
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        label = {
            Text(
                text = preset.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}

@Composable
private fun AnimeVideoFeedSourcePickerDialog(
    sources: List<Source>,
    onDismissRequest: () -> Unit,
    onSelectSource: (Source) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        ScrollbarLazyColumn(contentPadding = topSmallPaddingValues) {
            items(items = sources, key = { "anime-video-feed-source-${it.id}" }) { source ->
                BaseSourceItem(
                    source = source,
                    modifier = Modifier.animateItemFastScroll(),
                    onClickItem = { onSelectSource(source) },
                )
            }
        }
    }
}

@Composable
private fun AnimeVideoFeedPresetPickerDialog(
    source: Source,
    presets: List<SourceFeedPreset>,
    onDismissRequest: () -> Unit,
    onSelectPreset: (SourceFeedPreset) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        Column(modifier = Modifier.padding(vertical = MaterialTheme.padding.small)) {
            Text(
                text = source.name,
                modifier = Modifier.padding(
                    horizontal = MaterialTheme.padding.medium,
                    vertical = MaterialTheme.padding.small,
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            presets.forEach { preset ->
                BaseSourceItem(
                    source = source,
                    showLanguageInContent = false,
                    onClickItem = { onSelectPreset(preset) },
                    content = { _, _ ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AnimeVideoManageFeedsDialog(
    state: FeedsScreenModel.State,
    screenModel: FeedsScreenModel,
    onDismissRequest: () -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(listState, topSmallPaddingValues) { from, to ->
        screenModel.reorderFeed(from.key as String, to.key as String)
    }

    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        ScrollbarLazyColumn(
            state = listState,
            contentPadding = topSmallPaddingValues,
        ) {
            items(items = state.feeds, key = { it.id }) { feed ->
                val source = screenModel.sourceFor(feed.sourceId) ?: return@items
                val preset = screenModel.presetFor(feed) ?: return@items
                ReorderableItem(reorderableState, feed.id, enabled = state.feeds.size > 1) {
                    AnimeVideoManageFeedItem(
                        feed = feed,
                        source = source,
                        preset = preset,
                        onToggleEnabled = { screenModel.toggleFeed(feed.id, it) },
                        onDelete = { screenModel.removeFeed(feed.id) },
                        modifier = Modifier.animateItemFastScroll(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.AnimeVideoManageFeedItem(
    feed: SourceFeed,
    source: Source,
    preset: SourceFeedPreset,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                SourceIcon(
                    source = source,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                )
                Text(
                    text = source.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = preset.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(
            checked = feed.enabled,
            onCheckedChange = onToggleEnabled,
        )
        Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
            )
        }
        Icon(
            imageVector = Icons.Outlined.DragHandle,
            contentDescription = null,
            modifier = Modifier
                .draggableHandle()
                .padding(MaterialTheme.padding.small),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rememberAnimeVideoFeedTimelineModel(
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
    sourceId: Long,
    listingQuery: String?,
    initialFilterSnapshot: List<FilterStateNode>,
): AnimeChronologicalFeedScreenModel {
    val profileKey = activeProfileId?.toString() ?: "none"
    return object : Screen {
        override val key: ScreenKey = "anime-video-feed-timeline-model-$profileKey-$activeFeedId-$presetBehaviorKey"

        @Composable
        override fun Content() {
            error("Not used")
        }
    }.rememberScreenModel(tag = "anime-video-timeline:$profileKey:$activeFeedId:$presetBehaviorKey") {
        AnimeChronologicalFeedScreenModel(
            feedId = activeFeedId,
            sourceId = sourceId,
            listingQuery = listingQuery,
            initialFilterSnapshot = initialFilterSnapshot,
        )
    }
}

@Composable
private fun rememberAnimeVideoFeedPlaybackModel(
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
): AnimeVideoFeedPlaybackScreenModel {
    val profileKey = activeProfileId?.toString() ?: "none"
    return object : Screen {
        override val key: ScreenKey = "anime-video-feed-playback-model-$profileKey-$activeFeedId-$presetBehaviorKey"

        @Composable
        override fun Content() {
            error("Not used")
        }
    }.rememberScreenModel(tag = "anime-video-playback:$profileKey:$activeFeedId:$presetBehaviorKey") {
        AnimeVideoFeedPlaybackScreenModel()
    }
}

private fun SourceFeedPreset.behaviorKey(): String {
    return buildString {
        append(sourceId)
        append(':')
        append(listingMode.name)
        append(':')
        append(chronological)
        append(':')
        append(query.orEmpty())
        append(':')
        append(filters.hashCode())
    }
}
