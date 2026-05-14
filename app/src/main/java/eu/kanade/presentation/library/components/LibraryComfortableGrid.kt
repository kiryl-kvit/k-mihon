package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.manga.components.toGridCoverType
import eu.kanade.tachiyomi.source.model.SourceItemOrientation
import eu.kanade.tachiyomi.ui.library.LibraryItem
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.MangaCover
import tachiyomi.domain.manga.model.presentationTitle

@Composable
internal fun LibraryComfortableGrid(
    items: List<LibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: Set<Long>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            span = { libraryItem ->
                GridItemSpan(
                    if (libraryItem.sourceItemOrientation == SourceItemOrientation.HORIZONTAL) {
                        minOf(2, maxLineSpan)
                    } else {
                        1
                    },
                )
            },
            contentType = { "library_comfortable_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            MangaComfortableGridItem(
                isSelected = manga.id in selection,
                title = manga.presentationTitle(),
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverType = libraryItem.sourceItemOrientation.toGridCoverType(),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnreadBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueReading = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryItem.libraryManga) }
                } else {
                    null
                },
            )
        }
    }
}
