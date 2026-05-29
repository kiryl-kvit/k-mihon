package eu.kanade.tachiyomi.ui.browse.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.domain.anime.model.toMangaCover
import eu.kanade.presentation.browse.components.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.browse.components.InlineAnimeHoverPreview
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.presentation.manga.components.toGridCoverType
import eu.kanade.presentation.manga.components.toListCoverType
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.source.model.SourceItemOrientation
import eu.kanade.tachiyomi.source.model.SAnimeHoverPreview
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import eu.kanade.presentation.manga.components.MangaCover as CoverType

@Composable
fun AnimeChronologicalFeedBrowseContent(
    screenModel: AnimeChronologicalFeedScreenModel,
    columns: GridCells,
    displayMode: LibraryDisplayMode,
    sourceItemOrientation: SourceItemOrientation,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long> = emptyList(),
    onAnimeHover: (AnimeTitle) -> Unit = {},
    onAnimeHoverExit: (AnimeTitle) -> Unit = {},
    onHoverPreviewReset: (Long) -> Unit = {},
    onHoverPreviewEnded: (AnimeTitle) -> Unit = {},
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview? = { null },
    onWebViewClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val state by screenModel.state.collectAsState()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var restoredDisplayMode by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeHoverPreviewAnimeIds, displayMode) {
        snapshotFlow {
            when (displayMode) {
                LibraryDisplayMode.List -> listState.layoutInfo.visibleItemsInfo.map { it.key }
                else -> gridState.layoutInfo.visibleItemsInfo.map { it.key }
            }
        }.collect { visible ->
            activeHoverPreviewAnimeIds
                .filterNot { it in visible }
                .forEach(onHoverPreviewReset)
        }
    }

    val getErrorMessage: (Throwable) -> String = { throwable ->
        with(context) { throwable.formattedMessage }
    }
    val savedAnchor = screenModel.savedAnchorSnapshot()

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.animeIds.isEmpty()) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = getErrorMessage(error),
            actionLabel = context.stringResource(MR.strings.action_retry),
            duration = SnackbarDuration.Indefinite,
        )
        when (result) {
            SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
            SnackbarResult.ActionPerformed -> screenModel.refresh()
        }
    }

    LaunchedEffect(displayMode, state.animeIds, savedAnchor) {
        if (state.animeIds.isEmpty()) return@LaunchedEffect

        val modeKey = displayMode.serialize()
        if (restoredDisplayMode == modeKey) return@LaunchedEffect

        val anchorIndex = savedAnchor.mangaId
            ?.let(state.animeIds::indexOf)
            ?.takeIf { it >= 0 }
            ?: 0

        when (displayMode) {
            LibraryDisplayMode.List -> listState.scrollToItem(anchorIndex, savedAnchor.scrollOffset)
            else -> gridState.scrollToItem(anchorIndex, savedAnchor.scrollOffset)
        }

        restoredDisplayMode = modeKey
    }

    LaunchedEffect(displayMode, state.animeIds) {
        if (displayMode != LibraryDisplayMode.List) return@LaunchedEffect

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(ANCHOR_SAVE_DEBOUNCE_MILLIS)
            .collectLatest { (index, offset) ->
                screenModel.saveAnchor(
                    animeId = state.animeIds.getOrNull(index),
                    scrollOffset = offset,
                )
            }
    }

    LaunchedEffect(displayMode, state.animeIds) {
        if (displayMode == LibraryDisplayMode.List) return@LaunchedEffect

        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .debounce(ANCHOR_SAVE_DEBOUNCE_MILLIS)
            .collectLatest { (index, offset) ->
                screenModel.saveAnchor(
                    animeId = state.animeIds.getOrNull(index),
                    scrollOffset = offset,
                )
            }
    }

    LaunchedEffect(displayMode, state.newItemsAvailableCount) {
        if (state.newItemsAvailableCount == 0) return@LaunchedEffect

        if (displayMode == LibraryDisplayMode.List) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { firstVisibleItemIndex ->
                    if (firstVisibleItemIndex < state.newItemsAvailableCount) {
                        screenModel.consumeNewItemsIndicator()
                    }
                }
        } else {
            snapshotFlow { gridState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { firstVisibleItemIndex ->
                    if (firstVisibleItemIndex < state.newItemsAvailableCount) {
                        screenModel.consumeNewItemsIndicator()
                    }
                }
        }
    }

    LaunchedEffect(displayMode, state.isRefreshing, state.isAppending, state.nextPageKey, state.animeIds.size) {
        if (displayMode != LibraryDisplayMode.List) return@LaunchedEffect

        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collectLatest { lastVisibleItemIndex ->
                if (shouldLoadMore(lastVisibleItemIndex, state)) {
                    screenModel.loadMore()
                }
            }
    }

    LaunchedEffect(displayMode, state.isRefreshing, state.isAppending, state.nextPageKey, state.animeIds.size) {
        if (displayMode == LibraryDisplayMode.List) return@LaunchedEffect

        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collectLatest { lastVisibleItemIndex ->
                if (shouldLoadMore(lastVisibleItemIndex, state)) {
                    screenModel.loadMore()
                }
            }
    }

    if (!state.hasLoaded && state.isRefreshing && state.animeIds.isEmpty()) {
        LoadingScreen(Modifier.padding(contentPadding))
        return
    }

    if (state.animeIds.isEmpty()) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = state.error?.let(getErrorMessage) ?: stringResource(MR.strings.no_results_found),
            actions = persistentListOf<EmptyScreenAction>().builder()
                .apply {
                    add(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = Icons.Outlined.Refresh,
                            onClick = screenModel::refresh,
                        ),
                    )
                    onWebViewClick?.let {
                        add(
                            EmptyScreenAction(
                                stringRes = MR.strings.action_open_in_web_view,
                                icon = Icons.Outlined.Public,
                                onClick = it,
                            ),
                        )
                    }
                    onSettingsClick?.let {
                        add(
                            EmptyScreenAction(
                                stringRes = MR.strings.action_settings,
                                icon = Icons.Outlined.Settings,
                                onClick = it,
                            ),
                        )
                    }
                }
                .build(),
        )
        return
    }

    Box {
        when (displayMode) {
            LibraryDisplayMode.ComfortableGrid -> AnimeChronologicalFeedComfortableGrid(
                screenModel = screenModel,
                animeIds = state.animeIds,
                columns = columns,
                contentPadding = contentPadding,
                gridState = gridState,
                sourceItemOrientation = sourceItemOrientation,
                isAppending = state.isAppending,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
            LibraryDisplayMode.ComfortableList -> AnimeChronologicalFeedComfortableGrid(
                screenModel = screenModel,
                animeIds = state.animeIds,
                columns = GridCells.Fixed(1),
                contentPadding = contentPadding,
                gridState = gridState,
                sourceItemOrientation = sourceItemOrientation,
                isAppending = state.isAppending,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
            LibraryDisplayMode.List -> AnimeChronologicalFeedList(
                screenModel = screenModel,
                animeIds = state.animeIds,
                contentPadding = contentPadding,
                listState = listState,
                sourceItemOrientation = sourceItemOrientation,
                isAppending = state.isAppending,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
            LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> AnimeChronologicalFeedCompactGrid(
                screenModel = screenModel,
                animeIds = state.animeIds,
                columns = columns,
                contentPadding = contentPadding,
                gridState = gridState,
                sourceItemOrientation = sourceItemOrientation,
                isAppending = state.isAppending,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
        }

        if (state.newItemsAvailableCount > 0 && !state.isRefreshing) {
            NewAnimeItemsChip(
                count = state.newItemsAvailableCount,
                onClick = {
                    screenModel.consumeNewItemsIndicator()
                    scope.launch {
                        when (displayMode) {
                            LibraryDisplayMode.List -> listState.animateScrollToItem(0)
                            else -> gridState.animateScrollToItem(0)
                        }
                    }
                },
                modifier = Modifier
                    .padding(top = 16.dp)
                    .align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun NewAnimeItemsChip(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.KeyboardArrowUp,
                contentDescription = null,
            )
            Text(
                style = MaterialTheme.typography.labelLarge,
                text = pluralStringResource(MR.plurals.browse_feed_new_items, count, count),
            )
        }
    }
}

@Composable
private fun AnimeChronologicalFeedList(
    screenModel: AnimeChronologicalFeedScreenModel,
    animeIds: List<Long>,
    contentPadding: PaddingValues,
    listState: androidx.compose.foundation.lazy.LazyListState,
    sourceItemOrientation: SourceItemOrientation,
    isAppending: Boolean,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        items(
            count = animeIds.size,
            key = { index -> animeIds[index] },
        ) { index ->
            AnimeChronologicalFeedListItem(
                animeId = animeIds[index],
                screenModel = screenModel,
                sourceItemOrientation = sourceItemOrientation,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
        }

        if (isAppending) {
            item { BrowseSourceLoadingItem() }
        }
    }
}

@Composable
private fun AnimeChronologicalFeedCompactGrid(
    screenModel: AnimeChronologicalFeedScreenModel,
    animeIds: List<Long>,
    columns: GridCells,
    contentPadding: PaddingValues,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    sourceItemOrientation: SourceItemOrientation,
    isAppending: Boolean,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        items(
            count = animeIds.size,
            key = { index -> animeIds[index] },
        ) { index ->
            AnimeChronologicalFeedCompactGridItem(
                animeId = animeIds[index],
                screenModel = screenModel,
                sourceItemOrientation = sourceItemOrientation,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
        }

        if (isAppending) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
    }
}

@Composable
private fun AnimeChronologicalFeedComfortableGrid(
    screenModel: AnimeChronologicalFeedScreenModel,
    animeIds: List<Long>,
    columns: GridCells,
    contentPadding: PaddingValues,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    sourceItemOrientation: SourceItemOrientation,
    isAppending: Boolean,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        items(
            count = animeIds.size,
            key = { index -> animeIds[index] },
        ) { index ->
            AnimeChronologicalFeedComfortableGridItem(
                animeId = animeIds[index],
                screenModel = screenModel,
                sourceItemOrientation = sourceItemOrientation,
                onAnimeClick = onAnimeClick,
                onAnimeLongClick = onAnimeLongClick,
                activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
                onAnimeHover = onAnimeHover,
                onAnimeHoverExit = onAnimeHoverExit,
                onHoverPreviewEnded = onHoverPreviewEnded,
                onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
            )
        }

        if (isAppending) {
            item(span = { GridItemSpan(maxLineSpan) }) { BrowseSourceLoadingItem() }
        }
    }
}

@Composable
private fun AnimeChronologicalFeedListItem(
    animeId: Long,
    screenModel: AnimeChronologicalFeedScreenModel,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val anime = rememberChronologicalAnime(animeId, screenModel)
    if (anime == null) {
        AnimeChronologicalFeedListItemPlaceholder(sourceItemOrientation.toListCoverType())
        return
    }

    MangaListItem(
        title = anime.displayTitle,
        coverData = anime.toMangaCover(),
        coverType = sourceItemOrientation.toListCoverType(),
        coverAlpha = anime.browseCoverAlpha(),
        badge = { InLibraryBadge(enabled = anime.favorite) },
        coverOverlay = animeHoverPreviewCover(
            anime = anime,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        ),
        coverModifier = Modifier.animeHoverPreview(
            anime = anime,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
        ),
        onLongClick = { onAnimeLongClick(anime) },
        onClick = { onAnimeClick(anime) },
    )
}

@Composable
private fun AnimeChronologicalFeedCompactGridItem(
    animeId: Long,
    screenModel: AnimeChronologicalFeedScreenModel,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val anime = rememberChronologicalAnime(animeId, screenModel)
    if (anime == null) {
        AnimeChronologicalFeedCompactGridItemPlaceholder(sourceItemOrientation.toGridCoverType())
        return
    }

    MangaCompactGridItem(
        title = anime.displayTitle,
        coverData = anime.toMangaCover(),
        coverType = sourceItemOrientation.toGridCoverType(),
        coverAlpha = anime.browseCoverAlpha(),
        coverOverlay = animeHoverPreviewCover(
            anime = anime,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        ),
        coverModifier = Modifier.animeHoverPreview(
            anime = anime,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
        ),
        coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
        onLongClick = { onAnimeLongClick(anime) },
        onClick = { onAnimeClick(anime) },
    )
}

@Composable
private fun AnimeChronologicalFeedComfortableGridItem(
    animeId: Long,
    screenModel: AnimeChronologicalFeedScreenModel,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val anime = rememberChronologicalAnime(animeId, screenModel)
    if (anime == null) {
        AnimeChronologicalFeedComfortableGridItemPlaceholder(sourceItemOrientation.toGridCoverType())
        return
    }

    MangaComfortableGridItem(
        title = anime.displayTitle,
        coverData = anime.toMangaCover(),
        coverType = sourceItemOrientation.toGridCoverType(),
        coverAlpha = anime.browseCoverAlpha(),
        coverOverlay = animeHoverPreviewCover(
            anime = anime,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        ),
        coverModifier = Modifier.animeHoverPreview(
            anime = anime,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
        ),
        coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
        onLongClick = { onAnimeLongClick(anime) },
        onClick = { onAnimeClick(anime) },
    )
}

@Composable
private fun rememberChronologicalAnime(
    animeId: Long,
    screenModel: AnimeChronologicalFeedScreenModel,
): AnimeTitle? {
    var anime by remember(animeId) { mutableStateOf<AnimeTitle?>(null) }

    LaunchedEffect(animeId, screenModel) {
        screenModel.subscribeAnime(animeId).collectLatest {
            anime = it
        }
    }

    return anime
}

@Composable
private fun AnimeChronologicalFeedListItemPlaceholder(coverType: CoverType) {
    Row(
        modifier = Modifier
            .height(56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimeChronologicalFeedPlaceholderBlock(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(coverType.ratio),
        )
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimeChronologicalFeedPlaceholderBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp),
            )
            AnimeChronologicalFeedPlaceholderBlock(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp),
            )
        }
    }
}

@Composable
private fun AnimeChronologicalFeedCompactGridItemPlaceholder(coverType: CoverType) {
    AnimeChronologicalFeedPlaceholderBlock(
        modifier = Modifier
            .padding(4.dp)
            .fillMaxWidth()
            .aspectRatio(coverType.ratio),
    )
}

@Composable
private fun AnimeChronologicalFeedComfortableGridItemPlaceholder(coverType: CoverType) {
    Column(
        modifier = Modifier.padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AnimeChronologicalFeedPlaceholderBlock(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(coverType.ratio),
        )
        AnimeChronologicalFeedPlaceholderBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
        )
        AnimeChronologicalFeedPlaceholderBlock(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(12.dp),
        )
    }
}

@Composable
private fun AnimeChronologicalFeedPlaceholderBlock(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
}

private fun AnimeTitle.browseCoverAlpha(): Float {
    return if (favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f
}

@Composable
private fun animeHoverPreviewCover(
    anime: AnimeTitle,
    activeHoverPreviewAnimeIds: List<Long>,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
): (@Composable BoxScope.() -> Unit)? {
    if (anime.id !in activeHoverPreviewAnimeIds) return null

    val preview by produceState<SAnimeHoverPreview?>(initialValue = null, anime.id) {
        value = onAnimeHoverPreviewRequest(anime).also { preview ->
            if (preview == null) {
                onHoverPreviewEnded(anime)
            }
        }
    }

    return preview?.let { hoverPreview ->
        {
            InlineAnimeHoverPreview(
                preview = hoverPreview,
                onEnded = { onHoverPreviewEnded(anime) },
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

private fun Modifier.animeHoverPreview(
    anime: AnimeTitle,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
): Modifier {
    return pointerInput(anime.id) {
        awaitPointerEventScope {
            while (true) {
                when (awaitPointerEvent().type) {
                    PointerEventType.Enter -> onAnimeHover(anime)
                    PointerEventType.Exit -> onAnimeHoverExit(anime)
                    PointerEventType.Press -> onAnimeHover(anime)
                    else -> Unit
                }
            }
        }
    }
}

private fun shouldLoadMore(
    lastVisibleItemIndex: Int,
    state: AnimeChronologicalFeedScreenModel.State,
): Boolean {
    if (lastVisibleItemIndex < 0) return false
    if (state.isRefreshing || state.isAppending || state.nextPageKey == null) return false

    return lastVisibleItemIndex >= state.animeIds.lastIndex - LOAD_MORE_VISIBLE_THRESHOLD
}

private const val ANCHOR_SAVE_DEBOUNCE_MILLIS = 150L
private const val LOAD_MORE_VISIBLE_THRESHOLD = 3
