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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.source.model.FeedListingMode
import eu.kanade.domain.source.model.SourceFeed
import eu.kanade.domain.source.model.SourceFeedPreset
import eu.kanade.domain.source.model.applySnapshot
import eu.kanade.domain.source.model.snapshot
import eu.kanade.presentation.anime.AnimeBrowseSourceContent
import eu.kanade.presentation.anime.AnimeMergeTargetPickerDialog
import eu.kanade.presentation.anime.DuplicateAnimeDialog
import eu.kanade.presentation.browse.components.BaseSourceItem
import eu.kanade.presentation.browse.components.BrowseAnimePreviewSheet
import eu.kanade.presentation.browse.components.BrowseLibraryActionDialog
import eu.kanade.presentation.browse.components.BrowseMergeEditorDialog
import eu.kanade.presentation.browse.components.SourceIcon
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.source.ConfigurableAnimeSource
import eu.kanade.tachiyomi.source.online.AnimeHttpSource
import eu.kanade.tachiyomi.source.resolveFilterList
import eu.kanade.tachiyomi.ui.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.anime.browse.AnimeBrowseSourceScreenModel
import eu.kanade.tachiyomi.ui.anime.browse.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.anime.browse.BrowseFilterUiState
import eu.kanade.tachiyomi.ui.anime.pushSourceAnimeScreen
import eu.kanade.tachiyomi.ui.browse.source.SourceCatalogKind
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mihon.feature.migration.dialog.MigrateAnimeDialog
import mihon.feature.profiles.core.ProfileManager
import mihon.presentation.core.util.collectAsLazyPagingItems
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.animeFeedsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val profileManager = remember { Injekt.get<ProfileManager>() }
    val activeProfile by profileManager.activeProfile.collectAsState()
    val screenModel = rememberScreenModel(tag = SourceCatalogKind.ANIME.name) {
        FeedsScreenModel(SourceCatalogKind.ANIME)
    }
    val state by screenModel.state.collectAsState()
    val singleEnabledFeed = state.enabledFeeds.singleOrNull()
    val singleEnabledFeedSource = singleEnabledFeed?.let { screenModel.sourceFor(it.sourceId) }
    val singleEnabledFeedPreset = singleEnabledFeed?.let(screenModel::presetFor)

    return TabContent(
        titleRes = MR.strings.browse_feeds,
        tabLabel = if (singleEnabledFeedSource != null && singleEnabledFeedPreset != null) {
            {
                AnimeSingleFeedTabLabel(
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
                title = stringResource(MR.strings.browse_manage_feeds),
                onClick = screenModel::showManageDialog,
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            AnimeFeedsTabContent(
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
private fun AnimeSingleFeedTabLabel(
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
private fun AnimeFeedsTabContent(
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val getMergedAnime = remember { Injekt.get<tachiyomi.domain.anime.interactor.GetMergedAnime>() }
    val openSourceAnime: (Long) -> Unit = { animeId ->
        scope.launch {
            navigator.pushSourceAnimeScreen(animeId, getMergedAnime)
        }
    }

    if (state.enabledFeeds.isEmpty()) {
        EmptyScreen(
            message = stringResource(MR.strings.browse_feeds_empty),
            modifier = Modifier.padding(contentPadding),
        )
    } else if (activeFeed != null && activeSource != null && activePreset != null) {
        val enabledFeeds = state.enabledFeeds
        val browseContentStateHolder = rememberSaveableStateHolder()
        val chipListState = rememberLazyListState()
        val presetBehaviorKey = activePreset.behaviorKey()
        val activeIndex = remember(enabledFeeds, activeFeed.id) {
            enabledFeeds.indexOfFirst { it.id == activeFeed.id }
        }
        val hasPreviousFeed = activeIndex > 0
        val hasNextFeed = activeIndex in 0 until enabledFeeds.lastIndex

        Column {
            key(activeProfileId, activeFeed.id, presetBehaviorKey) {
                val browseModel = rememberActiveAnimeFeedScreenModel(
                    activeProfileId = activeProfileId,
                    activeFeedId = activeFeed.id,
                    presetBehaviorKey = presetBehaviorKey,
                    sourceId = activeSource.id,
                    listingQuery = SourceCatalogKind.ANIME.requestQuery(activePreset),
                    initialFilterSnapshot = activePreset.filters,
                )
                val browseModelState by browseModel.state.collectAsState()
                val chronologicalFeedModel = if (activePreset.chronological) {
                    rememberAnimeChronologicalFeedScreenModel(
                        activeProfileId = activeProfileId,
                        activeFeedId = activeFeed.id,
                        presetBehaviorKey = presetBehaviorKey,
                        sourceId = activeSource.id,
                        listingQuery = SourceCatalogKind.ANIME.requestQuery(activePreset),
                        initialFilterSnapshot = activePreset.filters,
                    )
                } else {
                    null
                }
                val chronologicalFeedState = chronologicalFeedModel?.state?.collectAsState()?.value

                LaunchedEffect(activeProfileId) {
                    screenModel.closeDialog()
                    browseModel.dismissDialog()
                }

                LaunchedEffect(activeFeed.id, activePreset.id, presetBehaviorKey) {
                    if (browseModelState.isWaitingForInitialFilterLoad) return@LaunchedEffect

                    val currentFilters = browseModelState.filters.snapshot()
                    val shouldApplyPreset = when (activePreset.listingMode) {
                        FeedListingMode.Popular ->
                            browseModelState.listing != AnimeBrowseSourceScreenModel.Listing.Popular
                        FeedListingMode.Latest ->
                            browseModelState.listing != AnimeBrowseSourceScreenModel.Listing.Latest
                        FeedListingMode.Search -> {
                            val listing = browseModelState.listing as? AnimeBrowseSourceScreenModel.Listing.Search
                            listing?.query != activePreset.query || currentFilters != activePreset.filters
                        }
                    }

                    if (!shouldApplyPreset) return@LaunchedEffect

                    when (activePreset.listingMode) {
                        FeedListingMode.Popular -> {
                            browseModel.resetFilters()
                            browseModel.setListing(AnimeBrowseSourceScreenModel.Listing.Popular)
                        }
                        FeedListingMode.Latest -> {
                            browseModel.resetFilters()
                            browseModel.setListing(AnimeBrowseSourceScreenModel.Listing.Latest)
                        }
                        FeedListingMode.Search -> {
                            val source = browseModel.source ?: return@LaunchedEffect
                            val filters = source.resolveFilterList().applySnapshot(activePreset.filters)
                            browseModel.setFilters(filters)
                            browseModel.search(
                                query = activePreset.query,
                                filters = filters,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    val feedContentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())
                    AnimeFeedBrowseContent(
                        stateHolder = browseContentStateHolder,
                        activeProfileId = activeProfileId,
                        activeFeedId = activeFeed.id,
                        presetBehaviorKey = presetBehaviorKey,
                    ) {
                        val httpSource = browseModel.source as? AnimeHttpSource
                        val configurableSource = browseModel.source as? ConfigurableAnimeSource
                        val onWebViewClick = httpSource?.let {
                            {
                                navigator.push(
                                    WebViewScreen(
                                        url = it.baseUrl,
                                        initialTitle = it.name,
                                        headers = it.headers.toMultimap().mapValues { values ->
                                            values.value.firstOrNull().orEmpty()
                                        },
                                    ),
                                )
                            }
                        }
                        val onSettingsClick = configurableSource?.let {
                            { navigator.push(AnimeSourcePreferencesScreen(activeSource.id, activeSource.name)) }
                        }

                        if (chronologicalFeedModel != null) {
                            PullRefresh(
                                refreshing = chronologicalFeedState?.isRefreshing == true,
                                enabled = true,
                                onRefresh = { chronologicalFeedModel.refresh(manual = true) },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                AnimeChronologicalFeedBrowseContent(
                                    screenModel = chronologicalFeedModel,
                                    columns = browseModel.getColumnsPreference(LocalConfiguration.current.orientation),
                                    displayMode = browseModel.displayMode,
                                    snackbarHostState = snackbarHostState,
                                    contentPadding = feedContentPadding,
                                    onAnimeClick = { openSourceAnime(it.id) },
                                    onAnimeLongClick = { anime ->
                                        scope.launch {
                                            if (browseModel.onAnimeLongClick(anime)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                    },
                                    onWebViewClick = onWebViewClick,
                                    onSettingsClick = onSettingsClick,
                                )
                            }
                        } else {
                            val animeList = browseModel.animePagerFlow.collectAsLazyPagingItems()
                            val isRefreshing = when {
                                browseModelState.isWaitingForInitialFilterLoad -> {
                                    browseModelState.filterState is BrowseFilterUiState.Loading
                                }
                                else -> animeList.itemCount > 0 && animeList.loadState.refresh is LoadState.Loading
                            }

                            PullRefresh(
                                refreshing = isRefreshing,
                                enabled = true,
                                onRefresh = {
                                    if (browseModelState.isWaitingForInitialFilterLoad) {
                                        browseModel.retryFilterLoad()
                                    } else {
                                        animeList.refresh()
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                if (browseModelState.isWaitingForInitialFilterLoad) {
                                    when (val filterState = browseModelState.filterState) {
                                        is BrowseFilterUiState.Error -> {
                                            EmptyScreen(
                                                message = filterState.throwable.message
                                                    ?: stringResource(MR.strings.unknown_error),
                                                modifier = Modifier.padding(feedContentPadding),
                                            )
                                        }
                                        else -> LoadingScreen(Modifier.padding(feedContentPadding))
                                    }
                                } else {
                                    AnimeBrowseSourceContent(
                                        animeList = animeList,
                                        columns = browseModel.getColumnsPreference(
                                            LocalConfiguration.current.orientation,
                                        ),
                                        displayMode = browseModel.displayMode,
                                        snackbarHostState = snackbarHostState,
                                        contentPadding = feedContentPadding,
                                        onAnimeClick = { openSourceAnime(it.id) },
                                        onAnimeLongClick = { anime ->
                                            scope.launch {
                                                if (browseModel.onAnimeLongClick(anime)) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                            }
                                        },
                                        onWebViewClick = onWebViewClick,
                                        onSettingsClick = onSettingsClick,
                                    )
                                }
                            }
                        }
                    }
                }

                when (val dialog = browseModelState.dialog) {
                    is AnimeBrowseSourceScreenModel.Dialog.ChangeAnimeCategory -> {
                        ChangeCategoryDialog(
                            initialSelection = dialog.initialSelection,
                            onDismissRequest = browseModel::dismissDialog,
                            onEditCategories = { navigator.push(CategoryScreen()) },
                            onConfirm = { include, _ ->
                                browseModel.changeAnimeFavorite(dialog.anime)
                                browseModel.moveAnimeToCategories(dialog.anime, include)
                            },
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.AnimePreview -> {
                        BrowseAnimePreviewSheet(
                            animeId = dialog.animeId,
                            previewSize = browseModel.animePreviewSizeUi(),
                            onLibraryAction = browseModel::confirmBrowseLibraryAction,
                            onMergeAction = browseModel::showMergeTargetPicker,
                            onOpenAnime = openSourceAnime,
                            onDismissRequest = browseModel::dismissDialog,
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.RemoveAnime -> {
                        RemoveAnimeDialog(
                            onDismissRequest = browseModel::dismissDialog,
                            onConfirm = { browseModel.changeAnimeFavorite(dialog.anime) },
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser -> {
                        BrowseLibraryActionDialog(
                            mangaTitle = dialog.anime.displayTitle,
                            favorite = dialog.anime.favorite,
                            onDismissRequest = browseModel::dismissDialog,
                            onLibraryAction = {
                                browseModel.dismissDialog()
                                browseModel.confirmBrowseLibraryAction(dialog.anime)
                            },
                            onMergeIntoLibrary = { browseModel.showMergeTargetPicker(dialog.anime) },
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.DuplicateAnime -> {
                        DuplicateAnimeDialog(
                            duplicates = dialog.duplicates,
                            onDismissRequest = browseModel::dismissDialog,
                            onConfirm = { browseModel.addFavorite(dialog.anime) },
                            onOpenAnime = { navigator.push(AnimeScreen(it.id)) },
                            onMigrate = { browseModel.showMigrateDialog(current = it, target = dialog.anime) },
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.Migrate -> {
                        MigrateAnimeDialog(
                            current = dialog.current,
                            target = dialog.target,
                            onClickTitle = { navigator.push(AnimeScreen(dialog.target.id)) },
                            onDismissRequest = browseModel::dismissDialog,
                            onComplete = {
                                browseModel.dismissDialog()
                                navigator.push(AnimeScreen(dialog.target.id))
                            },
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.SelectMergeTarget -> {
                        AnimeMergeTargetPickerDialog(
                            title = stringResource(MR.strings.action_merge_into_library),
                            query = dialog.query,
                            visibleTargets = dialog.visibleTargets,
                            onDismissRequest = browseModel::dismissDialog,
                            onQueryChange = browseModel::updateMergeTargetQuery,
                            onSelectTarget = browseModel::openMergeEditor,
                        )
                    }
                    is AnimeBrowseSourceScreenModel.Dialog.EditMerge -> {
                        BrowseMergeEditorDialog(
                            entries = dialog.entries,
                            targetId = dialog.targetId,
                            targetLocked = dialog.targetLocked,
                            removedIds = dialog.removedIds,
                            libraryRemovalIds = dialog.libraryRemovalIds,
                            confirmEnabled = dialog.enabled,
                            onDismissRequest = browseModel::dismissDialog,
                            onMove = browseModel::moveMergeEntry,
                            onSelectTarget = browseModel::setMergeTarget,
                            onToggleRemove = browseModel::toggleMergeEntryRemoval,
                            onToggleLibraryRemove = browseModel::toggleMergeEntryLibraryRemoval,
                            onConfirm = browseModel::confirmBrowseMerge,
                        )
                    }
                    else -> Unit
                }
            }

            if (enabledFeeds.size > 1) {
                AnimeFeedNavigationBar(
                    feeds = enabledFeeds,
                    selectedFeedId = activeFeed.id,
                    chipListState = chipListState,
                    screenModel = screenModel,
                    canGoPrevious = hasPreviousFeed,
                    canGoNext = hasNextFeed,
                    onPreviousClick = {
                        enabledFeeds.getOrNull(activeIndex - 1)?.let { screenModel.selectFeed(it.id) }
                    },
                    onNextClick = {
                        enabledFeeds.getOrNull(activeIndex + 1)?.let { screenModel.selectFeed(it.id) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    when (val dialog = state.dialog) {
        FeedsScreenModel.Dialog.SelectSource -> {
            AnimeFeedSourcePickerDialog(
                sources = state.sources,
                onDismissRequest = screenModel::closeDialog,
                onSelectSource = screenModel::selectSource,
            )
        }
        is FeedsScreenModel.Dialog.SelectPreset -> {
            val source = screenModel.sourceFor(dialog.sourceId)
            if (source != null) {
                AnimeFeedPresetPickerDialog(
                    source = source,
                    presets = screenModel.presetsFor(source),
                    onDismissRequest = screenModel::closeDialog,
                    onSelectPreset = { preset -> screenModel.createFeed(source.id, preset.id) },
                )
            }
        }
        FeedsScreenModel.Dialog.ManageFeeds -> {
            AnimeManageFeedsDialog(
                state = state,
                screenModel = screenModel,
                onDismissRequest = screenModel::closeDialog,
            )
        }
        null -> Unit
    }
}

@Composable
private fun RemoveAnimeDialog(
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            ) {
                Text(text = stringResource(MR.strings.action_remove))
            }
        },
        title = {
            Text(text = stringResource(MR.strings.action_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_from_library))
        },
    )
}

@Composable
private fun AnimeFeedBrowseContent(
    stateHolder: androidx.compose.runtime.saveable.SaveableStateHolder,
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
    content: @Composable () -> Unit,
) {
    stateHolder.SaveableStateProvider(
        key = "anime-feed-content-${activeProfileId ?: "none"}-$activeFeedId-$presetBehaviorKey",
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun rememberActiveAnimeFeedScreenModel(
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
    sourceId: Long,
    listingQuery: String?,
    initialFilterSnapshot: List<eu.kanade.domain.source.model.FilterStateNode>,
): AnimeBrowseSourceScreenModel {
    val profileKey = activeProfileId?.toString() ?: "none"
    return object : Screen {
        override val key: ScreenKey = "anime-feed-screen-model-$profileKey-$activeFeedId-$presetBehaviorKey"

        @Composable
        override fun Content() {
            error("Not used")
        }
    }.rememberScreenModel(tag = "anime:$profileKey:$activeFeedId:$presetBehaviorKey") {
        AnimeBrowseSourceScreenModel(
            sourceId = sourceId,
            listingQuery = listingQuery,
            initialFilterSnapshot = initialFilterSnapshot,
        )
    }
}

@Composable
private fun rememberAnimeChronologicalFeedScreenModel(
    activeProfileId: Long?,
    activeFeedId: String,
    presetBehaviorKey: String,
    sourceId: Long,
    listingQuery: String?,
    initialFilterSnapshot: List<eu.kanade.domain.source.model.FilterStateNode>,
): AnimeChronologicalFeedScreenModel {
    val profileKey = activeProfileId?.toString() ?: "none"
    return object : Screen {
        override val key: ScreenKey =
            "anime-chronological-feed-screen-model-$profileKey-$activeFeedId-$presetBehaviorKey"

        @Composable
        override fun Content() {
            error("Not used")
        }
    }.rememberScreenModel(tag = "anime-chronological:$profileKey:$activeFeedId:$presetBehaviorKey") {
        AnimeChronologicalFeedScreenModel(
            feedId = activeFeedId,
            sourceId = sourceId,
            listingQuery = listingQuery,
            initialFilterSnapshot = initialFilterSnapshot,
        )
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

@Composable
private fun AnimeFeedNavigationBar(
    feeds: List<SourceFeed>,
    selectedFeedId: String,
    chipListState: LazyListState,
    screenModel: FeedsScreenModel,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
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

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(bottom = MaterialTheme.padding.small),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    top = MaterialTheme.padding.small,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPreviousClick, enabled = canGoPrevious) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(MR.strings.transition_previous),
                )
            }
            LazyRow(
                modifier = Modifier.weight(1f),
                state = chipListState,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(feeds.size, key = { feeds[it].id }) { index ->
                    val feed = feeds[index]
                    val source = screenModel.sourceFor(feed.sourceId) ?: return@items
                    val preset = screenModel.presetFor(feed) ?: return@items
                    AnimeFeedChip(
                        source = source,
                        preset = preset,
                        selected = selectedFeedId == feed.id,
                        onClick = { screenModel.selectFeed(feed.id) },
                    )
                }
            }
            IconButton(onClick = onNextClick, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = stringResource(MR.strings.transition_next),
                )
            }
        }
    }
}

@Composable
private fun AnimeFeedChip(
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
                MaterialTheme.colorScheme.surfaceContainerHigh
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
private fun AnimeFeedSourcePickerDialog(
    sources: List<Source>,
    onDismissRequest: () -> Unit,
    onSelectSource: (Source) -> Unit,
) {
    AdaptiveSheet(onDismissRequest = onDismissRequest) {
        ScrollbarLazyColumn(contentPadding = topSmallPaddingValues) {
            items(items = sources, key = { "anime-feed-source-${it.id}" }) { source ->
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
private fun AnimeFeedPresetPickerDialog(
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
                AnimeFeedPresetItem(
                    source = source,
                    preset = preset,
                    onClick = { onSelectPreset(preset) },
                )
            }
        }
    }
}

@Composable
private fun AnimeFeedPresetItem(
    source: Source,
    preset: SourceFeedPreset,
    onClick: () -> Unit,
) {
    BaseSourceItem(
        source = source,
        showLanguageInContent = false,
        onClickItem = onClick,
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

@Composable
private fun AnimeManageFeedsDialog(
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
                    AnimeManageFeedItem(
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
private fun ReorderableCollectionItemScope.AnimeManageFeedItem(
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
