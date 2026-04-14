package eu.kanade.tachiyomi.data.library

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.cancelNotification
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.math.RoundingMode
import java.text.NumberFormat

class AnimeLibraryUpdateNotifier(
    private val context: Context,
    private val securityPreferences: SecurityPreferences = Injekt.get(),
) {

    private val percentFormatter = NumberFormat.getPercentInstance().apply {
        roundingMode = RoundingMode.DOWN
        maximumFractionDigits = 0
    }

    private val cancelIntent by lazy {
        NotificationReceiver.cancelAnimeLibraryUpdatePendingBroadcast(context)
    }

    private val notificationBitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
    }

    val progressNotificationBuilder by lazy {
        context.notificationBuilder(Notifications.CHANNEL_LIBRARY_PROGRESS) {
            setContentTitle(context.stringResource(MR.strings.app_name))
            setSmallIcon(R.drawable.ic_refresh_24dp)
            setLargeIcon(notificationBitmap)
            setOngoing(true)
            setOnlyAlertOnce(true)
            addAction(R.drawable.ic_close_24dp, context.stringResource(MR.strings.action_cancel), cancelIntent)
        }
    }

    fun showProgressNotification(anime: List<AnimeTitle>, current: Int, total: Int) {
        progressNotificationBuilder
            .setContentTitle(
                context.stringResource(
                    MR.strings.notification_updating_progress,
                    percentFormatter.format(current.toFloat() / total),
                ),
            )

        if (!securityPreferences.hideNotificationContent.get()) {
            val updatingText = anime.joinToString("\n") { it.displayTitle.chop(40) }
            progressNotificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(updatingText))
        }

        context.notify(
            Notifications.ID_ANIME_LIBRARY_PROGRESS,
            progressNotificationBuilder
                .setProgress(total, current, false)
                .build(),
        )
    }

    fun showUpdateErrorNotification(failed: Int, uri: Uri) {
        if (failed == 0) {
            return
        }

        context.notify(
            Notifications.ID_ANIME_LIBRARY_ERROR,
            Notifications.CHANNEL_LIBRARY_ERROR,
        ) {
            setContentTitle(context.pluralStringResource(MR.plurals.notification_update_error, failed, failed))
            setContentText(context.stringResource(MR.strings.action_show_errors))
            setSmallIcon(R.drawable.ic_mihon)

            setContentIntent(NotificationReceiver.openErrorLogPendingActivity(context, uri))
        }
    }

    fun cancelProgressNotification() {
        context.cancelNotification(Notifications.ID_ANIME_LIBRARY_PROGRESS)
    }
}
