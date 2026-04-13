package tachiyomi.domain.anime.interactor

import tachiyomi.domain.anime.model.AnimeTitle
import tachiyomi.domain.anime.repository.AnimeRepository

class NetworkToLocalAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend operator fun invoke(anime: AnimeTitle): AnimeTitle {
        return invoke(listOf(anime)).single()
    }

    suspend operator fun invoke(animes: List<AnimeTitle>): List<AnimeTitle> {
        return animeRepository.insertNetworkAnime(animes)
    }
}
