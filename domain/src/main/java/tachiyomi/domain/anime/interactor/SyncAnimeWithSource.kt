package tachiyomi.domain.anime.interactor

import mihon.domain.anime.model.copyFrom
import mihon.domain.anime.model.toDomainEpisode
import mihon.domain.anime.model.toSAnime
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.domain.source.service.AnimeSourceManager
import tachiyomi.domain.anime.model.AnimeEpisode
import tachiyomi.domain.anime.model.AnimeEpisodeUpdate
import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.model.AnimeTitleUpdate
import tachiyomi.domain.anime.repository.AnimeEpisodeRepository
import tachiyomi.domain.anime.repository.AnimeRepository
import java.time.Instant

class SyncAnimeWithSource(
    private val animeRepository: AnimeRepository,
    private val animeEpisodeRepository: AnimeEpisodeRepository,
    private val animeSourceManager: AnimeSourceManager,
) {

    suspend operator fun invoke(anime: AnimeTitle) {
        val source = animeSourceManager.get(anime.source) ?: throw SourceNotInstalledException()
        val networkAnime = source.getAnimeDetails(anime.toSAnime())

        val animeUpdate = AnimeTitleUpdate(
            id = anime.id,
            title = networkAnime.title.takeIf { it.isNotBlank() && it != anime.title },
            description = networkAnime.description.takeIf { !it.isNullOrBlank() && it != anime.description },
            genre = networkAnime.getGenres().takeIf { !it.isNullOrEmpty() && it != anime.genre },
            thumbnailUrl = networkAnime.thumbnail_url.takeIf { !it.isNullOrBlank() && it != anime.thumbnailUrl },
            initialized = true.takeIf { !anime.initialized },
        )
        if (animeUpdate != AnimeTitleUpdate(id = anime.id) && !animeRepository.update(animeUpdate)) {
            error("Failed to update anime ${anime.id}")
        }

        val existingEpisodes = animeEpisodeRepository.getEpisodesByAnimeId(anime.id)
            .associateBy { it.url }
        val now = Instant.now().toEpochMilli()
        val episodesToInsert = mutableListOf<AnimeEpisode>()
        val episodesToUpdate = mutableListOf<AnimeEpisodeUpdate>()

        source.getEpisodeList(networkAnime)
            .distinctBy { it.url }
            .forEachIndexed { index, sourceEpisode ->
                val sourceOrder = index.toLong()
                val existingEpisode = existingEpisodes[sourceEpisode.url]
                if (existingEpisode == null) {
                    episodesToInsert += sourceEpisode.toDomainEpisode(
                        animeId = anime.id,
                        sourceOrder = sourceOrder,
                        dateFetch = now,
                    )
                    return@forEachIndexed
                }

                val updatedEpisode = existingEpisode.copyFrom(sourceEpisode, sourceOrder)
                val episodeUpdate = AnimeEpisodeUpdate(
                    id = existingEpisode.id,
                    name = updatedEpisode.name.takeIf { it != existingEpisode.name },
                    dateUpload = updatedEpisode.dateUpload.takeIf { it != existingEpisode.dateUpload },
                    episodeNumber = updatedEpisode.episodeNumber.takeIf { it != existingEpisode.episodeNumber },
                    sourceOrder = updatedEpisode.sourceOrder.takeIf { it != existingEpisode.sourceOrder },
                )
                if (episodeUpdate != AnimeEpisodeUpdate(id = existingEpisode.id)) {
                    episodesToUpdate += episodeUpdate
                }
            }

        if (episodesToInsert.isNotEmpty()) {
            animeEpisodeRepository.addAll(episodesToInsert)
        }
        if (episodesToUpdate.isNotEmpty()) {
            animeEpisodeRepository.updateAll(episodesToUpdate)
        }
    }
}
