package mihon.feature.profiles.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class ProfileNameValidationTest {

    @Test
    fun `name conflict matches same profile type`() {
        val profiles = listOf(
            profile(id = 1L, name = "Default", type = ProfileType.MANGA),
            profile(id = 2L, name = "Default", type = ProfileType.ANIME),
        )

        profiles.hasNameConflict(name = "default", type = ProfileType.MANGA) shouldBe true
    }

    @Test
    fun `name conflict ignores profiles from other types`() {
        val profiles = listOf(
            profile(id = 1L, name = "Default", type = ProfileType.MANGA),
        )

        profiles.hasNameConflict(name = "default", type = ProfileType.ANIME) shouldBe false
    }

    @Test
    fun `name conflict ignores the renamed profile`() {
        val profiles = listOf(
            profile(id = 1L, name = "Default", type = ProfileType.MANGA),
            profile(id = 2L, name = "Anime", type = ProfileType.ANIME),
        )

        profiles.hasNameConflict(
            name = " default ",
            type = ProfileType.MANGA,
            excludedProfileId = 1L,
        ) shouldBe false
    }

    private fun profile(id: Long, name: String, type: ProfileType) = Profile(
        id = id,
        uuid = "uuid-$id",
        name = name,
        type = type,
        colorSeed = id,
        position = id,
        requiresAuth = false,
        isArchived = false,
    )
}
