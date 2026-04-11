package eu.kanade.presentation.more.settings.screen

import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class SettingsProfileVisibilityTest {

    @Test
    fun `video profiles hide manga-specific settings screens`() {
        isSettingsScreenVisibleForProfileType(SettingsLibraryScreen, ProfileType.VIDEO) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsReaderScreen, ProfileType.VIDEO) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsDownloadScreen, ProfileType.VIDEO) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsTrackingScreen, ProfileType.VIDEO) shouldBe false
        isSettingsScreenVisibleForProfileType(SettingsBrowseScreen, ProfileType.VIDEO) shouldBe false
        isSettingsScreenVisibleForProfileType(CustomSettingsScreen, ProfileType.VIDEO) shouldBe false
    }

    @Test
    fun `video profiles keep neutral settings screens`() {
        isSettingsScreenVisibleForProfileType(SettingsAppearanceScreen, ProfileType.VIDEO) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsDataScreen, ProfileType.VIDEO) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsSecurityScreen, ProfileType.VIDEO) shouldBe true
        isSettingsScreenVisibleForProfileType(SettingsAdvancedScreen, ProfileType.VIDEO) shouldBe true
        isSettingsScreenVisibleForProfileType(AboutScreen, ProfileType.VIDEO) shouldBe true
    }

    @Test
    fun `video profile disallowed direct destination falls back to neutral start screen`() {
        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.Tracking,
            profileType = ProfileType.VIDEO,
            twoPane = false,
        ) shouldBe SettingsMainScreen

        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.Tracking,
            profileType = ProfileType.VIDEO,
            twoPane = true,
        ) shouldBe SettingsAppearanceScreen
    }

    @Test
    fun `video profile allowed direct destination stays intact`() {
        resolveSettingsStartScreen(
            destination = SettingsScreen.Destination.DataAndStorage,
            profileType = ProfileType.VIDEO,
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
