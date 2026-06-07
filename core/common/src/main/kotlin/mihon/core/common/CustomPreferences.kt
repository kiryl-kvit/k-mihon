package mihon.core.common

import dev.icerock.moko.resources.StringResource
import mihon.core.common.HomeScreenTabs
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.coerceIn
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class CustomPreferences(
    preferenceStore: PreferenceStore,
) {
    companion object {
        val MANGA_PREVIEW_PAGE_COUNT_RANGE = 1..50
        val ANIME_PREVIEW_PAGE_COUNT_RANGE = 1..50

        const val HOME_SCREEN_STARTUP_TAB_KEY = "home_screen_startup_tab"
        const val HOME_SCREEN_TABS_KEY = "home_screen_tabs"
        const val HOME_SCREEN_TAB_ORDER_KEY = "home_screen_tab_order"
        const val ENABLE_MANGA_PREVIEW_KEY = "enable_manga_preview"
        const val MANGA_PREVIEW_PAGE_COUNT_KEY = "manga_preview_page_count"
        const val MANGA_PREVIEW_SIZE_KEY = "manga_preview_size"
        const val ENABLE_ANIME_PREVIEW_KEY = "enable_anime_preview"
        const val ANIME_PREVIEW_PAGE_COUNT_KEY = "anime_preview_page_count"
        const val ANIME_PREVIEW_SIZE_KEY = "anime_preview_size"
        const val ENABLE_FEEDS_KEY = "enable_feeds"
        const val ENABLE_ANIME_PICTURE_IN_PICTURE_KEY = "enable_anime_picture_in_picture"
        const val ENABLE_ANIME_SEEK_PREVIEW_KEY = "enable_anime_seek_preview"
        const val ENABLE_ANIME_VIDEO_PREVIEW_KEY = "enable_anime_video_preview"
        const val BROWSE_LONG_PRESS_ACTION_KEY = "browse_long_press_action"

        val profileKeys = setOf(
            HOME_SCREEN_STARTUP_TAB_KEY,
            HOME_SCREEN_TABS_KEY,
            HOME_SCREEN_TAB_ORDER_KEY,
            ENABLE_MANGA_PREVIEW_KEY,
            MANGA_PREVIEW_PAGE_COUNT_KEY,
            MANGA_PREVIEW_SIZE_KEY,
            ENABLE_ANIME_PREVIEW_KEY,
            ANIME_PREVIEW_PAGE_COUNT_KEY,
            ANIME_PREVIEW_SIZE_KEY,
            ENABLE_FEEDS_KEY,
            ENABLE_ANIME_PICTURE_IN_PICTURE_KEY,
            ENABLE_ANIME_SEEK_PREVIEW_KEY,
            ENABLE_ANIME_VIDEO_PREVIEW_KEY,
            BROWSE_LONG_PRESS_ACTION_KEY,
        )
    }

    val homeScreenStartupTab: Preference<HomeScreenTabs> = preferenceStore.getEnum(
        HOME_SCREEN_STARTUP_TAB_KEY,
        HomeScreenTabs.Library,
    )

    val homeScreenTabs: Preference<Set<String>> = preferenceStore.getStringSet(
        HOME_SCREEN_TABS_KEY,
        defaultHomeScreenTabs(),
    )

    val homeScreenTabOrder: Preference<List<HomeScreenTabs>> = preferenceStore.getObjectFromString(
        HOME_SCREEN_TAB_ORDER_KEY,
        defaultHomeScreenTabOrder(),
        serializer = { it.toHomeScreenTabOrderPreferenceValue() },
        deserializer = { it.toHomeScreenTabOrder() },
    )

    val enableMangaPreview: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_MANGA_PREVIEW_KEY,
        false,
    )

    val mangaPreviewPageCount: Preference<Int> = preferenceStore.getInt(
        MANGA_PREVIEW_PAGE_COUNT_KEY,
        5,
    ).coerceIn(MANGA_PREVIEW_PAGE_COUNT_RANGE)

    val mangaPreviewSize: Preference<MangaPreviewSize> = preferenceStore.getEnum(
        MANGA_PREVIEW_SIZE_KEY,
        MangaPreviewSize.MEDIUM,
    )

    val enableAnimePreview: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_ANIME_PREVIEW_KEY,
        false,
    )

    val animePreviewPageCount: Preference<Int> = preferenceStore.getInt(
        ANIME_PREVIEW_PAGE_COUNT_KEY,
        5,
    ).coerceIn(ANIME_PREVIEW_PAGE_COUNT_RANGE)

    val animePreviewSize: Preference<AnimePreviewSize> = preferenceStore.getEnum(
        ANIME_PREVIEW_SIZE_KEY,
        AnimePreviewSize.MEDIUM,
    )

    val enableFeeds: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_FEEDS_KEY,
        true,
    )

    val enableAnimePictureInPicture: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_ANIME_PICTURE_IN_PICTURE_KEY,
        false,
    )

    val enableAnimeSeekPreview: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_ANIME_SEEK_PREVIEW_KEY,
        false,
    )

    val enableAnimeVideoPreview: Preference<Boolean> = preferenceStore.getBoolean(
        ENABLE_ANIME_VIDEO_PREVIEW_KEY,
        false,
    )

    val browseLongPressAction: Preference<BrowseLongPressAction> = preferenceStore.getObjectFromString(
        BROWSE_LONG_PRESS_ACTION_KEY,
        BrowseLongPressAction.LIBRARY_ACTION,
        serializer = { it.name },
        deserializer = {
            when (it) {
                "MANGA_PREVIEW" -> BrowseLongPressAction.PREVIEW
                else -> runCatching { BrowseLongPressAction.valueOf(it) }
                    .getOrDefault(BrowseLongPressAction.LIBRARY_ACTION)
            }
        },
    )

    enum class MangaPreviewSize(val titleRes: StringResource) {
        SMALL(MR.strings.pref_manga_preview_size_small),
        MEDIUM(MR.strings.pref_manga_preview_size_medium),
        LARGE(MR.strings.pref_manga_preview_size_large),
        EXTRA_LARGE(MR.strings.pref_manga_preview_size_extra_large),
    }

    enum class AnimePreviewSize(val titleRes: StringResource) {
        SMALL(MR.strings.pref_manga_preview_size_small),
        MEDIUM(MR.strings.pref_manga_preview_size_medium),
        LARGE(MR.strings.pref_manga_preview_size_large),
        EXTRA_LARGE(MR.strings.pref_manga_preview_size_extra_large),
    }

    enum class BrowseLongPressAction(val titleRes: StringResource) {
        LIBRARY_ACTION(MR.strings.pref_browse_long_press_action_library_action),
        PREVIEW(MR.strings.pref_browse_long_press_action_preview),
    }
}
