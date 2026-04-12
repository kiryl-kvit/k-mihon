package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.data.ActiveProfileProvider
import tachiyomi.data.DatabaseHandler
import tachiyomi.domain.category.interactor.GetVideoCategories
import tachiyomi.domain.video.model.VideoEpisode
import tachiyomi.domain.video.model.VideoHistory
import tachiyomi.domain.video.model.VideoPlaybackState
import tachiyomi.domain.video.model.VideoTitle
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoHistoryRepository
import tachiyomi.domain.video.repository.VideoPlaybackStateRepository
import tachiyomi.domain.video.repository.VideoRepository
import java.util.Date

class VideoBackupCreatorTest {

    private val handler = mockk<DatabaseHandler>()
    private val profileProvider = mockk<ActiveProfileProvider>()
    private val getVideoCategories = mockk<GetVideoCategories>()
    private val videoRepository = mockk<VideoRepository>()
    private val videoEpisodeRepository = mockk<VideoEpisodeRepository>()
    private val videoHistoryRepository = mockk<VideoHistoryRepository>()
    private val videoPlaybackStateRepository = mockk<VideoPlaybackStateRepository>()

    private val creator = VideoBackupCreator(
        handler = handler,
        profileProvider = profileProvider,
        getVideoCategories = getVideoCategories,
        videoRepository = videoRepository,
        videoEpisodeRepository = videoEpisodeRepository,
        videoHistoryRepository = videoHistoryRepository,
        videoPlaybackStateRepository = videoPlaybackStateRepository,
    )

    init {
        every { profileProvider.activeProfileId } returns 1L
    }

    @Test
    fun `non-active profile backup reads child video data from requested profile`() = runTest {
        val video = VideoTitle.create().copy(
            id = 100L,
            source = 10L,
            url = "/video",
            title = "Video",
            favorite = true,
            initialized = true,
        )
        val episode = VideoEpisode.create().copy(
            id = 200L,
            videoId = video.id,
            url = "/episode-1",
            name = "Episode 1",
            watched = true,
            dateFetch = 123L,
            dateUpload = 456L,
            episodeNumber = 1.0,
            sourceOrder = 1L,
        )
        val playbackState = VideoPlaybackState(
            episodeId = episode.id,
            positionMs = 12_000L,
            durationMs = 24_000L,
            completed = false,
            lastWatchedAt = 789L,
        )
        val history = VideoHistory(
            id = 300L,
            episodeId = episode.id,
            watchedAt = Date(999L),
            watchedDuration = 1_500L,
        )

        coEvery { handler.awaitList<Any>(false, any()) } returnsMany listOf(
            listOf(episode),
            listOf(history),
        )
        coEvery { handler.awaitOneOrNull<Any>(false, any()) } returnsMany listOf(
            episode,
            playbackState,
            episode,
        )

        val backup = creator.invoke(
            profileId = 2L,
            videos = listOf(video),
            options = BackupOptions(categories = false, chapters = true, history = true),
        )

        backup.size shouldBe 1
        backup.single().episodes.single().url shouldBe episode.url
        backup.single().playbackStates.single().url shouldBe episode.url
        backup.single().playbackStates.single().positionMs shouldBe playbackState.positionMs
        backup.single().history.single().url shouldBe episode.url
        backup.single().history.single().lastWatched shouldBe history.watchedAt?.time
        backup.single().history.single().watchedDuration shouldBe history.watchedDuration

        coVerify(exactly = 2) { handler.awaitList<Any>(false, any()) }
        coVerify(exactly = 3) { handler.awaitOneOrNull<Any>(false, any()) }

        coVerify(exactly = 0) { videoEpisodeRepository.getEpisodesByVideoId(any()) }
        coVerify(exactly = 0) { videoEpisodeRepository.getEpisodeByUrlAndVideoId(any(), any()) }
        coVerify(exactly = 0) { videoEpisodeRepository.getEpisodeById(any()) }
        coVerify(exactly = 0) { videoHistoryRepository.getHistoryByVideoId(any()) }
        coVerify(exactly = 0) { videoPlaybackStateRepository.getByEpisodeId(any()) }
    }
}
