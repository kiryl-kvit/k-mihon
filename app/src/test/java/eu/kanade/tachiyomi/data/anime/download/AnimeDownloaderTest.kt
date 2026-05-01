package eu.kanade.tachiyomi.data.anime.download

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AnimeDownloaderTest {

    @Test
    fun `treats extensionless stream variant as nested playlist`() {
        isHlsPlaylistReference(
            url = "https://cdn.example.com/720p",
            previousTagLine = "#EXT-X-STREAM-INF:BANDWIDTH=1920000,RESOLUTION=1280x720",
        ) shouldBe true
    }

    @Test
    fun `treats media uri as nested playlist even without extension`() {
        isHlsPlaylistReference(
            url = "https://cdn.example.com/audio/main",
            currentTagLine = "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"audio\",URI=\"audio/main\"",
        ) shouldBe true
    }

    @Test
    fun `does not treat key uri as nested playlist`() {
        isHlsPlaylistReference(
            url = "https://cdn.example.com/key",
            currentTagLine = "#EXT-X-KEY:METHOD=AES-128,URI=\"key\"",
        ) shouldBe false
    }
}
