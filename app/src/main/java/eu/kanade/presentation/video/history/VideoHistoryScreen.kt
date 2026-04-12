package eu.kanade.presentation.video.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.components.HistoryDeleteAllDialog
import eu.kanade.presentation.history.components.HistoryDeleteDialog
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.video.history.VideoHistoryScreenModel
import eu.kanade.tachiyomi.ui.video.history.VideoHistoryUiModel
import eu.kanade.tachiyomi.util.lang.toTimestampString
import eu.kanade.presentation.util.toDurationString
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VideoHistoryScreen(
    state: VideoHistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onSearchQueryChange: (String?) -> Unit,
    onClickCover: (videoId: Long) -> Unit,
    onClickResume: (videoId: Long, episodeId: Long) -> Unit,
    onDelete: (tachiyomi.domain.video.model.VideoHistoryWithRelations, Boolean) -> Unit,
    onDeleteAll: () -> Unit,
    onDialogChange: (VideoHistoryScreenModel.Dialog?) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            SearchToolbar(
                titleContent = { AppBarTitle(stringResource(MR.strings.history)) },
                searchQuery = state.searchQuery,
                onChangeSearchQuery = onSearchQueryChange,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.pref_clear_history),
                                icon = Icons.Outlined.DeleteSweep,
                                onClick = { onDialogChange(VideoHistoryScreenModel.Dialog.DeleteAll) },
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        val list = state.list
        when {
            list == null -> LoadingScreen(Modifier.padding(contentPadding))
            list.isEmpty() -> EmptyScreen(
                stringRes = if (!state.searchQuery.isNullOrEmpty()) {
                    MR.strings.no_results_found
                } else {
                    MR.strings.information_no_recent_manga
                },
                modifier = Modifier.padding(contentPadding),
            )
            else -> VideoHistoryScreenContent(
                history = list,
                contentPadding = contentPadding,
                onClickCover = { onClickCover(it.history.videoId) },
                onClickResume = { onClickResume(it.history.videoId, it.history.episodeId) },
                onClickDelete = { onDialogChange(VideoHistoryScreenModel.Dialog.Delete(it)) },
            )
        }
    }

    when (val dialog = state.dialog) {
        is VideoHistoryScreenModel.Dialog.Delete -> {
            HistoryDeleteDialog(
                onDismissRequest = { onDialogChange(null) },
                onDelete = { removeEverything ->
                    onDelete(dialog.history, removeEverything)
                    onDialogChange(null)
                },
            )
        }
        VideoHistoryScreenModel.Dialog.DeleteAll -> {
            HistoryDeleteAllDialog(
                onDismissRequest = { onDialogChange(null) },
                onDelete = {
                    onDeleteAll()
                    onDialogChange(null)
                },
            )
        }
        null -> Unit
    }
}

@Composable
private fun VideoHistoryScreenContent(
    history: List<VideoHistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (VideoHistoryUiModel.Item) -> Unit,
    onClickResume: (VideoHistoryUiModel.Item) -> Unit,
    onClickDelete: (tachiyomi.domain.video.model.VideoHistoryWithRelations) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = history,
            key = { "video-history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is VideoHistoryUiModel.Header -> "header"
                    is VideoHistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is VideoHistoryUiModel.Header -> {
                    ListGroupHeader(text = relativeDateText(item.date))
                }
                is VideoHistoryUiModel.Item -> {
                    VideoHistoryItem(
                        history = item.history,
                        onClickCover = { onClickCover(item) },
                        onClickResume = { onClickResume(item) },
                        onClickDelete = { onClickDelete(item.history) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoHistoryItem(
    history: tachiyomi.domain.video.model.VideoHistoryWithRelations,
    onClickCover: () -> Unit,
    onClickResume: () -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .clickable(onClick = onClickResume)
            .height(96.dp)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
    ) {
        MangaCover.Book(
            modifier = Modifier.fillMaxHeight(),
            data = history.coverData,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MaterialTheme.padding.medium, end = MaterialTheme.padding.small),
        ) {
            Text(
                text = history.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = history.episodeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    val watchedAt = history.watchedAt?.toTimestampString()
                    if (!watchedAt.isNullOrBlank()) append(watchedAt)
                    if (history.watchedDuration > 0L) {
                        if (isNotBlank()) append(" • ")
                        append(
                            history.watchedDuration.milliseconds.toDurationString(
                                context = context,
                                fallback = stringResource(MR.strings.not_applicable),
                            ),
                        )
                    }
                }.ifBlank { stringResource(MR.strings.not_applicable) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClickDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(MR.strings.action_delete),
            )
        }
    }
}
