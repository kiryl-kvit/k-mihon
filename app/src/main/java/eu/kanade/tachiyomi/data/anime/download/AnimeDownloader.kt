package eu.kanade.tachiyomi.data.anime.download

import com.hippo.unifile.UniFile
import eu.kanade.domain.anime.model.toSEpisode
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadFailure
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.AnimeSubtitleSource
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoRequest
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import eu.kanade.tachiyomi.source.model.VideoSubtitle
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import tachiyomi.domain.anime.model.AnimeDownloadQualityMode
import tachiyomi.domain.source.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import kotlin.math.abs

class AnimeDownloader(
    private val provider: AnimeDownloadProvider = Injekt.get(),
    private val cache: AnimeDownloadCache = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val networkHelper: NetworkHelper = Injekt.get(),
    private val json: Json = Injekt.get(),
) {

    suspend fun download(download: AnimeDownload): AnimeDownloadFailure? {
        val source = sourceManager.get(download.anime.source)
            ?: return AnimeDownloadFailure(AnimeDownloadFailure.Reason.SOURCE_NOT_FOUND)

        val selectedDubKey = download.preferences.dubKey
        val requestedSelection = VideoPlaybackSelection(
            dubKey = selectedDubKey,
            streamKey = download.preferences.streamKey,
        )

        val playbackData = runCatching {
            source.getPlaybackData(download.episode.toSEpisode(), requestedSelection)
        }.getOrElse {
            return AnimeDownloadFailure(
                reason = AnimeDownloadFailure.Reason.NETWORK,
                message = it.message,
            )
        }

        if (selectedDubKey != null && playbackData.selection.dubKey != selectedDubKey) {
            return AnimeDownloadFailure(AnimeDownloadFailure.Reason.DUB_NOT_AVAILABLE)
        }

        val progressiveStreams = playbackData.streams
            .filter { it.request.url.isNotBlank() }
            .filter { it.type == VideoStreamType.PROGRESSIVE }
        if (progressiveStreams.isEmpty()) {
            return AnimeDownloadFailure(AnimeDownloadFailure.Reason.UNSUPPORTED_STREAM)
        }

        val stream = selectStream(download, progressiveStreams)
            ?: return AnimeDownloadFailure(AnimeDownloadFailure.Reason.STREAM_NOT_AVAILABLE)

        val subtitles = if (download.preferences.subtitleKey != null && source is AnimeSubtitleSource) {
            runCatching {
                source.getSubtitles(download.episode.toSEpisode(), playbackData.selection)
            }.getOrElse {
                return AnimeDownloadFailure(
                    reason = AnimeDownloadFailure.Reason.NETWORK,
                    message = it.message,
                )
            }
                .filter { it.request.url.isNotBlank() }
                .let { allSubtitles ->
                    val selected = allSubtitles.firstOrNull { subtitleKey(it) == download.preferences.subtitleKey }
                        ?: return AnimeDownloadFailure(AnimeDownloadFailure.Reason.SUBTITLE_NOT_AVAILABLE)
                    listOf(selected)
                }
        } else {
            emptyList()
        }

        val animeDir = provider.getAnimeDir(download.anime.title, source)
            .getOrElse {
                return AnimeDownloadFailure(
                    reason = AnimeDownloadFailure.Reason.UNKNOWN,
                    message = it.message,
                )
            }
        val episodeDirName = provider.getEpisodeDirName(download.episode.name, download.episode.url)
        animeDir.findFile(episodeDirName + AnimeDownloadManager.TMP_DIR_SUFFIX)?.delete()
        val tmpDir = animeDir.createDirectory(episodeDirName + AnimeDownloadManager.TMP_DIR_SUFFIX)
            ?: return AnimeDownloadFailure(AnimeDownloadFailure.Reason.UNKNOWN)

        try {
            download.status = AnimeDownload.State.RESOLVING
            download.selection = playbackData.selection.copy(streamKey = streamKey(stream))
            download.playbackData = playbackData.copy(selection = download.selection)
            download.stream = stream
            download.subtitles = subtitles

            download.status = AnimeDownload.State.DOWNLOADING
            download.progress = 5

            val videoFileName = downloadVideo(stream, tmpDir)
            download.progress = if (subtitles.isEmpty()) 80 else 60

            val subtitleFiles = subtitles.mapIndexed { index, subtitle ->
                downloadSubtitle(index, subtitle, tmpDir)
            }
            download.progress = 90

            writeManifest(download, stream, videoFileName, subtitleFiles, tmpDir)

            if (!tmpDir.renameTo(episodeDirName)) {
                return AnimeDownloadFailure(AnimeDownloadFailure.Reason.UNKNOWN, "Failed to finalize episode package")
            }
            cache.addEpisode(episodeDirName, animeDir, download.anime)
            download.progress = 100
            download.status = AnimeDownload.State.DOWNLOADED
            return null
        } catch (e: Throwable) {
            tmpDir.delete()
            return AnimeDownloadFailure(
                reason = if (e is IOException) AnimeDownloadFailure.Reason.NETWORK else AnimeDownloadFailure.Reason.UNKNOWN,
                message = e.message,
            )
        }
    }

    private suspend fun downloadVideo(stream: VideoStream, tmpDir: UniFile): String {
        val extension = inferExtension(stream.request.url, stream.mimeType, fallback = "mp4")
        val fileName = "video.$extension"
        val file = tmpDir.createFile(fileName) ?: error("Failed to create video file")
        fetchToFile(stream.request, file)
        return fileName
    }

    private suspend fun downloadSubtitle(index: Int, subtitle: VideoSubtitle, tmpDir: UniFile): DownloadedSubtitle {
        val extension = inferExtension(subtitle.request.url, subtitle.mimeType, fallback = "vtt")
        val fileName = "subtitle_${index + 1}.$extension"
        val file = tmpDir.createFile(fileName) ?: error("Failed to create subtitle file")
        fetchToFile(subtitle.request, file)
        return DownloadedSubtitle(
            key = subtitleKey(subtitle),
            label = subtitle.label,
            language = subtitle.language,
            mimeType = subtitle.mimeType,
            fileName = fileName,
            isDefault = subtitle.isDefault,
            isForced = subtitle.isForced,
        )
    }

    private suspend fun fetchToFile(request: VideoRequest, file: UniFile) {
        val headers = Headers.Builder().apply {
            request.headers.forEach { (key, value) -> add(key, value) }
        }.build()
        val response = networkHelper.client.newCall(
            Request.Builder()
                .url(request.url)
                .headers(headers)
                .build(),
        ).awaitSuccess()
        response.use {
            val body = it.body
            body.source().saveTo(file.openOutputStream())
        }
    }

    private fun selectStream(download: AnimeDownload, streams: List<VideoStream>): VideoStream? {
        val explicitKey = download.preferences.streamKey
        if (explicitKey != null) {
            return streams.firstOrNull { streamKey(it) == explicitKey }
        }

        return when (download.preferences.qualityMode) {
            AnimeDownloadQualityMode.BEST -> streams.maxByOrNull(::streamHeightScore)
            AnimeDownloadQualityMode.DATA_SAVING -> streams.minByOrNull(::streamHeightScore)
            AnimeDownloadQualityMode.BALANCED -> streams.minByOrNull { abs(streamHeightScore(it) - 720) }
        } ?: streams.firstOrNull()
    }

    private fun streamHeightScore(stream: VideoStream): Int {
        val label = stream.label
        val match = """(\d{3,4})p?""".toRegex(RegexOption.IGNORE_CASE).find(label)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun streamKey(stream: VideoStream): String {
        return stream.key.ifBlank { stream.label.ifBlank { stream.request.url } }
    }

    private fun subtitleKey(subtitle: VideoSubtitle): String {
        return subtitle.key.ifBlank {
            listOf(subtitle.label, subtitle.language, subtitle.request.url).joinToString("|")
        }
    }

    private fun inferExtension(url: String, mimeType: String?, fallback: String): String {
        val mimeExtension = when {
            mimeType?.contains("mp4", ignoreCase = true) == true -> "mp4"
            mimeType?.contains("webm", ignoreCase = true) == true -> "webm"
            mimeType?.contains("mpeg", ignoreCase = true) == true -> "mp4"
            mimeType?.contains("vtt", ignoreCase = true) == true -> "vtt"
            mimeType?.contains("srt", ignoreCase = true) == true -> "srt"
            mimeType?.contains("ass", ignoreCase = true) == true -> "ass"
            else -> null
        }
        if (mimeExtension != null) return mimeExtension

        val path = url.substringBefore('?').substringBefore('#')
        val urlExtension = path.substringAfterLast('.', missingDelimiterValue = "")
        return urlExtension.takeIf { it.isNotBlank() && it.length <= 5 } ?: fallback
    }

    private fun writeManifest(
        download: AnimeDownload,
        stream: VideoStream,
        videoFileName: String,
        subtitleFiles: List<DownloadedSubtitle>,
        tmpDir: UniFile,
    ) {
        val file = tmpDir.createFile(MANIFEST_FILE_NAME) ?: error("Failed to create anime manifest")
        val manifest = AnimeDownloadManifest(
            animeId = download.anime.id,
            episodeId = download.episode.id,
            animeTitle = download.anime.title,
            episodeTitle = download.episode.name,
            originalEpisodeUrl = download.episode.url,
            qualityMode = download.preferences.qualityMode.name,
            selection = download.selection,
            video = DownloadedVideo(
                fileName = videoFileName,
                sourceUrl = stream.request.url,
                headers = stream.request.headers,
                label = stream.label,
                mimeType = stream.mimeType,
            ),
            subtitles = subtitleFiles,
        )
        file.openOutputStream().use {
            it.write(json.encodeToString(manifest).toByteArray())
        }
    }

    companion object {
        const val MANIFEST_FILE_NAME = "anime_download.json"
    }
}

@Serializable
private data class AnimeDownloadManifest(
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
private data class DownloadedVideo(
    val fileName: String,
    val sourceUrl: String,
    val headers: Map<String, String>,
    val label: String,
    val mimeType: String?,
)

@Serializable
private data class DownloadedSubtitle(
    val key: String,
    val label: String,
    val language: String?,
    val mimeType: String?,
    val fileName: String,
    val isDefault: Boolean,
    val isForced: Boolean,
)
