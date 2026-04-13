package eu.kanade.tachiyomi.source.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoStream(
    val request: VideoRequest,
    val label: String = "",
    val type: VideoStreamType = VideoStreamType.UNKNOWN,
    val mimeType: String? = null,
    val key: String = "",
)

@Serializable
enum class VideoStreamType {
    HLS,
    DASH,
    PROGRESSIVE,
    UNKNOWN,
}
