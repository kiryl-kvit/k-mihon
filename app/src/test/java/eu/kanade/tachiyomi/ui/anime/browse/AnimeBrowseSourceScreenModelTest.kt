package eu.kanade.tachiyomi.ui.anime.browse

import android.app.Application
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.domain.source.model.FeedListingMode
import eu.kanade.domain.source.model.snapshot
import eu.kanade.domain.source.service.BrowseFeedService
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.AnimeHoverPreviewSource
import eu.kanade.tachiyomi.source.AnimePreviewSource
import eu.kanade.tachiyomi.source.model.AnimesPage
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SAnimeHoverPreview
import eu.kanade.tachiyomi.source.model.SAnimePreview
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import mihon.core.common.CustomPreferences
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.anime.interactor.GetAnime
import tachiyomi.domain.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.anime.interactor.GetMergedAnime
import tachiyomi.domain.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.anime.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.anime.interactor.UpdateMergedAnime
import tachiyomi.domain.anime.model.AnimeMerge
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeTitleUpdate
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.anime.repository.MergedAnimeRepository
import tachiyomi.domain.category.interactor.GetAnimeCategories
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.model.CategoryUpdate
import tachiyomi.domain.category.repository.CategoryRepository
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.service.DuplicatePreferences
import tachiyomi.domain.source.interactor.GetRemoteAnime
import tachiyomi.domain.source.repository.AnimeSourcePagingSource
import tachiyomi.domain.source.repository.AnimeSourceRepository
import tachiyomi.domain.source.service.AnimeSourceManager
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeBrowseSourceScreenModelTest {

    private lateinit var dispatcher: TestDispatcher
    private val createdModels = mutableListOf<AnimeBrowseSourceScreenModel>()

    @BeforeEach
    fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        createdModels.asReversed().forEach { it.onDispose() }
        createdModels.clear()
        dispatcher.scheduler.advanceUntilIdle()
        Dispatchers.resetMain()
    }

    @Test
    fun `edited filters from popular save as search preset without sentinel query`() {
        val (filters, textFilter) = testFilters()
        textFilter.state = "romance"

        val saved = AnimeBrowseSourceScreenModel.State(
            listing = AnimeBrowseSourceScreenModel.Listing.Popular,
            filters = filters,
        ).toSavedPresetState(defaultFilters = testFilters().first)

        saved.listingMode shouldBe FeedListingMode.Search
        saved.query shouldBe null
        saved.filters shouldBe filters.snapshot()
    }

    @Test
    fun `search listing initializes with saved filter snapshot`() {
        val (savedFilters, savedTextFilter) = testFilters()
        savedTextFilter.state = "romance"
        val snapshot = savedFilters.snapshot()

        val initialized = AnimeBrowseSourceScreenModel.State(
            listing = AnimeBrowseSourceScreenModel.Listing.Search(
                query = null,
                filters = FilterList(),
            ),
        ).initializeForSource(
            sourceFilters = testFilters().first,
            initialFilterSnapshot = snapshot,
        )

        initialized.filters.snapshot() shouldBe snapshot
        (initialized.listing as AnimeBrowseSourceScreenModel.Listing.Search).query shouldBe null
        initialized.listing.filters.snapshot() shouldBe snapshot
    }

    @Test
    fun `save preset persists and apply restores anime filters`() = runTest(dispatcher) {
        val anime = anime(id = 50L, favorite = false)
        val sourcePreferences = SourcePreferences(InMemoryPreferenceStore(), testJson)
        val customPreferences = CustomPreferences(InMemoryPreferenceStore())
        val browseFeedService = BrowseFeedService(sourcePreferences)
        val (filters, textFilter) = testFilters()
        val source = FakeAnimeCatalogueSource(anime.source, filters)

        val model = createModel(
            anime = anime,
            sourcePreferences = sourcePreferences,
            customPreferences = customPreferences,
            browseFeedService = browseFeedService,
            animeSourceManager = FakeAnimeSourceManager(source),
        )

        textFilter.state = "frieren"
        model.setFilters(filters)
        model.search(query = "hero", filters = filters)
        model.showSavePresetDialog()
        model.savePreset(name = "Saved", chronological = true)

        eventually(2.seconds) {
            browseFeedService.stateSnapshot().presets.single().name shouldBe "Saved"
            browseFeedService.stateSnapshot().presets.single().kind shouldBe
                eu.kanade.domain.source.model.SourceFeedKind.ANIME
            (model.state.value.listing as AnimeBrowseSourceScreenModel.Listing.Search).query shouldBe "hero"
            model.state.value.appliedCustomPresetId shouldBe browseFeedService.stateSnapshot().presets.single().id
        }

        val preset = browseFeedService.stateSnapshot().presets.single()
        model.resetFilters()
        model.setListing(AnimeBrowseSourceScreenModel.Listing.Popular)
        model.applyPreset(preset.id)

        eventually(2.seconds) {
            val listing = model.state.value.listing as AnimeBrowseSourceScreenModel.Listing.Search
            listing.query shouldBe "hero"
            model.state.value.filters.snapshot() shouldBe preset.filters
        }
    }

    @Test
    fun `long press on favorite opens library action chooser`() = runTest(dispatcher) {
        val anime = anime(id = 1L, favorite = true)

        val model = createModel(anime = anime)

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser(anime)
        }
    }

    @Test
    fun `preview long press opens anime preview when source supports preview`() = runTest(dispatcher) {
        val anime = anime(id = 10L, favorite = false)
        val customPreferences = CustomPreferences(InMemoryPreferenceStore()).apply {
            enableAnimePreview.set(true)
            browseLongPressAction.set(CustomPreferences.BrowseLongPressAction.PREVIEW)
        }

        val model = createModel(
            anime = anime,
            customPreferences = customPreferences,
            animeSourceManager = FakeAnimeSourceManager(FakeAnimePreviewSource(anime.source)),
        )

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.AnimePreview(anime.id)
        }
    }

    @Test
    fun `preview long press falls back to library action when source does not support preview`() = runTest(dispatcher) {
        val anime = anime(id = 11L, favorite = true)
        val customPreferences = CustomPreferences(InMemoryPreferenceStore()).apply {
            enableAnimePreview.set(true)
            browseLongPressAction.set(CustomPreferences.BrowseLongPressAction.PREVIEW)
        }

        val model = createModel(anime = anime, customPreferences = customPreferences)

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser(anime)
        }
    }

    @Test
    fun `preview long press falls back to library action when anime preview is disabled`() = runTest(dispatcher) {
        val anime = anime(id = 12L, favorite = true)
        val customPreferences = CustomPreferences(InMemoryPreferenceStore()).apply {
            enableAnimePreview.set(false)
            browseLongPressAction.set(CustomPreferences.BrowseLongPressAction.PREVIEW)
        }

        val model = createModel(
            anime = anime,
            customPreferences = customPreferences,
            animeSourceManager = FakeAnimeSourceManager(FakeAnimePreviewSource(anime.source)),
        )

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser(anime)
        }
    }

    @Test
    fun `hover preview caches successful response`() = runTest(dispatcher) {
        val anime = anime(id = 13L, favorite = false)
        val preview = SAnimeHoverPreview(videoUrl = "https://example.org/preview.mp4")
        val source = FakeAnimeHoverPreviewSource(anime.source, preview, null)

        val model = createModel(
            anime = anime,
            animeSourceManager = FakeAnimeSourceManager(source),
        )

        model.getAnimeHoverPreview(anime) shouldBe preview
        model.getAnimeHoverPreview(anime) shouldBe preview
        source.requestCount shouldBe 1
    }

    @Test
    fun `hover preview does not cache null response`() = runTest(dispatcher) {
        val anime = anime(id = 14L, favorite = false)
        val preview = SAnimeHoverPreview(videoUrl = "https://example.org/preview.mp4")
        val source = FakeAnimeHoverPreviewSource(anime.source, null, preview)

        val model = createModel(
            anime = anime,
            animeSourceManager = FakeAnimeSourceManager(source),
        )

        model.getAnimeHoverPreview(anime) shouldBe null
        model.getAnimeHoverPreview(anime) shouldBe preview
        source.requestCount shouldBe 2
    }

    @Test
    fun `long press on non favorite with default category uses chooser then adds to library`() = runTest(dispatcher) {
        val anime = anime(id = 2L, favorite = false)
        val existing = anime(id = 22L, favorite = true, title = "Existing")
        val animeRepository = FakeAnimeRepository(
            anime = listOf(anime, existing),
            favorites = listOf(existing),
        )
        val categoryRepository = FakeCategoryRepository(
            categories = listOf(
                Category(0L, "Default", 0L, 0L),
                Category(1L, "Watching", 1L, 0L),
            ),
        )
        val libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()).apply {
            defaultCategory.set(1)
        }

        val model = createModel(
            anime = anime,
            animeRepository = animeRepository,
            categoryRepository = categoryRepository,
            libraryPreferences = libraryPreferences,
        )

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser(anime)
        }

        model.dismissDialog()
        model.confirmBrowseLibraryAction(anime)

        eventually(2.seconds) {
            animeRepository.updates.size shouldBe 2
            animeRepository.updates.first().id shouldBe anime.id
            animeRepository.updates.first().episodeFlags shouldBe 0L
            animeRepository.updates.last() shouldBe AnimeTitleUpdate(
                id = anime.id,
                favorite = true,
                dateAdded = animeRepository.updates.last().dateAdded,
            )
            animeRepository.categoryUpdates shouldContainExactly listOf(anime.id to listOf(1L))
            model.state.value.dialog shouldBe null
        }
    }

    @Test
    fun `long press on non favorite without default category uses chooser then opens category dialog`() = runTest(
        dispatcher,
    ) {
        val anime = anime(id = 3L, favorite = false)
        val existing = anime(id = 23L, favorite = true, title = "Existing")
        val categoryRepository = FakeCategoryRepository(
            categories = listOf(
                Category(0L, "Default", 0L, 0L),
                Category(2L, "Seasonal", 1L, 0L),
            ),
        )
        val libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()).apply {
            defaultCategory.set(-1)
        }

        val model = createModel(
            anime = anime,
            animeRepository = FakeAnimeRepository(
                anime = listOf(anime, existing),
                favorites = listOf(existing),
            ),
            categoryRepository = categoryRepository,
            libraryPreferences = libraryPreferences,
        )

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            model.state.value.dialog shouldBe AnimeBrowseSourceScreenModel.Dialog.LibraryActionChooser(anime)
        }

        model.confirmBrowseLibraryAction(anime)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.ChangeAnimeCategory
            dialog.anime shouldBe anime
            dialog.initialSelection.map { it.value.id } shouldContainExactly listOf(2L)
        }
    }

    @Test
    fun `long press with empty library bypasses chooser and opens category dialog`() = runTest(dispatcher) {
        val anime = anime(id = 4L, favorite = false)
        val categoryRepository = FakeCategoryRepository(
            categories = listOf(
                Category(0L, "Default", 0L, 0L),
                Category(2L, "Seasonal", 1L, 0L),
            ),
        )
        val libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()).apply {
            defaultCategory.set(-1)
        }

        val model = createModel(
            anime = anime,
            animeRepository = FakeAnimeRepository(listOf(anime), favorites = emptyList()),
            categoryRepository = categoryRepository,
            libraryPreferences = libraryPreferences,
        )

        model.onAnimeLongClick(anime) shouldBe true

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.ChangeAnimeCategory
            dialog.anime shouldBe anime
        }
    }

    @Test
    fun `confirm browse library action opens duplicate dialog when duplicates exist`() = runTest(dispatcher) {
        val anime = anime(id = 5L, favorite = false)
        val duplicate = anime(id = 6L, favorite = true).copy(title = anime.title)
        val animeRepository = FakeAnimeRepository(
            anime = listOf(anime, duplicate),
            favorites = listOf(duplicate),
        )
        val duplicateInteractor = GetDuplicateLibraryAnime(
            animeRepository = animeRepository,
            animeEpisodeRepository = FakeAnimeEpisodeRepository(),
            mergedAnimeRepository = FakeMergedAnimeRepository(),
            duplicatePreferences = tachiyomi.domain.manga.service.DuplicatePreferences(InMemoryPreferenceStore()),
        )

        val model = createModel(
            anime = anime,
            animeRepository = animeRepository,
            getDuplicateLibraryAnime = duplicateInteractor,
        )

        model.confirmBrowseLibraryAction(anime)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.DuplicateAnime
            dialog.anime shouldBe anime
            dialog.duplicates.map { it.anime.id } shouldContainExactly listOf(duplicate.id)
        }
    }

    @Test
    fun `show merge target picker excludes current anime and its merge members`() = runTest(dispatcher) {
        val current = anime(id = 10L, favorite = false)
        val mergedRoot = anime(id = 20L, favorite = true, title = "Merged Root")
        val mergedChild = anime(id = 21L, favorite = true, title = "Merged Child")
        val other = anime(id = 30L, favorite = true, title = "Other Target")
        val animeRepository = FakeAnimeRepository(
            anime = listOf(current, mergedRoot, mergedChild, other),
            favorites = listOf(mergedRoot, mergedChild, other),
        )
        val mergedRepository = FakeMergedAnimeRepository(
            merges = listOf(
                AnimeMerge(targetId = 20L, animeId = 20L, position = 0),
                AnimeMerge(targetId = 20L, animeId = 21L, position = 1),
                AnimeMerge(targetId = 10L, animeId = 10L, position = 0),
                AnimeMerge(targetId = 10L, animeId = 21L, position = 1),
            ),
        )

        val model = createModel(
            anime = current,
            animeRepository = animeRepository,
            getMergedAnime = GetMergedAnime(mergedRepository),
        )

        model.showMergeTargetPicker(current)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.SelectMergeTarget
            dialog.targets.map { it.id } shouldContainExactly listOf(30L)
        }
    }

    @Test
    fun `show merge target picker strips duplicate patterns from browse anime title and prefilters targets`() = runTest(
        dispatcher,
    ) {
        val current = anime(id = 10L, favorite = false, title = "Current Search [1080p] Season 2")
        val matching = anime(id = 20L, favorite = true, title = "Current Search Results")
        val other = anime(id = 30L, favorite = true, title = "Other Target")
        val animeRepository = FakeAnimeRepository(
            anime = listOf(current, matching, other),
            favorites = listOf(matching, other),
        )

        val model = createModel(
            anime = current,
            animeRepository = animeRepository,
            duplicatePreferences = DuplicatePreferences(InMemoryPreferenceStore()).apply {
                titleExclusionPatterns.set(listOf("[*]", "Season *"))
            },
        )

        model.showMergeTargetPicker(current)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.SelectMergeTarget
            dialog.query shouldBe "Current Search"
            dialog.visibleTargets.map { it.id } shouldContainExactly listOf(20L)
        }
    }

    @Test
    fun `open merge editor places newly added anime before existing merge members`() = runTest(dispatcher) {
        val current = anime(id = 10L, favorite = false, title = "Current")
        val target = anime(id = 20L, favorite = true, title = "Target")
        val member = anime(id = 21L, favorite = true, title = "Member")
        val animeRepository = FakeAnimeRepository(
            anime = listOf(current, target, member),
            favorites = listOf(target, member),
        )
        val mergedRepository = FakeMergedAnimeRepository(
            merges = listOf(
                AnimeMerge(targetId = target.id, animeId = target.id, position = 0L),
                AnimeMerge(targetId = target.id, animeId = member.id, position = 1L),
            ),
        )

        val model = createModel(
            anime = current,
            animeRepository = animeRepository,
            getMergedAnime = GetMergedAnime(mergedRepository),
        )

        model.showMergeTargetPicker(current)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.SelectMergeTarget
            dialog.targets.map { it.id } shouldContainExactly listOf(target.id)
        }

        model.openMergeEditor(target.id)

        eventually(2.seconds) {
            val dialog = model.state.value.dialog as AnimeBrowseSourceScreenModel.Dialog.EditMerge
            dialog.entries.map { it.id } shouldContainExactly listOf(current.id, target.id, member.id)
            dialog.targetId shouldBe target.id
        }
    }

    private fun createModel(
        anime: AnimeTitle,
        animeRepository: FakeAnimeRepository = FakeAnimeRepository(listOf(anime)),
        categoryRepository: FakeCategoryRepository = FakeCategoryRepository(),
        sourcePreferences: SourcePreferences = SourcePreferences(InMemoryPreferenceStore(), testJson),
        customPreferences: CustomPreferences = CustomPreferences(InMemoryPreferenceStore()),
        browseFeedService: BrowseFeedService = BrowseFeedService(sourcePreferences),
        animeSourceManager: AnimeSourceManager = FakeAnimeSourceManager(FakeAnimeCatalogueSource(anime.source)),
        libraryPreferences: LibraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
        getDuplicateLibraryAnime: GetDuplicateLibraryAnime = GetDuplicateLibraryAnime(
            animeRepository = animeRepository,
            animeEpisodeRepository = FakeAnimeEpisodeRepository(),
            mergedAnimeRepository = FakeMergedAnimeRepository(),
            duplicatePreferences = tachiyomi.domain.manga.service.DuplicatePreferences(InMemoryPreferenceStore()),
        ),
        getMergedAnime: GetMergedAnime = GetMergedAnime(FakeMergedAnimeRepository()),
        duplicatePreferences: DuplicatePreferences = DuplicatePreferences(InMemoryPreferenceStore()),
    ): AnimeBrowseSourceScreenModel {
        val sourcePreferences = SourcePreferences(InMemoryPreferenceStore(), testJson)
        val getIncognitoState = mockk<GetIncognitoState>()
        every { getIncognitoState.await(any()) } returns false
        val setAnimeEpisodeFlags = SetAnimeEpisodeFlags(animeRepository)
        return AnimeBrowseSourceScreenModel(
            sourceId = anime.source,
            listingQuery = null,
            animeSourceManager = animeSourceManager,
            sourcePreferences = sourcePreferences,
            customPreferences = customPreferences,
            browseFeedService = browseFeedService,
            libraryPreferences = libraryPreferences,
            getRemoteAnime = GetRemoteAnime(FakeAnimeSourceRepository()),
            getAnime = GetAnime(animeRepository),
            getDuplicateLibraryAnime = getDuplicateLibraryAnime,
            getCategories = GetCategories(categoryRepository),
            getAnimeCategories = GetAnimeCategories(categoryRepository),
            getMergedAnime = getMergedAnime,
            duplicatePreferences = duplicatePreferences,
            networkToLocalAnime = NetworkToLocalAnime(animeRepository),
            setAnimeCategories = SetAnimeCategories(animeRepository),
            animeRepository = animeRepository,
            setAnimeDefaultEpisodeFlags = SetAnimeDefaultEpisodeFlags(libraryPreferences, setAnimeEpisodeFlags),
            updateMergedAnime = UpdateMergedAnime(FakeMergedAnimeRepository()),
            getIncognitoState = getIncognitoState,
            application = mockk<Application>(relaxed = true),
        ).also(createdModels::add)
    }

    private fun anime(id: Long, favorite: Boolean, title: String = "Anime $id"): AnimeTitle {
        return AnimeTitle.create().copy(
            id = id,
            source = 99L,
            favorite = favorite,
            title = title,
            url = "/anime/$id",
            initialized = true,
        )
    }

    private class FakeAnimeRepository(
        private val anime: List<AnimeTitle>,
        private val favorites: List<AnimeTitle> = anime.filter { it.favorite },
    ) : AnimeRepository {
        val updates = mutableListOf<AnimeTitleUpdate>()
        val categoryUpdates = mutableListOf<Pair<Long, List<Long>>>()

        override suspend fun getAnimeById(id: Long): AnimeTitle = anime.first { it.id == id }
        override suspend fun getAnimeByIdAsFlow(id: Long): Flow<AnimeTitle> = flowOf(anime.first { it.id == id })
        override suspend fun getAnimeByUrlAndSourceId(
            url: String,
            sourceId: Long,
        ): AnimeTitle? = anime.firstOrNull {
            it.url ==
                url &&
                it.source == sourceId
        }
        override fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<AnimeTitle?> = flowOf(
            anime.firstOrNull { it.url == url && it.source == sourceId },
        )
        override suspend fun getFavorites(): List<AnimeTitle> = favorites
        override fun getFavoritesAsFlow(): Flow<List<AnimeTitle>> = flowOf(favorites)
        override suspend fun getAllAnimeByProfile(profileId: Long): List<AnimeTitle> = anime
        override suspend fun updateDisplayName(animeId: Long, displayName: String?): Boolean = true
        override suspend fun update(update: AnimeTitleUpdate): Boolean {
            updates += update
            return true
        }
        override suspend fun updateAll(animeUpdates: List<AnimeTitleUpdate>): Boolean {
            updates += animeUpdates
            return true
        }
        override suspend fun insertNetworkAnime(animes: List<AnimeTitle>): List<AnimeTitle> = animes
        override suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>) {
            categoryUpdates += animeId to categoryIds
        }
    }

    private class FakeCategoryRepository(
        private val categories: List<Category> = listOf(Category(0L, "Default", 0L, 0L)),
        private val animeCategories: Map<Long, List<Category>> = emptyMap(),
    ) : CategoryRepository {
        override suspend fun get(id: Long): Category? = categories.firstOrNull { it.id == id }
        override suspend fun getAll(): List<Category> = categories
        override fun getAllAsFlow(): Flow<List<Category>> = flowOf(categories)
        override suspend fun getCategoriesByMangaId(mangaId: Long): List<Category> = emptyList()
        override fun getCategoriesByMangaIdAsFlow(mangaId: Long): Flow<List<Category>> = flowOf(emptyList())
        override suspend fun getCategoriesByAnimeId(animeId: Long): List<Category> = animeCategories[animeId].orEmpty()
        override fun getCategoriesByAnimeIdAsFlow(
            animeId: Long,
        ): Flow<List<Category>> = flowOf(animeCategories[animeId].orEmpty())
        override suspend fun getAnimeCategoryIds(animeIds: List<Long>): Map<Long, List<Long>> {
            return animeIds.associateWith { animeCategories[it].orEmpty().map(Category::id) }
        }
        override suspend fun insert(category: Category) = Unit
        override suspend fun updatePartial(update: CategoryUpdate) = Unit
        override suspend fun updatePartial(updates: List<CategoryUpdate>) = Unit
        override suspend fun updateAllFlags(flags: Long?) = Unit
        override suspend fun delete(categoryId: Long) = Unit
    }

    private class FakeAnimeSourceManager(
        private val source: AnimeCatalogueSource,
    ) : AnimeSourceManager {
        override val isInitialized = MutableStateFlow(true)
        override val catalogueSources = flowOf(listOf(source))
        override fun get(sourceKey: Long) = source.takeIf { it.id == sourceKey }
        override fun getCatalogueSources(): List<AnimeCatalogueSource> = listOf(source)
    }

    private open class FakeAnimeCatalogueSource(
        override val id: Long,
        private val filters: FilterList = FilterList(),
    ) : AnimeCatalogueSource {
        override val name: String = "Fake Anime Source"
        override val lang: String = "en"
        override val supportsLatest: Boolean = true
        override suspend fun getPopularAnime(page: Int): AnimesPage = AnimesPage(emptyList(), false)
        override suspend fun getSearchAnime(
            page: Int,
            query: String,
            filters: FilterList,
        ): AnimesPage = AnimesPage(emptyList(), false)
        override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), false)
        override fun getFilterList(): FilterList = filters
        override suspend fun getAnimeDetails(anime: SAnime): SAnime = anime
        override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = emptyList()
        override suspend fun getPlaybackData(
            episode: SEpisode,
            selection: VideoPlaybackSelection,
        ): VideoPlaybackData = error("Not used")
    }

    private class FakeAnimePreviewSource(
        id: Long,
    ) : FakeAnimeCatalogueSource(id), AnimePreviewSource {
        override suspend fun getAnimePreview(anime: SAnime): List<SAnimePreview> {
            return listOf(SAnimePreview(index = 0, imageUrl = "https://example.org/preview.jpg"))
        }
    }

    private class FakeAnimeHoverPreviewSource(
        id: Long,
        private vararg val previews: SAnimeHoverPreview?,
    ) : FakeAnimeCatalogueSource(id), AnimeHoverPreviewSource {
        var requestCount = 0

        override suspend fun getAnimeHoverPreview(anime: SAnime): SAnimeHoverPreview? {
            val index = requestCount.coerceAtMost(previews.lastIndex)
            requestCount++
            return previews[index]
        }
    }

    private class FakeAnimeSourceRepository : AnimeSourceRepository {
        override fun getPopular(sourceId: Long): AnimeSourcePagingSource = error("Not used")
        override fun getLatest(sourceId: Long): AnimeSourcePagingSource = error("Not used")
        override fun search(
            sourceId: Long,
            query: String,
            filterList: FilterList,
        ): AnimeSourcePagingSource = error("Not used")
    }

    private class FakeAnimeEpisodeRepository(
        private val episodesByAnimeId: Map<Long, List<tachiyomi.domain.anime.model.AnimeEpisode>> = emptyMap(),
    ) : AnimeEpisodeRepository {
        override suspend fun addAll(
            episodes: List<tachiyomi.domain.anime.model.AnimeEpisode>,
        ): List<tachiyomi.domain.anime.model.AnimeEpisode> = episodes
        override suspend fun update(episodeUpdate: tachiyomi.domain.anime.model.AnimeEpisodeUpdate) = Unit
        override suspend fun updateAll(episodeUpdates: List<tachiyomi.domain.anime.model.AnimeEpisodeUpdate>) = Unit
        override suspend fun removeEpisodesWithIds(episodeIds: List<Long>) = Unit
        override suspend fun getEpisodesByAnimeId(
            animeId: Long,
        ): List<tachiyomi.domain.anime.model.AnimeEpisode> = episodesByAnimeId[animeId].orEmpty()
        override fun getEpisodesByAnimeIdAsFlow(
            animeId: Long,
        ): Flow<List<tachiyomi.domain.anime.model.AnimeEpisode>> = flowOf(episodesByAnimeId[animeId].orEmpty())
        override fun getEpisodesByAnimeIdsAsFlow(
            animeIds: List<Long>,
        ): Flow<List<tachiyomi.domain.anime.model.AnimeEpisode>> {
            return flowOf(animeIds.flatMap { episodesByAnimeId[it].orEmpty() })
        }
        override suspend fun getEpisodeById(
            id: Long,
        ): tachiyomi.domain.anime.model.AnimeEpisode? = episodesByAnimeId.values.flatten().firstOrNull {
            it.id ==
                id
        }
        override suspend fun getEpisodeByUrlAndAnimeId(
            url: String,
            animeId: Long,
        ): tachiyomi.domain.anime.model.AnimeEpisode? {
            return episodesByAnimeId[animeId].orEmpty().firstOrNull { it.url == url }
        }
    }

    private class FakeMergedAnimeRepository(
        private val merges: List<AnimeMerge> = emptyList(),
    ) : MergedAnimeRepository {
        override suspend fun getAll(): List<AnimeMerge> = merges
        override fun subscribeAll(): Flow<List<AnimeMerge>> = flowOf(merges)
        override suspend fun getGroupByAnimeId(animeId: Long): List<AnimeMerge> {
            val targetId = merges.firstOrNull { it.animeId == animeId }?.targetId ?: return emptyList()
            return merges.filter { it.targetId == targetId }
        }
        override fun subscribeGroupByAnimeId(animeId: Long): Flow<List<AnimeMerge>> {
            val targetId = merges.firstOrNull { it.animeId == animeId }?.targetId
            return flowOf(targetId?.let { id -> merges.filter { it.targetId == id } }.orEmpty())
        }
        override suspend fun getGroupByTargetId(targetAnimeId: Long): List<AnimeMerge> = merges.filter {
            it.targetId ==
                targetAnimeId
        }
        override suspend fun getTargetId(animeId: Long): Long? = merges.firstOrNull { it.animeId == animeId }?.targetId
        override fun subscribeTargetId(
            animeId: Long,
        ): Flow<Long?> = flowOf(merges.firstOrNull { it.animeId == animeId }?.targetId)
        override suspend fun upsertGroup(targetAnimeId: Long, orderedAnimeIds: List<Long>) = Unit
        override suspend fun removeMembers(targetAnimeId: Long, animeIds: List<Long>) = Unit
        override suspend fun deleteGroup(targetAnimeId: Long) = Unit
    }
    companion object {
        private val testJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}

private fun testFilters(): Pair<FilterList, TestTextFilter> {
    val text = TestTextFilter()
    return FilterList(text) to text
}

private class TestTextFilter : Filter.Text("Genre")
