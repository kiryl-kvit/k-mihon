package eu.kanade.domain.extension.interactor

import eu.kanade.domain.extension.model.AnimeExtensions
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetAnimeExtensions(
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<AnimeExtensions> {
        return combine(
            extensionManager.installedAnimeExtensionsFlow,
            extensionManager.untrustedAnimeExtensionsFlow,
            extensionManager.availableAnimeExtensionsFlow,
        ) { installed, untrusted, available ->
            buildAnimeExtensions(
                installed = installed,
                available = available,
                untrusted = untrusted,
            )
        }
    }
}

internal fun buildAnimeExtensions(
    installed: List<Extension.InstalledAnime>,
    available: List<Extension.AvailableAnime>,
    untrusted: List<Extension.Untrusted>,
): AnimeExtensions {
    val sortedInstalled = installed
        .sortedWith(
            compareBy<Extension.InstalledAnime> { !it.isObsolete }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
        )
    val updates = sortedInstalled.filter { it.hasUpdate }
    val activeInstalled = sortedInstalled.filterNot { it.hasUpdate }

    val availablePackages = available
        .filter { extension ->
            installed.none { it.pkgName == extension.pkgName } &&
                untrusted.none { it.pkgName == extension.pkgName }
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    return AnimeExtensions(
        updates = updates,
        installed = activeInstalled,
        available = availablePackages,
        untrusted = untrusted.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
    )
}
