package eu.kanade.tachiyomi.data.anime.download

import android.app.Application
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadManifest
import eu.kanade.domain.anime.model.toSEpisode
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadFailure
import eu.kanade.tachiyomi.data.anime.download.model.DownloadedSubtitle
import eu.kanade.tachiyomi.data.anime.download.model.DownloadedVideo
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.AnimeSubtitleSource
import eu.kanade.tachiyomi.source.model.VideoPlaybackOption
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoRequest
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import eu.kanade.tachiyomi.source.model.VideoSubtitle
import eu.kanade.tachiyomi.util.lang.Hash.md5
import eu.kanade.tachiyomi.util.storage.saveTo
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okio.buffer
import okio.sink
import tachiyomi.domain.anime.model.AnimeDownloadQualityMode
import tachiyomi.domain.source.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.IOException
import kotlin.math.abs

private val HEIGHT_REGEX = Regex("""(\d{3,4})p?""", RegexOption.IGNORE_CASE)

class AnimeDownloader(
    private val application: Application = Injekt.get(),
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

        var playbackData = runCatching {
            source.getPlaybackData(download.episode.toSEpisode(), requestedSelection)
        }.getOrElse {
            return AnimeDownloadFailure(
                reason = AnimeDownloadFailure.Reason.NETWORK,
                message = it.message,
            )
        }

        if (download.preferences.streamKey == null) {
            val selectedSourceQualityKey = selectSourceQualityForPreferences(
                qualityMode = download.preferences.qualityMode,
                sourceQualities = playbackData.sourceQualities,
            )
            if (
                selectedSourceQualityKey != null &&
                selectedSourceQualityKey != playbackData.selection.sourceQualityKey
            ) {
                playbackData = runCatching {
                    source.getPlaybackData(
                        download.episode.toSEpisode(),
                        playbackData.selection.copy(sourceQualityKey = selectedSourceQualityKey),
                    )
                }.getOrElse {
                    return AnimeDownloadFailure(
                        reason = AnimeDownloadFailure.Reason.NETWORK,
                        message = it.message,
                    )
                }
            }
        }

        if (selectedDubKey != null && playbackData.selection.dubKey != selectedDubKey) {
            return AnimeDownloadFailure(AnimeDownloadFailure.Reason.DUB_NOT_AVAILABLE)
        }

        val playableStreams = playbackData.streams
            .filter { it.request.url.isNotBlank() }
        if (playableStreams.isEmpty()) {
            return AnimeDownloadFailure(AnimeDownloadFailure.Reason.STREAM_NOT_AVAILABLE)
        }
        val progressiveStreams = playableStreams.filter { effectiveStreamType(it) == VideoStreamType.PROGRESSIVE }
        val streamCandidates = progressiveStreams.ifEmpty { playableStreams }

        val stream = selectStream(download, streamCandidates)
            ?: return AnimeDownloadFailure(AnimeDownloadFailure.Reason.STREAM_NOT_AVAILABLE)
        val streamType = effectiveStreamType(stream)
        if (streamType != VideoStreamType.PROGRESSIVE && streamType != VideoStreamType.HLS) {
            return AnimeDownloadFailure(AnimeDownloadFailure.Reason.UNSUPPORTED_STREAM)
        }

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
            download.stream = stream.copy(type = streamType)
            download.subtitles = subtitles

            download.status = AnimeDownload.State.DOWNLOADING
            download.progress = 0

            val videoFileName = downloadVideo(download, stream, tmpDir)
            download.progress = maxOf(download.progress, if (subtitles.isEmpty()) 90 else 80)

            val subtitleFiles = subtitles.mapIndexed { index, subtitle ->
                downloadSubtitle(index, subtitle, tmpDir)
            }
            download.progress = 95

            writeManifest(download, stream, videoFileName, subtitleFiles, tmpDir)

            if (!tmpDir.renameTo(episodeDirName)) {
                return AnimeDownloadFailure(AnimeDownloadFailure.Reason.UNKNOWN, "Failed to finalize episode package")
            }
            cache.addEpisode(episodeDirName, animeDir, download.anime)
            download.progress = 100
            download.status = AnimeDownload.State.DOWNLOADED
            return null
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            tmpDir.delete()
            return AnimeDownloadFailure(
                reason = when {
                    e is UnsupportedOperationException -> AnimeDownloadFailure.Reason.UNSUPPORTED_STREAM
                    e is IOException -> AnimeDownloadFailure.Reason.NETWORK
                    else -> AnimeDownloadFailure.Reason.UNKNOWN
                },
                message = e.message,
            )
        }
    }

    private suspend fun downloadVideo(download: AnimeDownload, stream: VideoStream, tmpDir: UniFile): String {
        val progressTracker = VideoDownloadProgressTracker(download)
        return when (effectiveStreamType(stream)) {
            VideoStreamType.PROGRESSIVE -> {
                val extension = inferExtension(stream.request.url, stream.mimeType, fallback = "mp4")
                val fileName = "video.$extension"
                val file = tmpDir.createFile(fileName) ?: error("Failed to create video file")
                fetchToFile(stream.request, file, progressTracker = progressTracker)
                progressTracker.onVideoTransferComplete()
                fileName
            }
            VideoStreamType.HLS -> {
                downloadHlsStream(stream, tmpDir, progressTracker)
            }
            else -> {
                throw UnsupportedOperationException("Unsupported stream type: ${stream.type}")
            }
        }
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

    private suspend fun fetchToFile(
        request: VideoRequest,
        file: UniFile,
        progressTracker: VideoDownloadProgressTracker? = null,
    ) {
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
            val contentLength = body.contentLength().takeIf { it > 0L }
            progressTracker?.onResponseStarted(contentLength)
            body.source().use { source ->
                file.openOutputStream().use { output ->
                    val sink = output.sink().buffer()
                    var totalRead = 0L
                    while (true) {
                        val read = source.read(sink.buffer, 8_192)
                        if (read == -1L) break
                        sink.emit()
                        totalRead += read
                        progressTracker?.onBytesCopied(read)
                    }
                    sink.flush()
                    progressTracker?.onResponseFinished(contentLength ?: totalRead)
                }
            }
        }
    }

    private suspend fun fetchText(request: VideoRequest): String {
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
            return it.body.string()
        }
    }

    private suspend fun downloadHlsStream(
        stream: VideoStream,
        tmpDir: UniFile,
        progressTracker: VideoDownloadProgressTracker,
    ): String {
        progressTracker.markAsHls()
        val downloaded = LinkedHashMap<String, String>()
        val stagingDir = File(application.cacheDir, "anime_hls_${md5(stream.request.url).take(8)}_${System.currentTimeMillis()}")
        stagingDir.mkdirs()
        return try {
            val rootFileName = downloadHlsPlaylist(
                stream.request,
                stagingDir,
                downloaded,
                progressTracker,
                rootFileName = "video.m3u8",
            )
            progressTracker.onVideoTransferComplete()
            var resolvedRootFileName = rootFileName
            val stagedFiles = stagingDir.listFiles().orEmpty()
            if (stagedFiles.isNotEmpty()) {
                val copyStart = 90
                val copyEnd = 94
                stagedFiles.forEachIndexed { index, stagedFile ->
                    val copyFraction = (index + 1).toFloat() / stagedFiles.size.toFloat()
                    val targetProgress = copyStart + ((copyEnd - copyStart) * copyFraction).toInt()
                    progressTracker.onNonVideoPhaseProgress(targetProgress)
                    val target = tmpDir.createFile(stagedFile.name) ?: error("Failed to create staged HLS file")
                    stagedFile.inputStream().use { input ->
                        target.openOutputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (stagedFile.name == rootFileName) {
                        resolvedRootFileName = target.name ?: rootFileName
                    }
                }
            }
            resolvedRootFileName
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private suspend fun downloadHlsPlaylist(
        request: VideoRequest,
        stagingDir: File,
        downloaded: MutableMap<String, String>,
        progressTracker: VideoDownloadProgressTracker,
        rootFileName: String? = null,
    ): String {
        downloaded[request.url]?.let { return it }

        val baseUrl = request.url.toHttpUrlOrNull() ?: error("Invalid HLS url: ${request.url}")
        val playlistText = fetchText(request)
        val fileName = rootFileName ?: uniqueLocalName(request.url, fallback = "playlist.m3u8")
        downloaded[request.url] = fileName

        var assetsInPlaylist = 0
        var previousTagLine: String? = null
        for (line in playlistText.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> continue
                !trimmed.startsWith("#") -> {
                    val absoluteUrl = baseUrl.resolve(trimmed)?.toString() ?: trimmed
                    if (!isHlsPlaylistReference(absoluteUrl, previousTagLine = previousTagLine)) {
                        assetsInPlaylist++
                    }
                }
                "URI=\"" in trimmed -> {
                    val match = URI_ATTRIBUTE_REGEX.find(line)
                    if (match != null) {
                        val absoluteUrl = baseUrl.resolve(match.groupValues[1])?.toString() ?: match.groupValues[1]
                        if (!isHlsPlaylistReference(absoluteUrl, currentTagLine = trimmed)) {
                            assetsInPlaylist++
                        }
                    }
                }
            }
            if (trimmed.startsWith("#")) {
                previousTagLine = trimmed
            }
        }
        progressTracker.onAssetsFound(assetsInPlaylist)

        previousTagLine = null
        val rewrittenLines = buildList {
            for (line in playlistText.lineSequence()) {
                val trimmed = line.trim()
                val rewrittenLine = when {
                    trimmed.isBlank() -> line
                    !trimmed.startsWith("#") -> {
                        val absoluteUrl = baseUrl.resolve(trimmed)?.toString() ?: trimmed
                        if (isHlsPlaylistReference(absoluteUrl, previousTagLine = previousTagLine)) {
                            downloadHlsPlaylist(
                                request = VideoRequest(url = absoluteUrl, headers = request.headers),
                                stagingDir = stagingDir,
                                downloaded = downloaded,
                                progressTracker = progressTracker,
                            )
                        } else {
                            downloadHlsAsset(absoluteUrl, request.headers, stagingDir, downloaded, progressTracker)
                        }
                    }
                    "URI=\"" in trimmed -> {
                        val match = URI_ATTRIBUTE_REGEX.find(line)
                        if (match != null) {
                            val absoluteUrl = baseUrl.resolve(match.groupValues[1])?.toString() ?: match.groupValues[1]
                            val localName = if (isHlsPlaylistReference(absoluteUrl, currentTagLine = trimmed)) {
                                downloadHlsPlaylist(
                                    request = VideoRequest(url = absoluteUrl, headers = request.headers),
                                    stagingDir = stagingDir,
                                    downloaded = downloaded,
                                    progressTracker = progressTracker,
                                )
                            } else {
                                downloadHlsAsset(absoluteUrl, request.headers, stagingDir, downloaded, progressTracker)
                            }
                            line.replace(match.value, "URI=\"$localName\"")
                        } else {
                            line
                        }
                    }
                    else -> line
                }
                add(rewrittenLine)
                if (trimmed.startsWith("#")) {
                    previousTagLine = trimmed
                }
            }
        }
        val rewritten = rewrittenLines.joinToString("\n")

        val file = File(stagingDir, fileName)
        file.outputStream().bufferedWriter().use { writer ->
            writer.write(rewritten)
        }
        return fileName
    }

    private suspend fun downloadHlsAsset(
        url: String,
        headers: Map<String, String>,
        stagingDir: File,
        downloaded: MutableMap<String, String>,
        progressTracker: VideoDownloadProgressTracker,
    ): String {
        downloaded[url]?.let { 
            progressTracker.onAssetDownloaded()
            return it 
        }
        val fileName = uniqueLocalName(url)
        val file = File(stagingDir, fileName)
        if (url.isHlsVideoAssetUrl()) {
            fetchToFile(VideoRequest(url = url, headers = headers), file, progressTracker = progressTracker)
        } else {
            fetchToFile(VideoRequest(url = url, headers = headers), file)
        }
        downloaded[url] = fileName
        progressTracker.onAssetDownloaded()
        return fileName
    }

    private suspend fun fetchToFile(
        request: VideoRequest,
        file: File,
        progressTracker: VideoDownloadProgressTracker? = null,
    ) {
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
            val contentLength = body.contentLength().takeIf { it > 0L }
            progressTracker?.onResponseStarted(contentLength)
            file.outputStream().use { output ->
                val sink = output.sink().buffer()
                body.source().use { source ->
                    var totalRead = 0L
                    while (true) {
                        val read = source.read(sink.buffer, 8_192)
                        if (read == -1L) break
                        sink.emit()
                        totalRead += read
                        progressTracker?.onBytesCopied(read)
                    }
                    sink.flush()
                    progressTracker?.onResponseFinished(contentLength ?: totalRead)
                }
            }
        }
    }

    private fun uniqueLocalName(url: String, fallback: String = inferExtension(url, mimeType = null, fallback = "bin")):
        String {
        val path = url.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val baseName = path.ifBlank { fallback }
        val sanitized = baseName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return "${md5(url).take(8)}_$sanitized"
    }

    private fun selectStream(download: AnimeDownload, streams: List<VideoStream>): VideoStream? {
        return selectStreamForPreferences(download.preferences.streamKey, download.preferences.qualityMode, streams)
    }

    private fun streamHeightScore(stream: VideoStream): Int {
        return scoreStreamHeight(stream)
    }

    private fun streamKey(stream: VideoStream): String {
        return streamChoiceKey(stream)
    }

    private fun subtitleKey(subtitle: VideoSubtitle): String {
        return subtitleChoiceKey(subtitle)
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
                streamType = effectiveStreamType(stream),
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
        private val URI_ATTRIBUTE_REGEX = Regex("URI=\"([^\"]+)\"")
    }
}

internal fun selectStreamForPreferences(
    explicitKey: String?,
    qualityMode: AnimeDownloadQualityMode,
    streams: List<VideoStream>,
): VideoStream? {
    if (explicitKey != null) {
        return streams.firstOrNull { streamChoiceKey(it) == explicitKey }
    }

    return when (qualityMode) {
        AnimeDownloadQualityMode.BEST -> streams.maxByOrNull(::scoreStreamHeight)
        AnimeDownloadQualityMode.DATA_SAVING -> streams.minByOrNull(::scoreStreamHeight)
        AnimeDownloadQualityMode.BALANCED -> streams.minByOrNull { abs(scoreStreamHeight(it) - 720) }
    } ?: streams.firstOrNull()
}

internal fun selectSourceQualityForPreferences(
    qualityMode: AnimeDownloadQualityMode,
    sourceQualities: List<VideoPlaybackOption>,
): String? {
    if (sourceQualities.isEmpty()) return null

    return when (qualityMode) {
        AnimeDownloadQualityMode.BEST -> sourceQualities.maxByOrNull(::scorePlaybackOptionHeight)
        AnimeDownloadQualityMode.DATA_SAVING -> sourceQualities.minByOrNull(::scorePlaybackOptionHeight)
        AnimeDownloadQualityMode.BALANCED -> sourceQualities.minByOrNull { abs(scorePlaybackOptionHeight(it) - 720) }
    }?.key ?: sourceQualities.firstOrNull()?.key
}

internal fun scoreStreamHeight(stream: VideoStream): Int {
    val match = HEIGHT_REGEX.find(stream.label)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

internal fun scorePlaybackOptionHeight(option: VideoPlaybackOption): Int {
    val match = HEIGHT_REGEX.find(option.label)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}

internal fun streamChoiceKey(stream: VideoStream): String {
    return stream.key.ifBlank { stream.label.ifBlank { stream.request.url } }
}

internal fun subtitleChoiceKey(subtitle: VideoSubtitle): String {
    return subtitle.key.ifBlank {
        listOf(subtitle.label, subtitle.language, subtitle.request.url).joinToString("|")
    }
}

internal fun effectiveStreamType(stream: VideoStream): VideoStreamType {
    if (stream.type != VideoStreamType.UNKNOWN) return stream.type

    val url = stream.request.url.substringBefore('?').substringBefore('#')
    val mime = stream.mimeType.orEmpty()
    return when {
        url.endsWith(".m3u8", ignoreCase = true) -> VideoStreamType.HLS
        url.endsWith(".mpd", ignoreCase = true) -> VideoStreamType.DASH
        mime.contains("mpegurl", ignoreCase = true) || mime.contains("m3u8", ignoreCase = true) -> VideoStreamType.HLS
        mime.contains("dash", ignoreCase = true) || mime.contains("mpd", ignoreCase = true) -> VideoStreamType.DASH
        mime.contains("mp4", ignoreCase = true) || mime.contains("webm", ignoreCase = true) -> VideoStreamType.PROGRESSIVE
        else -> VideoStreamType.UNKNOWN
    }
}

private fun String.isHlsPlaylistUrl(): Boolean {
    return substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
}

internal fun isHlsPlaylistReference(
    url: String,
    previousTagLine: String? = null,
    currentTagLine: String? = null,
): Boolean {
    if (url.isHlsPlaylistUrl()) return true

    return when {
        previousTagLine?.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) == true -> true
        currentTagLine?.startsWith("#EXT-X-MEDIA", ignoreCase = true) == true -> true
        currentTagLine?.startsWith("#EXT-X-I-FRAME-STREAM-INF", ignoreCase = true) == true -> true
        currentTagLine?.startsWith("#EXT-X-IMAGE-STREAM-INF", ignoreCase = true) == true -> true
        else -> false
    }
}

private class VideoDownloadProgressTracker(
    private val download: AnimeDownload,
) {
    private var totalBytesExpected = 0L
    private var downloadedBytes = 0L
    private var transferCompleted = false
    private var isHls = false
    private var totalAssetsExpected = 0
    private var downloadedAssets = 0

    fun markAsHls() {
        isHls = true
    }

    fun onAssetsFound(count: Int) {
        totalAssetsExpected += count
        update()
    }

    fun onAssetDownloaded() {
        downloadedAssets++
        update()
    }

    fun onResponseStarted(contentLength: Long?) {
        if (isHls) return
        if (contentLength != null) {
            totalBytesExpected += contentLength
            update()
        }
    }

    fun onBytesCopied(read: Long) {
        if (isHls) return
        downloadedBytes += read
        update()
    }

    fun onResponseFinished(contentLengthOrActual: Long) {
        if (isHls) return
        if (totalBytesExpected < downloadedBytes) {
            totalBytesExpected = downloadedBytes
        }
        if (totalBytesExpected == 0L) {
            totalBytesExpected = contentLengthOrActual
        }
        update()
    }

    fun onVideoTransferComplete() {
        transferCompleted = true
        download.progress = maxOf(download.progress, 90)
    }

    fun onNonVideoPhaseProgress(progress: Int) {
        transferCompleted = true
        download.progress = maxOf(download.progress, progress)
    }

    private fun update() {
        if (transferCompleted) return
        val fraction = if (isHls) {
            if (totalAssetsExpected <= 0) 0.0 else downloadedAssets.toDouble() / totalAssetsExpected.toDouble()
        } else {
            if (totalBytesExpected <= 0L) return
            downloadedBytes.toDouble() / totalBytesExpected.toDouble()
        }
        val next = (fraction.coerceIn(0.0, 1.0) * 90.0).toInt().coerceIn(0, 90)
        download.progress = maxOf(download.progress, next)
    }
}

private fun String.isHlsVideoAssetUrl(): Boolean {
    val path = substringBefore('?').substringBefore('#')
    return path.endsWith(".ts", ignoreCase = true) ||
        path.endsWith(".m4s", ignoreCase = true) ||
        path.endsWith(".mp4", ignoreCase = true) ||
        path.endsWith(".m4v", ignoreCase = true) ||
        path.endsWith(".aac", ignoreCase = true)
}
