package eu.kanade.tachiyomi.ui.video.player

import eu.kanade.domain.video.model.toSEpisode
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.source.service.VideoSourceManager
import tachiyomi.domain.video.model.VideoEpisode
import tachiyomi.domain.video.model.VideoTitle
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ResolveVideoStream(
    private val videoRepository: VideoRepository = Injekt.get(),
    private val videoEpisodeRepository: VideoEpisodeRepository = Injekt.get(),
    private val videoSourceManager: VideoSourceManager = Injekt.get(),
    private val sourceInitTimeoutMs: Long = SOURCE_INIT_TIMEOUT_MS,
    private val streamFetchTimeoutMs: Long = STREAM_FETCH_TIMEOUT_MS,
) : VideoStreamResolver {

    override suspend operator fun invoke(videoId: Long, episodeId: Long): Result {
        val video = runCatching { videoRepository.getVideoById(videoId) }
            .getOrElse { return Result.Error(Reason.VideoNotFound) }
        val episode = videoEpisodeRepository.getEpisodeById(episodeId)
            ?: return Result.Error(Reason.EpisodeNotFound)

        if (episode.videoId != video.id) {
            return Result.Error(Reason.EpisodeMismatch)
        }

        val initialized = withTimeoutOrNull(sourceInitTimeoutMs) {
            videoSourceManager.isInitialized.filter { it }.first()
        }
        if (initialized != true) {
            return Result.Error(Reason.SourceLoadTimeout)
        }

        val source = videoSourceManager.get(video.source)
            ?: return Result.Error(Reason.SourceNotFound)

        val streams = runCatching {
            withTimeoutOrNull(streamFetchTimeoutMs) {
                source.getStreamList(episode.toSEpisode())
            } ?: return Result.Error(Reason.StreamFetchTimeout)
        }.getOrElse {
            return Result.Error(Reason.StreamFetchFailed(it))
        }

        val stream = streams
            .filter { it.request.url.isNotBlank() }
            .maxByOrNull(::streamScore)
            ?: return Result.Error(Reason.NoStreams)

        return Result.Success(
            video = video,
            episode = episode,
            stream = stream,
        )
    }

    sealed interface Result {
        data class Success(
            val video: VideoTitle,
            val episode: VideoEpisode,
            val stream: VideoStream,
        ) : Result

        data class Error(val reason: Reason) : Result
    }

    sealed interface Reason {
        data object VideoNotFound : Reason

        data object EpisodeNotFound : Reason

        data object EpisodeMismatch : Reason

        data object SourceLoadTimeout : Reason

        data object SourceNotFound : Reason

        data object NoStreams : Reason

        data object StreamFetchTimeout : Reason

        data class StreamFetchFailed(val cause: Throwable) : Reason
    }

    private fun streamScore(stream: VideoStream): Int {
        val typeScore = when (stream.type) {
            VideoStreamType.PROGRESSIVE -> 400
            VideoStreamType.HLS -> 300
            VideoStreamType.DASH -> 200
            VideoStreamType.UNKNOWN -> 100
        }
        val mimeScore = when {
            stream.mimeType?.contains("mp4", ignoreCase = true) == true -> 30
            stream.mimeType?.contains("mpegurl", ignoreCase = true) == true -> 20
            stream.mimeType?.contains("dash", ignoreCase = true) == true -> 10
            else -> 0
        }
        val headerScore = if (stream.request.headers.isNotEmpty()) 5 else 0
        val labelScore = if (stream.label.isNotBlank()) 1 else 0
        return typeScore + mimeScore + headerScore + labelScore
    }

    private companion object {
        private const val SOURCE_INIT_TIMEOUT_MS = 5_000L
        private const val STREAM_FETCH_TIMEOUT_MS = 15_000L
    }
}
