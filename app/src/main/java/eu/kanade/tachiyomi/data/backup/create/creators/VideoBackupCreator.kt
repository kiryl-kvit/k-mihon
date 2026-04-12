package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.BackupVideo
import eu.kanade.tachiyomi.data.backup.models.BackupVideoEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupVideoHistory
import eu.kanade.tachiyomi.data.backup.models.BackupVideoPlaybackState
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.video.VideoEpisodeMapper
import tachiyomi.data.video.VideoHistoryMapper
import tachiyomi.data.video.VideoPlaybackStateMapper
import tachiyomi.domain.category.interactor.GetVideoCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.video.model.VideoEpisode
import tachiyomi.domain.video.model.VideoHistory
import tachiyomi.domain.video.model.VideoPlaybackState
import tachiyomi.domain.video.model.VideoTitle
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoHistoryRepository
import tachiyomi.domain.video.repository.VideoPlaybackStateRepository
import tachiyomi.domain.video.repository.VideoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VideoBackupCreator(
    private val handler: DatabaseHandler = Injekt.get(),
    private val profileProvider: ActiveProfileProvider = Injekt.get(),
    private val getVideoCategories: GetVideoCategories = Injekt.get(),
    private val videoRepository: VideoRepository = Injekt.get(),
    private val videoEpisodeRepository: VideoEpisodeRepository = Injekt.get(),
    private val videoHistoryRepository: VideoHistoryRepository = Injekt.get(),
    private val videoPlaybackStateRepository: VideoPlaybackStateRepository = Injekt.get(),
) {

    suspend operator fun invoke(videos: List<VideoTitle>, options: BackupOptions): List<BackupVideo> {
        return invoke(profileProvider.activeProfileId, videos, options)
    }

    suspend operator fun invoke(
        profileId: Long,
        videos: List<VideoTitle>,
        options: BackupOptions,
    ): List<BackupVideo> {
        return videos.map { backupVideo(profileId, it, options) }
    }

    private suspend fun backupVideo(
        profileId: Long,
        video: VideoTitle,
        options: BackupOptions,
    ): BackupVideo {
        val videoObject = video.toBackupVideo()

        if (options.chapters) {
            videoObject.episodes = getEpisodesForBackup(profileId, video.id)
                .map {
                    BackupVideoEpisode(
                        url = it.url,
                        name = it.name,
                        watched = it.watched,
                        completed = it.completed,
                        dateFetch = it.dateFetch,
                        dateUpload = it.dateUpload,
                        episodeNumber = it.episodeNumber.toFloat(),
                        sourceOrder = it.sourceOrder,
                        lastModifiedAt = it.lastModifiedAt,
                        version = it.version,
                    )
                }

            videoObject.playbackStates = videoObject.episodes.mapNotNull { backupEpisode ->
                val episode = getEpisodeByUrlForBackup(profileId, video.id, backupEpisode.url) ?: return@mapNotNull null
                val state = getPlaybackStateForBackup(profileId, episode.id) ?: return@mapNotNull null
                BackupVideoPlaybackState(
                    url = backupEpisode.url,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    completed = state.completed,
                    lastWatchedAt = state.lastWatchedAt,
                )
            }
        }

        if (options.categories) {
            val categoriesForVideo = if (profileId == profileProvider.activeProfileId) {
                getVideoCategories.await(video.id)
            } else {
                handler.awaitList {
                    categoriesQueries.getCategoriesByVideoId(profileId, video.id) { id, name, order, flags ->
                        Category(id = id, name = name, order = order, flags = flags)
                    }
                }
            }
            if (categoriesForVideo.isNotEmpty()) {
                videoObject.categories = categoriesForVideo.map { it.order }
            }
        }

        if (options.history) {
            val history = getHistoryForBackup(profileId, video.id)
            if (history.isNotEmpty()) {
                videoObject.history = history.mapNotNull { item ->
                    val episode = getEpisodeByIdForBackup(profileId, item.episodeId) ?: return@mapNotNull null
                    BackupVideoHistory(
                        url = episode.url,
                        lastWatched = item.watchedAt?.time ?: 0L,
                        watchedDuration = item.watchedDuration,
                    )
                }
            }
        }

        return videoObject
    }

    private suspend fun getEpisodesForBackup(profileId: Long, videoId: Long): List<VideoEpisode> {
        return if (profileId == profileProvider.activeProfileId) {
            videoEpisodeRepository.getEpisodesByVideoId(videoId)
        } else {
            handler.awaitList {
                video_episodesQueries.getEpisodesByVideoId(profileId, videoId, VideoEpisodeMapper::mapEpisode)
            }
        }
    }

    private suspend fun getEpisodeByUrlForBackup(
        profileId: Long,
        videoId: Long,
        episodeUrl: String,
    ): VideoEpisode? {
        return if (profileId == profileProvider.activeProfileId) {
            videoEpisodeRepository.getEpisodeByUrlAndVideoId(episodeUrl, videoId)
        } else {
            handler.awaitOneOrNull {
                video_episodesQueries.getEpisodeByUrlAndVideoId(profileId, episodeUrl, videoId, VideoEpisodeMapper::mapEpisode)
            }
        }
    }

    private suspend fun getEpisodeByIdForBackup(profileId: Long, episodeId: Long): VideoEpisode? {
        return if (profileId == profileProvider.activeProfileId) {
            videoEpisodeRepository.getEpisodeById(episodeId)
        } else {
            handler.awaitOneOrNull {
                video_episodesQueries.getEpisodeById(episodeId, profileId, VideoEpisodeMapper::mapEpisode)
            }
        }
    }

    private suspend fun getPlaybackStateForBackup(profileId: Long, episodeId: Long): VideoPlaybackState? {
        return if (profileId == profileProvider.activeProfileId) {
            videoPlaybackStateRepository.getByEpisodeId(episodeId)
        } else {
            handler.awaitOneOrNull {
                video_playback_stateQueries.getByEpisodeId(profileId, episodeId, VideoPlaybackStateMapper::mapState)
            }
        }
    }

    private suspend fun getHistoryForBackup(profileId: Long, videoId: Long): List<VideoHistory> {
        return if (profileId == profileProvider.activeProfileId) {
            videoHistoryRepository.getHistoryByVideoId(videoId)
        } else {
            handler.awaitList {
                video_historyQueries.getHistoryByVideoId(profileId, videoId, VideoHistoryMapper::mapHistory)
            }
        }
    }
}

private fun VideoTitle.toBackupVideo() = BackupVideo(
    url = this.url,
    title = this.title,
    displayName = this.displayName,
    description = this.description,
    genre = this.genre.orEmpty(),
    thumbnailUrl = this.thumbnailUrl,
    favorite = this.favorite,
    source = this.source,
    dateAdded = this.dateAdded,
    initialized = this.initialized,
    lastUpdate = this.lastUpdate,
    nextUpdate = this.nextUpdate,
    lastModifiedAt = this.lastModifiedAt,
    favoriteModifiedAt = this.favoriteModifiedAt,
    version = this.version,
    notes = this.notes,
)
