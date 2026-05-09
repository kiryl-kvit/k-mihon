package mihon.feature.migration.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.anime.download.AnimeDownloadManager
import kotlinx.coroutines.flow.update
import mihon.domain.migration.models.MigrationFlag
import mihon.domain.migration.usecases.MigrateAnimeUseCase
import mihon.feature.common.utils.getLabel
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
internal fun MigrateAnimeDialog(
    current: AnimeTitle,
    target: AnimeTitle,
    onClickTitle: () -> Unit,
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit = onDismissRequest,
) {
    val scope = rememberCoroutineScope()

    val screenModel = remember { MigrateAnimeDialogScreenModel() }
    LaunchedEffect(current, target) {
        screenModel.init(current, target)
    }
    val state by screenModel.state.collectAsState()

    if (state.isMigrated) return

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                state.applicableFlags.fastForEach { flag ->
                    LabeledCheckbox(
                        label = stringResource(flag.getLabel()),
                        checked = flag in state.selectedFlags,
                        onCheckedChange = { screenModel.toggleSelection(flag) },
                    )
                }
            }
        },
        confirmButton = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
            ) {
                TextButton(
                    onClick = {
                        onDismissRequest()
                        onClickTitle()
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_show_manga))
                }

                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launchIO {
                            screenModel.migrateAnime(replace = false)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.copy))
                }
                TextButton(
                    onClick = {
                        scope.launchIO {
                            screenModel.migrateAnime(replace = true)
                            withUIContext { onComplete() }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.migrate))
                }
            }
        },
    )
}

private class MigrateAnimeDialogScreenModel(
    private val sourcePreference: SourcePreferences = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val migrateAnime: MigrateAnimeUseCase = Injekt.get(),
) : StateScreenModel<MigrateAnimeDialogScreenModel.State>(State()) {

    fun init(current: AnimeTitle, target: AnimeTitle) {
        val applicableFlags = buildList {
            MigrationFlag.entries.forEach {
                val applicable = when (it) {
                    MigrationFlag.CHAPTER -> true
                    MigrationFlag.CATEGORY -> true
                    MigrationFlag.CUSTOM_COVER -> false
                    MigrationFlag.NOTES -> current.notes.isNotBlank()
                    MigrationFlag.REMOVE_DOWNLOAD -> animeDownloadManager.getDownloadCount(current) > 0
                }
                if (applicable) add(it)
            }
        }
        val selectedFlags = sourcePreference.migrationFlags.get()
        mutableState.update {
            State(
                current = current,
                target = target,
                applicableFlags = applicableFlags,
                selectedFlags = selectedFlags,
            )
        }
    }

    fun toggleSelection(flag: MigrationFlag) {
        mutableState.update {
            val selectedFlags = it.selectedFlags.toMutableSet()
                .apply { if (contains(flag)) remove(flag) else add(flag) }
                .toSet()
            it.copy(selectedFlags = selectedFlags)
        }
    }

    suspend fun migrateAnime(replace: Boolean) {
        val state = state.value
        val current = state.current ?: return
        val target = state.target ?: return
        sourcePreference.migrationFlags.set(state.selectedFlags)
        mutableState.update { it.copy(isMigrating = true) }
        migrateAnime(current, target, replace)
        mutableState.update { it.copy(isMigrating = false, isMigrated = true) }
    }

    data class State(
        val current: AnimeTitle? = null,
        val target: AnimeTitle? = null,
        val applicableFlags: List<MigrationFlag> = emptyList(),
        val selectedFlags: Set<MigrationFlag> = emptySet(),
        val isMigrating: Boolean = false,
        val isMigrated: Boolean = false,
    )
}
