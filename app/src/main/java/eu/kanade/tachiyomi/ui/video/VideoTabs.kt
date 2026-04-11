package eu.kanade.tachiyomi.ui.video

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R

sealed class VideoPlaceholderTab(
    private val index: UShort,
    private val titleText: String,
    private val animationRes: Int,
) : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(animationRes)
            return TabOptions(
                index = index,
                title = titleText,
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) = Unit

    @Composable
    override fun Content() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = titleText,
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

data object VideoLibraryTab : VideoPlaceholderTab(0u, "Video Library", R.drawable.anim_library_enter)

data object VideoUpdatesTab : VideoPlaceholderTab(1u, "Video Updates", R.drawable.anim_updates_enter)

data object VideoHistoryTab : VideoPlaceholderTab(2u, "Video History", R.drawable.anim_history_enter)

data object VideoBrowseTab : VideoPlaceholderTab(3u, "Video Browse", R.drawable.anim_browse_enter)

data object VideoMoreTab : VideoPlaceholderTab(4u, "Video More", R.drawable.anim_more_enter)
