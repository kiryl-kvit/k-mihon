package eu.kanade.tachiyomi.data.anime.download.model

import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoSubtitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeTitle

data class AnimeDownload(
    val anime: AnimeTitle,
    val episode: AnimeEpisode,
    val preferences: AnimeDownloadPreferences,
) {
    var selection: VideoPlaybackSelection = VideoPlaybackSelection(
        dubKey = preferences.dubKey,
        streamKey = preferences.streamKey,
    )
    var playbackData: VideoPlaybackData? = null
    var stream: VideoStream? = null
    var subtitles: List<VideoSubtitle> = emptyList()

    @Transient
    private val _statusFlow = MutableStateFlow(State.NOT_DOWNLOADED)

    @Transient
    val statusFlow = _statusFlow.asStateFlow()
    var status: State
        get() = _statusFlow.value
        set(status) {
            _statusFlow.value = status
        }

    @Transient
    private val _progressFlow = MutableStateFlow(0)

    @Transient
    val progressFlow = _progressFlow.asStateFlow()
    var progress: Int
        get() = _progressFlow.value
        set(progress) {
            _progressFlow.value = progress.coerceIn(0, 100)
        }

    @Transient
    private val _failureFlow = MutableStateFlow<AnimeDownloadFailure?>(null)

    @Transient
    val failureFlow = _failureFlow.asStateFlow()
    var failure: AnimeDownloadFailure?
        get() = _failureFlow.value
        set(failure) {
            _failureFlow.value = failure
        }

    enum class State {
        NOT_DOWNLOADED,
        QUEUE,
        RESOLVING,
        DOWNLOADING,
        DOWNLOADED,
        ERROR,
    }
}

data class AnimeDownloadFailure(
    val reason: Reason,
    val message: String? = null,
) {
    enum class Reason {
        SOURCE_NOT_FOUND,
        EPISODE_NOT_FOUND,
        PREFERENCES_NOT_SUPPORTED,
        DUB_NOT_AVAILABLE,
        STREAM_NOT_AVAILABLE,
        SUBTITLE_NOT_AVAILABLE,
        QUALITY_NOT_AVAILABLE,
        STREAM_EXPIRED,
        UNSUPPORTED_STREAM,
        INSUFFICIENT_STORAGE,
        NETWORK,
        UNKNOWN,
    }
}
