package eu.kanade.tachiyomi.ui.video.player

import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimePlaybackState
import java.util.Date

internal class VideoPlaybackSession(
    private val episodeId: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private var savedPositionMs: Long = 0L

    fun restore(positionMs: Long) {
        savedPositionMs = positionMs.coerceAtLeast(0L)
    }

    fun snapshot(positionMs: Long, durationMs: Long): Snapshot {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        val completed = safeDurationMs > 0L && safePositionMs * 100 >= safeDurationMs * COMPLETION_PERCENTAGE
        val watchedDelta = (safePositionMs - savedPositionMs).coerceAtLeast(0L)
        val timestamp = now()

        savedPositionMs = safePositionMs

        return Snapshot(
            playbackState = AnimePlaybackState(
                episodeId = episodeId,
                positionMs = safePositionMs,
                durationMs = safeDurationMs,
                completed = completed,
                lastWatchedAt = timestamp,
            ),
            historyUpdate = watchedDelta.takeIf { it > 0L }?.let {
                AnimeHistoryUpdate(
                    episodeId = episodeId,
                    watchedAt = Date(timestamp),
                    sessionWatchedDuration = it,
                )
            },
        )
    }

    data class Snapshot(
        val playbackState: AnimePlaybackState,
        val historyUpdate: AnimeHistoryUpdate?,
    )

    private companion object {
        const val COMPLETION_PERCENTAGE = 90L
    }
}
