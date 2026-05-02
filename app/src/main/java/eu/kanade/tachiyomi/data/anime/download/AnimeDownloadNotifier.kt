package eu.kanade.tachiyomi.data.anime.download

import android.content.Context
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadFailure
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

internal class AnimeDownloadNotifier(
    private val context: Context,
) {
    private val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_PROGRESS) {
            setSmallIcon(android.R.drawable.stat_sys_download)
            setAutoCancel(false)
            setOnlyAlertOnce(true)
            setOngoing(true)
        }
    }

    private val errorNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_DOWNLOADER_ERROR) {
            setAutoCancel(true)
            setSmallIcon(R.drawable.ic_warning_white_24dp)
        }
    }

    private fun NotificationCompat.Builder.show(id: Int) {
        context.notify(id, build())
    }

    fun dismissProgress() {
        context.cancelNotification(Notifications.ID_DOWNLOAD_ANIME_PROGRESS)
    }

    fun onProgressChange(download: AnimeDownload) {
        with(progressNotificationBuilder) {
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            clearActions()
            addAction(
                R.drawable.ic_pause_24dp,
                context.stringResource(MR.strings.action_pause),
                NotificationReceiver.pauseDownloadsPendingBroadcast(context),
            )
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.stringResource(MR.strings.action_resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )

            setContentTitle(download.anime.title)
            setContentText(download.episode.name.ifBlank { "Episode" })
            val progress = download.progress.coerceIn(0, 100)
            setProgress(100, progress, progress <= 0)
            show(Notifications.ID_DOWNLOAD_ANIME_PROGRESS)
        }
    }

    fun onPaused() {
        with(progressNotificationBuilder) {
            setContentTitle(context.stringResource(MR.strings.chapter_paused))
            setContentText(context.stringResource(MR.strings.download_notifier_download_paused))
            setSmallIcon(R.drawable.ic_pause_24dp)
            setProgress(0, 0, false)
            setOngoing(false)
            clearActions()
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            addAction(
                R.drawable.ic_play_arrow_24dp,
                context.stringResource(MR.strings.action_resume),
                NotificationReceiver.resumeDownloadsPendingBroadcast(context),
            )
            addAction(
                R.drawable.ic_close_24dp,
                context.stringResource(MR.strings.action_cancel_all),
                NotificationReceiver.clearDownloadsPendingBroadcast(context),
            )
            show(Notifications.ID_DOWNLOAD_ANIME_PROGRESS)
        }
    }

    fun onComplete() {
        dismissProgress()
    }

    fun onError(download: AnimeDownload) {
        with(errorNotificationBuilder) {
            setContentTitle(download.anime.title)
            setContentText(download.failure.toReadableMessage(context))
            setContentIntent(NotificationHandler.openDownloadManagerPendingActivity(context))
            show(Notifications.ID_DOWNLOAD_ANIME_ERROR)
        }
    }
}

private fun AnimeDownloadFailure?.toReadableMessage(context: Context): String {
    val failure = this ?: return context.stringResource(MR.strings.download_notifier_unknown_error)
    return failure.message?.takeIf { it.isNotBlank() } ?: when (failure.reason) {
        AnimeDownloadFailure.Reason.SOURCE_NOT_FOUND -> "Source not available"
        AnimeDownloadFailure.Reason.EPISODE_NOT_FOUND -> "Episode not found"
        AnimeDownloadFailure.Reason.PREFERENCES_NOT_SUPPORTED -> "Selected download options are not supported"
        AnimeDownloadFailure.Reason.DUB_NOT_AVAILABLE -> "Selected dub is not available"
        AnimeDownloadFailure.Reason.STREAM_NOT_AVAILABLE -> "Selected stream is not available"
        AnimeDownloadFailure.Reason.SUBTITLE_NOT_AVAILABLE -> "Selected subtitle is not available"
        AnimeDownloadFailure.Reason.QUALITY_NOT_AVAILABLE -> "Selected quality is not available"
        AnimeDownloadFailure.Reason.STREAM_EXPIRED -> "Stream URL expired"
        AnimeDownloadFailure.Reason.UNSUPPORTED_STREAM -> "Source only provides unsupported stream format for download"
        AnimeDownloadFailure.Reason.INSUFFICIENT_STORAGE -> "Insufficient storage space"
        AnimeDownloadFailure.Reason.NETWORK -> "Network error while resolving download stream"
        AnimeDownloadFailure.Reason.UNKNOWN -> context.stringResource(MR.strings.download_notifier_unknown_error)
    }
}
