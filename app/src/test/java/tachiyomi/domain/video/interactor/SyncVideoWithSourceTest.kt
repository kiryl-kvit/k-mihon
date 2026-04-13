package tachiyomi.domain.anime.interactor

import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.AnimeSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.AnimesPage
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.service.AnimeSourceManager
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeEpisodeUpdate
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeTitleUpdate
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeRepository

class SyncAnimeWithSourceTest {

    @Test
    fun `sync updates video details and upserts episodes`() = runTest {
        val localVideo = AnimeTitle.create().copy(
            id = 1L,
            source = 99L,
            url = "/animes/1",
            title = "Old title",
            initialized = false,
        )
        val existingEpisode = AnimeEpisode.create().copy(
            id = 10L,
            animeId = localVideo.id,
            url = "/animes/1/episodes/1",
            name = "Old episode",
            watched = true,
            completed = true,
            dateUpload = 1L,
            episodeNumber = 5.0,
            sourceOrder = 7L,
        )
        val source = FakeAnimeSource(
            details = SAnime.create().also {
                it.url = localVideo.url
                it.title = "New title"
                it.description = "New description"
                it.genre = "Action, Drama"
                it.thumbnail_url = "https://cdn.example.com/video.jpg"
                it.initialized = true
            },
            episodes = listOf(
                SEpisode.create().also {
                    it.url = existingEpisode.url
                    it.name = "Episode 1"
                    it.date_upload = 123L
                    it.episode_number = 1f
                },
                SEpisode.create().also {
                    it.url = "/animes/1/episodes/2"
                    it.name = "Episode 2"
                    it.date_upload = 456L
                    it.episode_number = 2f
                },
            ),
        )
        val videoRepository = FakeAnimeRepository(localVideo)
        val episodeRepository = FakeAnimeEpisodeRepository(listOf(existingEpisode))

        SyncAnimeWithSource(
            animeRepository = videoRepository,
            animeEpisodeRepository = episodeRepository,
            animeSourceManager = FakeAnimeSourceManager(source),
        )(localVideo)

        videoRepository.updates.single() shouldBe AnimeTitleUpdate(
            id = localVideo.id,
            title = "New title",
            description = "New description",
            genre = listOf("Action", "Drama"),
            thumbnailUrl = "https://cdn.example.com/video.jpg",
            initialized = true,
        )

        episodeRepository.updates.single() shouldBe AnimeEpisodeUpdate(
            id = existingEpisode.id,
            name = "Episode 1",
            dateUpload = 123L,
            episodeNumber = 1.0,
            sourceOrder = 0L,
        )
        episodeRepository.inserts shouldHaveSize 1
        episodeRepository.inserts.single().animeId shouldBe localVideo.id
        episodeRepository.inserts.single().url shouldBe "/animes/1/episodes/2"
        episodeRepository.inserts.single().name shouldBe "Episode 2"
        episodeRepository.inserts.single().episodeNumber shouldBe 2.0
        episodeRepository.inserts.single().sourceOrder shouldBe 1L
    }

    private class FakeAnimeRepository(
        private val video: AnimeTitle,
    ) : AnimeRepository {
        val updates = mutableListOf<AnimeTitleUpdate>()

        override suspend fun getAnimeById(id: Long): AnimeTitle = video

        override suspend fun getAnimeByIdAsFlow(id: Long): Flow<AnimeTitle> = emptyFlow()

        override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): AnimeTitle? = error("Not used")

        override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<AnimeTitle?> = emptyFlow()

        override suspend fun getFavorites(): List<AnimeTitle> = error("Not used")

        override fun getFavoritesAsFlow(): Flow<List<AnimeTitle>> = flowOf(emptyList())

        override suspend fun getAllAnimeByProfile(profileId: Long): List<AnimeTitle> = error("Not used")

        override suspend fun update(update: AnimeTitleUpdate): Boolean {
            updates += update
            return true
        }

        override suspend fun updateAll(videoUpdates: List<AnimeTitleUpdate>): Boolean = error("Not used")

        override suspend fun insertNetworkAnime(animes: List<AnimeTitle>): List<AnimeTitle> = error("Not used")

        override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) = error("Not used")
    }

    private class FakeAnimeEpisodeRepository(
        existingEpisodes: List<AnimeEpisode>,
    ) : AnimeEpisodeRepository {
        private val episodes = existingEpisodes.associateBy { it.url }
        val inserts = mutableListOf<AnimeEpisode>()
        val updates = mutableListOf<AnimeEpisodeUpdate>()

        override suspend fun addAll(episodes: List<AnimeEpisode>): List<AnimeEpisode> {
            inserts += episodes
            return episodes
        }

        override suspend fun update(episodeUpdate: AnimeEpisodeUpdate) = error("Not used")

        override suspend fun updateAll(episodeUpdates: List<AnimeEpisodeUpdate>) {
            updates += episodeUpdates
        }

        override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) = error("Not used")

        override suspend fun getEpisodesByAnimeId(animeId: Long): List<AnimeEpisode> = episodes.values.toList()

        override fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeEpisode>> = emptyFlow()

        override fun getEpisodesByAnimeIdsAsFlow(videoIds: List<Long>): Flow<List<AnimeEpisode>> = emptyFlow()

        override suspend fun getEpisodeById(id: Long): AnimeEpisode? = error("Not used")

        override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): AnimeEpisode? = error("Not used")
    }

    private class FakeAnimeSourceManager(
        private val source: AnimeSource,
    ) : AnimeSourceManager {
        override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)

        override val catalogueSources: Flow<List<AnimeCatalogueSource>> = emptyFlow()

        override fun get(sourceKey: Long): AnimeSource? = source.takeIf { it.id == sourceKey }

        override fun getCatalogueSources(): List<AnimeCatalogueSource> = emptyList()
    }

    private class FakeAnimeSource(
        private val details: SAnime,
        private val episodes: List<SEpisode>,
    ) : AnimeCatalogueSource {
        override val id: Long = 99L
        override val name: String = "Fake"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true

        override suspend fun getPopularAnime(page: Int): AnimesPage = error("Not used")

        override suspend fun getSearchAnime(page: Int, query: String, filters: FilterList): AnimesPage = error("Not used")

        override suspend fun getLatestUpdates(page: Int): AnimesPage = error("Not used")

        override fun getFilterList(): FilterList = FilterList()

        override suspend fun getAnimeDetails(anime: SAnime): SAnime = details

        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = episodes

        override suspend fun getStreamList(episode: SEpisode): List<VideoStream> = error("Not used")
    }
}
