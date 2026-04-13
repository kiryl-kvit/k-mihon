package eu.kanade.tachiyomi.ui.anime.updates

import android.app.Application
import io.kotest.assertions.nondeterministic.eventually
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
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.anime.model.AnimeUpdatesWithRelations
import tachiyomi.domain.anime.repository.AnimeUpdatesRepository
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeUpdatesScreenModelTest {

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
    fun `groups updates by fetch date`() = runTest(dispatcher) {
        val repository = FakeAnimeUpdatesRepository(
            listOf(
                update(1L, 1_700_000_000_000L),
                update(2L, 1_700_000_000_000L),
                update(3L, 1_699_000_000_000L),
            ),
        )

        val model = AnimeUpdatesScreenModel(
            animeUpdatesRepository = repository,
            application = mockk<Application>(relaxed = true),
        )

        advanceUntilIdle()

        eventually(2.seconds) {
            val list = model.state.value.list
            list.shouldHaveSize(5)
            list[0]::class shouldBe AnimeUpdatesUiModel.Header::class
            list[1]::class shouldBe AnimeUpdatesUiModel.Item::class
            list[2]::class shouldBe AnimeUpdatesUiModel.Item::class
            list[3]::class shouldBe AnimeUpdatesUiModel.Header::class
            list[4]::class shouldBe AnimeUpdatesUiModel.Item::class
        }
    }

    private fun update(id: Long, dateFetch: Long): AnimeUpdatesWithRelations {
        return AnimeUpdatesWithRelations(
            episodeId = id,
            animeId = id,
            episodeName = "Episode $id",
            episodeUrl = "/episode/$id",
            animeTitle = "Video $id",
            coverData = tachiyomi.domain.manga.model.MangaCover(
                mangaId = id,
                sourceId = 1L,
                isMangaFavorite = false,
                url = "https://example.com/$id.jpg",
                lastModified = 0L,
            ),
            dateFetch = dateFetch,
            watched = false,
            completed = false,
            sourceId = 1L,
        )
    }

    private class FakeAnimeUpdatesRepository(
        private val items: List<AnimeUpdatesWithRelations>,
    ) : AnimeUpdatesRepository {
        override suspend fun awaitWithWatched(watched: Boolean, after: Long, limit: Long): List<AnimeUpdatesWithRelations> {
            return items
        }

        override fun subscribeWithWatched(watched: Boolean, after: Long, limit: Long): Flow<List<AnimeUpdatesWithRelations>> {
            return flowOf(items)
        }
    }
}
