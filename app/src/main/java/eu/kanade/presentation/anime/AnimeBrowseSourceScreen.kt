package eu.kanade.presentation.anime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
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
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

@Composable
fun AnimeBrowseSourceContent(
    animeList: LazyPagingItems<StateFlow<AnimeTitle>>,
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

    val errorState = animeList.loadState.refresh.takeIf { it is LoadState.Error }
        ?: animeList.loadState.append.takeIf { it is LoadState.Error }

    val getErrorMessage: (LoadState.Error) -> String = { state -> with(context) { state.error.formattedMessage } }

    LaunchedEffect(errorState) {
        if (animeList.itemCount > 0 && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> animeList.retry()
            }
        }
    }

    if (animeList.itemCount == 0 && animeList.loadState.refresh is LoadState.Loading) {
        LoadingScreen(Modifier.padding(contentPadding))
        return
    }

    if (animeList.itemCount == 0) {
        EmptyScreen(
            modifier = Modifier.padding(contentPadding),
            message = when (errorState) {
                is LoadState.Error -> getErrorMessage(errorState)
                else -> stringResource(MR.strings.no_results_found)
            },
            actions = persistentListOf<EmptyScreenAction>().builder()
                .apply {
                    add(
                        EmptyScreenAction(
                            stringRes = MR.strings.action_retry,
                            icon = Icons.Outlined.Refresh,
                            onClick = animeList::refresh,
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

    when (displayMode) {
        LibraryDisplayMode.ComfortableGrid -> AnimeBrowseComfortableGrid(
            animeList = animeList,
            columns = columns,
            contentPadding = contentPadding,
            sourceItemOrientation = sourceItemOrientation,
            onAnimeClick = onAnimeClick,
            onAnimeLongClick = onAnimeLongClick,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
            onHoverPreviewReset = onHoverPreviewReset,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        )
        LibraryDisplayMode.ComfortableList -> AnimeBrowseComfortableGrid(
            animeList = animeList,
            columns = GridCells.Fixed(1),
            contentPadding = contentPadding,
            sourceItemOrientation = sourceItemOrientation,
            onAnimeClick = onAnimeClick,
            onAnimeLongClick = onAnimeLongClick,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
            onHoverPreviewReset = onHoverPreviewReset,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        )
        LibraryDisplayMode.List -> AnimeBrowseList(
            animeList = animeList,
            contentPadding = contentPadding,
            sourceItemOrientation = sourceItemOrientation,
            onAnimeClick = onAnimeClick,
            onAnimeLongClick = onAnimeLongClick,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
            onHoverPreviewReset = onHoverPreviewReset,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        )
        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> AnimeBrowseCompactGrid(
            animeList = animeList,
            columns = columns,
            contentPadding = contentPadding,
            sourceItemOrientation = sourceItemOrientation,
            onAnimeClick = onAnimeClick,
            onAnimeLongClick = onAnimeLongClick,
            activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
            onAnimeHover = onAnimeHover,
            onAnimeHoverExit = onAnimeHoverExit,
            onHoverPreviewReset = onHoverPreviewReset,
            onHoverPreviewEnded = onHoverPreviewEnded,
            onAnimeHoverPreviewRequest = onAnimeHoverPreviewRequest,
        )
    }
}

@Composable
private fun AnimeBrowseList(
    animeList: LazyPagingItems<StateFlow<AnimeTitle>>,
    contentPadding: PaddingValues,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewReset: (Long) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val listState = rememberLazyListState()
    ResetHoverPreviewOnScrollAway(
        activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
        visibleKeys = { listState.layoutInfo.visibleItemsInfo.map { it.key } },
        onHoverPreviewReset = onHoverPreviewReset,
    )

    LazyColumn(
        state = listState,
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (animeList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = animeList.itemCount,
            key = { index -> animeList.peek(index)?.value?.id ?: "anime-browse-list-$index" },
        ) { index ->
            val anime by animeList[index]?.collectAsState() ?: return@items
            MangaListItem(
                title = anime.displayTitle,
                coverData = anime.toMangaCover(),
                coverType = sourceItemOrientation.toListCoverType(),
                coverAlpha = if (anime.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
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

        item {
            if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun AnimeBrowseComfortableGrid(
    animeList: LazyPagingItems<StateFlow<AnimeTitle>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewReset: (Long) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val gridState = rememberLazyGridState()
    ResetHoverPreviewOnScrollAway(
        activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
        visibleKeys = { gridState.layoutInfo.visibleItemsInfo.map { it.key } },
        onHoverPreviewReset = onHoverPreviewReset,
    )

    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = animeList.itemCount,
            key = { index -> animeList.peek(index)?.value?.id ?: "anime-browse-comfortable-$index" },
        ) { index ->
            val anime by animeList[index]?.collectAsState() ?: return@items
            MangaComfortableGridItem(
                title = anime.displayTitle,
                coverData = anime.toMangaCover(),
                coverType = sourceItemOrientation.toGridCoverType(),
                coverAlpha = if (anime.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
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

        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun AnimeBrowseCompactGrid(
    animeList: LazyPagingItems<StateFlow<AnimeTitle>>,
    columns: GridCells,
    contentPadding: PaddingValues,
    sourceItemOrientation: SourceItemOrientation,
    onAnimeClick: (AnimeTitle) -> Unit,
    onAnimeLongClick: (AnimeTitle) -> Unit,
    activeHoverPreviewAnimeIds: List<Long>,
    onAnimeHover: (AnimeTitle) -> Unit,
    onAnimeHoverExit: (AnimeTitle) -> Unit,
    onHoverPreviewReset: (Long) -> Unit,
    onHoverPreviewEnded: (AnimeTitle) -> Unit,
    onAnimeHoverPreviewRequest: suspend (AnimeTitle) -> SAnimeHoverPreview?,
) {
    val gridState = rememberLazyGridState()
    ResetHoverPreviewOnScrollAway(
        activeHoverPreviewAnimeIds = activeHoverPreviewAnimeIds,
        visibleKeys = { gridState.layoutInfo.visibleItemsInfo.map { it.key } },
        onHoverPreviewReset = onHoverPreviewReset,
    )

    LazyVerticalGrid(
        columns = columns,
        state = gridState,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
    ) {
        if (animeList.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = animeList.itemCount,
            key = { index -> animeList.peek(index)?.value?.id ?: "anime-browse-compact-$index" },
        ) { index ->
            val anime by animeList[index]?.collectAsState() ?: return@items
            MangaCompactGridItem(
                title = anime.displayTitle,
                coverData = anime.toMangaCover(),
                coverType = sourceItemOrientation.toGridCoverType(),
                coverAlpha = if (anime.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f,
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

        if (animeList.loadState.refresh is LoadState.Loading || animeList.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun ResetHoverPreviewOnScrollAway(
    activeHoverPreviewAnimeIds: List<Long>,
    visibleKeys: () -> List<Any>,
    onHoverPreviewReset: (Long) -> Unit,
) {
    LaunchedEffect(activeHoverPreviewAnimeIds) {
        snapshotFlow { visibleKeys() }
            .collect { visible ->
                activeHoverPreviewAnimeIds
                    .filterNot { it in visible }
                    .forEach(onHoverPreviewReset)
            }
    }
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
