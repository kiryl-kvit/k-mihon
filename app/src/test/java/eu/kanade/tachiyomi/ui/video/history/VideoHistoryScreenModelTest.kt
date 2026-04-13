package eu.kanade.tachiyomi.ui.anime.history

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
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeHistoryScreenModelTest {

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
        val repository = FakeAnimeHistoryRepository(
            listOf(
                historyWithRelations(1L, Date(1_700_000_000_000L)),
                historyWithRelations(2L, Date(1_700_000_000_000L)),
                historyWithRelations(3L, Date(1_699_000_000_000L)),
            ),
        )

        val model = AnimeHistoryScreenModel(animeHistoryRepository = repository)

        advanceUntilIdle()

        val list = (model.state.value.list ?: emptyList())
        list.shouldHaveSize(5)
        list[0]::class shouldBe AnimeHistoryUiModel.Header::class
        list[1]::class shouldBe AnimeHistoryUiModel.Item::class
        list[2]::class shouldBe AnimeHistoryUiModel.Item::class
        list[3]::class shouldBe AnimeHistoryUiModel.Header::class
        list[4]::class shouldBe AnimeHistoryUiModel.Item::class
    }

    private fun historyWithRelations(id: Long, watchedAt: Date): AnimeHistoryWithRelations {
        return AnimeHistoryWithRelations(
            id = id,
            episodeId = id,
            animeId = id,
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

    private class FakeAnimeHistoryRepository(
        private val items: List<AnimeHistoryWithRelations>,
    ) : AnimeHistoryRepository {
        override fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>> = flowOf(items)

        override suspend fun getLastHistory(): AnimeHistoryWithRelations? = items.firstOrNull()

        override fun getLastHistoryAsFlow(): Flow<AnimeHistoryWithRelations?> = flowOf(items.firstOrNull())

        override suspend fun getTotalWatchedDuration(): Long = 0L

        override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> = emptyList()

        override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) = Unit

        override suspend fun resetHistory(historyId: Long) = Unit

        override suspend fun resetHistoryByAnimeId(animeId: Long) = Unit

        override suspend fun deleteAllHistory(): Boolean = true
    }
}
