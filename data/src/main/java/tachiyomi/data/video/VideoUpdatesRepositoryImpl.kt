package tachiyomi.data.video

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.video.model.VideoUpdatesWithRelations
import tachiyomi.domain.video.repository.VideoUpdatesRepository

@OptIn(ExperimentalCoroutinesApi::class)
class VideoUpdatesRepositoryImpl(
    private val databaseHandler: DatabaseHandler,
    private val profileProvider: ActiveProfileProvider,
) : VideoUpdatesRepository {

    override suspend fun awaitWithWatched(
        watched: Boolean,
        after: Long,
        limit: Long,
    ): List<VideoUpdatesWithRelations> {
        return databaseHandler.awaitList {
            video_updatesQueries.getVideoUpdatesByWatchedStatus(
                profileId = profileProvider.activeProfileId,
                watched = watched,
                after = after,
                limit = limit,
                mapper = VideoUpdatesMapper::mapUpdatesWithRelations,
            )
        }
    }

    override fun subscribeWithWatched(
        watched: Boolean,
        after: Long,
        limit: Long,
    ): Flow<List<VideoUpdatesWithRelations>> {
        return profileProvider.activeProfileIdFlow.flatMapLatest { profileId ->
            databaseHandler.subscribeToList {
                video_updatesQueries.getVideoUpdatesByWatchedStatus(
                    profileId = profileId,
                    watched = watched,
                    after = after,
                    limit = limit,
                    mapper = VideoUpdatesMapper::mapUpdatesWithRelations,
                )
            }
        }
    }
}
