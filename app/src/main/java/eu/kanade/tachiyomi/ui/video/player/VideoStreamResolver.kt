package eu.kanade.tachiyomi.ui.video.player

fun interface VideoStreamResolver {
    suspend operator fun invoke(videoId: Long, episodeId: Long): ResolveVideoStream.Result
}
