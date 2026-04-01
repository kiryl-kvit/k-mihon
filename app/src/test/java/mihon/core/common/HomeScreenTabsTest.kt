package mihon.core.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class HomeScreenTabsTest {

    @Test
    fun `default home tabs exclude profiles`() {
        defaultHomeScreenTabs() shouldBe setOf(
            HomeScreenTabs.Library.name,
            HomeScreenTabs.Updates.name,
            HomeScreenTabs.History.name,
            HomeScreenTabs.Browse.name,
            HomeScreenTabs.More.name,
        )
    }

    @Test
    fun `visible home tabs include profiles only when picker is shown`() {
        val tabs = setOf(HomeScreenTabs.Library, HomeScreenTabs.Profiles)

        resolveVisibleHomeScreenTabs(tabs, showProfilesTab = false) shouldBe listOf(
            HomeScreenTabs.Library,
            HomeScreenTabs.More,
        )

        resolveVisibleHomeScreenTabs(tabs, showProfilesTab = true) shouldBe listOf(
            HomeScreenTabs.Library,
            HomeScreenTabs.More,
            HomeScreenTabs.Profiles,
        )
    }

    @Test
    fun `sanitized home tabs preserve profiles in stored selection`() {
        sanitizeHomeScreenTabs(setOf(HomeScreenTabs.Library, HomeScreenTabs.Profiles)) shouldBe listOf(
            HomeScreenTabs.Library,
            HomeScreenTabs.More,
            HomeScreenTabs.Profiles,
        )
    }

    @Test
    fun `startup fallback prefers library when available`() {
        resolveHomeScreenTab(
            requestedTab = HomeScreenTabs.Updates,
            enabledTabs = listOf(HomeScreenTabs.Library, HomeScreenTabs.More),
        ) shouldBe HomeScreenTabs.Library
    }

    @Test
    fun `startup fallback uses next enabled tab when library is unavailable`() {
        resolveHomeScreenTab(
            requestedTab = HomeScreenTabs.Updates,
            enabledTabs = listOf(HomeScreenTabs.More, HomeScreenTabs.Profiles),
        ) shouldBe HomeScreenTabs.More
    }

    @Test
    fun `startup fallback skips profiles for content tabs when not included`() {
        resolveHomeScreenTab(
            requestedTab = HomeScreenTabs.Browse,
            enabledTabs = listOf(HomeScreenTabs.More),
        ) shouldBe HomeScreenTabs.More
    }
}
