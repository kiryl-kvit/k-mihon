package eu.kanade.presentation.browse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.domain.extension.interactor.AnimeExtensionSourceItem
import eu.kanade.presentation.browse.components.ExtensionIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.WarningBanner
import eu.kanade.presentation.more.settings.widget.TrailingWidgetBuffer
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.anime.browse.extension.AnimeExtensionDetailsScreenModel
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun AnimeExtensionDetailsScreen(
    navigateUp: () -> Unit,
    state: AnimeExtensionDetailsScreenModel.State,
    onClickEnableAll: () -> Unit,
    onClickDisableAll: () -> Unit,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    remember(state.extension) { state.extension?.repoUrl }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.label_extension_info),
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        actions = persistentListOf(
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_enable_all),
                                onClick = onClickEnableAll,
                            ),
                            AppBar.OverflowAction(
                                title = stringResource(MR.strings.action_disable_all),
                                onClick = onClickDisableAll,
                            ),
                        ),
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (state.extension == null) {
            EmptyScreen(
                MR.strings.empty_screen,
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        VideoExtensionDetails(
            contentPadding = paddingValues,
            extension = state.extension,
            sources = state.sources,
            incognitoMode = state.isIncognito,
            onClickUninstall = onClickUninstall,
            onClickSource = onClickSource,
            onClickIncognito = onClickIncognito,
        )
    }
}

@Composable
private fun VideoExtensionDetails(
    contentPadding: PaddingValues,
    extension: Extension.InstalledAnime,
    sources: ImmutableList<AnimeExtensionSourceItem>,
    incognitoMode: Boolean,
    onClickUninstall: () -> Unit,
    onClickSource: (sourceId: Long) -> Unit,
    onClickIncognito: (Boolean) -> Unit,
) {
    val context = LocalContext.current

    ScrollbarLazyColumn(
        contentPadding = contentPadding,
    ) {
        if (extension.isObsolete) {
            item {
                WarningBanner(MR.strings.obsolete_extension_message)
            }
        }

        item {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(
                            top = MaterialTheme.padding.medium,
                            bottom = MaterialTheme.padding.small,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ExtensionIcon(
                        modifier = Modifier.size(112.dp),
                        extension = extension,
                    )
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = extension.pkgName.substringAfter("eu.kanade.tachiyomi.extension."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(top = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onClickUninstall,
                    ) {
                        Text(stringResource(MR.strings.ext_uninstall))
                    }

                    if (extension.isShared) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", extension.pkgName, null)
                                    context.startActivity(this)
                                }
                            },
                        ) {
                            Text(
                                text = stringResource(MR.strings.ext_app_info),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }

                TextPreferenceWidget(
                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.small),
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(MR.strings.pref_incognito_mode_extension_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    widget = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = incognitoMode,
                                onCheckedChange = onClickIncognito,
                                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                            )
                        }
                    },
                )

                HorizontalDivider()
            }
        }

        items(
            count = sources.size,
            key = { index -> sources[index].source.id },
        ) { index ->
            val source = sources[index]
            TextPreferenceWidget(
                title = if (source.labelAsName) {
                    source.source.toString()
                } else {
                    LocaleHelper.getSourceDisplayName(source.source.lang, context)
                },
                widget = {
                    Switch(
                        checked = source.enabled,
                        onCheckedChange = null,
                        modifier = Modifier.padding(start = TrailingWidgetBuffer),
                    )
                },
                onPreferenceClick = { onClickSource(source.source.id) },
            )
        }
    }
}
