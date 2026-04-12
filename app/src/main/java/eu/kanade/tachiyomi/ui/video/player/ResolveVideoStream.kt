package eu.kanade.tachiyomi.ui.video.player

import eu.kanade.domain.video.model.toSEpisode
import eu.kanade.tachiyomi.source.model.VideoStream
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
) {

    suspend operator fun invoke(videoId: Long, episodeId: Long): Result {
        val video = runCatching { videoRepository.getVideoById(videoId) }
            .getOrElse { return Result.Error(Reason.VideoNotFound) }
        val episode = videoEpisodeRepository.getEpisodeById(episodeId)
            ?: return Result.Error(Reason.EpisodeNotFound)

        if (episode.videoId != video.id) {
            return Result.Error(Reason.EpisodeMismatch)
        }

        videoSourceManager.isInitialized.filter { it }.first()
        val source = videoSourceManager.get(video.source)
            ?: return Result.Error(Reason.SourceNotFound)

        val streams = runCatching {
            source.getStreamList(episode.toSEpisode())
        }.getOrElse {
            return Result.Error(Reason.StreamFetchFailed(it))
        }

        val stream = streams.firstOrNull() ?: return Result.Error(Reason.NoStreams)
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

        data object SourceNotFound : Reason

        data object NoStreams : Reason

        data class StreamFetchFailed(val cause: Throwable) : Reason
    }
}
