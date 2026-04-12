package tachiyomi.domain.video.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.video.model.VideoPlaybackState

interface VideoPlaybackStateRepository {

    suspend fun getByEpisodeId(episodeId: Long): VideoPlaybackState?

    fun getByEpisodeIdAsFlow(episodeId: Long): Flow<VideoPlaybackState?>

    suspend fun upsert(state: VideoPlaybackState)

    suspend fun upsertAndSyncEpisodeState(state: VideoPlaybackState)
}
