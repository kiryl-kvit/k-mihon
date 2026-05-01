package eu.kanade.presentation.components

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import eu.kanade.presentation.anime.AnimeDownloadAction
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AnimeDownloadDropdownMenu(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onDownloadClicked: (AnimeDownloadAction) -> Unit,
    offset: DpOffset? = null,
) {
    if (offset != null) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            offset = offset,
            content = {
                AnimeDownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                )
            },
        )
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = {
                AnimeDownloadDropdownMenuItems(
                    onDismissRequest = onDismissRequest,
                    onDownloadClicked = onDownloadClicked,
                )
            },
        )
    }
}

@Composable
private fun AnimeDownloadDropdownMenuItems(
    onDismissRequest: () -> Unit,
    onDownloadClicked: (AnimeDownloadAction) -> Unit,
) {
    val options = persistentListOf(
        AnimeDownloadAction.NEXT_1_EPISODE to pluralStringResource(MR.plurals.download_amount_episodes, 1, 1),
        AnimeDownloadAction.NEXT_5_EPISODES to pluralStringResource(MR.plurals.download_amount_episodes, 5, 5),
        AnimeDownloadAction.NEXT_10_EPISODES to pluralStringResource(MR.plurals.download_amount_episodes, 10, 10),
        AnimeDownloadAction.NEXT_25_EPISODES to pluralStringResource(MR.plurals.download_amount_episodes, 25, 25),
        AnimeDownloadAction.UNWATCHED_EPISODES to stringResource(MR.strings.download_unwatched),
    )

    options.map { (downloadAction, string) ->
        DropdownMenuItem(
            text = { Text(text = string) },
            onClick = {
                onDownloadClicked(downloadAction)
                onDismissRequest()
            },
        )
    }
}
