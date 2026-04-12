package eu.kanade.domain.extension.interactor

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.VideoSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetVideoExtensionSources(
    private val preferences: SourcePreferences,
) {

    fun subscribe(extension: Extension.InstalledVideo): Flow<List<VideoExtensionSourceItem>> {
        val isMultiSource = extension.sources.size > 1
        val isMultiLangSingleSource =
            isMultiSource && extension.sources.map { it.name }.distinct().size == 1

        return preferences.disabledVideoSources.changes().map { disabledSources ->
            fun VideoSource.isEnabled() = id.toString() !in disabledSources

            extension.sources
                .map { source ->
                    VideoExtensionSourceItem(
                        source = source,
                        enabled = source.isEnabled(),
                        labelAsName = isMultiSource && !isMultiLangSingleSource,
                    )
                }
        }
    }
}

data class VideoExtensionSourceItem(
    val source: VideoSource,
    val enabled: Boolean,
    val labelAsName: Boolean,
)
