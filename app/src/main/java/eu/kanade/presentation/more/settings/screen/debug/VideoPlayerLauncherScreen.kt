package eu.kanade.presentation.more.settings.screen.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerActivity
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import mihon.feature.profiles.core.ProfileManager
import tachiyomi.domain.profile.model.ProfileType
import tachiyomi.domain.video.repository.VideoEpisodeRepository
import tachiyomi.domain.video.repository.VideoRepository
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VideoPlayerLauncherScreen : Screen() {

    companion object {
        const val TITLE = "Video player launcher"
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { Model() }
        val state by screenModel.state.collectAsState()

        Scaffold(
            topBar = {
                AppBar(
                    title = TITLE,
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            when (val current = state) {
                is State.Loading -> {
                    EmptyState(
                        message = "Loading stored video episodes...",
                        contentPadding = contentPadding,
                    )
                }
                is State.NotVideoProfile -> {
                    EmptyState(
                        message = "Switch to a VIDEO profile to launch the built-in video player.",
                        contentPadding = contentPadding,
                    )
                }
                is State.Empty -> {
                    EmptyState(
                        message = "No stored video episodes found in the active VIDEO profile.",
                        contentPadding = contentPadding,
                    )
                }
                is State.Ready -> {
                    LazyColumn(
                        contentPadding = contentPadding + PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(current.items, key = { item -> item.episodeId }) { item ->
                            TextPreferenceWidget(
                                title = item.videoTitle,
                                subtitle = item.episodeName,
                                onPreferenceClick = {
                                    context.startActivity(
                                        VideoPlayerActivity.newIntent(
                                            context = context,
                                            videoId = item.videoId,
                                            episodeId = item.episodeId,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState(
        message: String,
        contentPadding: PaddingValues,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    private class Model(
        private val profileManager: ProfileManager = Injekt.get(),
        private val videoRepository: VideoRepository = Injekt.get(),
        private val videoEpisodeRepository: VideoEpisodeRepository = Injekt.get(),
    ) : ScreenModel {

        val state = profileManager.activeProfile
            .flatMapLatest { activeProfile ->
                flow {
                    emit(
                        when {
                            activeProfile == null -> State.Loading
                            activeProfile.type != ProfileType.VIDEO -> State.NotVideoProfile
                            else -> {
                                val items = videoRepository.getAllVideosByProfile(activeProfile.id)
                                    .sortedBy { it.displayTitle.lowercase() }
                                    .flatMap { video ->
                                        videoEpisodeRepository.getEpisodesByVideoId(video.id)
                                            .sortedBy { episode -> episode.episodeNumber }
                                            .map { episode ->
                                                VideoLauncherItem(
                                                    videoId = video.id,
                                                    episodeId = episode.id,
                                                    videoTitle = video.displayTitle,
                                                    episodeName = episode.name.ifBlank { episode.url },
                                                )
                                            }
                                    }
                                if (items.isEmpty()) {
                                    State.Empty
                                } else {
                                    State.Ready(items.toImmutableList())
                                }
                            }
                        },
                    )
                }
            }
            .stateIn(ioCoroutineScope, SharingStarted.WhileSubscribed(5_000), State.Loading)
    }

    private sealed interface State {
        data object Loading : State

        data object NotVideoProfile : State

        data object Empty : State

        data class Ready(val items: kotlinx.collections.immutable.ImmutableList<VideoLauncherItem>) : State
    }

    private data class VideoLauncherItem(
        val videoId: Long,
        val episodeId: Long,
        val videoTitle: String,
        val episodeName: String,
    )
}
