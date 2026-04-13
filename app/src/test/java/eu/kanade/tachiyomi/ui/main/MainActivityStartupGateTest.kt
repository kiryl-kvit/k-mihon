package eu.kanade.tachiyomi.ui.main

import io.kotest.matchers.shouldBe
import mihon.feature.profiles.core.Profile
import org.junit.jupiter.api.Test
import tachiyomi.domain.profile.model.ProfileType

class MainActivityStartupGateTest {

    @Test
    fun `initial startup shows picker before profile auth`() {
        val profile = profile(id = 2L, type = ProfileType.ANIME)

        resolveInitialStartupGateDecision(
            shouldShowPicker = true,
            initialProfile = profile,
            requiresProfileUnlock = true,
            shouldSkipProfileAuth = false,
        ) shouldBe ProfileStartupDecision(
            allowAppUnlockPrompt = false,
            state = ProfileStartupGateState.Picker,
            pendingAuthProfile = null,
        )
    }

    @Test
    fun `initial startup authenticates locked video profile when picker is skipped`() {
        val profile = profile(id = 2L, type = ProfileType.ANIME)

        resolveInitialStartupGateDecision(
            shouldShowPicker = false,
            initialProfile = profile,
            requiresProfileUnlock = true,
            shouldSkipProfileAuth = false,
        ) shouldBe ProfileStartupDecision(
            allowAppUnlockPrompt = true,
            state = ProfileStartupGateState.Authenticating,
            pendingAuthProfile = profile,
        )
    }

    @Test
    fun `initial startup skips duplicate profile auth when app unlock already covers it`() {
        val profile = profile(id = 1L, type = ProfileType.MANGA)

        resolveInitialStartupGateDecision(
            shouldShowPicker = false,
            initialProfile = profile,
            requiresProfileUnlock = true,
            shouldSkipProfileAuth = true,
        ) shouldBe ProfileStartupDecision(
            allowAppUnlockPrompt = true,
            state = ProfileStartupGateState.Ready,
            pendingAuthProfile = null,
        )
    }

    @Test
    fun `picker collapse authenticates remaining locked profile regardless of type`() {
        val profile = profile(id = 3L, type = ProfileType.ANIME)

        resolvePickerCollapseStartupGateDecision(
            profile = profile,
            requiresProfileUnlock = true,
            shouldSkipProfileAuth = false,
        ) shouldBe ProfileStartupDecision(
            allowAppUnlockPrompt = true,
            state = ProfileStartupGateState.Authenticating,
            pendingAuthProfile = profile,
        )
    }

    @Test
    fun `picker collapse becomes ready when remaining profile does not require auth`() {
        resolvePickerCollapseStartupGateDecision(
            profile = profile(id = 4L, type = ProfileType.ANIME),
            requiresProfileUnlock = false,
            shouldSkipProfileAuth = false,
        ) shouldBe ProfileStartupDecision(
            allowAppUnlockPrompt = true,
            state = ProfileStartupGateState.Ready,
            pendingAuthProfile = null,
        )
    }

    private fun profile(id: Long, type: ProfileType) = Profile(
        id = id,
        uuid = "uuid-$id",
        name = "Profile $id",
        type = type,
        colorSeed = 0L,
        position = id,
        requiresAuth = false,
        isArchived = false,
    )
}
