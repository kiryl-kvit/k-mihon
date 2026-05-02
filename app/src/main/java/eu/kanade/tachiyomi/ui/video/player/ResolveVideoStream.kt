package eu.kanade.tachiyomi.ui.video.player

import android.app.Application
import eu.kanade.domain.anime.model.toSEpisode
import eu.kanade.tachiyomi.data.anime.download.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.anime.download.AnimeDownloader
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownloadManifest
import eu.kanade.tachiyomi.source.AnimeSubtitleSource
import eu.kanade.tachiyomi.source.model.VideoPlaybackData
import eu.kanade.tachiyomi.source.model.VideoPlaybackSelection
import eu.kanade.tachiyomi.source.model.VideoRequest
import eu.kanade.tachiyomi.source.model.VideoStream
import eu.kanade.tachiyomi.source.model.VideoStreamType
import eu.kanade.tachiyomi.source.model.VideoSubtitle
import eu.kanade.tachiyomi.util.system.isOnline
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimePlaybackPreferences
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.PlayerQualityMode
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimePlaybackPreferencesRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import tachiyomi.domain.source.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ResolveVideoStream(
    private val videoRepository: AnimeRepository = Injekt.get(),
    private val videoEpisodeRepository: AnimeEpisodeRepository = Injekt.get(),
    private val animePlaybackPreferencesRepository: AnimePlaybackPreferencesRepository = Injekt.get(),
    private val videoSourceManager: AnimeSourceManager = Injekt.get(),
    private val animeDownloadProvider: AnimeDownloadProvider = Injekt.get(),
    private val json: Json = Injekt.get(),
    private val context: Application = Injekt.get(),
    private val sourceInitTimeoutMs: Long = SOURCE_INIT_TIMEOUT_MS,
    private val streamFetchTimeoutMs: Long = STREAM_FETCH_TIMEOUT_MS,
) : VideoStreamResolver {

    override suspend operator fun invoke(
        animeId: Long,
        episodeId: Long,
        ownerAnimeId: Long,
        selection: VideoPlaybackSelection?,
    ): Result {
        val visibleAnime = runCatching { videoRepository.getAnimeById(animeId) }
            .getOrElse { return Result.Error(Reason.VideoNotFound) }
        val ownerAnime = runCatching { videoRepository.getAnimeById(ownerAnimeId) }
            .getOrElse { return Result.Error(Reason.VideoNotFound) }
        val episode = videoEpisodeRepository.getEpisodeById(episodeId)
            ?: return Result.Error(Reason.EpisodeNotFound)

        if (episode.animeId != ownerAnime.id) {
            return Result.Error(Reason.EpisodeMismatch)
        }

        // Try offline playback FIRST, before source initialization or any network calls.
        // This ensures downloaded episodes are playable even when completely offline,
        // mirroring how downloaded manga chapters work without requiring connectivity.
        val offlineResult = tryOfflinePlayback(visibleAnime, ownerAnime, episode)
        if (offlineResult != null) return offlineResult

        // No downloaded episode found — if offline, fail with a clear message.
        if (!context.isOnline()) {
            return Result.Error(Reason.OfflineNoDownload)
        }

        // Online path: wait for full source initialization before network requests.
        val initialized = withTimeoutOrNull(sourceInitTimeoutMs) {
            videoSourceManager.isInitialized.filter { it }.first()
        }
        if (initialized != true) {
            return Result.Error(Reason.SourceLoadTimeout)
        }

        val source = videoSourceManager.get(ownerAnime.source)
            ?: return Result.Error(Reason.SourceNotFound)

        val savedPreferences = animePlaybackPreferencesRepository.getByAnimeId(ownerAnime.id)
            ?: defaultPlaybackPreferences(ownerAnime.id)
        val requestedSelection = selection ?: VideoPlaybackSelection(
            dubKey = savedPreferences.dubKey,
            streamKey = savedPreferences.streamKey,
            sourceQualityKey = savedPreferences.sourceQualityKey,
        )
        val sourceEpisode = episode.toSEpisode()

        val playbackData = runCatching {
            withTimeoutOrNull(streamFetchTimeoutMs) {
                source.getPlaybackData(sourceEpisode, requestedSelection)
            } ?: return Result.Error(Reason.StreamFetchTimeout)
        }.getOrElse {
            return Result.Error(Reason.StreamFetchFailed(it))
        }

        val subtitles = runCatching {
            val subtitleSource = source as? AnimeSubtitleSource ?: return@runCatching emptyList()
            withTimeoutOrNull(streamFetchTimeoutMs) {
                subtitleSource.getSubtitles(sourceEpisode, playbackData.selection)
            } ?: emptyList()
        }.getOrElse {
            emptyList()
        }

        val validStreams = playbackData.streams.filter { it.request.url.isNotBlank() }
        val stream = validStreams
            .firstOrNull { streamChoiceKey(it) == requestedSelection.streamKey }
            ?: validStreams.maxByOrNull(::streamScore)
            ?: return Result.Error(Reason.NoStreams)

        val resolvedPlaybackData = playbackData.copy(
            selection = playbackData.selection.copy(
                streamKey = streamChoiceKey(stream),
            ),
        )

        return Result.Success(
            visibleAnime = visibleAnime,
            ownerAnime = ownerAnime,
            episode = episode,
            playbackData = resolvedPlaybackData,
            stream = stream,
            subtitles = subtitles.filter { it.request.url.isNotBlank() },
            savedPreferences = savedPreferences,
        )
    }

    sealed interface Result {
        data class Success(
            val visibleAnime: AnimeTitle,
            val ownerAnime: AnimeTitle,
            val episode: AnimeEpisode,
            val playbackData: VideoPlaybackData,
            val stream: VideoStream,
            val subtitles: List<VideoSubtitle>,
            val savedPreferences: AnimePlaybackPreferences,
        ) : Result

        data class Error(val reason: Reason) : Result
    }

    sealed interface Reason {
        data object VideoNotFound : Reason

        data object EpisodeNotFound : Reason

        data object EpisodeMismatch : Reason

        data object SourceLoadTimeout : Reason

        data object SourceNotFound : Reason

        data object NoStreams : Reason

        data object StreamFetchTimeout : Reason

        data object OfflineNoDownload : Reason

        data class StreamFetchFailed(val cause: Throwable) : Reason
    }

    private fun streamScore(stream: VideoStream): Int {
        val typeScore = when (stream.type) {
            VideoStreamType.HLS -> 400
            VideoStreamType.PROGRESSIVE -> 300
            VideoStreamType.DASH -> 200
            VideoStreamType.UNKNOWN -> 100
        }
        val mimeScore = when {
            stream.mimeType?.contains("mp4", ignoreCase = true) == true -> 30
            stream.mimeType?.contains("mpegurl", ignoreCase = true) == true -> 20
            stream.mimeType?.contains("dash", ignoreCase = true) == true -> 10
            else -> 0
        }
        val headerScore = if (stream.request.headers.isNotEmpty()) 5 else 0
        val labelScore = if (stream.label.isNotBlank()) 1 else 0
        return typeScore + mimeScore + headerScore + labelScore
    }

    private fun streamChoiceKey(stream: VideoStream): String {
        return stream.key.ifBlank {
            stream.label.ifBlank { stream.request.url }
        }
    }

    /**
     * Attempts to find and return a downloaded episode for offline playback.
     * Tries multiple source candidates (visible anime source, owner anime source)
     * without requiring the source manager to be fully initialized.
     * This is the key to making downloaded episodes work offline.
     */
    private fun tryOfflinePlayback(
        visibleAnime: AnimeTitle,
        ownerAnime: AnimeTitle,
        episode: AnimeEpisode,
    ): Result.Success? {
        // Collect distinct source candidates — the sources may already be available
        // from installed extension APKs even before full source manager initialization.
        val sourceCandidates = listOfNotNull(
            videoSourceManager.get(visibleAnime.source),
            videoSourceManager.get(ownerAnime.source),
        ).distinctBy { it.id }

        for (source in sourceCandidates) {
            val offline = readOfflineManifest(
                anime = visibleAnime,
                episode = episode,
                source = source,
            ) ?: continue

            return Result.Success(
                visibleAnime = visibleAnime,
                ownerAnime = ownerAnime,
                episode = episode,
                playbackData = offline.playbackData,
                stream = offline.stream,
                subtitles = offline.subtitles,
                savedPreferences = defaultPlaybackPreferences(ownerAnime.id),
            )
        }
        return null
    }

    private fun readOfflineManifest(
        anime: AnimeTitle,
        episode: AnimeEpisode,
        source: eu.kanade.tachiyomi.source.AnimeSource,
    ): OfflinePlaybackData? {
        val episodeDir = animeDownloadProvider.findEpisodeDir(
            episodeName = episode.name,
            episodeUrl = episode.url,
            animeTitle = anime.title,
            source = source,
        ) ?: return null

        val manifestFile = episodeDir.findFile(AnimeDownloader.MANIFEST_FILE_NAME)
        val manifest = runCatching {
            manifestFile?.openInputStream()?.bufferedReader()?.use { reader ->
                json.decodeFromString<AnimeDownloadManifest>(reader.readText())
            }
        }.getOrNull()

        if (manifest != null) {
            val videoFile = resolveDownloadedVideoFile(episodeDir, manifest) ?: return null

            val stream = VideoStream(
                request = VideoRequest(
                    url = videoFile.uri.toString(),
                    headers = emptyMap(),
                ),
                label = manifest.video.label,
                type = manifest.video.streamType,
                mimeType = manifest.video.mimeType,
                key = manifest.selection.streamKey.orEmpty(),
            )
            val subtitles = manifest.subtitles.mapNotNull { subtitle ->
                val subtitleFile = episodeDir.findFile(subtitle.fileName) ?: return@mapNotNull null
                VideoSubtitle(
                    request = VideoRequest(
                        url = subtitleFile.uri.toString(),
                        headers = emptyMap(),
                    ),
                    label = subtitle.label,
                    language = subtitle.language,
                    mimeType = subtitle.mimeType,
                    key = subtitle.key,
                    isDefault = subtitle.isDefault,
                    isForced = subtitle.isForced,
                )
            }
            val playbackData = VideoPlaybackData(
                selection = manifest.selection,
                streams = listOf(stream),
            )
            return OfflinePlaybackData(playbackData, stream, subtitles)
        }

        // Fallback for older downloads without a valid manifest
        val videoFile = episodeDir.listFiles()?.firstOrNull {
            it.name?.endsWith(".mp4") == true ||
                it.name?.endsWith(".mkv") == true ||
                it.name?.endsWith(".m3u8") == true ||
                it.name?.endsWith(".m3u") == true
        } ?: return null

        val stream = VideoStream(
            request = VideoRequest(
                url = videoFile.uri.toString(),
                headers = emptyMap(),
            ),
            label = "Downloaded",
            type = if (videoFile.name?.endsWith(".m3u8") == true) VideoStreamType.HLS else VideoStreamType.PROGRESSIVE,
            mimeType = null,
            key = "downloaded",
        )
        val playbackData = VideoPlaybackData(
            selection = VideoPlaybackSelection(streamKey = "downloaded"),
            streams = listOf(stream),
        )
        return OfflinePlaybackData(playbackData, stream, emptyList())
    }

    private fun defaultPlaybackPreferences(animeId: Long): AnimePlaybackPreferences {
        return AnimePlaybackPreferences(
            animeId = animeId,
            dubKey = null,
            streamKey = null,
            sourceQualityKey = null,
            playerQualityMode = PlayerQualityMode.AUTO,
            playerQualityHeight = null,
            subtitleOffsetX = null,
            subtitleOffsetY = null,
            subtitleTextSize = null,
            subtitleTextColor = null,
            subtitleBackgroundColor = null,
            subtitleBackgroundOpacity = null,
            updatedAt = 0L,
        )
    }

    private fun resolveDownloadedVideoFile(
        episodeDir: com.hippo.unifile.UniFile,
        manifest: AnimeDownloadManifest,
    ): com.hippo.unifile.UniFile? {
        episodeDir.findFile(manifest.video.fileName)?.let { return it }

        if (manifest.video.streamType != VideoStreamType.HLS) {
            return null
        }

        val expectedBaseName = manifest.video.fileName.substringBeforeLast('.')
        return episodeDir.listFiles()?.firstOrNull { file ->
            val name = file.name ?: return@firstOrNull false
            name == "$expectedBaseName.m3u" ||
                name == "$expectedBaseName.m3u8" ||
                name.endsWith(".m3u8") ||
                name.endsWith(".m3u")
        }
    }

    private companion object {
        private const val SOURCE_INIT_TIMEOUT_MS = 5_000L
        private const val STREAM_FETCH_TIMEOUT_MS = 15_000L
    }
}

private data class OfflinePlaybackData(
    val playbackData: VideoPlaybackData,
    val stream: VideoStream,
    val subtitles: List<VideoSubtitle>,
)
