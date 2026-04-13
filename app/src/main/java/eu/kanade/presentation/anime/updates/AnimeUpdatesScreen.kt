package eu.kanade.presentation.anime.updates

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
import androidx.compose.material.icons.outlined.Refresh
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
import eu.kanade.tachiyomi.ui.anime.updates.AnimeUpdatesScreenModel
import eu.kanade.tachiyomi.ui.anime.updates.AnimeUpdatesUiModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import kotlinx.collections.immutable.persistentListOf

@Composable
fun AnimeUpdatesScreen(
    state: AnimeUpdatesScreenModel.State,
    onClickCover: (animeId: Long) -> Unit,
    onClickUpdate: (animeId: Long, episodeId: Long) -> Unit,
    onRetry: () -> Unit,
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
                actions = persistentListOf(
                    EmptyScreenAction(
                        stringRes = MR.strings.action_retry,
                        icon = Icons.Outlined.Refresh,
                        onClick = onRetry,
                    ),
                ),
                modifier = Modifier.padding(contentPadding),
            )
            state.list.isEmpty() -> EmptyScreen(
                stringRes = MR.strings.information_no_recent,
                modifier = Modifier.padding(contentPadding),
            )
            else -> AnimeUpdatesScreenContent(
                items = state.list,
                contentPadding = contentPadding,
                onClickCover = onClickCover,
                onClickUpdate = onClickUpdate,
            )
        }
    }
}

@Composable
private fun AnimeUpdatesScreenContent(
    items: List<AnimeUpdatesUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (animeId: Long) -> Unit,
    onClickUpdate: (animeId: Long, episodeId: Long) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(
            items = items,
            key = { "anime-updates-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is AnimeUpdatesUiModel.Header -> "header"
                    is AnimeUpdatesUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is AnimeUpdatesUiModel.Header -> {
                    ListGroupHeader(text = relativeDateText(item.date))
                }
                is AnimeUpdatesUiModel.Item -> {
                    AnimeUpdatesItem(
                        update = item.update,
                        onClickCover = { onClickCover(item.update.animeId) },
                        onClickUpdate = { onClickUpdate(item.update.animeId, item.update.episodeId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeUpdatesItem(
    update: tachiyomi.domain.anime.model.AnimeUpdatesWithRelations,
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
                text = update.animeTitle,
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
                    } else {
                        Text(
                            text = stringResource(MR.strings.anime_watched),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
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
