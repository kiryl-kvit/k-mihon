package mihon.core.common

import dev.icerock.moko.resources.StringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.i18n.MR

class CustomPreferences(
    preferenceStore: PreferenceStore,
) {
    val homeScreenStartupTab: Preference<HomeScreenTabs> = preferenceStore.getEnum(
        Preference.appStateKey("home_screen_startup_tab"),
        HomeScreenTabs.Library,
    )

    val enableMangaPreview: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("enable_manga_preview"),
        false,
    )

    val mangaPreviewPageCount: Preference<Int> = preferenceStore.getInt(
        Preference.appStateKey("manga_preview_page_count"),
        5,
    )

    val mangaPreviewSize: Preference<MangaPreviewSize> = preferenceStore.getEnum(
        Preference.appStateKey("manga_preview_size"),
        MangaPreviewSize.MEDIUM,
    )

    enum class MangaPreviewSize(val titleRes: StringResource) {
        SMALL(MR.strings.pref_manga_preview_size_small),
        MEDIUM(MR.strings.pref_manga_preview_size_medium),
        LARGE(MR.strings.pref_manga_preview_size_large),
    }
}
