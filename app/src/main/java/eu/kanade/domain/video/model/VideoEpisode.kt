package eu.kanade.domain.video.model

import eu.kanade.tachiyomi.source.model.SEpisode
import tachiyomi.domain.video.model.VideoEpisode

fun VideoEpisode.toSEpisode(): SEpisode = SEpisode.create().also {
    it.url = url
    it.name = name
    it.date_upload = dateUpload
    it.episode_number = episodeNumber.toFloat()
}
