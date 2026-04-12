package tachiyomi.domain.video.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VideoModelsTest {

    @Test
    fun `VideoTitle create has safe defaults`() {
        val title = VideoTitle.create()

        title.id shouldBe -1L
        title.source shouldBe -1L
        title.favorite shouldBe false
        title.url shouldBe ""
        title.title shouldBe ""
        title.displayTitle shouldBe ""
    }

    @Test
    fun `VideoEpisode create has safe defaults`() {
        val episode = VideoEpisode.create()

        episode.id shouldBe -1L
        episode.videoId shouldBe -1L
        episode.watched shouldBe false
        episode.completed shouldBe false
        episode.url shouldBe ""
        episode.episodeNumber shouldBe -1.0
    }
}
