package eu.kanade.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

data class VideoExtensions(
    val updates: List<Extension.InstalledVideo>,
    val installed: List<Extension.InstalledVideo>,
    val available: List<Extension.AvailableVideo>,
    val untrusted: List<Extension.Untrusted>,
)
