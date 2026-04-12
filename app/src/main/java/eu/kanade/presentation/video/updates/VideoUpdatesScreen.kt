package eu.kanade.presentation.video.updates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.tachiyomi.ui.video.updates.VideoUpdatesScreenModel
import eu.kanade.tachiyomi.ui.video.updates.VideoUpdatesUiModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun VideoUpdatesScreen(
    state: VideoUpdatesScreenModel.State,
    onClickCover: (videoId: Long) -> Unit,
    onClickUpdate: (videoId: Long, episodeId: Long) -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_recent_updates),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        when {
            state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
            state.error != null -> EmptyScreen(
                message = state.error,
                modifier = Modifier.padding(contentPadding),
            )
            state.list.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> VideoUpdatesScreenContent(
                items = state.list,
                contentPadding = contentPadding,
                onClickCover = onClickCover,
                onClickUpdate = onClickUpdate,
            )
        }
    }
}

@Composable
private fun VideoUpdatesScreenContent(
    items: List<VideoUpdatesUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (videoId: Long) -> Unit,
    onClickUpdate: (videoId: Long, episodeId: Long) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = items,
            key = { "video-updates-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is VideoUpdatesUiModel.Header -> "header"
                    is VideoUpdatesUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is VideoUpdatesUiModel.Header -> {
                    ListGroupHeader(text = relativeDateText(item.date))
                }
                is VideoUpdatesUiModel.Item -> {
                    VideoUpdatesItem(
                        update = item.update,
                        onClickCover = { onClickCover(item.update.videoId) },
                        onClickUpdate = { onClickUpdate(item.update.videoId, item.update.episodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoUpdatesItem(
    update: tachiyomi.domain.video.model.VideoUpdatesWithRelations,
    onClickCover: () -> Unit,
    onClickUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClickUpdate)
            .height(72.dp)
            .padding(horizontal = MaterialTheme.padding.medium),
    ) {
        MangaCover.Square(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .fillMaxHeight(),
            data = update.coverData,
            onClick = onClickCover,
        )
        Column(
            modifier = Modifier
                .padding(horizontal = MaterialTheme.padding.medium)
                .weight(1f),
        ) {
            Text(
                text = update.videoTitle,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                if (update.completed) {
                    Text(
                        text = stringResource(MR.strings.completed),
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else {
                    if (!update.watched) {
                        Icon(
                            imageVector = Icons.Filled.Circle,
                            contentDescription = stringResource(MR.strings.unread),
                            modifier = Modifier
                                .height(8.dp)
                                .padding(end = 4.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = update.episodeName,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = stringResource(MR.strings.action_start),
            modifier = Modifier.padding(vertical = 24.dp),
        )
    }
}
