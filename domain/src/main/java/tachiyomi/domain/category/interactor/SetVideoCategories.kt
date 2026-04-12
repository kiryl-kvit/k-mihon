package tachiyomi.domain.category.interactor

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.video.repository.VideoRepository

class SetVideoCategories(
    private val videoRepository: VideoRepository,
) {

    suspend fun await(videoId: Long, categoryIds: List<Long>) {
        try {
            videoRepository.setVideoCategories(videoId, categoryIds)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
        }
    }
}
