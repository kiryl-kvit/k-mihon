package eu.kanade.tachiyomi.extension.api

import eu.kanade.tachiyomi.extension.model.ExtensionType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionApiTypeParsingTest {

    @Test
    fun `missing repo type defaults to manga`() {
        extensionTypeFromRepoValue(null) shouldBe ExtensionType.MANGA
    }

    @Test
    fun `unknown repo type defaults to manga`() {
        extensionTypeFromRepoValue("novel") shouldBe ExtensionType.MANGA
    }

    @Test
    fun `manga repo type parses case insensitively`() {
        extensionTypeFromRepoValue("MANGA") shouldBe ExtensionType.MANGA
    }

    @Test
    fun `anime repo type parses case insensitively`() {
        extensionTypeFromRepoValue("AnImE") shouldBe ExtensionType.ANIME
    }
}
