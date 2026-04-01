package eu.kanade.presentation.more.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.Preference
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import mihon.core.common.CustomPreferences
import mihon.core.common.GlobalCustomPreferences
import mihon.core.common.HomeScreenTabs
import mihon.core.common.homeScreenTabOrder
import mihon.core.common.resolveHomeScreenTab
import mihon.core.common.sanitizeHomeScreenTabs
import mihon.core.common.toHomeScreenTabPreferenceValue
import mihon.core.common.toHomeScreenTabs
import mihon.feature.profiles.core.ProfilesPreferences
import mihon.feature.profiles.ui.ProfilesSettingsScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object CustomSettingsScreen : SearchableSettings {
    private fun readResolve(): Any = CustomSettingsScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_custom

    @Composable
    override fun getPreferences(): List<Preference> {
        val customPreferences = remember { Injekt.get<CustomPreferences>() }
        val globalCustomPreferences = remember { Injekt.get<GlobalCustomPreferences>() }
        val profilesPreferences = remember { Injekt.get<ProfilesPreferences>() }
        val previewEnabled by customPreferences.enableMangaPreview.collectAsState()
        val previewPageCount by customPreferences.mangaPreviewPageCount.collectAsState()
        val startupTab by customPreferences.homeScreenStartupTab.collectAsState()
        val homeScreenTabs by customPreferences.homeScreenTabs.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        var showProfilesInfo by rememberSaveable { mutableStateOf(false) }
        var showHomeTabsDialog by rememberSaveable { mutableStateOf(false) }
        val homeTabEntries = rememberHomeTabEntries()
        val enabledHomeTabs = remember(homeScreenTabs) {
            sanitizeHomeScreenTabs(homeScreenTabs.toHomeScreenTabs())
        }
        val startupTabEntries = remember(enabledHomeTabs, homeTabEntries) {
            enabledHomeTabs
                .filterNot { it == HomeScreenTabs.Profiles }
                .associateWith { homeTabEntries.getValue(it) }
                .toImmutableMap()
        }

        if (showProfilesInfo) {
            AlertDialog(
                onDismissRequest = { showProfilesInfo = false },
                title = { Text(text = stringResource(MR.strings.profiles_info_title)) },
                text = { Text(text = stringResource(MR.strings.profiles_info_description)) },
                confirmButton = {
                    TextButton(onClick = { showProfilesInfo = false }) {
                        Text(text = stringResource(MR.strings.action_close))
                    }
                },
            )
        }

        if (showHomeTabsDialog) {
            val selectedTabs = remember(homeScreenTabs) {
                enabledHomeTabs.toMutableStateList()
            }
            AlertDialog(
                onDismissRequest = { showHomeTabsDialog = false },
                title = { Text(text = stringResource(MR.strings.pref_home_screen_tabs)) },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn {
                        homeScreenTabOrder.forEach { tab ->
                            item {
                                val isSelected = tab in selectedTabs
                                val isLocked = tab == HomeScreenTabs.More
                                LabeledCheckbox(
                                    label = homeTabEntries.getValue(tab),
                                    checked = isSelected,
                                    enabled = !isLocked,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (tab !in selectedTabs) {
                                                selectedTabs.add(tab)
                                            }
                                        } else {
                                            selectedTabs.remove(tab)
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newTabs = sanitizeHomeScreenTabs(selectedTabs.toSet())
                            customPreferences.homeScreenTabs.set(newTabs.toHomeScreenTabPreferenceValue())
                            val resolvedStartupTab = resolveHomeScreenTab(
                                requestedTab = startupTab,
                                enabledTabs = newTabs.filterNot { it == HomeScreenTabs.Profiles },
                            )
                            if (resolvedStartupTab != startupTab) {
                                customPreferences.homeScreenStartupTab.set(resolvedStartupTab)
                            }
                            showHomeTabsDialog = false
                        },
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showHomeTabsDialog = false }) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.profiles_user_profiles),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.profiles_manage_title),
                        subtitle = stringResource(MR.strings.profiles_manage_summary),
                        widget = {
                            IconButton(onClick = { showProfilesInfo = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(MR.strings.profiles_info_title),
                                )
                            }
                        },
                        onClick = {
                            navigator.push(ProfilesSettingsScreen())
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = profilesPreferences.pickerEnabled,
                        title = stringResource(MR.strings.profiles_choose_on_launch),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_category_general),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(MR.strings.pref_home_screen_tabs),
                        subtitle = enabledHomeTabs.joinToString { homeTabEntries.getValue(it) },
                        onClick = { showHomeTabsDialog = true },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = customPreferences.homeScreenStartupTab,
                        entries = startupTabEntries,
                        title = stringResource(MR.strings.pref_startup_screen),
                        onValueChanged = {
                            customPreferences.homeScreenStartupTab.set(
                                resolveHomeScreenTab(
                                    requestedTab = it,
                                    enabledTabs = enabledHomeTabs.filterNot { it == HomeScreenTabs.Profiles },
                                ),
                            )
                            false
                        },
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = globalCustomPreferences.extensionsAutoUpdates,
                        title = stringResource(MR.strings.pref_extensions_auto_update),
                    ),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.pref_manga_preview),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = customPreferences.enableMangaPreview,
                        title = stringResource(MR.strings.pref_enable_manga_preview),
                        subtitle = stringResource(MR.strings.pref_enable_manga_preview_summary),
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        value = previewPageCount.coerceIn(1, 30),
                        preference = customPreferences.mangaPreviewPageCount,
                        valueRange = 1..30,
                        title = stringResource(MR.strings.pref_manga_preview_page_count),
                        valueString = previewPageCount.coerceIn(1, 30).toString(),
                        enabled = previewEnabled,
                        onValueChanged = {
                            customPreferences.mangaPreviewPageCount.set(it.coerceIn(1, 30))
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = customPreferences.mangaPreviewSize,
                        entries = CustomPreferences.MangaPreviewSize.entries
                            .associateWith { stringResource(it.titleRes) }
                            .toImmutableMap(),
                        title = stringResource(MR.strings.pref_manga_preview_size),
                        enabled = previewEnabled,
                    ),
                ),
            ),
        )
    }

    @Composable
    private fun rememberHomeTabEntries(): ImmutableMap<HomeScreenTabs, String> {
        return persistentMapOf(
            HomeScreenTabs.Library to stringResource(MR.strings.label_library),
            HomeScreenTabs.Updates to stringResource(MR.strings.label_recent_updates),
            HomeScreenTabs.History to stringResource(MR.strings.history),
            HomeScreenTabs.Browse to stringResource(MR.strings.browse),
            HomeScreenTabs.More to stringResource(MR.strings.label_more),
            HomeScreenTabs.Profiles to stringResource(MR.strings.profiles_title),
        )
    }
}
