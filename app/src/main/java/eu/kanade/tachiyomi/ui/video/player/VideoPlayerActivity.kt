package eu.kanade.tachiyomi.ui.video.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import android.view.ViewGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class VideoPlayerActivity : BaseActivity() {

    private val viewModel by viewModels<VideoPlayerViewModel>()
    private val networkHelper: NetworkHelper by lazy { Injekt.get() }
    private var player by mutableStateOf<ExoPlayer?>(null)
    private var progressSaveJob: Job? = null

    init {
        registerSecureActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        val animeId = intent.extras?.getLong(EXTRA_VIDEO_ID, INVALID_ID) ?: INVALID_ID
        val episodeId = intent.extras?.getLong(EXTRA_EPISODE_ID, INVALID_ID) ?: INVALID_ID
        if (animeId == INVALID_ID || episodeId == INVALID_ID) {
            finish()
            return
        }
        viewModel.init(animeId, episodeId)

        setComposeContent {
            val state by viewModel.state.collectAsState()

            VideoPlayerScaffold(
                state = state,
                animeId = animeId,
                episodeId = episodeId,
                networkHelper = networkHelper,
            )
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    @Composable
    private fun VideoPlayerScaffold(
        state: VideoPlayerViewModel.State,
        animeId: Long,
        episodeId: Long,
        networkHelper: NetworkHelper,
    ) {
        val current = state as? VideoPlayerViewModel.State.Ready
        tachiyomi.presentation.core.components.material.Scaffold(
            topBar = {
                AppBar(
                    title = current?.videoTitle ?: "Video Player",
                    subtitle = current?.episodeName,
                    navigateUp = ::finish,
                    actions = {
                        current?.let { readyState ->
                            AppBarActions(
                                persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_open_in_browser),
                                        icon = Icons.AutoMirrored.Outlined.OpenInNew,
                                        onClick = { openInExternalPlayer(readyState.stream) },
                                    ),
                                ),
                            )
                        }
                    },
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            VideoPlayerScreen(
                state = state,
                animeId = animeId,
                episodeId = episodeId,
                networkHelper = networkHelper,
                contentPadding = contentPadding,
            )
        }
    }

    @Composable
    private fun VideoPlayerScreen(
        state: VideoPlayerViewModel.State,
        animeId: Long,
        episodeId: Long,
        networkHelper: NetworkHelper,
        contentPadding: androidx.compose.foundation.layout.PaddingValues,
    ) {
        when (val current = state) {
            VideoPlayerViewModel.State.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Loading video stream...",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = "Video ID: $animeId\nEpisode ID: $episodeId",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            is VideoPlayerViewModel.State.Ready -> {
                val context = LocalContext.current
                LaunchedEffect(context, current.streamUrl) {
                    releasePlayer()
                    player = buildVideoPlayer(
                        context = context,
                        networkHelper = networkHelper,
                        stream = current.stream,
                    ).also { exoPlayer ->
                        exoPlayer.addListener(
                            object : Player.Listener {
                                override fun onPositionDiscontinuity(
                                    oldPosition: Player.PositionInfo,
                                    newPosition: Player.PositionInfo,
                                    reason: Int,
                                ) {
                                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                                        viewModel.resetPlaybackBaseline(newPosition.positionMs)
                                    }
                                }
                            },
                        )
                        if (current.resumePositionMs > 0L) {
                            exoPlayer.seekTo(current.resumePositionMs)
                        }
                        startProgressSaves(exoPlayer)
                    }
                }

                DisposableEffect(current.streamUrl) {
                    onDispose {
                        flushPlaybackState()
                        releasePlayer()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { androidContext ->
                            PlayerView(androidContext).apply {
                                useController = true
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                            }
                        },
                        update = { playerView ->
                            playerView.player = player
                        },
                    )
                }
            }
            is VideoPlayerViewModel.State.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Unable to open video",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = current.message,
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    override fun onPause() {
        player?.pause()
        flushPlaybackState()
        super.onPause()
    }

    override fun onStop() {
        flushPlaybackState()
        super.onStop()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun startProgressSaves(player: ExoPlayer) {
        progressSaveJob?.cancel()
        progressSaveJob = lifecycleScope.launchIO {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                persistPlayback(player)
            }
        }
    }

    private fun flushPlaybackState() {
        player?.let(::persistPlayback)
    }

    private fun persistPlayback(player: ExoPlayer) {
        viewModel.persistPlayback(
            positionMs = player.currentPosition,
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
        )
    }

    private fun releasePlayer() {
        progressSaveJob?.cancel()
        progressSaveJob = null
        player?.release()
        player = null
    }

    private fun openInExternalPlayer(stream: eu.kanade.tachiyomi.source.model.VideoStream) {
        val uri = android.net.Uri.parse(stream.request.url)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, stream.mimeType ?: stream.type.toExternalMimeType())
            putExtra(
                Browser.EXTRA_HEADERS,
                android.os.Bundle().apply {
                    stream.request.headers.forEach { (key, value) ->
                        putString(key, value)
                    }
                },
            )
        }
        try {
            startActivity(Intent.createChooser(intent, null))
        } catch (_: ActivityNotFoundException) {
            toast(stringResource(MR.strings.anime_source_compatibility_note))
        } catch (e: Throwable) {
            toast(e.message ?: stringResource(MR.strings.anime_source_compatibility_note))
        }
    }

    companion object {
        private const val EXTRA_VIDEO_ID = "video_id"
        private const val EXTRA_EPISODE_ID = "episode_id"
        private const val INVALID_ID = -1L
        private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L

        fun newIntent(context: Context, animeId: Long, episodeId: Long): Intent {
            return Intent(context, VideoPlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_VIDEO_ID, animeId)
                putExtra(EXTRA_EPISODE_ID, episodeId)
            }
        }
    }
}

private fun eu.kanade.tachiyomi.source.model.VideoStreamType.toExternalMimeType(): String {
    return when (this) {
        eu.kanade.tachiyomi.source.model.VideoStreamType.HLS -> "application/vnd.apple.mpegurl"
        eu.kanade.tachiyomi.source.model.VideoStreamType.DASH -> "application/dash+xml"
        eu.kanade.tachiyomi.source.model.VideoStreamType.PROGRESSIVE -> "video/*"
        eu.kanade.tachiyomi.source.model.VideoStreamType.UNKNOWN -> "video/*"
    }
}
