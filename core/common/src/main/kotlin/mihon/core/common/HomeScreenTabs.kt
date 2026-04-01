package mihon.core.common

enum class HomeScreenTabs {
    Library,
    Updates,
    History,
    Browse,
    More,
    Profiles,
}

val homeScreenTabOrder = listOf(
    HomeScreenTabs.Library,
    HomeScreenTabs.Updates,
    HomeScreenTabs.History,
    HomeScreenTabs.Browse,
    HomeScreenTabs.More,
    HomeScreenTabs.Profiles,
)

val homeScreenContentTabOrder = homeScreenTabOrder.filterNot { it == HomeScreenTabs.Profiles }

fun Set<String>.toHomeScreenTabs(): Set<HomeScreenTabs> {
    return mapNotNullTo(linkedSetOf()) { entry ->
        HomeScreenTabs.entries.find { it.name == entry }
    }
}

fun sanitizeHomeScreenTabs(tabs: Set<HomeScreenTabs>): List<HomeScreenTabs> {
    return homeScreenTabOrder.filter { it == HomeScreenTabs.More || it in tabs }
}

fun resolveVisibleHomeScreenTabs(
    tabs: Set<HomeScreenTabs>,
    showProfilesTab: Boolean,
): List<HomeScreenTabs> {
    return sanitizeHomeScreenTabs(tabs).filter { it != HomeScreenTabs.Profiles || showProfilesTab }
}

fun defaultHomeScreenTabs(): Set<String> {
    return homeScreenContentTabOrder.mapTo(linkedSetOf()) { it.name }
}

fun Collection<HomeScreenTabs>.toHomeScreenTabPreferenceValue(): Set<String> {
    return mapTo(linkedSetOf()) { it.name }
}

fun resolveHomeScreenTab(
    requestedTab: HomeScreenTabs,
    enabledTabs: Collection<HomeScreenTabs>,
): HomeScreenTabs {
    val sanitizedTabs = homeScreenTabOrder.filter { it in enabledTabs }
    return when {
        requestedTab in sanitizedTabs -> requestedTab
        HomeScreenTabs.Library in sanitizedTabs -> HomeScreenTabs.Library
        else -> {
            val requestedIndex = homeScreenTabOrder.indexOf(requestedTab)
            sanitizedTabs.firstOrNull { homeScreenTabOrder.indexOf(it) > requestedIndex }
                ?: sanitizedTabs.firstOrNull()
                ?: HomeScreenTabs.More
        }
    }
}
