package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupVideo
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetVideoCategories
import tachiyomi.domain.video.model.VideoEpisodeUpdate
import tachiyomi.domain.video.model.VideoHistoryUpdate
import tachiyomi.domain.video.model.VideoPlaybackState
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoHistoryRepository
import tachiyomi.domain.video.repository.VideoPlaybackStateRepository
import tachiyomi.domain.video.repository.VideoRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

class VideoRestorer(
    private val handler: DatabaseHandler = Injekt.get(),
    private val profileProvider: ActiveProfileProvider = Injekt.get(),
    private val getVideoCategories: GetVideoCategories = Injekt.get(),
    private val videoRepository: VideoRepository = Injekt.get(),
    private val videoEpisodeRepository: VideoEpisodeRepository = Injekt.get(),
    private val videoHistoryRepository: VideoHistoryRepository = Injekt.get(),
    private val videoPlaybackStateRepository: VideoPlaybackStateRepository = Injekt.get(),
) {

    suspend fun sortByNew(backupVideos: List<BackupVideo>): List<BackupVideo> {
        val urlsBySource = videoRepository.getAllVideosByProfile(profileProvider.activeProfileId)
            .groupBy({ it.source }, { it.url })

        return backupVideos
            .sortedWith(
                compareBy<BackupVideo> { it.url in urlsBySource[it.source].orEmpty() }
                    .then(compareByDescending { it.lastModifiedAt }),
            )
    }

    suspend fun restore(
        backupVideo: BackupVideo,
        backupCategories: List<BackupCategory>,
    ) {
        handler.await(inTransaction = true) {
            val dbVideo = videoRepository.getVideoByUrlAndSourceId(backupVideo.url, backupVideo.source)
            val video = backupVideo.getVideoImpl()
            val restoredVideo = if (dbVideo == null) {
                videoRepository.insertNetworkVideo(listOf(video)).first()
            } else {
                videoRepository.update(
                    tachiyomi.domain.video.model.VideoTitleUpdate(
                        id = dbVideo.id,
                        source = video.source,
                        url = video.url,
                        title = video.title,
                        displayName = video.displayName,
                        description = video.description,
                        genre = video.genre,
                        thumbnailUrl = video.thumbnailUrl,
                        favorite = dbVideo.favorite || video.favorite,
                        initialized = dbVideo.initialized || video.initialized,
                        lastUpdate = video.lastUpdate,
                        nextUpdate = video.nextUpdate,
                        dateAdded = video.dateAdded,
                        version = maxOf(dbVideo.version, video.version),
                        notes = video.notes,
                    ),
                )
                videoRepository.getVideoById(dbVideo.id)
            }

            restoreCategories(restoredVideo.id, backupVideo.categories, backupCategories)
            restoreEpisodes(restoredVideo.id, backupVideo)
            restoreHistory(restoredVideo.id, backupVideo)
        }
    }

    private suspend fun restoreCategories(
        videoId: Long,
        categories: List<Long>,
        backupCategories: List<BackupCategory>,
    ) {
        val dbCategories = getVideoCategories.await(videoId)
        val dbCategoriesByName = dbCategories.associateBy { it.name }
        val backupCategoriesByOrder = backupCategories.associateBy { it.order }

        val videoCategoriesToUpdate = categories.mapNotNull { backupCategoryOrder ->
            backupCategoriesByOrder[backupCategoryOrder]?.let { backupCategory ->
                dbCategoriesByName[backupCategory.name]?.id
            }
        }

        if (videoCategoriesToUpdate.isNotEmpty()) {
            videoRepository.setVideoCategories(videoId, videoCategoriesToUpdate)
        }
    }

    private suspend fun restoreEpisodes(videoId: Long, backupVideo: BackupVideo) {
        val dbEpisodesByUrl = videoEpisodeRepository.getEpisodesByVideoId(videoId).associateBy { it.url }

        val toInsert = mutableListOf<tachiyomi.domain.video.model.VideoEpisode>()
        val toUpdate = mutableListOf<VideoEpisodeUpdate>()

        backupVideo.episodes.forEach { backupEpisode ->
            val episode = backupEpisode.toEpisodeImpl()
            val dbEpisode = dbEpisodesByUrl[episode.url]
            if (dbEpisode == null) {
                toInsert += episode.copy(videoId = videoId)
            } else {
                toUpdate += VideoEpisodeUpdate(
                    id = dbEpisode.id,
                    watched = dbEpisode.watched || episode.watched,
                    completed = dbEpisode.completed || episode.completed,
                    version = maxOf(dbEpisode.version, episode.version),
                )
            }
        }

        if (toInsert.isNotEmpty()) {
            videoEpisodeRepository.addAll(toInsert)
        }
        if (toUpdate.isNotEmpty()) {
            videoEpisodeRepository.updateAll(toUpdate)
        }

        backupVideo.playbackStates.forEach { backupState ->
            val episode = videoEpisodeRepository.getEpisodeByUrlAndVideoId(backupState.url, videoId) ?: return@forEach
            videoPlaybackStateRepository.upsertAndSyncEpisodeState(
                VideoPlaybackState(
                    episodeId = episode.id,
                    positionMs = backupState.positionMs,
                    durationMs = backupState.durationMs,
                    completed = backupState.completed,
                    lastWatchedAt = backupState.lastWatchedAt,
                ),
            )
        }
    }

    private suspend fun restoreHistory(videoId: Long, backupVideo: BackupVideo) {
        backupVideo.history.forEach { history ->
            val episode = videoEpisodeRepository.getEpisodeByUrlAndVideoId(history.url, videoId) ?: return@forEach
            val item = history.getHistoryImpl()
            videoHistoryRepository.upsertHistory(
                VideoHistoryUpdate(
                    episodeId = episode.id,
                    watchedAt = item.watchedAt ?: Date(0L),
                    sessionWatchedDuration = item.watchedDuration,
                ),
            )
        }
    }
}
