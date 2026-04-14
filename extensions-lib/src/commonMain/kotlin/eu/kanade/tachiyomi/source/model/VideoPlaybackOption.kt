package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoPlaybackOption(
    val key: String,
    val label: String,
    val description: String? = null,
)

@Serializable
data class VideoPlaybackSelection(
    val dubKey: String? = null,
    val streamKey: String? = null,
    val sourceQualityKey: String? = null,
)

@Serializable
data class VideoPlaybackData(
    val selection: VideoPlaybackSelection = VideoPlaybackSelection(),
    val dubs: List<VideoPlaybackOption> = emptyList(),
    val sourceQualities: List<VideoPlaybackOption> = emptyList(),
    val streams: List<VideoStream> = emptyList(),
)
