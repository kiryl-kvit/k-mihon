package eu.kanade.tachiyomi.ui.video.player

import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackOption
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoStream
import tachiyomi.domain.anime.model.PlayerQualityMode

data class VideoPlaybackUiState(
    val sourceSelection: VideoPlaybackSelection,
    val preferredSourceQualityKey: String?,
    val currentStream: VideoStream,
    val currentStreamLabel: String,
    val streamOptions: List<VideoPlaybackOption>,
    val playbackData: VideoPlaybackData,
    val adaptiveQualities: List<VideoAdaptiveQualityOption> = emptyList(),
    val currentAdaptiveQuality: VideoAdaptiveQualityPreference = VideoAdaptiveQualityPreference.Auto,
) {
    val showsAdaptiveQualitySelector: Boolean
        get() = playbackData.sourceQualities.isEmpty() && adaptiveQualities.size > 1

    val persistedSourceSelection: VideoPlaybackSelection
        get() = sourceSelection.copy(
            sourceQualityKey = preferredSourceQualityKey ?: sourceSelection.sourceQualityKey,
        )
}

sealed interface VideoAdaptiveQualityPreference {
    data object Auto : VideoAdaptiveQualityPreference

    data class SpecificHeight(val height: Int) : VideoAdaptiveQualityPreference

    fun toPlayerQualityMode(): PlayerQualityMode {
        return when (this) {
            Auto -> PlayerQualityMode.AUTO
            is SpecificHeight -> PlayerQualityMode.SPECIFIC_HEIGHT
        }
    }

    fun heightOrNull(): Int? {
        return when (this) {
            Auto -> null
            is SpecificHeight -> height
        }
    }
}

data class VideoAdaptiveQualityOption(
    val label: String,
    val preference: VideoAdaptiveQualityPreference,
)

internal fun VideoPlaybackSelection.withSelectedStream(streamKey: String?): VideoPlaybackSelection {
    return copy(streamKey = streamKey)
}

internal fun VideoPlaybackSelection.withSelectedDub(
    dubKey: String?,
    originalSelection: VideoPlaybackSelection,
): VideoPlaybackSelection {
    return copy(
        dubKey = dubKey,
        streamKey = null,
    ).restoringOriginalStreamIfCompatible(originalSelection)
}

internal fun VideoPlaybackSelection.withSelectedSourceQuality(
    sourceQualityKey: String?,
    originalSelection: VideoPlaybackSelection,
): VideoPlaybackSelection {
    return copy(
        sourceQualityKey = sourceQualityKey,
        streamKey = null,
    ).restoringOriginalStreamIfCompatible(originalSelection)
}

private fun VideoPlaybackSelection.restoringOriginalStreamIfCompatible(
    originalSelection: VideoPlaybackSelection,
): VideoPlaybackSelection {
    return if (dubKey == originalSelection.dubKey && sourceQualityKey == originalSelection.sourceQualityKey) {
        copy(streamKey = originalSelection.streamKey)
    } else {
        this
    }
}
