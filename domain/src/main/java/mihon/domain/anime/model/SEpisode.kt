package mihon.domain.anime.model

import eu.kanade.tachiyomi.source.model.SEpisode
import tachiyomi.domain.anime.model.AnimeEpisode

fun SEpisode.toDomainEpisode(
    animeId: Long,
    sourceOrder: Long,
    dateFetch: Long,
): AnimeEpisode {
    return AnimeEpisode.create().copy(
        animeId = animeId,
        url = url,
        name = name.ifBlank { url },
        dateFetch = dateFetch,
        sourceOrder = sourceOrder,
        dateUpload = date_upload,
        episodeNumber = episode_number.toDouble(),
    )
}

fun AnimeEpisode.copyFrom(sourceEpisode: SEpisode, sourceOrder: Long): AnimeEpisode {
    return copy(
        url = sourceEpisode.url,
        name = sourceEpisode.name.ifBlank { sourceEpisode.url },
        dateUpload = sourceEpisode.date_upload,
        episodeNumber = sourceEpisode.episode_number.toDouble(),
        sourceOrder = sourceOrder,
    )
}
