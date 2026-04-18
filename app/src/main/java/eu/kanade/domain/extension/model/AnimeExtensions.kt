package eu.kanade.domain.extension.model

import eu.kanade.tachiyomi.extension.model.Extension

data class AnimeExtensions(
    val updates: List<Extension.InstalledAnime>,
    val installed: List<Extension.InstalledAnime>,
    val available: List<Extension.AvailableAnime>,
    val untrusted: List<Extension.Untrusted>,
)
