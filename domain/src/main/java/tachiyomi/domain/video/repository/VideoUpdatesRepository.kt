package tachiyomi.domain.video.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.video.model.VideoUpdatesWithRelations

interface VideoUpdatesRepository {

    suspend fun awaitWithWatched(watched: Boolean, after: Long, limit: Long): List<VideoUpdatesWithRelations>

    fun subscribeWithWatched(watched: Boolean, after: Long, limit: Long): Flow<List<VideoUpdatesWithRelations>>
}
