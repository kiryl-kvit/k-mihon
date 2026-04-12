package mihon.feature.profiles.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ProfileVideoSourcePreferenceProviderTest {

    @Test
    fun `video source preference key uses separate namespace from manga source prefs`() {
        val mangaKey = "source_7_42"
        val videoKey = videoSourcePreferenceKey(profileId = 7L, sourceId = 42L)

        videoKey shouldBe "video_source_7_42"
        videoKey shouldBeNot mangaKey
    }

    @Test
    fun `video source preference key is profile scoped`() {
        videoSourcePreferenceKey(profileId = 1L, sourceId = 42L) shouldBe "video_source_1_42"
        videoSourcePreferenceKey(profileId = 2L, sourceId = 42L) shouldBe "video_source_2_42"
    }

    private infix fun String.shouldBeNot(other: String) {
        (this == other) shouldBe false
    }
}
