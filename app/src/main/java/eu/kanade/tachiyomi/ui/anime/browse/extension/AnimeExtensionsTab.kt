package eu.kanade.tachiyomi.ui.anime.browse.extension

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.ExtensionReposScreen
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.browse.extension.ExtensionUiModel
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun animeExtensionsTab(
    screenModel: AnimeExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val state by screenModel.state.collectAsState()

    var privateExtensionToUninstall by remember { mutableStateOf<Extension?>(null) }
    var trustState by remember { mutableStateOf<Extension.Untrusted?>(null) }

    return TabContent(
        titleRes = MR.strings.label_extensions,
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(ExtensionReposScreen()) },
            ),
        ),
        content = { contentPadding, _ ->
            BackHandler(enabled = state.searchQuery != null) {
                screenModel.search(null)
            }

            PullRefresh(
                refreshing = state.isRefreshing,
                enabled = !state.isLoading,
                onRefresh = screenModel::findAvailableExtensions,
            ) {
                when {
                    state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                    state.isEmpty -> {
                        EmptyScreen(
                            stringRes = if (!state.searchQuery.isNullOrEmpty()) {
                                MR.strings.no_results_found
                            } else {
                                MR.strings.empty_screen
                            },
                            modifier = Modifier.padding(contentPadding),
                        )
                    }
                    else -> {
                        ScrollbarLazyColumn(contentPadding = contentPadding) {
                            state.items.forEach { entry ->
                                val header = entry.key
                                val items = entry.value

                                item(key = "anime-ext-header-${header.hashCode()}") {
                                    Text(
                                        text = when (header) {
                                            is ExtensionUiModel.Header.Resource -> stringResource(header.textRes)
                                            is ExtensionUiModel.Header.Text -> header.text
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = MaterialTheme.padding.medium,
                                            vertical = MaterialTheme.padding.small,
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }

                                items(
                                    count = items.size,
                                    key = { index ->
                                        val extension = items[index].extension
                                        "anime-ext-${extension.pkgName}-${extension.versionCode}"
                                    },
                                ) { index ->
                                    val item = items[index]
                                    val extension = item.extension

                                    Row(
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    when (extension) {
                                                        is Extension.InstalledAnime -> navigator.push(AnimeExtensionDetailsScreen(extension.pkgName))
                                                        is Extension.AvailableAnime -> screenModel.installExtension(extension)
                                                        is Extension.Untrusted -> trustState = extension
                                                        else -> Unit
                                                    }
                                                },
                                                onLongClick = {
                                                    if (context.isPackageInstalled(extension.pkgName)) {
                                                        screenModel.uninstallExtension(extension)
                                                    } else {
                                                        privateExtensionToUninstall = extension
                                                    }
                                                },
                                            )
                                            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            modifier = Modifier.size(40.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            if (item.installStep.isCompleted()) {
                                                ExtensionIcon(
                                                    extension = extension,
                                                    modifier = Modifier.size(40.dp),
                                                )
                                            } else {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp,
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(start = MaterialTheme.padding.medium),
                                        ) {
                                            Text(
                                                text = extension.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            val meta = buildList {
                                                extension.lang?.takeIf { it.isNotBlank() }?.let(::add)
                                                extension.versionName.takeIf { it.isNotBlank() }?.let(::add)
                                                if (extension is Extension.Untrusted) {
                                                    add(stringResource(MR.strings.ext_untrusted))
                                                }
                                            }.joinToString(" · ")
                                            if (meta.isNotBlank()) {
                                                Text(
                                                    text = meta,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                )
                                            }
                                        }

                                        when {
                                            extension is Extension.Untrusted -> {
                                                IconButton(onClick = { trustState = extension }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.VerifiedUser,
                                                        contentDescription = stringResource(MR.strings.ext_trust),
                                                    )
                                                }
                                            }
                                            extension is Extension.AvailableAnime -> {
                                                IconButton(onClick = { screenModel.installExtension(extension) }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.GetApp,
                                                        contentDescription = stringResource(MR.strings.ext_install),
                                                    )
                                                }
                                            }
                                            extension is Extension.InstalledAnime && extension.hasUpdate -> {
                                                IconButton(onClick = { screenModel.updateExtension(extension) }) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Refresh,
                                                        contentDescription = stringResource(MR.strings.ext_update),
                                                    )
                                                }
                                            }
                                            else -> Unit
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            trustState?.let { extension ->
                AlertDialog(
                    title = { Text(text = stringResource(MR.strings.untrusted_extension)) },
                    text = { Text(text = stringResource(MR.strings.untrusted_extension_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                screenModel.trustExtension(extension)
                                trustState = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_trust))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                screenModel.uninstallExtension(extension)
                                trustState = null
                            },
                        ) {
                            Text(text = stringResource(MR.strings.ext_uninstall))
                        }
                    },
                    onDismissRequest = { trustState = null },
                )
            }

            privateExtensionToUninstall?.let { extension ->
                AnimeExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = { screenModel.uninstallExtension(extension) },
                    onDismissRequest = { privateExtensionToUninstall = null },
                )
            }
        },
    )
}

@Composable
private fun AnimeExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(MR.strings.ext_confirm_remove)) },
        text = { Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName)) },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
