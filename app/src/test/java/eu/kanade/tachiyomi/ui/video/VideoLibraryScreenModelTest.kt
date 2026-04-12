package eu.kanade.tachiyomi.ui.video

import android.app.Application
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.service.VideoSourceManager
import tachiyomi.domain.video.model.VideoEpisode
import tachiyomi.domain.video.model.VideoHistory
import tachiyomi.domain.video.model.VideoHistoryUpdate
import tachiyomi.domain.video.model.VideoHistoryWithRelations
import tachiyomi.domain.video.model.VideoPlaybackState
import tachiyomi.domain.video.model.VideoTitle
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoHistoryRepository
import tachiyomi.domain.video.repository.VideoPlaybackStateRepository
import tachiyomi.domain.video.repository.VideoRepository
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VideoLibraryScreenModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `prefers in-progress episode as primary library action`() = runTest(dispatcher) {
        val video = VideoTitle.create().copy(
            id = 1L,
            source = 99L,
            title = "Video 1",
            favorite = true,
            initialized = true,
            url = "/video/1",
        )
        val firstEpisode = VideoEpisode.create().copy(id = 10L, videoId = 1L, url = "/e/1", name = "Episode 1", sourceOrder = 0L, completed = false)
        val secondEpisode = VideoEpisode.create().copy(id = 11L, videoId = 1L, url = "/e/2", name = "Episode 2", sourceOrder = 1L, completed = false)
        val inProgress = VideoPlaybackState(episodeId = secondEpisode.id, positionMs = 5_000L, durationMs = 10_000L, completed = false, lastWatchedAt = 100L)

        val model = VideoLibraryScreenModel(
            videoRepository = FakeVideoRepository(listOf(video)),
            videoEpisodeRepository = FakeVideoEpisodeRepository(listOf(firstEpisode, secondEpisode)),
            videoPlaybackStateRepository = FakeVideoPlaybackStateRepository(mapOf(video.id to listOf(inProgress))),
            videoHistoryRepository = FakeVideoHistoryRepository(),
            videoSourceManager = FakeVideoSourceManager(),
            application = mockk<Application>(relaxed = true),
        )

        advanceUntilIdle()

        eventually(2.seconds) {
            val state = model.state.value as VideoLibraryScreenModel.State.Success
            state.videos.single().primaryEpisodeId shouldBe secondEpisode.id
            state.videos.single().hasInProgress shouldBe true
        }
    }

    private class FakeVideoRepository(
        private val favorites: List<VideoTitle>,
    ) : VideoRepository {
        override suspend fun getVideoById(id: Long): VideoTitle = favorites.first { it.id == id }
        override suspend fun getVideoByIdAsFlow(id: Long): Flow<VideoTitle> = flowOf(favorites.first { it.id == id })
        override suspend fun getVideoByUrlAndSourceId(url: String, sourceId: Long): VideoTitle? = null
        override fun getVideoByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<VideoTitle?> = flowOf(null)
        override suspend fun getFavorites(): List<VideoTitle> = favorites
        override fun getFavoritesAsFlow(): Flow<List<VideoTitle>> = flowOf(favorites)
        override suspend fun getAllVideosByProfile(profileId: Long): List<VideoTitle> = favorites
        override suspend fun update(update: tachiyomi.domain.video.model.VideoTitleUpdate): Boolean = true
        override suspend fun updateAll(videoUpdates: List<tachiyomi.domain.video.model.VideoTitleUpdate>): Boolean = true
        override suspend fun insertNetworkVideo(videos: List<VideoTitle>): List<VideoTitle> = videos
        override suspend fun setVideoCategories(videoId: Long, categoryIds: List<Long>) = Unit
    }

    private class FakeVideoEpisodeRepository(
        private val episodes: List<VideoEpisode>,
    ) : VideoEpisodeRepository {
        override suspend fun addAll(episodes: List<VideoEpisode>): List<VideoEpisode> = episodes
        override suspend fun update(episodeUpdate: tachiyomi.domain.video.model.VideoEpisodeUpdate) = Unit
        override suspend fun updateAll(episodeUpdates: List<tachiyomi.domain.video.model.VideoEpisodeUpdate>) = Unit
        override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) = Unit
        override suspend fun getEpisodesByVideoId(videoId: Long): List<VideoEpisode> = episodes.filter { it.videoId == videoId }
        override fun getEpisodesByVideoIdAsFlow(videoId: Long): Flow<List<VideoEpisode>> = flowOf(episodes.filter { it.videoId == videoId })
        override fun getEpisodesByVideoIdsAsFlow(videoIds: List<Long>): Flow<List<VideoEpisode>> = flowOf(episodes.filter { it.videoId in videoIds })
        override suspend fun getEpisodeById(id: Long): VideoEpisode? = episodes.firstOrNull { it.id == id }
        override suspend fun getEpisodeByUrlAndVideoId(url: String, videoId: Long): VideoEpisode? = episodes.firstOrNull { it.url == url && it.videoId == videoId }
    }

    private class FakeVideoPlaybackStateRepository(
        private val statesByVideoId: Map<Long, List<VideoPlaybackState>>,
    ) : VideoPlaybackStateRepository {
        override suspend fun getByEpisodeId(episodeId: Long): VideoPlaybackState? = statesByVideoId.values.flatten().firstOrNull { it.episodeId == episodeId }
        override fun getByEpisodeIdAsFlow(episodeId: Long): Flow<VideoPlaybackState?> {
            return flowOf(statesByVideoId.values.flatten().firstOrNull { it.episodeId == episodeId })
        }
        override fun getByVideoIdAsFlow(videoId: Long): Flow<List<VideoPlaybackState>> = flowOf(statesByVideoId[videoId].orEmpty())
        override suspend fun upsert(state: VideoPlaybackState) = Unit
        override suspend fun upsertAndSyncEpisodeState(state: VideoPlaybackState) = Unit
    }

    private class FakeVideoHistoryRepository : VideoHistoryRepository {
        override fun getHistory(query: String): Flow<List<VideoHistoryWithRelations>> = emptyFlow()
        override suspend fun getLastHistory(): VideoHistoryWithRelations? = null
        override fun getLastHistoryAsFlow(): Flow<VideoHistoryWithRelations?> = flowOf(null)
        override suspend fun getTotalWatchedDuration(): Long = 0L
        override suspend fun getHistoryByVideoId(videoId: Long): List<VideoHistory> = emptyList()
        override suspend fun upsertHistory(historyUpdate: VideoHistoryUpdate) = Unit
        override suspend fun resetHistory(historyId: Long) = Unit
        override suspend fun resetHistoryByVideoId(videoId: Long) = Unit
        override suspend fun deleteAllHistory(): Boolean = true
    }

    private class FakeVideoSourceManager : VideoSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = emptyFlow<List<eu.kanade.tachiyomi.source.VideoCatalogueSource>>()
        override fun get(sourceKey: Long): eu.kanade.tachiyomi.source.VideoSource? = null
        override fun getCatalogueSources(): List<eu.kanade.tachiyomi.source.VideoCatalogueSource> = emptyList()
    }
}
