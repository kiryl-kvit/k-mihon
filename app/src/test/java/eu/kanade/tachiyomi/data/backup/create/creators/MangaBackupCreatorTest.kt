package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaMerge
import tachiyomi.domain.manga.repository.MangaRepository

class MangaBackupCreatorTest {

    private val handler = mockk<DatabaseHandler>()
    private val profileProvider = mockk<ActiveProfileProvider>()
    private val getCategories = mockk<GetCategories>()
    private val getHistory = mockk<GetHistory>()
    private val mangaRepository = mockk<MangaRepository>()

    private val creator = MangaBackupCreator(
        handler = handler,
        profileProvider = profileProvider,
        getCategories = getCategories,
        getHistory = getHistory,
        mangaRepository = mangaRepository,
    )

    init {
        every { profileProvider.activeProfileId } returns 1L
        coEvery { mangaRepository.getAllMangaByProfile(any()) } returns emptyList()
    }

    @Test
    fun `non-active profile backup stores manga merge metadata from requested profile`() = runTest {
        val targetManga = Manga.create().copy(
            id = 10L,
            source = 100L,
            url = "/target",
            title = "Target",
        )
        val memberManga = Manga.create().copy(
            id = 11L,
            source = 200L,
            url = "/member",
            title = "Member",
        )

        coEvery { mangaRepository.getAllMangaByProfile(2L) } returns listOf(targetManga, memberManga)
        coEvery { handler.awaitList<Any>(false, any()) } returnsMany listOf(
            emptyList<String>(),
            listOf(
                MangaMerge(targetId = targetManga.id, mangaId = targetManga.id, position = 0L),
                MangaMerge(targetId = targetManga.id, mangaId = memberManga.id, position = 1L),
            ),
        )

        val backup = creator.invoke(
            profileId = 2L,
            mangas = listOf(memberManga),
            options = BackupOptions(categories = false, chapters = false, tracking = false, history = false),
        )

        backup.single().mergeTargetSource shouldBe targetManga.source
        backup.single().mergeTargetUrl shouldBe targetManga.url
        backup.single().mergePosition shouldBe 1
    }
}
