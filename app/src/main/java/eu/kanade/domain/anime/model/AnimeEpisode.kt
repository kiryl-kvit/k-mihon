package eu.kanade.domain.anime.model

import eu.kanade.tachiyomi.source.model.SEpisode
import tachiyomi.domain.anime.model.AnimeEpisode

fun AnimeEpisode.toSEpisode(): SEpisode = SEpisode.create().also {
    it.url = url
    it.name = name
    it.date_upload = dateUpload
    it.episode_number = episodeNumber.toFloat()
}
