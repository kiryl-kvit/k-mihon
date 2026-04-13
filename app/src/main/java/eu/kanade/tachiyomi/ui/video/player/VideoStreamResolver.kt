package eu.kanade.tachiyomi.ui.video.player

fun interface VideoStreamResolver {
    suspend operator fun invoke(animeId: Long, episodeId: Long): ResolveVideoStream.Result
}
