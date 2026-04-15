package mihon.feature.profiles.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import tachiyomi.domain.profile.model.ProfileType
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ProfileType.label(): String {
    return when (this) {
        ProfileType.MANGA -> stringResource(MR.strings.profiles_type_manga)
        ProfileType.ANIME -> stringResource(MR.strings.profiles_type_anime)
    }
}

internal fun ProfileType.icon(): ImageVector {
    return when (this) {
        ProfileType.MANGA -> Icons.Outlined.CollectionsBookmark
        ProfileType.ANIME -> Icons.Filled.PlayArrow
    }
}
