package eu.kanade.presentation.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.DownloadIndicator
import eu.kanade.presentation.components.DownloadIndicatorAction
import eu.kanade.presentation.components.DownloadIndicatorState
import eu.kanade.tachiyomi.data.download.model.Download
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

enum class ChapterDownloadAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> Download.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DownloadIndicator(
        enabled = enabled,
        modifier = modifier,
        downloadStateProvider = {
            when (downloadStateProvider()) {
                Download.State.NOT_DOWNLOADED -> DownloadIndicatorState.NOT_DOWNLOADED
                Download.State.QUEUE -> DownloadIndicatorState.QUEUE
                Download.State.DOWNLOADING -> DownloadIndicatorState.DOWNLOADING
                Download.State.DOWNLOADED -> DownloadIndicatorState.DOWNLOADED
                Download.State.ERROR -> DownloadIndicatorState.ERROR
            }
        },
        downloadProgressProvider = downloadProgressProvider,
        startContentDescription = stringResource(MR.strings.manga_download),
        errorContentDescription = stringResource(MR.strings.chapter_error),
        startNowText = stringResource(MR.strings.action_start_downloading_now),
        cancelText = stringResource(MR.strings.action_cancel),
        deleteText = stringResource(MR.strings.action_delete),
        onClick = {
            onClick(
                when (it) {
                    DownloadIndicatorAction.START -> ChapterDownloadAction.START
                    DownloadIndicatorAction.START_NOW -> ChapterDownloadAction.START_NOW
                    DownloadIndicatorAction.CANCEL -> ChapterDownloadAction.CANCEL
                    DownloadIndicatorAction.DELETE -> ChapterDownloadAction.DELETE
                },
            )
        },
    )
}
