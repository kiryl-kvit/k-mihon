package eu.kanade.presentation.more.settings.screen

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class SettingsLibraryScreenTest {

    @Test
    fun `anime profiles expose shared library sections except behavior`() {
        visibleLibrarySettingsSectionsForProfileType(ProfileType.ANIME) shouldBe listOf(
            LibrarySettingsSection.Categories,
            LibrarySettingsSection.Display,
            LibrarySettingsSection.Group,
            LibrarySettingsSection.LibraryUpdate,
        )
    }

    @Test
    fun `manga profiles expose all library settings sections`() {
        visibleLibrarySettingsSectionsForProfileType(ProfileType.MANGA) shouldBe listOf(
            LibrarySettingsSection.Categories,
            LibrarySettingsSection.Display,
            LibrarySettingsSection.Group,
            LibrarySettingsSection.LibraryUpdate,
            LibrarySettingsSection.Behavior,
        )
    }
}
