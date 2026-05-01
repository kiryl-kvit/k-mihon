package eu.kanade.tachiyomi.data.anime.download.model

import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoStreamType
import kotlinx.serialization.Serializable

@Serializable
data class AnimeDownloadManifest(
    val animeId: Long,
    val episodeId: Long,
    val animeTitle: String,
    val episodeTitle: String,
    val originalEpisodeUrl: String,
    val qualityMode: String,
    val selection: VideoPlaybackSelection,
    val video: DownloadedVideo,
    val subtitles: List<DownloadedSubtitle>,
)

@Serializable
data class DownloadedVideo(
    val fileName: String,
    val sourceUrl: String,
    val headers: Map<String, String>,
    val label: String,
    val streamType: VideoStreamType,
    val mimeType: String?,
)

@Serializable
data class DownloadedSubtitle(
    val key: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val fileName: String,
    val isDefault: Boolean,
    val isForced: Boolean,
)
