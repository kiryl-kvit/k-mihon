package tachiyomi.domain.anime.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VideoModelsTest {

    @Test
    fun `AnimeTitle create has safe defaults`() {
        val title = AnimeTitle.create()

        title.id shouldBe -1L
        title.source shouldBe -1L
        title.favorite shouldBe false
        title.url shouldBe ""
        title.title shouldBe ""
        title.displayTitle shouldBe ""
    }

    @Test
    fun `AnimeEpisode create has safe defaults`() {
        val episode = AnimeEpisode.create()

        episode.id shouldBe -1L
        episode.animeId shouldBe -1L
        episode.watched shouldBe false
        episode.completed shouldBe false
        episode.url shouldBe ""
        episode.episodeNumber shouldBe -1.0
    }
}
