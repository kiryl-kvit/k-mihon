package eu.kanade.presentation.browse.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.manga.components.PreviewError
import eu.kanade.presentation.manga.components.PreviewMessage
import eu.kanade.presentation.manga.components.PreviewSizeUi
import eu.kanade.presentation.manga.components.previewGridColumnCount
import eu.kanade.tachiyomi.ui.anime.AnimeScreenModel
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BrowseAnimePreviewSheet(
    animeId: Long,
    previewSize: PreviewSizeUi,
    onLibraryAction: (AnimeTitle) -> Unit,
    onMergeAction: (AnimeTitle) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    var hasRequestedPreview by rememberSaveable(animeId) { mutableStateOf(false) }
    val screenModel = object : Screen {
        override val key: ScreenKey = "browse-anime-preview-screen-$animeId"

        @Composable
        override fun Content() {
            error("Not used")
        }
    }.rememberScreenModel(tag = animeId.toString()) {
        AnimeScreenModel(
            context = context.applicationContext,
            animeId = animeId,
            fromSource = true,
        )
    }

    val state by screenModel.state.collectAsState()
    val previewState by screenModel.previewState.collectAsState()

    LaunchedEffect(state, hasRequestedPreview, previewState.pages) {
        if (!hasRequestedPreview && state is AnimeScreenModel.State.Success && previewState.pages.isEmpty()) {
            hasRequestedPreview = true
            screenModel.setPreviewExpanded(true)
        }
    }

    AdaptiveSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        enableImplicitDismiss = false,
    ) {
        when (val currentState = state) {
            AnimeScreenModel.State.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AnimeScreenModel.State.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    PreviewMessage(
                        icon = Icons.Outlined.Delete,
                        text = currentState.message,
                    )
                }
            }
            is AnimeScreenModel.State.Success -> {
                BrowseAnimePreviewDialogContent(
                    title = currentState.anime.displayTitle,
                    state = currentState,
                    previewState = previewState,
                    previewSize = previewSize,
                    snackbarHostState = screenModel.snackbarHostState,
                    onDismissRequest = onDismissRequest,
                    onLibraryAction = onLibraryAction,
                    onMergeAction = onMergeAction,
                    onOpenAnime = {
                        onDismissRequest()
                        onOpenAnime(currentState.anime.id)
                    },
                    onRetry = screenModel::retryPreview,
                )
            }
        }
    }
}

@Composable
private fun BrowseAnimePreviewDialogContent(
    title: String,
    state: AnimeScreenModel.State.Success,
    previewState: AnimeScreenModel.AnimePreviewState,
    previewSize: PreviewSizeUi,
    snackbarHostState: SnackbarHostState,
    onDismissRequest: () -> Unit,
    onLibraryAction: (AnimeTitle) -> Unit,
    onMergeAction: (AnimeTitle) -> Unit,
    onOpenAnime: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppBar(
                titleContent = { BrowseAnimePreviewTitle(title = title) },
                navigateUp = onDismissRequest,
            )
        },
        bottomBar = {
            BrowseAnimePreviewBottomBar(
                favorite = state.anime.favorite,
                onOpenAnime = onOpenAnime,
                onLibraryAction = { onLibraryAction(state.anime) },
                onMergeAction = { onMergeAction(state.anime) },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .systemBarsPadding()
                .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        ) {
            BrowseAnimePreviewContent(
                state = previewState,
                size = previewSize,
                onRetry = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = maxHeight)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun BrowseAnimePreviewContent(
    state: AnimeScreenModel.AnimePreviewState,
    size: PreviewSizeUi,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when {
            state.isLoading && state.pages.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.error != null -> {
                PreviewError(
                    message = state.error.message ?: stringResource(MR.strings.unknown_error),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.pages.isEmpty() -> {
                PreviewMessage(
                    icon = Icons.Outlined.Delete,
                    text = stringResource(MR.strings.anime_preview_empty),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columns = previewGridColumnCount(size, maxWidth)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.pages.chunked(columns).forEach { row ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                row.forEach { preview ->
                                    AsyncImage(
                                        model = preview.imageUrl,
                                        contentDescription = preview.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(16f / 9f),
                                    )
                                }
                                repeat(columns - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseAnimePreviewTitle(title: String) {
    Text(
        text = title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun BrowseAnimePreviewBottomBar(
    favorite: Boolean,
    onOpenAnime: () -> Unit,
    onLibraryAction: () -> Unit,
    onMergeAction: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            BrowseAnimePreviewBottomBarButton(
                text = stringResource(MR.strings.action_open),
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = onOpenAnime,
                modifier = Modifier.weight(1f),
            )
            BrowseAnimePreviewBottomBarButton(
                text = stringResource(if (favorite) MR.strings.action_remove else MR.strings.add_to_library),
                icon = if (favorite) Icons.Outlined.Delete else Icons.Outlined.FavoriteBorder,
                onClick = onLibraryAction,
                modifier = Modifier.weight(1f),
            )
            BrowseAnimePreviewBottomBarButton(
                text = stringResource(MR.strings.action_merge_into_library),
                icon = Icons.AutoMirrored.Outlined.CallSplit,
                onClick = onMergeAction,
                modifier = Modifier.weight(1f),
                tonal = true,
            )
        }
    }
}

@Composable
private fun BrowseAnimePreviewBottomBarButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
) {
    val content = @Composable {
        Icon(imageVector = icon, contentDescription = null)
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = modifier) { content() }
    } else {
        Button(onClick = onClick, modifier = modifier) { content() }
    }
}
