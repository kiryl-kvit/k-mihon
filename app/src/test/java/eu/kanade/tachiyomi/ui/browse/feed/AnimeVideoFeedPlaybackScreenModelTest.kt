package eu.kanade.tachiyomi.ui.browse.feed

import eu.kanade.tachiyomi.source.AnimeSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoRequest
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import eu.kanade.tachiyomi.ui.video.player.ResolveVideoStream
import eu.kanade.tachiyomi.ui.video.player.VideoStreamResolver
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import tachiyomi.domain.anime.interactor.SyncAnimeWithSource
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeEpisodeUpdate
import tachiyomi.domain.anime.model.AnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryUpdate
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.domain.anime.model.AnimePlaybackPreferences
import tachiyomi.domain.anime.model.AnimePlaybackState
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeTitleUpdate
import tachiyomi.domain.anime.model.PlayerQualityMode
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeHistoryRepository
import tachiyomi.domain.anime.repository.AnimePlaybackStateRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.source.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

class AnimeVideoFeedPlaybackScreenModelTest {

    @Test
    fun `load uses local episodes without syncing source`() = runTest {
        val anime = anime(id = 1L)
        val episode = episode(id = 10L, animeId = anime.id)
        val animeRepository = FakeAnimeRepository(anime)
        val episodeRepository = FakeAnimeEpisodeRepository(listOf(episode))
        val source = RecordingAnimeSource(anime.source)
        val screenModel = screenModel(
            syncAnimeWithSource = SyncAnimeWithSource(
                animeRepository = animeRepository,
                animeEpisodeRepository = episodeRepository,
                animeSourceManager = FakeAnimeSourceManager(source),
            ),
            animeEpisodeRepository = episodeRepository,
            resolveVideoStream = ImmediateResolver(anime, episode),
        )

        try {
            screenModel.load(anime)

            eventually(2.seconds) {
                val item = screenModel.state.value.items[anime.id]
                (item as AnimeVideoFeedPlaybackScreenModel.ItemState.Ready).episode shouldBe episode
                source.detailsRequests shouldBe 0
                source.episodeListRequests shouldBe 0
            }
        } finally {
            screenModel.onDispose()
        }
    }

    @Test
    fun `retain active loads cancels in flight playback resolution`() = runBlocking {
        val anime = anime(id = 1L)
        val episode = episode(id = 10L, animeId = anime.id)
        val episodeRepository = FakeAnimeEpisodeRepository(listOf(episode))
        val resolver = CancellableResolver()
        val screenModel = screenModel(
            animeEpisodeRepository = episodeRepository,
            resolveVideoStream = resolver,
        )

        try {
            screenModel.load(anime)
            withTimeout(2.seconds) { resolver.started.await() }

            screenModel.retainActiveLoads(emptySet())

            withTimeout(2.seconds) { resolver.cancelled.await() }
            eventually(2.seconds) {
                screenModel.state.value.items.containsKey(anime.id).shouldBeFalse()
            }
        } finally {
            screenModel.onDispose()
        }
    }

    private fun screenModel(
        syncAnimeWithSource: SyncAnimeWithSource = SyncAnimeWithSource(
            animeRepository = FakeAnimeRepository(anime(id = 1L)),
            animeEpisodeRepository = FakeAnimeEpisodeRepository(emptyList()),
            animeSourceManager = FakeAnimeSourceManager(RecordingAnimeSource(1L)),
        ),
        animeEpisodeRepository: AnimeEpisodeRepository,
        resolveVideoStream: VideoStreamResolver,
    ): AnimeVideoFeedPlaybackScreenModel {
        return AnimeVideoFeedPlaybackScreenModel(
            syncAnimeWithSource = syncAnimeWithSource,
            animeEpisodeRepository = animeEpisodeRepository,
            animePlaybackStateRepository = FakeAnimePlaybackStateRepository(),
            animeHistoryRepository = FakeAnimeHistoryRepository(),
            resolveVideoStream = resolveVideoStream,
        )
    }

    companion object {
        @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
        private val mainThreadSurrogate = newSingleThreadContext("UI thread")

        @JvmStatic
        @BeforeAll
        fun setUp() {
            Dispatchers.setMain(mainThreadSurrogate)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            Dispatchers.resetMain()
            mainThreadSurrogate.close()
        }
    }
}

private class ImmediateResolver(
    private val anime: AnimeTitle,
    private val episode: AnimeEpisode,
) : VideoStreamResolver {
    override suspend fun invoke(
        animeId: Long,
        episodeId: Long,
        ownerAnimeId: Long,
        selection: VideoPlaybackSelection?,
    ): ResolveVideoStream.Result {
        val stream = VideoStream(
            request = VideoRequest("https://cdn.example.com/video.m3u8"),
            label = "Auto",
            type = VideoStreamType.HLS,
        )
        return ResolveVideoStream.Result.Success(
            visibleAnime = anime,
            ownerAnime = anime,
            episode = episode,
            playbackData = VideoPlaybackData(
                selection = selection ?: VideoPlaybackSelection(),
                streams = listOf(stream),
            ),
            stream = stream,
            subtitles = emptyList(),
            savedPreferences = playbackPreferences(anime.id),
        )
    }
}

private class CancellableResolver : VideoStreamResolver {
    val started = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()

    override suspend fun invoke(
        animeId: Long,
        episodeId: Long,
        ownerAnimeId: Long,
        selection: VideoPlaybackSelection?,
    ): ResolveVideoStream.Result {
        started.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            cancelled.complete(Unit)
        }
    }
}

private class RecordingAnimeSource(override val id: Long) : AnimeSource {
    override val name = "Source"
    var detailsRequests = 0
        private set
    var episodeListRequests = 0
        private set

    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        detailsRequests++
        return anime
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        episodeListRequests++
        return emptyList()
    }

    override suspend fun getPlaybackData(episode: SEpisode, selection: VideoPlaybackSelection): VideoPlaybackData {
        error("Not used")
    }
}

private class FakeAnimeSourceManager(private val source: AnimeSource) : AnimeSourceManager {
    override val isInitialized = MutableStateFlow(true)
    override val catalogueSources = emptyFlow<List<eu.kanade.tachiyomi.source.AnimeCatalogueSource>>()

    override fun get(sourceKey: Long): AnimeSource? = source.takeIf { it.id == sourceKey }

    override fun getCatalogueSources() = emptyList<eu.kanade.tachiyomi.source.AnimeCatalogueSource>()
}

private class FakeAnimeEpisodeRepository(initialEpisodes: List<AnimeEpisode>) : AnimeEpisodeRepository {
    private val episodes = initialEpisodes.toMutableList()

    override suspend fun addAll(episodes: List<AnimeEpisode>): List<AnimeEpisode> {
        this.episodes += episodes
        return episodes
    }

    override suspend fun update(episodeUpdate: AnimeEpisodeUpdate) = Unit

    override suspend fun updateAll(episodeUpdates: List<AnimeEpisodeUpdate>) = Unit

    override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) {
        episodes.removeAll { it.id in episodeIds }
    }

    override suspend fun getEpisodesByAnimeId(animeId: Long): List<AnimeEpisode> {
        return episodes.filter { it.animeId == animeId }
    }

    override fun getEpisodesByAnimeIdAsFlow(animeId: Long): Flow<List<AnimeEpisode>> {
        return flowOf(episodes.filter { it.animeId == animeId })
    }

    override fun getEpisodesByAnimeIdsAsFlow(animeIds: List<Long>): Flow<List<AnimeEpisode>> {
        return flowOf(episodes.filter { it.animeId in animeIds })
    }

    override suspend fun getEpisodeById(id: Long): AnimeEpisode? {
        return episodes.firstOrNull { it.id == id }
    }

    override suspend fun getEpisodeByUrlAndAnimeId(url: String, animeId: Long): AnimeEpisode? {
        return episodes.firstOrNull { it.url == url && it.animeId == animeId }
    }
}

private class FakeAnimePlaybackStateRepository : AnimePlaybackStateRepository {
    override suspend fun getByEpisodeId(episodeId: Long): AnimePlaybackState? = null

    override fun getByEpisodeIdAsFlow(episodeId: Long): Flow<AnimePlaybackState?> = flowOf(null)

    override fun getByAnimeIdAsFlow(animeId: Long): Flow<List<AnimePlaybackState>> = flowOf(emptyList())

    override suspend fun upsert(state: AnimePlaybackState) = Unit

    override suspend fun upsertAndSyncEpisodeState(state: AnimePlaybackState) = Unit
}

private class FakeAnimeHistoryRepository : AnimeHistoryRepository {
    override fun getHistory(query: String): Flow<List<AnimeHistoryWithRelations>> = emptyFlow()

    override suspend fun getLastHistory(): AnimeHistoryWithRelations? = null

    override fun getLastHistoryAsFlow(): Flow<AnimeHistoryWithRelations?> = flowOf(null)

    override suspend fun getTotalWatchedDuration(): Long = 0L

    override suspend fun getHistoryByAnimeId(animeId: Long): List<AnimeHistory> = emptyList()

    override suspend fun resetHistory(historyId: Long) = Unit

    override suspend fun resetHistoryByAnimeId(animeId: Long) = Unit

    override suspend fun deleteAllHistory(): Boolean = true

    override suspend fun upsertHistory(historyUpdate: AnimeHistoryUpdate) = Unit
}

private class FakeAnimeRepository(private val anime: AnimeTitle) : AnimeRepository {
    override suspend fun getAnimeById(id: Long): AnimeTitle = anime

    override suspend fun getAnimeByIdAsFlow(id: Long): Flow<AnimeTitle> = flowOf(anime)

    override suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): AnimeTitle? = anime

    override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<AnimeTitle?> = flowOf(anime)

    override suspend fun getFavorites(): List<AnimeTitle> = emptyList()

    override fun getFavoritesAsFlow(): Flow<List<AnimeTitle>> = flowOf(emptyList())

    override suspend fun getAllAnimeByProfile(profileId: Long): List<AnimeTitle> = emptyList()

    override suspend fun updateDisplayName(animeId: Long, displayName: String?): Boolean = true

    override suspend fun update(update: AnimeTitleUpdate): Boolean = true

    override suspend fun updateAll(animeUpdates: List<AnimeTitleUpdate>): Boolean = true

    override suspend fun insertNetworkAnime(animes: List<AnimeTitle>): List<AnimeTitle> = animes

    override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) = Unit
}

private fun anime(id: Long): AnimeTitle {
    return AnimeTitle.create().copy(
        id = id,
        source = 1L,
        title = "Anime $id",
        url = "/anime/$id",
        initialized = true,
    )
}

private fun episode(id: Long, animeId: Long): AnimeEpisode {
    return AnimeEpisode.create().copy(
        id = id,
        animeId = animeId,
        name = "Episode $id",
        url = "/episode/$id",
        sourceOrder = id,
    )
}

private fun playbackPreferences(animeId: Long): AnimePlaybackPreferences {
    return AnimePlaybackPreferences(
        animeId = animeId,
        dubKey = null,
        streamKey = null,
        sourceQualityKey = null,
        playerQualityMode = PlayerQualityMode.AUTO,
        playerQualityHeight = null,
        updatedAt = 0L,
    )
}
