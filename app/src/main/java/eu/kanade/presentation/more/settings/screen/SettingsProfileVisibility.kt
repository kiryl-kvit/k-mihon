package eu.kanade.presentation.more.settings.screen

import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.more.settings.screen.about.AboutScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import tachiyomi.domain.profile.model.ProfileType

internal fun isSettingsScreenVisibleForProfileType(
    screen: Screen,
    profileType: ProfileType,
): Boolean {
    return when (profileType) {
        ProfileType.MANGA -> true
        ProfileType.VIDEO -> when (screen) {
            SettingsAppearanceScreen,
            SettingsDataScreen,
            SettingsSecurityScreen,
            SettingsAdvancedScreen,
            AboutScreen,
            -> true
            else -> false
        }
    }
}

internal fun resolveSettingsStartScreen(
    destination: SettingsScreen.Destination?,
    profileType: ProfileType,
    twoPane: Boolean,
): Screen {
    val requestedScreen = when (destination) {
        SettingsScreen.Destination.About -> AboutScreen
        SettingsScreen.Destination.DataAndStorage -> SettingsDataScreen
        SettingsScreen.Destination.Tracking -> SettingsTrackingScreen
        null -> if (twoPane) SettingsAppearanceScreen else SettingsMainScreen
    }

    if (isSettingsScreenVisibleForProfileType(requestedScreen, profileType)) {
        return requestedScreen
    }

    return if (twoPane) {
        SettingsAppearanceScreen
    } else {
        SettingsMainScreen
    }
}
