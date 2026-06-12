package eu.kanade.tachiyomi.ui.browse.feed

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import eu.kanade.domain.anime.model.toMangaCover
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.video.player.ResolveVideoStream
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerMediaCache
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerPlaybackSnapshot
import eu.kanade.tachiyomi.ui.video.player.buildVideoPlayer
import eu.kanade.tachiyomi.ui.video.player.capturePlaybackSnapshot
import eu.kanade.tachiyomi.ui.video.player.coerceToPlaybackDuration
import eu.kanade.tachiyomi.ui.video.player.formatPlaybackTimestamp
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun AnimeVideoFeedBrowseContent(
    timelineModel: AnimeChronologicalFeedScreenModel,
    playbackModel: AnimeVideoFeedPlaybackScreenModel,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    uiMode: AnimeVideoFeedUiMode,
    onUiModeToggle: () -> Unit,
    onAnimeClick: (AnimeTitle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by timelineModel.state.collectAsState()
    val playbackState by playbackModel.state.collectAsState()
    val retryLabel = stringResource(MR.strings.action_retry)
    val unknownError = stringResource(MR.strings.unknown_error)
    val pagerState = rememberPagerState { state.animeIds.size }
    val scope = rememberCoroutineScope()
    val savedAnchor = timelineModel.savedAnchorSnapshot()
    val anchorRestoreKey = "${savedAnchor.mangaId}:${savedAnchor.scrollOffset}"
    var restoredAnchorKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.animeIds.isEmpty()) return@LaunchedEffect

        val result = snackbarHostState.showSnackbar(
            message = error.message ?: unknownError,
            actionLabel = retryLabel,
            duration = SnackbarDuration.Indefinite,
        )
        when (result) {
            SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
            SnackbarResult.ActionPerformed -> timelineModel.refresh()
        }
    }

    LaunchedEffect(state.animeIds, anchorRestoreKey) {
        if (restoredAnchorKey == anchorRestoreKey || state.animeIds.isEmpty()) return@LaunchedEffect

        val anchorIndex = savedAnchor.mangaId
            ?.let(state.animeIds::indexOf)
            ?.takeIf { it >= 0 }
            ?: 0
        pagerState.scrollToPage(anchorIndex)
        restoredAnchorKey = anchorRestoreKey
    }

    LaunchedEffect(state.animeIds, pagerState.currentPage) {
        val firstLoadPage = pagerState.currentPage - PRELOAD_PAGE_RADIUS
        val lastLoadPage = pagerState.currentPage + PRELOAD_PAGE_RADIUS
        val activeLoadIds = (firstLoadPage..lastLoadPage)
            .mapNotNull(state.animeIds::getOrNull)
            .toSet()
        playbackModel.retainActiveLoads(activeLoadIds)
    }

    LaunchedEffect(state.isRefreshing, state.isAppending, state.nextPageKey, state.animeIds.size) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collectLatest { page ->
                if (restoredAnchorKey != anchorRestoreKey) return@collectLatest
                timelineModel.saveAnchor(
                    animeId = state.animeIds.getOrNull(page),
                    scrollOffset = 0,
                )
                if (page >= state.animeIds.lastIndex - LOAD_MORE_PAGE_THRESHOLD) {
                    timelineModel.loadMore()
                }
            }
    }

    if (!state.hasLoaded && state.isRefreshing && state.animeIds.isEmpty()) {
        LoadingScreen(modifier.padding(contentPadding))
        return
    }

    if (state.animeIds.isEmpty()) {
        EmptyScreen(
            modifier = modifier.padding(contentPadding),
            message = state.error?.message ?: stringResource(MR.strings.no_results_found),
            actions = persistentListOf(
                EmptyScreenAction(
                    stringRes = MR.strings.action_retry,
                    icon = Icons.Outlined.Refresh,
                    onClick = timelineModel::refresh,
                ),
            ),
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            key = { page -> state.animeIds[page] },
        ) { page ->
            val animeId = state.animeIds[page]
            val anime = rememberVideoFeedAnime(animeId, timelineModel)
            val itemState = anime?.let { playbackState.items[it.id] }
            val isActive = page == pagerState.currentPage

            val shouldLoad =
                page in (pagerState.currentPage - PRELOAD_PAGE_RADIUS)..(pagerState.currentPage + PRELOAD_PAGE_RADIUS)

            if (anime != null && shouldLoad) {
                LaunchedEffect(anime.id) {
                    playbackModel.load(anime)
                }
            }

            AnimeVideoFeedPage(
                anime = anime,
                itemState = itemState,
                isActive = isActive,
                playbackModel = playbackModel,
                uiMode = uiMode,
                onUiModeToggle = onUiModeToggle,
                onAnimeClick = onAnimeClick,
                showBackToTop = pagerState.currentPage > 0,
                onBackToTop = { scope.launch { pagerState.animateScrollToPage(0) } },
                onRetry = { anime?.let(playbackModel::retry) },
            )
        }
    }
}

@Composable
private fun AnimeVideoFeedPage(
    anime: AnimeTitle?,
    itemState: AnimeVideoFeedPlaybackScreenModel.ItemState?,
    isActive: Boolean,
    playbackModel: AnimeVideoFeedPlaybackScreenModel,
    uiMode: AnimeVideoFeedUiMode,
    onUiModeToggle: () -> Unit,
    onAnimeClick: (AnimeTitle) -> Unit,
    showBackToTop: Boolean,
    onBackToTop: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (anime != null) {
            AnimeVideoFeedPoster(anime = anime)
        }

        when (itemState) {
            is AnimeVideoFeedPlaybackScreenModel.ItemState.Ready -> {
                if (isActive) {
                    AnimeVideoFeedInlinePlayer(
                        itemState = itemState,
                        playbackModel = playbackModel,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is AnimeVideoFeedPlaybackScreenModel.ItemState.Error -> {
                AnimeVideoFeedError(
                    message = videoFeedFailureMessage(itemState.failure),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is AnimeVideoFeedPlaybackScreenModel.ItemState.Loading, null -> {
                if (isActive) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
        }

        if (anime != null) {
            AnimeVideoFeedOverlay(
                anime = anime,
                onAnimeClick = { onAnimeClick(anime) },
                uiMode = uiMode,
                onUiModeToggle = onUiModeToggle,
                showBackToTop = showBackToTop,
                onBackToTop = onBackToTop,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

@Composable
private fun AnimeVideoFeedPoster(anime: AnimeTitle) {
    AsyncImage(
        model = anime.toMangaCover(),
        contentDescription = anime.displayTitle,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f)),
    )
}

@Composable
private fun AnimeVideoFeedOverlay(
    anime: AnimeTitle,
    onAnimeClick: () -> Unit,
    uiMode: AnimeVideoFeedUiMode,
    onUiModeToggle: () -> Unit,
    showBackToTop: Boolean,
    onBackToTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 12.dp,
                top = 8.dp,
                end = 12.dp,
                bottom = 44.dp,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(end = 48.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = anime.displayTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.36f), MaterialTheme.shapes.medium)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBackToTop) {
                VideoFeedBackToTopButton(onClick = onBackToTop)
            }
            IconButton(
                onClick = onUiModeToggle,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.36f), CircleShape)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = when (uiMode) {
                        AnimeVideoFeedUiMode.Default -> Icons.Outlined.Fullscreen
                        AnimeVideoFeedUiMode.Immersive -> Icons.Outlined.FullscreenExit
                    },
                    contentDescription = when (uiMode) {
                        AnimeVideoFeedUiMode.Default -> stringResource(MR.strings.browse_video_feed_enter_immersive)
                        AnimeVideoFeedUiMode.Immersive -> stringResource(MR.strings.browse_video_feed_exit_immersive)
                    },
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = onAnimeClick,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.36f), CircleShape)
                    .size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = stringResource(MR.strings.action_open),
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun AnimeVideoFeedError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(MR.strings.action_retry))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun AnimeVideoFeedInlinePlayer(
    itemState: AnimeVideoFeedPlaybackScreenModel.ItemState.Ready,
    playbackModel: AnimeVideoFeedPlaybackScreenModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val networkHelper = remember { Injekt.get<NetworkHelper>() }
    val mediaCache = remember { Injekt.get<VideoPlayerMediaCache>() }
    val unknownError = stringResource(MR.strings.unknown_error)
    var playerErrorMessage by remember(itemState.episode.id, itemState.result.stream.request.url) {
        mutableStateOf<String?>(null)
    }
    var hasRenderedFirstFrame by remember(itemState.episode.id, itemState.result.stream.request.url) {
        mutableStateOf(false)
    }
    val player = remember(itemState.episode.id, itemState.result.stream.request.url) {
        buildVideoPlayer(
            context = context,
            networkHelper = networkHelper,
            mediaCache = mediaCache,
            stream = itemState.result.stream,
            subtitles = itemState.result.subtitles,
        ).also { exoPlayer ->
            exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        hasRenderedFirstFrame = true
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playerErrorMessage = error.message ?: unknownError
                    }
                },
            )
        }
    }
    var playbackSnapshot by remember(player) { mutableStateOf(player.capturePlaybackSnapshot()) }
    var speedBoostActive by remember(player) { mutableStateOf(false) }

    LaunchedEffect(player, itemState.resumePositionMs) {
        if (itemState.resumePositionMs > 0L) {
            player.seekTo(itemState.resumePositionMs)
        }
        player.playWhenReady = true
        player.prepare()
    }

    LaunchedEffect(player) {
        while (isActive) {
            playbackSnapshot = player.capturePlaybackSnapshot()
            delay(PLAYBACK_SNAPSHOT_INTERVAL_MS)
        }
    }

    LaunchedEffect(player, itemState.episode.id) {
        while (isActive) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            val snapshot = player.capturePlaybackSnapshot()
            playbackModel.persistPlayback(
                episodeId = itemState.episode.id,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
            )
        }
    }

    DisposableEffect(player, itemState.episode.id) {
        onDispose {
            val snapshot = player.capturePlaybackSnapshot()
            playbackModel.persistPlayback(
                episodeId = itemState.episode.id,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
            )
            player.stop()
            player.release()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { androidContext ->
                PlayerView(androidContext).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                    setKeepContentOnPlayerReset(true)
                    setEnableComposeSurfaceSyncWorkaround(true)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.setKeepContentOnPlayerReset(true)
                playerView.useController = false
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (hasRenderedFirstFrame) 1f else 0f),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onPress = {
                            coroutineScope {
                                var boosted = false
                                val boostJob = launch {
                                    delay(SPEED_BOOST_PRESS_DELAY_MS)
                                    player.setPlaybackSpeed(SPEED_BOOST_MULTIPLIER)
                                    boosted = true
                                    speedBoostActive = true
                                }
                                try {
                                    val released = tryAwaitRelease()
                                    if (!boosted && released) {
                                        if (playbackSnapshot.playbackEnded) {
                                            player.seekTo(0L)
                                        }
                                        if (player.isPlaying) {
                                            player.pause()
                                        } else {
                                            player.play()
                                        }
                                        playbackSnapshot = player.capturePlaybackSnapshot()
                                    }
                                } finally {
                                    boostJob.cancel()
                                    if (boosted) {
                                        player.setPlaybackSpeed(NORMAL_PLAYBACK_SPEED)
                                        speedBoostActive = false
                                        playbackSnapshot = player.capturePlaybackSnapshot()
                                    }
                                }
                            }
                        },
                    )
                },
        )

        if (speedBoostActive) {
            Text(
                text = "2x",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }

        AnimeVideoFeedTimeline(
            snapshot = playbackSnapshot,
            onSeek = { positionMs ->
                player.seekTo(positionMs)
                if (playbackSnapshot.playbackEnded) {
                    player.play()
                }
                playbackSnapshot = player.capturePlaybackSnapshot()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
        )

        if (!playbackSnapshot.isPlaying && !playbackSnapshot.isLoading && player.playbackState == Player.STATE_READY) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.action_play),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.36f), CircleShape)
                    .padding(18.dp),
                tint = Color.White,
            )
        }

        playerErrorMessage?.let { message ->
            Text(
                text = message,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.64f), MaterialTheme.shapes.medium)
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun AnimeVideoFeedTimeline(
    snapshot: VideoPlayerPlaybackSnapshot,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val durationMs = snapshot.durationMs
    var isScrubbing by remember(durationMs) { mutableStateOf(false) }
    var scrubPositionMs by remember(durationMs) { mutableStateOf(snapshot.positionMs) }
    val displayedPositionMs = if (isScrubbing) scrubPositionMs else snapshot.positionMs
    val playedFraction = if (durationMs > 0L) {
        displayedPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    val bufferedFraction = if (durationMs > 0L) {
        snapshot.bufferedPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val updateScrubPosition: (Float) -> Unit = { x ->
            if (durationMs > 0L && widthPx > 0f) {
                scrubPositionMs = (durationMs * (x / widthPx).coerceIn(0f, 1f)).toLong()
                    .coerceToPlaybackDuration(durationMs)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isScrubbing) {
                Text(
                    text = buildString {
                        append(formatPlaybackTimestamp(scrubPositionMs))
                        append(" / ")
                        append(formatPlaybackTimestamp(durationMs))
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.48f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .pointerInput(durationMs, widthPx) {
                        if (durationMs <= 0L) return@pointerInput
                        detectDragGestures(
                            onDragStart = { offset ->
                                isScrubbing = true
                                updateScrubPosition(offset.x)
                            },
                            onDrag = { change, _ ->
                                updateScrubPosition(change.position.x)
                            },
                            onDragEnd = {
                                onSeek(scrubPositionMs)
                                isScrubbing = false
                            },
                            onDragCancel = {
                                isScrubbing = false
                            },
                        )
                    },
            ) {
                val centerY = size.height / 2f
                val trackStroke = if (isScrubbing) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width, centerY),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.42f),
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width * bufferedFraction.coerceIn(0f, 1f), centerY),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White,
                    start = androidx.compose.ui.geometry.Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(size.width * playedFraction.coerceIn(0f, 1f), centerY),
                    strokeWidth = trackStroke,
                    cap = StrokeCap.Round,
                )
                if (isScrubbing) {
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(
                            x = size.width * playedFraction.coerceIn(0f, 1f),
                            y = centerY,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoFeedBackToTopButton(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color.Black.copy(alpha = 0.48f),
        contentColor = Color.White,
        shape = CircleShape,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
    ) {
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowUp,
            contentDescription = stringResource(MR.strings.action_move_to_top),
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
        )
    }
}

@Composable
private fun rememberVideoFeedAnime(
    animeId: Long,
    screenModel: AnimeChronologicalFeedScreenModel,
): AnimeTitle? {
    var anime by remember(animeId) { mutableStateOf<AnimeTitle?>(null) }

    LaunchedEffect(animeId, screenModel) {
        screenModel.subscribeAnime(animeId).collectLatest {
            anime = it
        }
    }

    return anime
}

@Composable
private fun videoFeedFailureMessage(failure: AnimeVideoFeedPlaybackScreenModel.Failure): String {
    return when (failure) {
        AnimeVideoFeedPlaybackScreenModel.Failure.NoEpisode -> stringResource(MR.strings.browse_video_feed_no_episode)
        is AnimeVideoFeedPlaybackScreenModel.Failure.Stream -> failure.reason.videoFeedMessage()
        is AnimeVideoFeedPlaybackScreenModel.Failure.Unexpected ->
            failure.throwable.message
                ?: stringResource(MR.strings.unknown_error)
    }
}

@Composable
private fun ResolveVideoStream.Reason.videoFeedMessage(): String {
    return when (this) {
        ResolveVideoStream.Reason.VideoNotFound -> stringResource(MR.strings.browse_video_feed_video_not_found)
        ResolveVideoStream.Reason.EpisodeNotFound -> stringResource(MR.strings.browse_video_feed_episode_not_found)
        ResolveVideoStream.Reason.EpisodeMismatch -> stringResource(MR.strings.browse_video_feed_episode_mismatch)
        ResolveVideoStream.Reason.SourceLoadTimeout -> stringResource(MR.strings.browse_video_feed_source_timeout)
        ResolveVideoStream.Reason.SourceNotFound -> stringResource(MR.strings.browse_video_feed_source_not_found)
        ResolveVideoStream.Reason.NoStreams -> stringResource(MR.strings.browse_video_feed_no_streams)
        ResolveVideoStream.Reason.StreamFetchTimeout -> stringResource(MR.strings.browse_video_feed_stream_timeout)
        ResolveVideoStream.Reason.OfflineNoDownload -> stringResource(MR.strings.browse_video_feed_offline_no_download)
        is ResolveVideoStream.Reason.StreamFetchFailed -> cause.message ?: stringResource(MR.strings.unknown_error)
    }
}

internal enum class AnimeVideoFeedUiMode {
    Default,
    Immersive,
}

private const val LOAD_MORE_PAGE_THRESHOLD = 3
private const val PRELOAD_PAGE_RADIUS = 1
private const val PLAYBACK_SNAPSHOT_INTERVAL_MS = 250L
private const val PROGRESS_SAVE_INTERVAL_MS = 10_000L
private const val SPEED_BOOST_PRESS_DELAY_MS = 350L
private const val SPEED_BOOST_MULTIPLIER = 2f
private const val NORMAL_PLAYBACK_SPEED = 1f
