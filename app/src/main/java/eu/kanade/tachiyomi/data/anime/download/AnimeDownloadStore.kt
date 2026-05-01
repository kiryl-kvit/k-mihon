package eu.kanade.tachiyomi.data.anime.download

import android.content.Context
import androidx.core.content.edit
import eu.kanade.tachiyomi.data.anime.download.model.AnimeDownload
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tachiyomi.domain.anime.model.AnimeDownloadPreferences
import tachiyomi.domain.anime.model.AnimeDownloadQualityMode
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadStore(
    context: Context,
    private val json: Json = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val animeEpisodeRepository: AnimeEpisodeRepository = Injekt.get(),
) {

    private val preferences = context.getSharedPreferences("active_anime_downloads", Context.MODE_PRIVATE)

    private var counter = 0

    fun addAll(downloads: List<AnimeDownload>) {
        preferences.edit {
            downloads.forEach { putString(getKey(it), serialize(it)) }
        }
    }

    fun remove(download: AnimeDownload) {
        preferences.edit {
            remove(getKey(download))
        }
    }

    fun removeAll(downloads: List<AnimeDownload>) {
        preferences.edit {
            downloads.forEach { remove(getKey(it)) }
        }
    }

    fun clear() {
        preferences.edit {
            clear()
        }
    }

    suspend fun restore(): List<AnimeDownload> {
        val objs = preferences.all
            .mapNotNull { it.value as? String }
            .mapNotNull(::deserialize)
            .sortedBy { it.order }

        val downloads = objs.mapNotNull { obj ->
            val anime = runCatching { animeRepository.getAnimeById(obj.animeId) }.getOrNull() ?: return@mapNotNull null
            val episode = animeEpisodeRepository.getEpisodeById(obj.episodeId) ?: return@mapNotNull null
            AnimeDownload(
                anime = anime,
                episode = episode,
                preferences = AnimeDownloadPreferences(
                    animeId = obj.animeId,
                    dubKey = obj.dubKey,
                    streamKey = obj.streamKey,
                    subtitleKey = obj.subtitleKey,
                    qualityMode = obj.qualityMode.fromDatabaseValue(),
                    updatedAt = obj.updatedAt,
                ),
            )
        }

        clear()
        return downloads
    }

    private fun getKey(download: AnimeDownload): String {
        return download.episode.id.toString()
    }

    private fun serialize(download: AnimeDownload): String {
        val obj = AnimeDownloadObject(
            animeId = download.anime.id,
            episodeId = download.episode.id,
            dubKey = download.preferences.dubKey,
            streamKey = download.preferences.streamKey,
            subtitleKey = download.preferences.subtitleKey,
            qualityMode = download.preferences.qualityMode.toDatabaseValue(),
            updatedAt = download.preferences.updatedAt,
            order = counter++,
        )
        return json.encodeToString(obj)
    }

    private fun deserialize(string: String): AnimeDownloadObject? {
        return runCatching {
            json.decodeFromString<AnimeDownloadObject>(string)
        }.getOrNull()
    }
}

@Serializable
private data class AnimeDownloadObject(
    val animeId: Long,
    val episodeId: Long,
    val dubKey: String?,
    val streamKey: String?,
    val subtitleKey: String?,
    val qualityMode: String,
    val updatedAt: Long,
    val order: Int,
)

private fun AnimeDownloadQualityMode.toDatabaseValue(): String {
    return when (this) {
        AnimeDownloadQualityMode.BEST -> "best"
        AnimeDownloadQualityMode.BALANCED -> "balanced"
        AnimeDownloadQualityMode.DATA_SAVING -> "data_saving"
    }
}

private fun String.fromDatabaseValue(): AnimeDownloadQualityMode {
    return when (this) {
        "best" -> AnimeDownloadQualityMode.BEST
        "data_saving" -> AnimeDownloadQualityMode.DATA_SAVING
        else -> AnimeDownloadQualityMode.BALANCED
    }
}
