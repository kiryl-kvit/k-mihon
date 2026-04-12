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
import eu.kanade.presentation.video.history.VideoHistoryScreen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.video.history.VideoHistoryScreenModel
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerActivity
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

data object VideoHistoryTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(MR.strings.history),
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

        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { VideoHistoryScreenModel() }
        val state by screenModel.state.collectAsState()

        VideoHistoryScreen(
            state = state,
            snackbarHostState = screenModel.snackbarHostState,
            onSearchQueryChange = screenModel::updateSearchQuery,
            onClickCover = { videoId -> navigator.push(VideoScreen(videoId)) },
            onClickResume = { videoId, episodeId ->
                context.openVideoEpisode(videoId, episodeId)
            },
            onDelete = screenModel::removeFromHistory,
            onDeleteAll = screenModel::removeAllHistory,
            onDialogChange = screenModel::setDialog,
        )

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                when (event) {
                    VideoHistoryScreenModel.Event.InternalError -> {
                        screenModel.snackbarHostState.showSnackbar(context.stringResource(MR.strings.internal_error))
                    }
                    VideoHistoryScreenModel.Event.HistoryCleared -> {
                        screenModel.snackbarHostState.showSnackbar(context.stringResource(MR.strings.clear_history_completed))
                    }
                }
            }
        }

        LaunchedEffect(state.list) {
            if (state.list != null) {
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
