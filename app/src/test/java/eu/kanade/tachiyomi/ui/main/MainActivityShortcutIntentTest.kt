package eu.kanade.tachiyomi.ui.main

import eu.kanade.tachiyomi.ui.home.HomeScreen
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.Constants

class MainActivityShortcutIntentTest {

    @Test
    fun `anime shortcut resolves to anime library entry tab`() {
        resolveShortcutTab(
            action = Constants.SHORTCUT_ANIME,
            animeIdToOpen = 42L,
        ) shouldBe HomeScreen.Tab.Library(animeIdToOpen = 42L)
    }

    @Test
    fun `manga shortcut resolves to manga library entry tab`() {
        resolveShortcutTab(
            action = Constants.SHORTCUT_MANGA,
            mangaIdToOpen = 24L,
        ) shouldBe HomeScreen.Tab.Library(mangaIdToOpen = 24L)
    }
}
