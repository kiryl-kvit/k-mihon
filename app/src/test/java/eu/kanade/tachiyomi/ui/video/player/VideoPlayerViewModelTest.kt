package eu.kanade.tachiyomi.ui.video.player

import androidx.lifecycle.SavedStateHandle
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoRequest
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimePlaybackPreferences
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.PlayerQualityMode
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackPreferencesRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository

@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest {

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
    fun `init exposes resume position from saved playback state`() = runTest(dispatcher) {
        val playbackRepository = FakeAnimePlaybackStateRepository(
            existingState = AnimePlaybackState(
                episodeId = 2L,
                positionMs = 12_345L,
                durationMs = 99_999L,
                completed = false,
                lastWatchedAt = 500L,
            ),
        )
        val historyRepository = FakeAnimeHistoryRepository()
        val viewModel = VideoPlayerViewModel(
            savedState = SavedStateHandle(),
            resolveVideoStream = fakeResolver(animeId = 1L, episodeId = 2L),
            animePlaybackPreferencesRepository = FakeAnimePlaybackPreferencesRepository(),
            videoPlaybackStateRepository = playbackRepository,
            videoHistoryRepository = historyRepository,
            resolveDispatcher = dispatcher,
            persistenceDispatcher = dispatcher,
        )

        viewModel.init(animeId = 1L, episodeId = 2L)
        advanceUntilIdle()

        val state = viewModel.state.value as VideoPlayerViewModel.State.Ready
        state.episodeId shouldBe 2L
        state.resumePositionMs shouldBe 12_345L
        state.streamUrl shouldBe "https://cdn.example.com/video.m3u8"
        playbackRepository.requestedEpisodeIds shouldBe listOf(2L)
        historyRepository.upserts.size shouldBe 0
    }

    @Test
    fun `persist playback writes playback state and history delta`() = runTest(dispatcher) {
        val playbackRepository = FakeAnimePlaybackStateRepository(existingState = null)
        val historyRepository = FakeAnimeHistoryRepository()
        val viewModel = VideoPlayerViewModel(
            savedState = SavedStateHandle(),
            resolveVideoStream = fakeResolver(animeId = 1L, episodeId = 2L),
            animePlaybackPreferencesRepository = FakeAnimePlaybackPreferencesRepository(),
            videoPlaybackStateRepository = playbackRepository,
            videoHistoryRepository = historyRepository,
            resolveDispatcher = dispatcher,
            persistenceDispatcher = dispatcher,
        )

        viewModel.init(animeId = 1L, episodeId = 2L)
        advanceUntilIdle()

        viewModel.persistPlayback(positionMs = 15_000L, durationMs = 100_000L)
        advanceUntilIdle()

        playbackRepository.upserts.single().episodeId shouldBe 2L
        playbackRepository.upserts.single().positionMs shouldBe 15_000L
        playbackRepository.upserts.single().durationMs shouldBe 100_000L
        playbackRepository.upserts.single().completed shouldBe false
        historyRepository.upserts.single().episodeId shouldBe 2L
        historyRepository.upserts.single().sessionWatchedDuration shouldBe 15_000L
    }

    @Test
    fun `reset playback baseline prevents duplicate history after seek`() = runTest(dispatcher) {
        val playbackRepository = FakeAnimePlaybackStateRepository(existingState = null)
        val historyRepository = FakeAnimeHistoryRepository()
        val viewModel = VideoPlayerViewModel(
            savedState = SavedStateHandle(),
            resolveVideoStream = fakeResolver(animeId = 1L, episodeId = 2L),
            animePlaybackPreferencesRepository = FakeAnimePlaybackPreferencesRepository(),
            videoPlaybackStateRepository = playbackRepository,
            videoHistoryRepository = historyRepository,
            resolveDispatcher = dispatcher,
            persistenceDispatcher = dispatcher,
        )

        viewModel.init(animeId = 1L, episodeId = 2L)
        advanceUntilIdle()

        viewModel.persistPlayback(positionMs = 30_000L, durationMs = 100_000L)
        advanceUntilIdle()
        viewModel.resetPlaybackBaseline(positionMs = 10_000L)
        viewModel.persistPlayback(positionMs = 12_000L, durationMs = 100_000L)
        advanceUntilIdle()

        historyRepository.upserts.size shouldBe 2
        historyRepository.upserts[0].sessionWatchedDuration shouldBe 30_000L
        historyRepository.upserts[1].sessionWatchedDuration shouldBe 2_000L
    }

    private fun fakeResolver(animeId: Long, episodeId: Long): VideoStreamResolver {
        val video = AnimeTitle.create().copy(
            id = animeId,
            source = 99L,
            title = "Video $animeId",
            initialized = true,
            url = "/video/$animeId",
        )
        val episode = AnimeEpisode.create().copy(
            id = episodeId,
            animeId = animeId,
            url = "/episode/$episodeId",
            name = "Episode $episodeId",
            episodeNumber = 1.0,
        )
        val stream = VideoStream(
            request = VideoRequest(url = "https://cdn.example.com/video.m3u8"),
            label = "Auto",
            type = VideoStreamType.HLS,
        )

        return object : VideoStreamResolver {
            override suspend fun invoke(
                animeId: Long,
                episodeId: Long,
                selection: VideoPlaybackSelection?,
            ): ResolveVideoStream.Result {
                return ResolveVideoStream.Result.Success(
                    video = video,
                    episode = episode,
                    playbackData = VideoPlaybackData(
                        selection = selection ?: VideoPlaybackSelection(),
                        streams = listOf(stream),
                    ),
                    stream = stream,
                    savedPreferences = AnimePlaybackPreferences(
                        animeId = animeId,
                        dubKey = null,
                        streamKey = null,
                        sourceQualityKey = null,
                        playerQualityMode = PlayerQualityMode.AUTO,
                        playerQualityHeight = null,
                        updatedAt = 0L,
                    ),
                )
            }
        }
    }

    private class FakeAnimePlaybackPreferencesRepository : AnimePlaybackPreferencesRepository {
        override suspend fun getByAnimeId(animeId: Long): AnimePlaybackPreferences? = null

        override fun getByAnimeIdAsFlow(animeId: Long): Flow<AnimePlaybackPreferences?> = emptyFlow()

        override suspend fun upsert(preferences: AnimePlaybackPreferences) = Unit
    }

    private class FakeAnimePlaybackStateRepository(
        private val existingState: AnimePlaybackState?,
    ) : AnimePlaybackStateRepository {
        val requestedEpisodeIds = mutableListOf<Long>()
        val upserts = mutableListOf<AnimePlaybackState>()

        override suspend fun getByEpisodeId(episodeId: Long): AnimePlaybackState? {
            requestedEpisodeIds += episodeId
            return existingState?.takeIf { it.episodeId == episodeId }
        }

        override fun getByEpisodeIdAsFlow(episodeId: Long): Flow<AnimePlaybackState?> = emptyFlow()

        override fun getByAnimeIdAsFlow(animeId: Long): Flow<List<AnimePlaybackState>> {
            return flowOf(existingState?.let(::listOf) ?: emptyList())
        }

        override suspend fun upsert(state: AnimePlaybackState) {
            error("Not used")
        }

        override suspend fun upsertAndSyncEpisodeState(state: AnimePlaybackState) {
            upserts += state
        }
    }

    private class FakeAnimeHistoryRepository : AnimeHistoryRepository {
        val upserts = mutableListOf<AnimeHistoryUpdate>()

        override fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>> = emptyFlow()

        override suspend fun getLastHistory(): AnimeHistoryWithRelations? = error("Not used")

        override fun getLastHistoryAsFlow(): Flow<AnimeHistoryWithRelations?> = emptyFlow()

        override suspend fun getTotalWatchedDuration(): Long = error("Not used")

        override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> = error("Not used")

        override suspend fun resetHistory(historyId: Long) = error("Not used")

        override suspend fun resetHistoryByAnimeId(animeId: Long) = error("Not used")

        override suspend fun deleteAllHistory(): Boolean = error("Not used")

        override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) {
            upserts += historyUpdate
        }
    }
}
