package eu.kanade.tachiyomi.ui.video.updates

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
import tachiyomi.domain.video.model.VideoUpdatesWithRelations
import tachiyomi.domain.video.repository.VideoUpdatesRepository
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class VideoUpdatesScreenModelTest {

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
        val repository = FakeVideoUpdatesRepository(
            listOf(
                update(1L, 1_700_000_000_000L),
                update(2L, 1_700_000_000_000L),
                update(3L, 1_699_000_000_000L),
            ),
        )

        val model = VideoUpdatesScreenModel(
            videoUpdatesRepository = repository,
            application = mockk<Application>(relaxed = true),
        )

        advanceUntilIdle()

        eventually(2.seconds) {
            val list = model.state.value.list
            list.shouldHaveSize(5)
            list[0]::class shouldBe VideoUpdatesUiModel.Header::class
            list[1]::class shouldBe VideoUpdatesUiModel.Item::class
            list[2]::class shouldBe VideoUpdatesUiModel.Item::class
            list[3]::class shouldBe VideoUpdatesUiModel.Header::class
            list[4]::class shouldBe VideoUpdatesUiModel.Item::class
        }
    }

    private fun update(id: Long, dateFetch: Long): VideoUpdatesWithRelations {
        return VideoUpdatesWithRelations(
            episodeId = id,
            videoId = id,
            episodeName = "Episode $id",
            episodeUrl = "/episode/$id",
            videoTitle = "Video $id",
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

    private class FakeVideoUpdatesRepository(
        private val items: List<VideoUpdatesWithRelations>,
    ) : VideoUpdatesRepository {
        override suspend fun awaitWithWatched(watched: Boolean, after: Long, limit: Long): List<VideoUpdatesWithRelations> {
            return items
        }

        override fun subscribeWithWatched(watched: Boolean, after: Long, limit: Long): Flow<List<VideoUpdatesWithRelations>> {
            return flowOf(items)
        }
    }
}
