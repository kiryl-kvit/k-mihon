package eu.kanade.tachiyomi.ui.video.history

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
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
import tachiyomi.domain.video.model.VideoHistory
import tachiyomi.domain.video.model.VideoHistoryUpdate
import tachiyomi.domain.video.model.VideoHistoryWithRelations
import tachiyomi.domain.video.repository.VideoHistoryRepository
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class VideoHistoryScreenModelTest {

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
    fun `groups history rows by watched date`() = runTest(dispatcher) {
        val repository = FakeVideoHistoryRepository(
            listOf(
                historyWithRelations(1L, Date(1_700_000_000_000L)),
                historyWithRelations(2L, Date(1_700_000_000_000L)),
                historyWithRelations(3L, Date(1_699_000_000_000L)),
            ),
        )

        val model = VideoHistoryScreenModel(videoHistoryRepository = repository)

        advanceUntilIdle()

        val list = (model.state.value.list ?: emptyList())
        list.shouldHaveSize(5)
        list[0]::class shouldBe VideoHistoryUiModel.Header::class
        list[1]::class shouldBe VideoHistoryUiModel.Item::class
        list[2]::class shouldBe VideoHistoryUiModel.Item::class
        list[3]::class shouldBe VideoHistoryUiModel.Header::class
        list[4]::class shouldBe VideoHistoryUiModel.Item::class
    }

    private fun historyWithRelations(id: Long, watchedAt: Date): VideoHistoryWithRelations {
        return VideoHistoryWithRelations(
            id = id,
            episodeId = id,
            videoId = id,
            title = "Video $id",
            episodeName = "Episode $id",
            coverData = tachiyomi.domain.manga.model.MangaCover(
                mangaId = id,
                sourceId = 1L,
                isMangaFavorite = false,
                url = "https://example.com/$id.jpg",
                lastModified = 0L,
            ),
            watchedDuration = 0L,
            watchedAt = watchedAt,
        )
    }

    private class FakeVideoHistoryRepository(
        private val items: List<VideoHistoryWithRelations>,
    ) : VideoHistoryRepository {
        override fun getHistory(query: String): Flow<List<VideoHistoryWithRelations>> = flowOf(items)

        override suspend fun getLastHistory(): VideoHistoryWithRelations? = items.firstOrNull()

        override fun getLastHistoryAsFlow(): Flow<VideoHistoryWithRelations?> = flowOf(items.firstOrNull())

        override suspend fun getTotalWatchedDuration(): Long = 0L

        override suspend fun getHistoryByVideoId(videoId: Long): List<VideoHistory> = emptyList()

        override suspend fun upsertHistory(historyUpdate: VideoHistoryUpdate) = Unit

        override suspend fun resetHistory(historyId: Long) = Unit

        override suspend fun resetHistoryByVideoId(videoId: Long) = Unit

        override suspend fun deleteAllHistory(): Boolean = true
    }
}
