package eu.kanade.presentation.more.settings.screen

import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class SettingsProfileVisibilityTest {

    @Test
    fun `anime profiles hide manga-specific settings screens`() {
        isSettingsScreenVisibleForProfileType(SettingsLibraryScreen, ProfileType.ANIME) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsReaderScreen, ProfileType.ANIME) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsDownloadScreen, ProfileType.ANIME) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsTrackingScreen, ProfileType.ANIME) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsBrowseScreen, ProfileType.ANIME) shouldBe false
    }

    @Test
    fun `anime profiles keep neutral settings screens`() {
        isSettingsScreenVisibleForProfileType(SettingsAppearanceScreen, ProfileType.ANIME) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsDataScreen, ProfileType.ANIME) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsSecurityScreen, ProfileType.ANIME) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsAdvancedScreen, ProfileType.ANIME) shouldBe true
        isSettingsScreenVisibleForProfileType(CustomSettingsScreen, ProfileType.ANIME) shouldBe true
        isSettingsScreenVisibleForProfileType(AboutScreen, ProfileType.ANIME) shouldBe true
    }

    @Test
    fun `anime profile disallowed direct destination falls back to neutral start screen`() {
        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.Tracking,
            profileType = ProfileType.ANIME,
            twoPane = false,
        ) shouldBe SettingsMainScreen

        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.Tracking,
            profileType = ProfileType.ANIME,
            twoPane = true,
        ) shouldBe SettingsAppearanceScreen
    }

    @Test
    fun `anime profile allowed direct destination stays intact`() {
        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.DataAndStorage,
            profileType = ProfileType.ANIME,
            twoPane = false,
        ) shouldBe SettingsDataScreen
    }

    @Test
    fun `manga profiles keep all settings screens visible`() {
        isSettingsScreenVisibleForProfileType(SettingsLibraryScreen, ProfileType.MANGA) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsReaderScreen, ProfileType.MANGA) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsTrackingScreen, ProfileType.MANGA) shouldBe true
        isSettingsScreenVisibleForProfileType(CustomSettingsScreen, ProfileType.MANGA) shouldBe true
    }
}
