package eu.kanade.tachiyomi.ui.anime

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.video.player.VideoPlayerActivity
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class AnimeScreen(
    private val animeId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AnimeScreenModel(context.applicationContext, animeId) }
        val state by screenModel.state.collectAsState()

        when (val current = state) {
            AnimeScreenModel.State.Loading -> LoadingScreen()
            is AnimeScreenModel.State.Error -> {
                Scaffold(
                    topBar = {
                        AppBar(
                            title = stringResource(MR.strings.browse),
                            navigateUp = navigator::pop,
                            scrollBehavior = it,
                        )
                    },
                ) { contentPadding ->
                    EmptyScreen(
                        message = current.message,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
            }
            is AnimeScreenModel.State.Success -> {
                AnimeScreen(
                    state = current,
                    snackbarHostState = screenModel.snackbarHostState,
                    navigateUp = navigator::pop,
                    onRefresh = screenModel::refresh,
                    onAddToLibraryClicked = screenModel::toggleFavorite,
                    onEditCategoryClicked = screenModel::showChangeCategoryDialog.takeIf { current.anime.favorite },
                    onEpisodeClick = { episodeId -> context.startAnimeEpisode(current.anime.id, episodeId) },
                )

                when (val dialog = current.dialog) {
                    is AnimeScreenModel.Dialog.ChangeCategory -> {
                        ChangeCategoryDialog(
                            initialSelection = dialog.initialSelection,
                            onDismissRequest = screenModel::dismissDialog,
                            onEditCategories = { navigator.push(CategoryScreen()) },
                            onConfirm = { include, _ -> screenModel.setCategories(include) },
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
}

private fun android.content.Context.startAnimeEpisode(animeId: Long, episodeId: Long) {
    startActivity(
        VideoPlayerActivity.newIntent(
            context = this,
            animeId = animeId,
            episodeId = episodeId,
        ),
    )
}
