package eu.kanade.tachiyomi.ui.video

import android.content.Context
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.core.util.ifVideoSourcesLoaded
import eu.kanade.presentation.util.Tab
import eu.kanade.presentation.video.updates.VideoUpdatesScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerActivity
import eu.kanade.tachiyomi.ui.video.updates.VideoUpdatesScreenModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

data object VideoUpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 1u,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) = Unit

    @Composable
    override fun Content() {
        if (!ifVideoSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { VideoUpdatesScreenModel() }
        val state by screenModel.state.collectAsState()

        VideoUpdatesScreen(
            state = state,
            onClickCover = { videoId -> navigator.push(VideoScreen(videoId)) },
            onClickUpdate = { videoId, episodeId -> context.openVideoEpisode(videoId, episodeId) },
            onRetry = { navigator.replace(VideoUpdatesTab) },
        )

        LaunchedEffect(state.isLoading) {
            if (!state.isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }
    }
}

private fun Context.openVideoEpisode(videoId: Long, episodeId: Long) {
    startActivity(
        VideoPlayerActivity.newIntent(
            context = this,
            videoId = videoId,
            episodeId = episodeId,
        ),
    )
}
