package eu.kanade.domain.extension.interactor

import eu.kanade.domain.extension.model.VideoExtensions
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class GetVideoExtensions(
    private val extensionManager: ExtensionManager,
) {

    fun subscribe(): Flow<VideoExtensions> {
        return combine(
            extensionManager.installedVideoExtensionsFlow,
            extensionManager.untrustedVideoExtensionsFlow,
            extensionManager.availableVideoExtensionsFlow,
        ) { installed, untrusted, available ->
            buildVideoExtensions(
                installed = installed,
                available = available,
                untrusted = untrusted,
            )
        }
    }
}

internal fun buildVideoExtensions(
    installed: List<Extension.InstalledVideo>,
    available: List<Extension.AvailableVideo>,
    untrusted: List<Extension.Untrusted>,
): VideoExtensions {
    val sortedInstalled = installed
        .sortedWith(
            compareBy<Extension.InstalledVideo> { !it.isObsolete }
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

    return VideoExtensions(
        updates = updates,
        installed = activeInstalled,
        available = availablePackages,
        untrusted = untrusted.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }),
    )
}
