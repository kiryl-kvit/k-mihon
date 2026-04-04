package tachiyomi.domain.manga.interactor

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.library.model.LibraryManga
import tachiyomi.domain.manga.model.DuplicateMangaMatchReason
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaMerge
import tachiyomi.domain.manga.repository.MangaRepository
import tachiyomi.domain.manga.repository.MergedMangaRepository

class GetDuplicateLibraryMangaTest {

    private val mangaRepository = mockk<MangaRepository>()
    private val mergedMangaRepository = mockk<MergedMangaRepository>()

    private val getDuplicateLibraryManga = GetDuplicateLibraryManga(
        mangaRepository = mangaRepository,
        mergedMangaRepository = mergedMangaRepository,
    )

    @Test
    fun `returns strong match for normalized same title`() = runTest {
        val description =
            "The mage Frieren journeys onward after the demon king falls and reflects on the lives she outlived."
        val current = manga(
            id = 1,
            title = "Frieren: Beyond Journey's End",
            description = description,
        )
        val duplicate = libraryManga(
            manga = manga(
                id = 2,
                title = "Frieren Beyond Journeys End",
                author = "Kanehito Yamada",
                description = description,
            ),
            totalChapters = 140,
        )

        coEvery { mangaRepository.getLibraryManga() } returns listOf(duplicate)
        coEvery { mergedMangaRepository.getAll() } returns emptyList()

        val results = getDuplicateLibraryManga(current)

        results shouldHaveSize 1
        results.single().manga.id shouldBe 2L
        results.single().score shouldBeGreaterThanOrEqual 40
        results.single().reasons shouldContain DuplicateMangaMatchReason.TITLE
        results.single().cheapScore shouldBe results.single().score
    }

    @Test
    fun `uses creator and status markers to boost likely duplicate`() = runTest {
        val description =
            "Noor keeps parrying impossible attacks and accidentally becomes the strongest while believing he is weak."
        val current = manga(
            id = 1,
            title = "I Parry Everything",
            author = "Nabeshiki",
            status = 1,
            genre = listOf("Action", "Fantasy"),
            description = description,
        )
        val duplicate = libraryManga(
            manga = manga(
                id = 2,
                title = "I Parry Everything: What Do You Mean I'm the Strongest",
                author = "Nabeshiki",
                status = 1,
                genre = listOf("Fantasy", "Action"),
                description = description,
            ),
            totalChapters = 40,
        )

        coEvery { mangaRepository.getLibraryManga() } returns listOf(duplicate)
        coEvery { mergedMangaRepository.getAll() } returns emptyList()

        val result = getDuplicateLibraryManga(current).single()

        result.reasons shouldContain DuplicateMangaMatchReason.TITLE
        result.reasons shouldContain DuplicateMangaMatchReason.AUTHOR
        result.reasons shouldContain DuplicateMangaMatchReason.STATUS
        result.reasons shouldContain DuplicateMangaMatchReason.GENRE
    }

    @Test
    fun `collapses merged library members into one candidate`() = runTest {
        val description = "Japan assembles an elite striker program to create the world's most selfish goal scorer."
        val current = manga(id = 1, title = "Blue Lock", description = description)
        val target =
            libraryManga(manga = manga(id = 10, title = "Blue Lock", description = description), totalChapters = 20)
        val member =
            libraryManga(
                manga = manga(id = 11, title = "Blue Lock (Official)", description = description),
                totalChapters = 22,
            )
        val merges = listOf(
            MangaMerge(targetId = 10, mangaId = 10, position = 0),
            MangaMerge(targetId = 10, mangaId = 11, position = 1),
        )

        coEvery { mangaRepository.getLibraryManga() } returns listOf(target, member)
        coEvery { mergedMangaRepository.getAll() } returns merges

        val results = getDuplicateLibraryManga(current)

        results shouldHaveSize 1
        results.single().manga.id shouldBe 10L
        results.single().chapterCount shouldBe 42L
    }

    @Test
    fun `ignores low similarity titles`() = runTest {
        val current = manga(
            id = 1,
            title = "Frieren",
            description =
            "An elf mage travels after the end of the hero's journey and learns what life means.",
        )
        val unrelated = libraryManga(
            manga = manga(
                id = 2,
                title = "One Piece",
                description =
                "A rubber pirate sails the seas to find a legendary treasure and become king of the pirates.",
            ),
            totalChapters = 1000,
        )

        coEvery { mangaRepository.getLibraryManga() } returns listOf(unrelated)
        coEvery { mergedMangaRepository.getAll() } returns emptyList()

        getDuplicateLibraryManga(current) shouldBe emptyList()
    }

    @Test
    fun `matches on strong description despite different title`() = runTest {
        val description =
            "Encrid dreamed of becoming a knight, but those words poisoned his childhood and he keeps returning to today."
        val current = manga(
            id = 1,
            title = "Eternally Regressing Knight",
            author = "Kanara",
            status = 1,
            genre = listOf("Action", "Fantasy", "Regression"),
            description = description,
        )
        val duplicate = libraryManga(
            manga = manga(
                id = 2,
                title = "The Knight Only Lives Today",
                author = "Ian",
                status = 1,
                genre = listOf("Action", "Fantasy", "Manhwa"),
                description = description,
            ),
            totalChapters = 105,
        )

        coEvery { mangaRepository.getLibraryManga() } returns listOf(duplicate)
        coEvery { mergedMangaRepository.getAll() } returns emptyList()

        val result = getDuplicateLibraryManga(current).single()

        result.reasons shouldContain DuplicateMangaMatchReason.DESCRIPTION
        result.reasons shouldContain DuplicateMangaMatchReason.STATUS
        result.reasons shouldContain DuplicateMangaMatchReason.GENRE
        result.score shouldBeGreaterThanOrEqual 32
    }

    private fun manga(
        id: Long,
        title: String,
        author: String? = null,
        status: Long = 0,
        genre: List<String>? = null,
        description: String? = null,
    ): Manga {
        return Manga.create().copy(
            id = id,
            source = id,
            title = title,
            author = author,
            status = status,
            genre = genre,
            description = description,
        )
    }

    private fun libraryManga(
        manga: Manga,
        totalChapters: Long,
    ): LibraryManga {
        return LibraryManga(
            manga = manga.copy(favorite = true),
            categories = emptyList(),
            totalChapters = totalChapters,
            readCount = 0,
            bookmarkCount = 0,
            latestUpload = 0,
            chapterFetchedAt = 0,
            lastRead = 0,
        )
    }
}
