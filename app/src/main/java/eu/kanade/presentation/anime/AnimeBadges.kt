package eu.kanade.presentation.anime

import androidx.compose.runtime.Composable
import tachiyomi.presentation.core.components.Badge

@Composable
fun AnimeDurationBadge(duration: String?) {
    val text = duration?.takeIf { it.isNotBlank() } ?: return
    Badge(text = text)
}
