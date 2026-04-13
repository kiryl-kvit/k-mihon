package eu.kanade.tachiyomi.ui.anime

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
import tachiyomi.domain.source.service.AnimeSourceManager
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeLibraryScreenModelTest {

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
        val video = AnimeTitle.create().copy(
            id = 1L,
            source = 99L,
            title = "Video 1",
            favorite = true,
            initialized = true,
            url = "/video/1",
        )
        val firstEpisode = AnimeEpisode.create().copy(id = 10L, animeId = 1L, url = "/e/1", name = "Episode 1", sourceOrder = 0L, completed = false)
        val secondEpisode = AnimeEpisode.create().copy(id = 11L, animeId = 1L, url = "/e/2", name = "Episode 2", sourceOrder = 1L, completed = false)
        val inProgress = AnimePlaybackState(episodeId = secondEpisode.id, positionMs = 5_000L, durationMs = 10_000L, completed = false, lastWatchedAt = 100L)

        val model = AnimeLibraryScreenModel(
            animeRepository = FakeAnimeRepository(listOf(video)),
            animeEpisodeRepository = FakeAnimeEpisodeRepository(listOf(firstEpisode, secondEpisode)),
            animePlaybackStateRepository = FakeAnimePlaybackStateRepository(mapOf(video.id to listOf(inProgress))),
            animeHistoryRepository = FakeAnimeHistoryRepository(),
            animeSourceManager = FakeAnimeSourceManager(),
            application = mockk<Application>(relaxed = true),
        )

        advanceUntilIdle()

        eventually(2.seconds) {
            val state = model.state.value as AnimeLibraryScreenModel.State.Success
            state.animes.single().primaryEpisodeId shouldBe secondEpisode.id
            state.animes.single().hasInProgress shouldBe true
        }
    }

    private class FakeAnimeRepository(
        private val favorites: List<AnimeTitle>,
    ) : AnimeRepository {
        override suspend fun getAnimeById(id: Long): AnimeTitle = favorites.first { it.id == id }
        override suspend fun getAnimeByIdAsFlow(id: Long): Flow<AnimeTitle> = flowOf(favorites.first { it.id == id })
        override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): AnimeTitle? = null
        override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<AnimeTitle?> = flowOf(null)
        override suspend fun getFavorites(): List<AnimeTitle> = favorites
        override fun getFavoritesAsFlow(): Flow<List<AnimeTitle>> = flowOf(favorites)
        override suspend fun getAllAnimeByProfile(profileId: Long): List<AnimeTitle> = favorites
        override suspend fun update(update: tachiyomi.domain.anime.model.AnimeTitleUpdate): Boolean = true
        override suspend fun updateAll(videoUpdates: List<tachiyomi.domain.anime.model.AnimeTitleUpdate>): Boolean = true
        override suspend fun insertNetworkAnime(animes: List<AnimeTitle>): List<AnimeTitle> = animes
        override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) = Unit
    }

    private class FakeAnimeEpisodeRepository(
        private val episodes: List<AnimeEpisode>,
    ) : AnimeEpisodeRepository {
        override suspend fun addAll(episodes: List<AnimeEpisode>): List<AnimeEpisode> = episodes
        override suspend fun update(episodeUpdate: tachiyomi.domain.anime.model.AnimeEpisodeUpdate) = Unit
        override suspend fun updateAll(episodeUpdates: List<tachiyomi.domain.anime.model.AnimeEpisodeUpdate>) = Unit
        override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) = Unit
        override suspend fun getEpisodesByAnimeId(animeId: Long): List<AnimeEpisode> = episodes.filter { it.animeId == animeId }
        override fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeEpisode>> = flowOf(episodes.filter { it.animeId == animeId })
        override fun getEpisodesByAnimeIdsAsFlow(videoIds: List<Long>): Flow<List<AnimeEpisode>> = flowOf(episodes.filter { it.animeId in videoIds })
        override suspend fun getEpisodeById(id: Long): AnimeEpisode? = episodes.firstOrNull { it.id == id }
        override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): AnimeEpisode? = episodes.firstOrNull { it.url == url && it.animeId == animeId }
    }

    private class FakeAnimePlaybackStateRepository(
        private val statesByVideoId: Map<Long, List<AnimePlaybackState>>,
    ) : AnimePlaybackStateRepository {
        override suspend fun getByEpisodeId(episodeId: Long): AnimePlaybackState? = statesByVideoId.values.flatten().firstOrNull { it.episodeId == episodeId }
        override fun getByEpisodeIdAsFlow(episodeId: Long): Flow<AnimePlaybackState?> {
            return flowOf(statesByVideoId.values.flatten().firstOrNull { it.episodeId == episodeId })
        }
        override fun getByAnimeIdAsFlow(animeId: Long): Flow<List<AnimePlaybackState>> = flowOf(statesByVideoId[animeId].orEmpty())
        override suspend fun upsert(state: AnimePlaybackState) = Unit
        override suspend fun upsertAndSyncEpisodeState(state: AnimePlaybackState) = Unit
    }

    private class FakeAnimeHistoryRepository : AnimeHistoryRepository {
        override fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>> = emptyFlow()
        override suspend fun getLastHistory(): AnimeHistoryWithRelations? = null
        override fun getLastHistoryAsFlow(): Flow<AnimeHistoryWithRelations?> = flowOf(null)
        override suspend fun getTotalWatchedDuration(): Long = 0L
        override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> = emptyList()
        override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) = Unit
        override suspend fun resetHistory(historyId: Long) = Unit
        override suspend fun resetHistoryByAnimeId(animeId: Long) = Unit
        override suspend fun deleteAllHistory(): Boolean = true
    }

    private class FakeAnimeSourceManager : AnimeSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = emptyFlow<List<eu.kanade.tachiyomi.source.AnimeCatalogueSource>>()
        override fun get(sourceKey: Long): eu.kanade.tachiyomi.source.AnimeSource? = null
        override fun getCatalogueSources(): List<eu.kanade.tachiyomi.source.AnimeCatalogueSource> = emptyList()
    }
}
